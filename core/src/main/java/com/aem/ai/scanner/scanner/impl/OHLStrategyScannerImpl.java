package com.aem.ai.scanner.scanner.impl;

import com.aem.ai.scanner.model.Candle;
import com.aem.ai.scanner.model.InstrumentSymbol;
import com.aem.ai.scanner.model.PivotLevels;
import com.aem.ai.scanner.model.Signal;
import com.aem.ai.scanner.scanner.OHLStrategyScanner;
import com.aem.ai.scanner.services.Ta4jService;
import com.aem.ai.scanner.services.TelegramService;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.*;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.rules.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OSGi Service for Open=High/Low (OHL) strategy with pivot-level filters and Telegram alerts.
 */
@Designate(ocd = OHLStrategyScannerImpl.Config.class)
@Component(service = OHLStrategyScanner.class, immediate = true)
public class OHLStrategyScannerImpl implements OHLStrategyScanner {

    private static final Logger log = LoggerFactory.getLogger(OHLStrategyScannerImpl.class);

    private volatile Config config;

    @Reference
    private TelegramService telegram;

    @Reference
    private Ta4jService ta4jService;

    private List<String> ohlTimeFrameList = new ArrayList<>();

    // --------------------- Configuration --------------------- //

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

        @AttributeDefinition(name = "Stop Loss %")
        double stopLossPct() default 0.01;

        @AttributeDefinition(name = "Target %")
        double targetPct() default 0.02;

        @AttributeDefinition(name = "Min Avg Volume")
        long minAvgVolume() default 5000;

        @AttributeDefinition(name = "Enable Pivot Filter")
        boolean enablePivotFilter() default true;

        @AttributeDefinition(name = "Pivot timeframe (daily/weekly/monthly)")
        String pivotTimeframe() default "daily";

        @AttributeDefinition(name = "Enable Telegram Alerts")
        boolean enableTelegram() default true;

        @AttributeDefinition(name = "OHL scanner timeframes")
        String ohl_scanner_tf() default "1m,3m,5m,10m";
    }

    @Activate
    @Modified
    protected void activate(Config cfg) {
        long start = System.currentTimeMillis();
        this.config = cfg;
        if (cfg.ohl_scanner_tf() != null) {
            ohlTimeFrameList = Arrays.stream(cfg.ohl_scanner_tf().split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
        log.info("OHLStrategyService activated enable={} tol={} atrBreak={} sl={} tp={} vol={} pivot={} telegram={} [{}ms]",
                cfg.enable(), cfg.tolerancePct(), cfg.atrBreakFactor(),
                cfg.stopLossPct(), cfg.targetPct(),
                cfg.minAvgVolume(), cfg.enablePivotFilter(), cfg.enableTelegram(),
                System.currentTimeMillis() - start);
    }

    @Deactivate
    protected void deactivate() {
        log.info("OHLStrategyService deactivated");
    }

    // --------------------- Strategy --------------------- //

    private Strategy buildStrategy(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ATRIndicator atr = new ATRIndicator(series, 14);
        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        EMAIndicator macdSignal = new EMAIndicator(macd, 9);

        Rule openLow = new OpenEqualsLowRule(series, config.tolerancePct());
        Rule openHigh = new OpenEqualsHighRule(series, config.tolerancePct());

        Rule bullishBreak = new ATRBreakRule(series, atr, config.atrBreakFactor(), true);
        Rule bearishBreak = new ATRBreakRule(series, atr, config.atrBreakFactor(), false);

        Rule macdBull = new OverIndicatorRule(macd, macdSignal);
        Rule macdBear = new UnderIndicatorRule(macd, macdSignal);

        Rule longEntry = openLow.and(bullishBreak.or(macdBull));
        Rule shortEntry = openHigh.and(bearishBreak.or(macdBear));
        Rule entry = longEntry.or(shortEntry);

        Rule exit = new StopGainRule(close, DecimalNum.valueOf(config.targetPct()))
                .or(new StopLossRule(close, DecimalNum.valueOf(config.stopLossPct())));

        return new BaseStrategy(entry, exit);
    }

    // --------------------- Evaluation --------------------- //
//todo: add volume filter and optimize strategy not matching with live scanner OHL signals
    public Optional<Signal> evaluateLatest(List<Candle> candles, String timeframe, InstrumentSymbol symbol) {
        if (config == null || !config.enable() || candles == null || candles.isEmpty()) {
            log.info("OHL strategy disabled or no candles tf={} symbol={}", timeframe, symbol);
            return Optional.empty();
        }

        try {
            String seriesName = symbol.getSymbol() + "-" + timeframe;
            BarSeries series = ta4jService.buildSeries(seriesName, candles, timeframe);
            Strategy strategy = buildStrategy(series);

            int last = series.getEndIndex();
            Bar lastBar = series.getBar(last);

            // BUY
            if (strategy.shouldEnter(last)) {
                Signal sig = new Signal(Signal.Side.BUY,
                        lastBar.getClosePrice().doubleValue(),
                        lastBar.getClosePrice().doubleValue() * (1 - config.stopLossPct()),
                        lastBar.getClosePrice().doubleValue() * (1 + config.targetPct()));

                if (config.enablePivotFilter() && !pivotOk(sig, candles)) {
                    log.info("BUY blocked by pivot filter symbol={} tf={} entry={}", symbol, timeframe, sig.getEntryPrice());
                    return Optional.empty();
                }

                log.info("BUY signal generated symbol={} tf={} {}", symbol, timeframe, sig);
                return Optional.of(sig);
            }

            // SELL
            if (strategy.shouldExit(last)) {
                Signal sig = new Signal(Signal.Side.SELL,
                        lastBar.getClosePrice().doubleValue(),
                        lastBar.getClosePrice().doubleValue(),
                        lastBar.getClosePrice().doubleValue());

                if (config.enablePivotFilter() && !pivotOk(sig, candles)) {
                    log.info("SELL blocked by pivot filter symbol={} tf={} entry={}", symbol, timeframe, sig.getEntryPrice());
                    return Optional.empty();
                }

                log.info("SELL signal generated symbol={} tf={} {}", symbol, timeframe, sig);
                return Optional.of(sig);
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("Error evaluating OHL strategy tf={} symbol={} error={}", timeframe, symbol, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // --------------------- Pivot Filter --------------------- //

    private boolean pivotOk(Signal signal, List<Candle> candles) {
        if (candles.size() < 2) return true;
        Candle yesterday = candles.get(candles.size() - 2);
        PivotLevels pivots = new PivotLevels(yesterday.high, yesterday.low, yesterday.close);
        double entry = signal.getEntryPrice();

        return signal.getSide() == Signal.Side.BUY
                ? entry < pivots.getR1()
                : entry > pivots.getS1();
    }

    // --------------------- Custom Rules --------------------- //

    private static class OpenEqualsLowRule extends AbstractRule {
        private final BarSeries series;
        private final double tolerance;

        OpenEqualsLowRule(BarSeries s, double tol) { this.series = s; this.tolerance = tol; }

        @Override
        public boolean isSatisfied(int i, TradingRecord r) {
            double open = series.getBar(i).getOpenPrice().doubleValue();
            double low = series.getBar(i).getLowPrice().doubleValue();
            return Math.abs(open - low) <= open * tolerance;
        }
    }

    private static class OpenEqualsHighRule extends AbstractRule {
        private final BarSeries series;
        private final double tolerance;

        OpenEqualsHighRule(BarSeries s, double tol) { this.series = s; this.tolerance = tol; }

        @Override
        public boolean isSatisfied(int i, TradingRecord r) {
            double open = series.getBar(i).getOpenPrice().doubleValue();
            double high = series.getBar(i).getHighPrice().doubleValue();
            return Math.abs(open - high) <= open * tolerance;
        }
    }

    private static class ATRBreakRule extends AbstractRule {
        private final BarSeries series;
        private final ATRIndicator atr;
        private final double factor;
        private final boolean bullish;

        ATRBreakRule(BarSeries series, ATRIndicator atr, double factor, boolean bullish) {
            this.series = series; this.atr = atr; this.factor = factor; this.bullish = bullish;
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord record) {
            double open = series.getBar(index).getOpenPrice().doubleValue();
            double close = series.getBar(index).getClosePrice().doubleValue();
            double threshold = atr.getValue(index).doubleValue() * factor;
            return bullish ? close > open + threshold : close < open - threshold;
        }
    }

    // --------------------- Getters --------------------- //

    public List<String> getOHLTimeFrameList() {
        return ohlTimeFrameList;
    }
}
