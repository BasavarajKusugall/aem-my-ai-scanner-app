package com.aem.ai.scanner.services;


import com.aem.ai.scanner.model.Bar;
import com.aem.ai.scanner.model.InstrumentSymbol;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;

import java.util.List;

public interface IndicatorService {
    BarSeries toSeries(InstrumentSymbol symbol, List<Bar> bars, String resolution);

    RSIIndicator rsi(BarSeries series, int period);

    EMAIndicator ema(BarSeries series, int period);

    MACDIndicator macd(BarSeries series, int shortP, int longP);

    ATRIndicator atr(BarSeries series, int period);
}
