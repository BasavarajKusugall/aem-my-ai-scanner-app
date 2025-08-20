package com.pm.dao.impl;

import com.pm.dao.PortfolioDao;
import com.pm.dto.*;
import com.pm.services.InstrumentResolver;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

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
                int[] results = ps.executeBatch();
                log.info("{}✅ Holdings upserted, batchSize={} rowsUpdated={}{}",
                        GREEN, snap.holdings.size(), results.length, RESET);
            }

            // 2) POSITIONS
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
                int[] results = ps.executeBatch();
                log.info("{}✅ Positions upserted, batchSize={} rowsUpdated={}{}",
                        GREEN, snap.positions.size(), results.length, RESET);
            }

            // 3) CASH
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cash_snapshot(user_broker_account_id, as_of, available, used, created_at) VALUES(?,?,?,?,NOW())")) {
                ps.setLong(1, acc.userBrokerAccountId);
                ps.setTimestamp(2, Timestamp.from(snap.asOf));
                ps.setBigDecimal(3, snap.cash.available);
                ps.setBigDecimal(4, snap.cash.used);
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
