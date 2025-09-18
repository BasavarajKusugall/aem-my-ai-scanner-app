package com.aem.ai.scanner.services;


import com.aem.ai.scanner.model.*;

import java.util.List;
import java.util.Optional;

public interface StrategyEngine {
    /** Run the strategy on candles and return a signal if any (entry/exit/sl). */
    Optional<Signal> evaluate(StrategyConfig cfg, List<Candle> candles, InstrumentSymbol symbol, String timeframe, PivotLevels pivots);

    /** Pretty, human-readable reason you can post to Telegram / DB comments. */
    String format(Signal signal, StrategyConfig cfg, InstrumentSymbol symbol, String timeframe);
}
