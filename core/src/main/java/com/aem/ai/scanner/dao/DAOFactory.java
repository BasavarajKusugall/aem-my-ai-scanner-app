package com.aem.ai.scanner.dao;



import com.aem.ai.scanner.model.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface DAOFactory {

    // Watchlist
    List<InstrumentSymbol> readWatchlistFromDb();

    // Insert trade
    void insertTrade(TradeModel t, TradeAnalysis tradeAnalysis) throws SQLException;

    // Find trade
    Optional<TradeModel> findOpenBySymbolAndSide(InstrumentSymbol symbol, Signal.Side side) throws SQLException;
    Optional<TradeModel> getTradeById(String tradeId) throws SQLException;

    // Update trade
    void updateLtp(TradeModel t, double ltp) throws SQLException;
    void updateQuantity(String tradeId, int newQuantity) throws SQLException;

    // Close trade
    void closeTrade(String tradeId, double exitPrice, String reason) throws SQLException;
    void closeTradeWithPnl(TradeModel t) throws SQLException;

    // List trades
    List<TradeModel> listOpenTrades() throws SQLException;

    // Telegram configs
    List<TelegramConfig> fetchTelegramConfigs();
    List<TelegramConfig> fetchTelegramMonitorConfigs();
    List<TelegramConfig> fetchTelegramDailyNewsConfigs();
    List<TelegramConfig> fetchTelegramDailyAlertsConfigs();
    List<TelegramConfig> fetchTelegramDailyCryptoAlertsConfigs();
    List<TelegramConfig> fetchTelegramBotUserIDConfigs(String bot_user_id);
    List<StrategyConfig> loadActiveStrategies() throws Exception;
    void persistBestStrategies(String watchListTable,String symbol, List<StrategyResult> bestConfigs);
}
