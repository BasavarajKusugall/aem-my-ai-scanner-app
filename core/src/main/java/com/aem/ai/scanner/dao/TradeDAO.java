package com.aem.ai.scanner.dao;



import com.aem.ai.scanner.model.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface TradeDAO {

    // Watchlist
    List<InstrumentSymbol> readWatchlistFromDb();

    // Insert trade
    void insertTrade(Trade t, TradeAnalysis tradeAnalysis) throws SQLException;

    // Find trade
    Optional<Trade> findOpenBySymbolAndSide(InstrumentSymbol symbol, Signal.Side side) throws SQLException;
    Optional<Trade> getTradeById(String tradeId) throws SQLException;

    // Update trade
    void updateLtp(Trade t, double ltp) throws SQLException;
    void updateQuantity(String tradeId, int newQuantity) throws SQLException;

    // Close trade
    void closeTrade(String tradeId, double exitPrice, String reason) throws SQLException;
    void closeTradeWithPnl(Trade t) throws SQLException;

    // List trades
    List<Trade> listOpenTrades() throws SQLException;

    // Telegram configs
    List<TelegramConfig> fetchTelegramConfigs();
    List<TelegramConfig> fetchTelegramMonitorConfigs();
}
