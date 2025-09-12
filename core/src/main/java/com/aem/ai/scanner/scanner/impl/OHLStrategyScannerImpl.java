package com.aem.ai.scanner.scanner.impl;


import com.aem.ai.scanner.model.Candle;
import com.aem.ai.scanner.model.Signal;
import com.aem.ai.scanner.scanner.OHLStrategyScanner;
import com.aem.ai.scanner.services.TelegramService;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.rules.*;

import java.time.*;
import java.util.*;

import static com.aem.ai.scanner.utils.Utils.mapTimeframe;

/**
 * OSGi Service for Open=High/Low (OHL) strategy with pivot-level filters and Telegram alerts.
 */
@Designate(ocd = OHLStrategyScannerImpl.Config.class)
@Component(
        service = OHLStrategyScanner.class,
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class OHLStrategyScannerImpl implements OHLStrategyScanner {

    private static final Logger log = LoggerFactory.getLogger(OHLStrategyScannerImpl.class);

    // --- ANSI color codes for logs
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";

    private volatile Config config;

    @Reference
    private TelegramService telegram;

    @ObjectClassDefinition(
            name = "OHL (Open=High/Low) Strategy Service",
            description = "Enable/disable OHL scanner with pivot-level filters and Telegram alerts"
    )
    public @interface Config {
        @AttributeDefinition(name = "Enable Strategy")
        boolean enable() default true;

        @AttributeDefinition(name = "Tolerance % (0.0015 = 0.15%)")
        double tolerancePct() default 0.0015;

        @AttributeDefinition(name = "ATR Break Factor")
        double atrBreakFactor() default 0.25;

        @AttributeDefinition(name = "Min Avg Volume")
        long minAvgVolume() default 5000;

        @AttributeDefinition(name = "Enable Pivot Filter")
        boolean enablePivotFilter() default true;

        @AttributeDefinition(name = "Enable Telegram Alerts")
        boolean enableTelegram() default true;
    }

    @Activate
    @Modified
    protected void activate(Config cfg) {
        this.config = cfg;
        log.info(CYAN + "✅ OHLStrategyService activated" + RESET +
                        " (enable={}, tol={}, atrBreak={}, vol={}, pivot={}, telegram={})",
                cfg.enable(), cfg.tolerancePct(), cfg.atrBreakFactor(),
                cfg.minAvgVolume(), cfg.enablePivotFilter(), cfg.enableTelegram());
    }

    @Deactivate
    protected void deactivate() {
        log.info(RED + "🛑 OHLStrategyService deactivated" + RESET);
    }


    // --- Pivot Levels calculator
    public static class PivotLevels {
        public final double pivot, r1, r2, r3, s1, s2, s3;
        public PivotLevels(double high, double low, double close) {
            pivot = (high + low + close) / 3.0;
            r1 = (2 * pivot) - low;
            s1 = (2 * pivot) - high;
            r2 = pivot + (high - low);
            s2 = pivot - (high - low);
            r3 = high + 2 * (pivot - low);
            s3 = low - 2 * (high - pivot);
        }
    }

    /** Convert List<Candle> -> BarSeries */
    private BarSeries buildSeries(String name, List<Candle> candles, Duration barDuration) {
        BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder();
        BarSeries series = builder.withName(name).build();

        for (Candle c : candles) {
            Bar bar = BaseBar.builder()
                    .timePeriod(barDuration)
                    .endTime(ZonedDateTime.from(c.time))
                    .openPrice(DecimalNum.valueOf(c.open))
                    .highPrice(DecimalNum.valueOf(c.high))
                    .lowPrice(DecimalNum.valueOf(c.low))
                    .closePrice(DecimalNum.valueOf(c.close))
                    .volume(DecimalNum.valueOf(c.volume))
                    .build();
            series.addBar(bar);
        }
        return series;
    }
    private boolean priceBreak(BarSeries series, ATRIndicator atr, int index, double factor, boolean bullish) {
        double open = series.getBar(0).getOpenPrice().doubleValue();
        double latestClose = series.getBar(index).getClosePrice().doubleValue();
        double threshold = atr.getValue(index).doubleValue() * factor;
        return bullish
                ? latestClose > open + threshold
                : latestClose < open - threshold;
    }

    /** Build OHL strategy rules */
    private Strategy buildStrategy(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ATRIndicator atr = new ATRIndicator(series, 14);
        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        EMAIndicator macdSignal = new EMAIndicator(macd, 9);

        Rule openLow = new OpenEqualsLowRule(series, config.tolerancePct());
        Rule openHigh = new OpenEqualsHighRule(series, config.tolerancePct());

        Rule bullishBreak = new AbstractRule() {
            @Override
            public boolean isSatisfied(int index, TradingRecord tradingRecord) {
                return priceBreak(series, atr, index, config.atrBreakFactor(), true);
            }
        };

        Rule bearishBreak = new AbstractRule() {
            @Override
            public boolean isSatisfied(int index, TradingRecord tradingRecord) {
                return priceBreak(series, atr, index, config.atrBreakFactor(), false);
            }
        };

        Rule macdBull = new OverIndicatorRule(macd, macdSignal);
        Rule macdBear = new UnderIndicatorRule(macd, macdSignal);

        Rule longEntry = openLow.and(bullishBreak.or(macdBull));
        Rule shortEntry = openHigh.and(bearishBreak.or(macdBear));
        Rule entry = longEntry.or(shortEntry);

        Rule exit = new StopGainRule(close, DecimalNum.valueOf(0.02))
                .or(new StopLossRule(close, DecimalNum.valueOf(0.01)));

        return new BaseStrategy(entry, exit);
    }

    /**
     * Evaluate latest signal with timeframe string (e.g. "5m", "15m", "1h", "1d")
     */
    public Optional<Signal> evaluateLatest(List<Candle> candles, String timeframe) {
        if (config == null || !config.enable()) {
            log.debug(YELLOW + "⚠️ OHL strategy disabled, skipping" + RESET);
            return Optional.empty();
        }
        if (candles == null || candles.isEmpty()) return Optional.empty();

        Duration barDuration = mapTimeframe(timeframe);
        BarSeries series = buildSeries("LIVE-" + timeframe, candles, barDuration);
        Strategy strategy = buildStrategy(series);

        int last = series.getEndIndex();
        Bar lastBar = series.getBar(last);

        if (strategy.shouldEnter(last)) {
            var sig = new Signal(Signal.Side.BUY,
                    lastBar.getClosePrice().doubleValue(),
                    lastBar.getClosePrice().doubleValue() * 0.99,
                    lastBar.getClosePrice().doubleValue() * 1.02);

            if (config.enablePivotFilter() && !pivotOk(sig, candles)) {
                log.info(YELLOW + "🚫 BUY blocked by pivot filter at {}" + RESET, sig.getEntryPrice());
                return Optional.empty();
            }

            log.info(GREEN + "📈 BUY [{}] signal: {}" + RESET, timeframe, sig);
            sendTelegram("📈 *BUY " + timeframe + "*\nEntry: " + sig.getEntryPrice() +
                    "\nSL: " + sig.getStopLoss() + "\nTP: " + sig.getTarget());
            return Optional.of(sig);
        }

        if (strategy.shouldExit(last)) {
            Signal sig = new Signal(Signal.Side.SELL,
                    lastBar.getClosePrice().doubleValue(),
                    lastBar.getClosePrice().doubleValue(),
                    lastBar.getClosePrice().doubleValue());

            if (config.enablePivotFilter() && !pivotOk(sig, candles)) {
                log.info(YELLOW + "🚫 SELL blocked by pivot filter at {}" + RESET, sig.getEntryPrice());
                return Optional.empty();
            }

            log.info(RED + "📉 SELL [{}] signal: {}" + RESET, timeframe, sig);
            sendTelegram("📉 *SELL " + timeframe + "*\nExit: " + sig.getEntryPrice());
            return Optional.of(sig);
        }

        log.debug(BLUE + "ℹ️ No entry/exit signal generated for latest bar [{}]" + RESET, timeframe);
        return Optional.empty();
    }


    /** Send to Telegram if enabled */
    private void sendTelegram(String msg) {
        if (config != null && config.enableTelegram()) {
            try {
                telegram.sendMessageDailyStocksAlerts(msg);
            } catch (Exception e) {
                log.error(RED + "❌ Failed to send Telegram alert: {}" + RESET, e.getMessage(), e);
            }
        }
    }

    /** Check pivot filter conditions */
    private boolean pivotOk(Signal signal, List<Candle> candles) {
        if (candles.size() < 2) return true; // not enough data for pivot check

        Candle yesterday = candles.get(candles.size() - 2);
        PivotLevels pivots = new PivotLevels(yesterday.high, yesterday.low, yesterday.close);
        double entry = signal.getEntryPrice();

        if (signal.getSide() == Signal.Side.BUY) {
            return entry < pivots.r1;
        } else {
            return entry > pivots.s1;
        }
    }

    // --- Custom Rules
    private static class OpenEqualsLowRule extends AbstractRule {
        private final BarSeries series; private final double tolerance;
        OpenEqualsLowRule(BarSeries s, double tol) { this.series=s; this.tolerance=tol; }
        @Override public boolean isSatisfied(int i, TradingRecord r) {
            double open = series.getBar(0).getOpenPrice().doubleValue();
            double low = series.getBar(i).getLowPrice().doubleValue();
            return Math.abs(open - low) <= open * tolerance;
        }
    }

    private static class OpenEqualsHighRule extends AbstractRule {
        private final BarSeries series; private final double tolerance;
        OpenEqualsHighRule(BarSeries s, double tol) { this.series=s; this.tolerance=tol; }
        @Override public boolean isSatisfied(int i, TradingRecord r) {
            double open = series.getBar(0).getOpenPrice().doubleValue();
            double high = series.getBar(i).getHighPrice().doubleValue();
            return Math.abs(open - high) <= open * tolerance;
        }
    }
}

