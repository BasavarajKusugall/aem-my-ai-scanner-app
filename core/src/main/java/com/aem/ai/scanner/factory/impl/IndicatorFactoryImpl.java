package com.aem.ai.scanner.factory.impl;


import com.aem.ai.scanner.factory.IndicatorFactoryService;
import com.aem.ai.scanner.model.Condition;
import org.osgi.service.component.annotations.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;


/**
 * OSGi Service implementation for building indicators and strategies.
 */
@Component(service = IndicatorFactoryService.class, immediate = true)
public class IndicatorFactoryImpl implements IndicatorFactoryService {

    @Override
    public CachedIndicator<Num> buildIndicator(Condition cond, BarSeries series) {
        String name = cond.indicator.toUpperCase();
        switch (name) {
            case "RSI": {
                int rsiPeriod = cond.period == null ? 14 : cond.period;
                return new RSIIndicator(new ClosePriceIndicator(series), rsiPeriod);
            }
            case "MACD": {
                int fast = cond.fast == null ? 12 : cond.fast;
                int slow = cond.slow == null ? 26 : cond.slow;
                MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series), fast, slow);
                if ("signal_line".equals(cond.value) || (cond.signal != null)) {
                    int sig = cond.signal == null ? 9 : cond.signal;
                    return new EMAIndicator(macd, sig);
                }
                return macd;
            }
            case "EMA": {
                int emaP = cond.period == null ? 21 : cond.period;
                return new EMAIndicator(new ClosePriceIndicator(series), emaP);
            }
            case "EMA_CROSS": {
                int fastEma = cond.fast == null ? 9 : cond.fast;
                int slowEma = cond.slow == null ? 21 : cond.slow;
                return new EMAIndicator(new ClosePriceIndicator(series), fastEma);
            }
            default:
                throw new IllegalArgumentException("Unknown indicator: " + cond.indicator);
        }
    }

}
