package com.aem.ai.scanner.factory.impl;

import com.aem.ai.scanner.factory.IndicatorFactoryService;
import com.aem.ai.scanner.factory.RuleFactoryService;
import com.aem.ai.scanner.model.Condition;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

@Component(service = RuleFactoryService.class, immediate = true)
public class RuleFactoryImpl implements RuleFactoryService {

    @Reference
    private IndicatorFactoryService indicatorFactoryService;

    @Override
    public Rule buildRule(Condition cond, BarSeries series) {
        var ind = indicatorFactoryService.buildIndicator(cond, series);

        String op = cond.operator.toLowerCase();
        switch (op) {
            case "<": {
                // RSI < threshold type rules
                Num thr = series.numOf(((Number) cond.value).doubleValue());
                return new UnderIndicatorRule(ind, thr);
            }
            case ">": {
                // RSI > threshold type rules
                Num thr = series.numOf(((Number) cond.value).doubleValue());
                return new OverIndicatorRule(ind, thr);
            }
            case "cross_up": {
                // MACD cross up with signal line
                if ("signal_line".equalsIgnoreCase(String.valueOf(cond.value))) {
                    MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series), cond.fast, cond.slow);
                    EMAIndicator sig = new EMAIndicator(macd, cond.signal);
                    return new CrossedUpIndicatorRule(macd, sig);
                }
                // EMA fast cross up EMA slow
                if ("EMA".equalsIgnoreCase(cond.indicator) && cond.fast != null && cond.slow != null) {
                    EMAIndicator fastEma = new EMAIndicator(new ClosePriceIndicator(series), cond.fast);
                    EMAIndicator slowEma = new EMAIndicator(new ClosePriceIndicator(series), cond.slow);
                    return new CrossedUpIndicatorRule(fastEma, slowEma);
                }
                // fallback: indicator crosses close price
                return new CrossedUpIndicatorRule(ind, new ClosePriceIndicator(series));
            }
            case "cross_down": {
                // MACD cross down with signal line
                if ("signal_line".equalsIgnoreCase(String.valueOf(cond.value))) {
                    MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series), cond.fast, cond.slow);
                    EMAIndicator sig = new EMAIndicator(macd, cond.signal);
                    return new CrossedDownIndicatorRule(macd, sig);
                }
                // EMA fast cross down EMA slow
                if ("EMA".equalsIgnoreCase(cond.indicator) && cond.fast != null && cond.slow != null) {
                    EMAIndicator fastEma = new EMAIndicator(new ClosePriceIndicator(series), cond.fast);
                    EMAIndicator slowEma = new EMAIndicator(new ClosePriceIndicator(series), cond.slow);
                    return new CrossedDownIndicatorRule(fastEma, slowEma);
                }
                // fallback: indicator crosses close price
                return new CrossedDownIndicatorRule(ind, new ClosePriceIndicator(series));
            }
            default:
                throw new IllegalArgumentException("Unknown operator: " + cond.operator);
        }
    }
}
