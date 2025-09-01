package com.aem.ai.scanner.factory.impl;

import com.aem.ai.scanner.factory.RuleFactoryService;
import com.aem.ai.scanner.factory.StrategyFactoryService;
import com.aem.ai.scanner.model.Condition;
import com.aem.ai.scanner.model.StrategyConfig;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.rules.BooleanRule;

import java.util.List;

@Component(service = StrategyFactoryService.class, immediate = true)
public class StrategyFactoryImpl implements StrategyFactoryService {

    @Reference
    private RuleFactoryService ruleFactoryService;

    @Override
    public Strategy buildStrategy(StrategyConfig config, BarSeries series) {
        List<StrategyConfig.RuleConfig> rules = config.getRules();

        Rule entryRule = null;
        Rule exitRule = null;

        for (StrategyConfig.RuleConfig rc : rules) {
            Rule combined = null;
            for (Condition cond : rc.getConditions()) {
                Rule r = ruleFactoryService.buildRule(cond, series);
                combined = (combined == null) ? r : combined.and(r);
            }

            if ("BUY".equalsIgnoreCase(rc.getAction())) {
                entryRule = (entryRule == null) ? combined : entryRule.or(combined);
            } else if ("SELL".equalsIgnoreCase(rc.getAction())) {
                exitRule = (exitRule == null) ? combined : exitRule.or(combined);
            }
        }

        if (entryRule == null) {
            entryRule = new BooleanRule(false);
        }
        if (exitRule == null) {
            exitRule = new BooleanRule(false);
        }

        return new BaseStrategy(config.getName(), entryRule, exitRule);
    }
}
