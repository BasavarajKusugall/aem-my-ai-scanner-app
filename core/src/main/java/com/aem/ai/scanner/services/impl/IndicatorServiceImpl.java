package com.aem.ai.scanner.services.impl;


import com.aem.ai.scanner.model.Bar;
import com.aem.ai.scanner.model.InstrumentSymbol;
import com.aem.ai.scanner.services.IndicatorService;
import com.aem.ai.scanner.services.IndicatorServiceConfig;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
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

import java.time.Duration;
import java.util.List;

@Component(service = IndicatorService.class, immediate = true)
@Designate(ocd = IndicatorServiceConfig.class)
public class IndicatorServiceImpl implements IndicatorService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorServiceImpl.class);

    private int maxBars;
    private String defaultResolution;

    @Activate
    @Modified
    protected void activate(IndicatorServiceConfig config) {
        this.maxBars = config.maxBars();
        this.defaultResolution = config.defaultResolution();
        log.info("IndicatorService activated with maxBars={} and defaultResolution={}", maxBars, defaultResolution);
    }

    @Override
    public BarSeries toSeries(InstrumentSymbol symbol, List<Bar> bars, String resolution) {
        String res = (StringUtils.isEmpty(resolution)) ? defaultResolution : resolution;

        int size = bars.size();
        int from = Math.max(0, size - maxBars);
        List<Bar> window = (from == 0) ? bars : bars.subList(from, size);

        log.info("Converting {} bars (windowed from total {}) for '{}' @ '{}'",
                window.size(), size, symbol, res);

        BarSeries series = new BaseBarSeriesBuilder().withName(symbol.getSymbol()).build();
        series.setMaximumBarCount(maxBars);

        for (Bar c : window) {
            Duration barDuration;
            switch (res) {
                case "5m":
                    barDuration = Duration.ofMinutes(5);
                    break;
                case "15m":
                    barDuration = Duration.ofMinutes(15);
                    break;
                case "1d":
                    barDuration = Duration.ofDays(1);
                    break;
                default:
                    barDuration = Duration.ofMinutes(5);
            }


            BaseBar bar = new BaseBar(
                    barDuration,
                    c.getTime(),
                    series.numOf(c.getOpen()),
                    series.numOf(c.getHigh()),
                    series.numOf(c.getLow()),
                    series.numOf(c.getClose()),
                    series.numOf(c.getVolume()),
                    series.numOf(0),
                    0
            );
            series.addBar(bar);
        }
        log.info("BarSeries '{}' ready with {} bars (max={})", symbol, series.getBarCount(), maxBars);
        return series;
    }

    @Override
    public RSIIndicator rsi(BarSeries s, int period) {
        return new RSIIndicator(new ClosePriceIndicator(s), period);
    }

    @Override
    public EMAIndicator ema(BarSeries s, int period) {
        return new EMAIndicator(new ClosePriceIndicator(s), period);
    }

    @Override
    public MACDIndicator macd(BarSeries s, int shortP, int longP) {
        ClosePriceIndicator close = new ClosePriceIndicator(s);
        return new MACDIndicator(close, shortP, longP);
    }

    @Override
    public ATRIndicator atr(BarSeries s, int period) {
        return new ATRIndicator(s, period);
    }
}
