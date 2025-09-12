package com.aem.ai.scanner.dao;



import com.aem.ai.scanner.model.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface DAOFactory {

    // Watchlist
    List<InstrumentSymbol> readWatchlistFromDb();

    // Insert trade
    void insertTrade(TradeModel t, TradeAnalysis tradeAnalysis, String tableName) throws SQLException;

    // Find trade
    Optional<TradeModel> findOpenBySymbolAndSide(InstrumentSymbol symbol, Signal.Side side, String tableName) throws SQLException;

    // Update trade
    void updateLtp(TradeModel t, double ltp,String tableName) throws SQLException;

    // Close trade
    void closeTradeWithPnl(TradeModel t, String tableName) throws SQLException;

    // List trades
    List<TradeModel> listOpenTrades(InstrumentSymbol symbol, String timeframe, Signal signal, String tableName) throws SQLException;

    // Telegram configs
    List<TelegramConfig> fetchTelegramConfigs();
    List<TelegramConfig> fetchTelegramMonitorConfigs();
    List<TelegramConfig> fetchTelegramDailyNewsConfigs();
    List<TelegramConfig> fetchTelegramDailyAlertsConfigs();
    List<TelegramConfig> fetchTelegramDailyCryptoAlertsConfigs();
    List<TelegramConfig> fetchTelegramBotUserIDConfigs(String bot_user_id);
    List<StrategyConfig> loadActiveStrategies() throws Exception;
    void persistBestStrategies(String watchListTable,String symbol, List<StrategyResult> bestConfigs);
    int appendOpenTradeComment(InstrumentSymbol symbol, Signal.Side side, String comment, String tableName) throws SQLException;
    boolean insertTradeIfNoOpen(TradeModel t, TradeAnalysis analysis,String tableName) throws SQLException;
    List<TradeModel> listOpenTradesForSymbol(String symbol,String tableName) throws SQLException;
    List<InstrumentSymbol> readWatchlistFromDb(String tableName);

    Optional<TradeModel> getTradeById(String tradeId, String tableName) throws SQLException;

    void updateQuantity(String tradeId, int newQuantity, String tableName) throws SQLException;

    void closeTrade(String tradeId, double exitPrice, String reason, String tableName) throws SQLException;
    void insertOrUpdateTelegramConfig(TelegramConfig cfg);
}
