package com.aem.ai.scanner.scanner;

import com.aem.ai.scanner.model.Candle;
import com.aem.ai.scanner.model.Signal;
import com.aem.ai.scanner.scanner.impl.OHLStrategyScannerImpl;

import java.util.List;
import java.util.Optional;

public interface OHLStrategyScanner {
    Optional<Signal> evaluateLatest(List<Candle> candles, String timeframe);
}
