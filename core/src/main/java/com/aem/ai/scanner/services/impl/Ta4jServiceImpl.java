package com.aem.ai.scanner.services.impl;


import com.aem.ai.scanner.indicators.MACDSignalIndicator;
import com.aem.ai.scanner.model.Candle;
import com.aem.ai.scanner.services.Ta4jService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component(service = Ta4jService.class, immediate = true)
@Designate(ocd = Ta4jServiceImpl.Config.class)
public class Ta4jServiceImpl implements Ta4jService {

    private static final Logger log = LoggerFactory.getLogger(Ta4jServiceImpl.class);

    @ObjectClassDefinition(
            name = "Ta4j Service Config",
            description = "Configuration for technical indicator calculations"
    )
    public @interface Config {
        int default_bar_seconds() default 60;
        int rsi_period() default 14;
        int ema_fast_period() default 9;
        int ema_slow_period() default 21;
        int macd_short() default 12;
        int macd_long() default 26;
        int macd_signal() default 9;
        int atr_period() default 14;
        int vwap_period() default 14;
    }

    private Config config;

    @Activate
    protected void activate(Config config) {
        this.config = config;
        log.info("Ta4jService activated with default_bar_seconds={} RSI={} EMAfast={}/EMAslow={}",
                config.default_bar_seconds(), config.rsi_period(), config.ema_fast_period(), config.ema_slow_period());
    }

    @Deactivate
    protected void deactivate() {
        log.info("Ta4jService deactivated");
    }

    @Override
    public BarSeries buildSeries(String name, List<Candle> candles) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        for (Candle c : candles) {
            Instant time = c.getTime();
            ZonedDateTime endTime = time.atZone(ZoneId.of("UTC"));
            series.addBar(new BaseBar(
                    Duration.ofSeconds(config.default_bar_seconds()),
                    endTime,
                    series.numOf(c.getOpen()),
                    series.numOf(c.getHigh()),
                    series.numOf(c.getLow()),
                    series.numOf(c.getClose()),
                    series.numOf(c.getVolume()),
                    series.numOf(0),
                    0
            ));
        }
        log.debug("Built bar series '{}' with {} bars", name, series.getBarCount());
        return series;
    }

    @Override
    public double rsi(BarSeries series, int barIndex, int timeFrame) {
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), timeFrame);
        return rsi.getValue(barIndex).doubleValue();
    }

    @Override
    public IndicatorsSnapshot computeIndicators(BarSeries series) {
        int last = series.getEndIndex();
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        RSIIndicator rsi = new RSIIndicator(close, config.rsi_period());
        EMAIndicator emaFast = new EMAIndicator(close, config.ema_fast_period());
        EMAIndicator emaSlow = new EMAIndicator(close, config.ema_slow_period());
        MACDIndicator macd = new MACDIndicator(close, config.macd_short(), config.macd_long());
        MACDSignalIndicator macdSignal = new MACDSignalIndicator(macd, config.macd_signal());
        ATRIndicator atr = new ATRIndicator(series, config.atr_period());
        VWAPIndicator vwap = new VWAPIndicator(series, config.vwap_period());

        return new IndicatorsSnapshot(
                rsi.getValue(last).doubleValue(),
                macd.getValue(last).doubleValue(),
                macdSignal.getValue(last).doubleValue(),
                emaFast.getValue(last).doubleValue(),
                emaSlow.getValue(last).doubleValue(),
                atr.getValue(last).doubleValue(),
                vwap.getValue(last).doubleValue()
        );
    }
}
