package com.pm.dao.impl;

import com.GenericeConstants;
import com.pm.dao.DataSourcePoolProviderService;
import com.pm.dao.PortfolioDao;
import com.pm.dto.*;
import com.pm.services.InstrumentResolver;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Component(service = PortfolioDao.class, immediate = true)
public class PortfolioDaoImpl implements PortfolioDao {

    private static final Logger log = LoggerFactory.getLogger(PortfolioDaoImpl.class);

    @Reference
    private InstrumentResolver resolver;

    // ANSI colors
    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;

    @Override
    public List<UserBrokerAccount> fetchActiveAccounts() {
       DataSource dataSource = dataSourcePoolProviderService.getDataSourceByName(GenericeConstants.MYSQL_PORTFOLIO_MGMT);
        if (dataSource == null) {
            log.error(RED + "DataSource not found! Cannot perform portfolio sync." + RESET);
            return null;
        }
        List<UserBrokerAccount> list = new ArrayList<>();
        String sql = "SELECT account_id, user_id, broker_id, broker_name, broker_account_ref, " +
                "account_alias, portfolio_holding_json, portfolio_positions_json, telegram_bot_user_id " +
                "FROM user_broker_account " +
                "WHERE status='ACTIVE' " +
                "AND ((portfolio_holding_json IS NOT NULL AND portfolio_holding_json <> '' AND portfolio_holding_json <> '{}') " +
                "OR (portfolio_positions_json IS NOT NULL AND portfolio_positions_json <> '' AND portfolio_positions_json <> '{}'))";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UserBrokerAccount uba = new UserBrokerAccount();
                uba.setAccountId(rs.getLong("account_id"));
                uba.setUserId(rs.getLong("user_id"));
                uba.setBrokerId(rs.getLong("broker_id"));
                uba.setBrokerName(rs.getString("broker_name"));
                uba.setBrokerAccountRef(rs.getString("broker_account_ref"));
                uba.setAccountAlias(rs.getString("account_alias"));
                uba.setPortfolioHoldingJson(rs.getString("portfolio_holding_json"));
                uba.setPortfolioPositionsJson(rs.getString("portfolio_positions_json"));
                uba.setTelegramBotUserId(rs.getString("telegram_bot_user_id"));

                list.add(uba);
            }
        } catch (Exception e) {
            e.printStackTrace(); // replace with SLF4J logging
        }
        return list;
    }

    @Override
    public void updateUserBrokerAccountJson(Connection c, BrokerAccountRef acc, PortfolioSnapshot snap) {
        String holdingJson  = snap.getHoldingsJson();
        String positionJson = snap.getPositionsJson();
        if (StringUtils.isEmpty(holdingJson) && StringUtils.isEmpty(positionJson)){
            log.info("{}⚠️ No holdings or positions to update for user_broker_accountId={}{}",
                    YELLOW, acc.userBrokerAccountId, RESET);
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE user_broker_account " +
                        "SET portfolio_holding_json = ?, " +
                        "    portfolio_positions_json = ?, " +
                        "    updated_at = NOW() " +
                        "WHERE broker_account_ref = ?")) {

            // Serialize holdings and positions
            ps.setString(1, holdingJson);
            ps.setString(2, positionJson);
            ps.setString(3, acc.brokerAccountRef);

            int rows = ps.executeUpdate();
            log.info("{}📥 Updated user_broker_account JSON for ubaId={}, rows={}{}",
                    GREEN, acc.userBrokerAccountId, rows, RESET);

        } catch (Exception e) {
            log.error("{}❌ Failed to update user_broker_account JSON for ubaId={} : {}{}",
                    RED, acc.userBrokerAccountId, e.getMessage(), RESET, e);
            throw new RuntimeException("Failed to update user_broker_account JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public void upsertAccountSnapshot(Connection c, BrokerAccountRef acc, PortfolioSnapshot snap) {
        log.info("{}📊 Upserting portfolio snapshot for brokerAccountId={} asOf={}{}",
                CYAN, acc.userBrokerAccountId, snap.asOf, RESET);

        try {
            // 1) HOLDINGS
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO holding(user_broker_account_id, instrument_id, quantity, avg_cost, updated_at) " +
                            "VALUES(?,?,?,?,NOW()) " +
                            "ON DUPLICATE KEY UPDATE quantity=VALUES(quantity), avg_cost=VALUES(avg_cost), updated_at=NOW()")) {

                for (HoldingItem h : snap.holdings) {
                    long iid = resolver.resolveForHolding(h);
                    ps.setLong(1, acc.userBrokerAccountId);
                    ps.setLong(2, iid);
                    ps.setBigDecimal(3, h.quantity);
                    ps.setBigDecimal(4, h.avgCost);
                    ps.addBatch();
                    log.debug("{}➕ Holding upsert queued: accountId={}, instrumentId={}, qty={}, cost={}{}",
                            GREEN, acc.userBrokerAccountId, iid, h.quantity, h.avgCost, RESET);
                }
                log.info("Executed query: {}", ps.toString());
                int[] results = ps.executeBatch();
                log.info("{}✅ Holdings upserted, batchSize={} rowsUpdated={}{}",
                        GREEN, snap.holdings.size(), results.length, RESET);
            }

            // 2) POSITIONS
            if (snap.positions != null && !snap.positions.isEmpty()) {
                log.info("{}⚠️ No positions to upsert for accountId={}{}",
                        YELLOW, acc.userBrokerAccountId, RESET);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO position(user_broker_account_id, instrument_id, side, quantity, avg_price, pnl_realized, updated_at) " +
                                "VALUES(?,?,?,?,?,?,NOW()) " +
                                "ON DUPLICATE KEY UPDATE side=VALUES(side), quantity=VALUES(quantity), avg_price=VALUES(avg_price), pnl_realized=VALUES(pnl_realized), updated_at=NOW()")) {

                    for (PositionItem p : snap.positions) {
                        long iid = resolver.resolveForPosition(p);
                        ps.setLong(1, acc.userBrokerAccountId);
                        ps.setLong(2, iid);
                        ps.setString(3, p.side);
                        ps.setBigDecimal(4, p.quantity);
                        ps.setBigDecimal(5, p.avgPrice);
                        ps.setBigDecimal(6, p.pnlRealized == null ? java.math.BigDecimal.ZERO : p.pnlRealized);
                        ps.addBatch();
                        log.debug("{}➕ Position upsert queued: accountId={}, instrumentId={}, side={}, qty={}, price={}, pnl={}{}",
                                GREEN, acc.userBrokerAccountId, iid, p.side, p.quantity, p.avgPrice, p.pnlRealized, RESET);
                    }
                    log.info("Executed query for positions: {}", ps.toString());
                    int[] results = ps.executeBatch();
                    log.info("{}✅ Positions upserted, batchSize={} rowsUpdated={}{}",
                            GREEN, snap.positions.size(), results.length, RESET);
                }
            }


            // 3) CASH
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cash_snapshot(user_broker_account_id, as_of, available, used, created_at) VALUES(?,?,?,?,NOW())")) {
                ps.setLong(1, acc.userBrokerAccountId);
                ps.setTimestamp(2, Timestamp.from(snap.asOf));
                ps.setBigDecimal(3, snap.cash.available);
                ps.setBigDecimal(4, snap.cash.used);
                log.info("Executed query: {}", ps.toString());
                int rows = ps.executeUpdate();
                log.info("{}💰 Cash snapshot inserted: accountId={}, asOf={}, available={}, used={}, rows={}{}",
                        GREEN, acc.userBrokerAccountId, snap.asOf, snap.cash.available, snap.cash.used, rows, RESET);
            }

        } catch (SQLException e) {
            log.error("{}❌ Portfolio upsert failed for accountId={} : {}{}",
                    RED, acc.userBrokerAccountId, e.getMessage(), RESET, e);
            throw new RuntimeException("Portfolio upsert failed: " + e.getMessage(), e);
        }
    }

}
