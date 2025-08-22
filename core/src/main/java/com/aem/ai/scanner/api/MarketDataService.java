package com.aem.ai.scanner.api;

import com.aem.ai.scanner.model.Candle;
import com.aem.ai.scanner.model.InstrumentSymbol;

import java.util.List;

public interface MarketDataService {
    /** Short code of the broker, e.g., UPSTOX, DELTA. */
    String brokerCode();

    /**
     * Fetch candles for a symbol.
     * @param symbol broker-specific symbol (or instrument key for Upstox)
     * @param timeframe timeframe code (e.g. 5m, 15m, 1h, 1d)
     * @param count number of candles to fetch
     * @param historical true if historical API must be used, false if intraday
     */
    List<Candle> fetchCandles(InstrumentSymbol symbol, String timeframe, int count, boolean historical) throws Exception;

    /** Sleep/throttle between calls as needed. */
    default void interCallDelay() {
        try { Thread.sleep(10_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
