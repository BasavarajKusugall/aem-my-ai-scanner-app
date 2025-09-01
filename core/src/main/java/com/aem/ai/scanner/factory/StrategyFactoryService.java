package com.aem.ai.scanner.factory;


import com.aem.ai.scanner.model.StrategyConfig;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

public interface StrategyFactoryService {
    Strategy buildStrategy(StrategyConfig config, BarSeries series);
}
