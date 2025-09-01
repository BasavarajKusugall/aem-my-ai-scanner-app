package com.aem.ai.scanner.factory;

import com.aem.ai.scanner.model.Condition;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

public interface IndicatorFactoryService {
    CachedIndicator<Num> buildIndicator(Condition cond, BarSeries series);
}
