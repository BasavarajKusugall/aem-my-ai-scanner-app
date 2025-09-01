package com.aem.ai.scanner.dao;

import com.aem.ai.scanner.model.InstrumentSymbol;

import java.util.List;

public interface WatchlistDao {
    List<InstrumentSymbol> symbolsForUpstox();    // read instrument_key or symbol
    List<InstrumentSymbol> symbolsForDelta();
    String upstoxTable();    // for info purposes
    String deltaTable();
}
