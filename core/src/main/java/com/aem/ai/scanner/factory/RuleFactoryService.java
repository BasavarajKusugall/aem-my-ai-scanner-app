package com.aem.ai.scanner.factory;


import com.aem.ai.scanner.model.Condition;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;

public interface RuleFactoryService {
    Rule buildRule(Condition cond, BarSeries series);
}
