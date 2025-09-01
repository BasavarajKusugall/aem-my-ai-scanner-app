package com.aem.ai.scanner.dao.impl;


import com.GenericeConstants;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.dao.DAOConfig;
import com.aem.ai.scanner.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pm.dao.DataSourcePoolProviderService;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Component(service = DAOFactory.class, immediate = true)
@Designate(ocd = DAOConfig.class)
public class DAOFactoryFactoryImpl implements DAOFactory {

    private static final Logger logger = LoggerFactory.getLogger(DAOFactoryFactoryImpl.class);


    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;

    private DataSource getDataSource() {
        return dataSourcePoolProviderService.getDataSourceByName(GenericeConstants.DB_ALGO_DB);
    }
    @Activate
    protected void activate(DAOConfig config) {
        logger.info("TradeDAO activated with DataSource: {}", config.datasourceName());
    }


    private Connection conn() throws SQLException {
        DataSource dataSource = getDataSource();
        Connection connection = dataSource.getConnection();
        if (connection != null){
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(true); // ensure commit per statement
            return connection;
        }
        return null;
    }

    // -------------------- WATCHLIST --------------------
    public List<InstrumentSymbol> readWatchlistFromDb() {
        List<InstrumentSymbol> watchlist = new ArrayList<>();
        String sql = "SELECT * FROM nifty500_watchlist";

        try (Connection conn = conn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String symbol = rs.getString("SYMBOL");
                String instrumentKey = rs.getString("INSTRUMENT_KEY");
                String bestStrategy = rs.getString("BEST_STRATEGY");
                InstrumentSymbol inst = new InstrumentSymbol(symbol, bestStrategy, GenericeConstants.NSE_EQ + instrumentKey);
                watchlist.add(inst);
            }
            logger.info("Loaded {} symbols from nifty500_watchlist", watchlist.size());
        } catch (SQLException e) {
            logger.error("Failed to read watchlist from DB", e);
        }
        return watchlist;
    }

    // -------------------- INSERT TRADE --------------------
    public void insertTrade(TradeModel t, TradeAnalysis tradeAnalysis) throws SQLException {
        String sql = "INSERT INTO trades(" +
                "instrument_key, symbol, side, entry_price, ltp, stop_loss, target, quantity, entry_time, " +
                "exit_price, exit_time, status, pnl, orderType, TIMEFRAME, " +
                "confidence_score, global_news_sentiment, market_trend, open_interest_build_up, " +
                "recommended_trade_timeframe, can_take_trade, final_verdict, GeminiAnalysis" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, t.getSymbol().getInstrumentKey());
            ps.setString(2, t.getSymbol().getSymbol());
            ps.setString(3, t.getSide().name());
            ps.setDouble(4, t.getEntryPrice());
            ps.setDouble(5, t.getEntryPrice());
            ps.setDouble(6, t.getStopLoss());
            ps.setDouble(7, t.getTarget());
            ps.setInt(8, t.getQuantity());
            ps.setTimestamp(9, Timestamp.valueOf(t.getEntryTime()));
            ps.setDouble(10, t.getExitPrice());
            if (t.getExitTime() != null) ps.setTimestamp(11, Timestamp.valueOf(t.getExitTime()));
            else ps.setNull(11, Types.TIMESTAMP);
            ps.setString(12, t.getStatus().toString());
            ps.setDouble(13, t.getPnl());
            ps.setString(14, StringUtils.contains(t.getTimeFrame(), "m") ? "MIS" : "CNC");
            ps.setString(15, t.getTimeFrame());

            if (tradeAnalysis != null) {
                ps.setString(16, String.valueOf(tradeAnalysis.getConfidence_score()));
                ps.setString(17, tradeAnalysis.getGlobal_news_sentiment());
                ps.setString(18, tradeAnalysis.getMarket_trend());
                ps.setString(19, tradeAnalysis.getOpen_interest_build_up());
                ps.setString(20, tradeAnalysis.getRecommended_trade_timeframe());
                ps.setString(21, tradeAnalysis.getCan_take_trade());
                ps.setString(22, tradeAnalysis.getFinal_verdict());
                ps.setString(23, tradeAnalysis.getResponseJson());
            } else {
                for (int i = 16; i <= 23; i++) ps.setNull(i, Types.VARCHAR);
            }

            int rows = ps.executeUpdate();
            logger.info("Inserted trade into DB. Rows affected: {}", rows);

        } catch (SQLException e) {
            logger.error("Failed to insert trade: {}", t.getSymbol().getSymbol(), e);
            throw e;
        }
    }

    // -------------------- FIND OPEN TRADE --------------------
    public Optional<TradeModel> findOpenBySymbolAndSide(InstrumentSymbol symbol, Signal.Side side) throws SQLException {
        String sql = "SELECT * FROM trades WHERE symbol = ? AND side = ? AND status = 'OPEN' ORDER BY entry_time DESC LIMIT 1";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, symbol.getInstrumentKey());
            ps.setString(2, side.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                else return Optional.empty();
            }
        }
    }

    // -------------------- UPDATE LTP & PNL --------------------
    public void updateLtp(TradeModel t, double ltp) throws SQLException {
        String sql = "UPDATE trades SET ltp = ?, pnl = ?, last_updated = CURRENT_TIMESTAMP WHERE symbol = ? AND status = 'OPEN'";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            double pnl = ltp - t.getEntryPrice();
            ps.setDouble(1, ltp);
            ps.setDouble(2, pnl);
            ps.setString(3, t.getSymbol().getSymbol());
            ps.executeUpdate();
        }
    }

    // -------------------- CLOSE TRADE --------------------
    public void closeTrade(String tradeId, double exitPrice, String reason) throws SQLException {
        String sql = "UPDATE trades SET status = 'CLOSED', exit_price = ?, exit_time = ?, Reason = ?, last_updated = CURRENT_TIMESTAMP WHERE trade_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, exitPrice);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(3, reason);
            ps.setString(4, tradeId);
            ps.executeUpdate();
        }
    }

    public void closeTradeWithPnl(TradeModel t) throws SQLException {
        String sql = "UPDATE trades SET status = 'CLOSED', exit_price = ?, exit_time = ?, pnl = ?, last_updated = CURRENT_TIMESTAMP WHERE status = 'OPEN' AND symbol = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, t.getExitPrice());
            ps.setTimestamp(2, Timestamp.valueOf(t.getExitTime()));
            ps.setDouble(3, t.getPnl());
            ps.setString(4, t.getSymbol().getSymbol());
            ps.executeUpdate();
        }
    }

    // -------------------- UPDATE QUANTITY --------------------
    public void updateQuantity(String tradeId, int newQuantity) throws SQLException {
        String sql = "UPDATE trades SET quantity = ?, last_updated = CURRENT_TIMESTAMP WHERE trade_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, newQuantity);
            ps.setString(2, tradeId);
            ps.executeUpdate();
        }
    }

    // -------------------- GET TRADE BY ID --------------------
    public Optional<TradeModel> getTradeById(String tradeId) throws SQLException {
        String sql = "SELECT * FROM trades WHERE trade_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tradeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                else return Optional.empty();
            }
        }
    }

    // -------------------- LIST OPEN TRADES --------------------
    public List<TradeModel> listOpenTrades() throws SQLException {
        String sql = "SELECT * FROM trades WHERE status = 'OPEN'";
        List<TradeModel> tradeModels = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) tradeModels.add(mapRow(rs));
        }
        return tradeModels;
    }

    // -------------------- TELEGRAM CONFIG --------------------
    public List<TelegramConfig> fetchTelegramConfigs() {
        String cond = " AND purpose='ALL'";
        return fetchTelegramConfigs(cond);
    }


    public List<TelegramConfig> fetchTelegramMonitorConfigs() {
        String cond = " AND purpose='MONITOR'";
        return fetchTelegramConfigs(cond);
    }

    public List<TelegramConfig> fetchTelegramDailyNewsConfigs() {
        String cond = " AND purpose='NEWS'";
        return fetchTelegramConfigs(cond);
    }

    public List<TelegramConfig> fetchTelegramDailyAlertsConfigs() {
        String cond = " AND purpose='STOCKS_ALERTS'";
        return fetchTelegramConfigs(cond);
    }

    public List<TelegramConfig> fetchTelegramDailyCryptoAlertsConfigs() {
        String cond = " AND purpose='CRYPTO_ALERTS'";
        return fetchTelegramConfigs(cond);
    }

    @Override
    public List<TelegramConfig> fetchTelegramBotUserIDConfigs(String bot_user_id) {
        String botUserIdCond = " AND bot_user_id='" + bot_user_id + "'";
        return fetchTelegramConfigs(botUserIdCond);
    }

    private List<TelegramConfig> fetchTelegramConfigs(String dynamicFilter) {
        List<TelegramConfig> configs = new ArrayList<>();
        String sql = "SELECT * FROM telegram_bot_config WHERE is_active = 1";
        if (StringUtils.isNotEmpty(dynamicFilter)){
            sql += dynamicFilter;
        }

        try (Connection conn = conn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                TelegramConfig cfg = new TelegramConfig();
                cfg.setBotChatId(rs.getLong("bot_chat_id"));
                cfg.setChatType(rs.getString("chat_type"));
                cfg.setChatTitle(rs.getString("chat_title"));
                cfg.setBotName(rs.getString("bot_name"));
                cfg.setBotToken(rs.getString("bot_token"));
                cfg.setBotUserId(rs.getLong("bot_user_id"));
                cfg.setPurpose(rs.getString("purpose"));
                cfg.setGroupEnabled(rs.getBoolean("is_group_enabled"));
                configs.add(cfg);
            }
        } catch (SQLException e) {
            logger.error("Failed to load telegram configs", e);
        }
        return configs;
    }

    // -------------------- MAP ROW --------------------
    private TradeModel mapRow(ResultSet rs) throws SQLException {
        String instrumentKey = rs.getString("instrument_key");
        InstrumentSymbol is = new InstrumentSymbol(rs.getString("symbol"), instrumentKey);
        TradeModel tradeModel = new TradeModel(is,
                Signal.Side.valueOf(rs.getString("side")),
                rs.getDouble("entry_price"),
                rs.getDouble("stop_loss"),
                rs.getDouble("target"),
                rs.getInt("quantity"));
        tradeModel.setTradeId(rs.getString("trade_id"));
        tradeModel.setExitPrice(rs.getDouble("exit_price"));
        Timestamp exitTime = rs.getTimestamp("exit_time");
        if (exitTime != null) tradeModel.setExitTime(exitTime.toLocalDateTime());
        tradeModel.setPnl(rs.getDouble("pnl"));
        tradeModel.setTimeFrame(rs.getString("TIMEFRAME"));
        tradeModel.setStatus(TradeModel.Status.valueOf(rs.getString("status")));
        return tradeModel;
    }

    public List<StrategyConfig> loadActiveStrategies() throws Exception {
        List<StrategyConfig> strategies = new ArrayList<>();
        String sql = "SELECT id, name, symbol, timeframe, json_config FROM strategies WHERE status='ACTIVE'";
        ObjectMapper mapper = new ObjectMapper();
        try (Connection conn = conn();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                StrategyConfig cfg = mapper.readValue(rs.getString("json_config"), StrategyConfig.class);
                cfg.setId(rs.getInt("id"));
                cfg.setName(rs.getString("name"));
                cfg.setSymbol(rs.getString("symbol"));
                cfg.setTimeframe(rs.getString("timeframe"));
                strategies.add(cfg);
            }
        }
        return strategies;
    }

    public void persistBestStrategies(String watchListTable, String symbol, List<StrategyResult> bestConfigs) {
        if (StringUtils.isEmpty(watchListTable) || bestConfigs == null || bestConfigs.isEmpty()) {
            logger.warn("No best strategies to persist for symbol: {}", symbol);
            return;
        }

        String selectSql = "SELECT BEST_STRATEGY FROM " + watchListTable + " WHERE SYMBOL=?";
        String updateSql = "UPDATE " + watchListTable + " SET BEST_STRATEGY=? WHERE SYMBOL=?";

        try (Connection c = conn()) {
            ObjectMapper mapper = new ObjectMapper();
            List<StrategyResult> existing = new ArrayList<>();

            // === Step 1: Load existing JSON ===
            try (PreparedStatement ps = c.prepareStatement(selectSql)) {
                ps.setString(1, symbol);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getString("BEST_STRATEGY") != null) {
                        String json = rs.getString("BEST_STRATEGY");
                        if (StringUtils.isNotEmpty(json)){
                            existing = mapper.readValue(json,
                                    mapper.getTypeFactory().constructCollectionType(List.class, StrategyResult.class));
                        }

                    }
                }
            }

            // === Step 2: Check if new results are better ===
            boolean shouldUpdate = false;
            for (StrategyResult newRes : bestConfigs) {
                for (StrategyResult oldRes : existing) {
                    if (newRes.getName().equalsIgnoreCase(oldRes.getName())) {
                        // Compare winRate & pnl
                        if (newRes.getPnl() >= oldRes.getPnl() + 0.01 ||
                                newRes.getWinRate() > oldRes.getWinRate()) {
                            shouldUpdate = true;
                            break;
                        }
                    } else {
                        // Completely new strategy – consider updating
                        shouldUpdate = true;
                    }
                }
            }

            // === Step 3: Persist only if improved ===
            if (shouldUpdate) {
                String newJson = mapper.writeValueAsString(bestConfigs);
                try (PreparedStatement ps = c.prepareStatement(updateSql)) {
                    ps.setString(1, newJson);
                    ps.setString(2, symbol);
                    ps.executeUpdate();
                    logger.info("🏆 Updated BEST_STRATEGY for {} -> {}", symbol, newJson);
                }
            } else {
                logger.info("✅ No update: Existing strategies for {} are still better/equal", symbol);
            }
        } catch (Exception e) {
            logger.error("❌ Failed updating BEST_STRATEGY for {}: {}", symbol, e.getMessage(), e);
        }
    }





}
