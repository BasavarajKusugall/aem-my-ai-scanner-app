package com.aem.ai.scanner.scanner;

import com.aem.ai.scanner.model.Candle;
import com.aem.ai.scanner.model.InstrumentSymbol;
import com.aem.ai.scanner.model.Signal;

import java.util.List;
import java.util.Optional;

public interface OHLStrategyScanner {
    Optional<Signal> evaluateLatest(List<Candle> candles, String timeframe, InstrumentSymbol symbol);
}
