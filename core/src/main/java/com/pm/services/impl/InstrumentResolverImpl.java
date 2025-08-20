package com.pm.services.impl;

import com.GenericeConstants;
import com.pm.dao.DataSourcePoolProviderService;
import com.pm.dto.*;
import com.pm.services.InstrumentResolver;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;

@Component(service = InstrumentResolver.class, immediate = true)
public class InstrumentResolverImpl implements InstrumentResolver {

    private static final Logger log = LoggerFactory.getLogger(InstrumentResolverImpl.class);

    // ANSI Colors
    private static final String RESET  = "\u001B[0m";
    private static final String GREEN  = "\u001B[32m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE   = "\u001B[34m";


    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;


    private DataSource getDataSource() {
        return dataSourcePoolProviderService.getDataSourceByName(GenericeConstants.MYSQL_PORTFOLIO_MGMT);
    }

    @Override
    public long resolveForHolding(HoldingItem h) {
        log.info(BLUE + "🔍 Resolving Holding: " + RESET +
                "ISIN=" + h.isin + ", Exchange=" + h.exchange + ", Symbol=" + h.symbol);
        DataSource dataSource = getDataSource();
        if (null == dataSource){
            log.error(RED + "❌ DataSource not found! Cannot resolve Holding instrument." + RESET);
            throw new RuntimeException("DataSource not found for portfolio management");
        }
        try (Connection c = dataSource.getConnection()) {
            Long id = byIsin(c, h.isin);
            if (id != null) {
                log.info(GREEN + "✅ Found instrument by ISIN: " + RESET + id);
                return id;
            }

            id = bySymbol(c, h.exchange, h.symbol);
            if (id != null) {
                log.info(GREEN + "✅ Found instrument by Symbol: " + RESET + id);
                return id;
            }

            long newId = insertStub(c, h.exchange, h.symbol, h.instrumentType, h.isin);
            log.warn(YELLOW + "⚠️ No match found. Inserted new stub instrument with ID=" + newId + RESET);
            return newId;

        } catch (SQLException e) {
            log.error(RED + "❌ Error resolving Holding instrument: " + e.getMessage() + RESET, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public long resolveForPosition(PositionItem p) {
        log.info(BLUE + "🔍 Resolving Position: " + RESET +
                "Exchange=" + p.exchange + ", Symbol=" + p.symbol +
                ", Expiry=" + p.expiry + ", Strike=" + p.strike + ", OptType=" + p.optionType);
        DataSource dataSource = getDataSource();
        if (dataSource == null ){
            log.error(RED + "❌ DataSource not found! Cannot resolve Position instrument." + RESET);
            throw new RuntimeException("DataSource not found for portfolio management");
        }
        try (Connection c = dataSource.getConnection()) {
            Long id = byDerivKey(c, p.exchange, p.symbol, p.expiry, p.strike, p.optionType);
            if (id != null) {
                log.info(GREEN + "✅ Found derivative instrument: " + RESET + id);
                return id;
            }

            id = bySymbol(c, p.exchange, p.symbol);
            if (id != null) {
                log.info(GREEN + "✅ Found underlying instrument: " + RESET + id);
                return id;
            }

            long newId = insertStub(c, p.exchange, p.symbol, p.instrumentType, null);
            log.warn(YELLOW + "⚠️ No match found. Inserted new stub instrument with ID=" + newId + RESET);
            return newId;

        } catch (SQLException e) {
            log.error(RED + "❌ Error resolving Position instrument: " + e.getMessage() + RESET, e);
            throw new RuntimeException(e);
        }
    }

    private Long byIsin(Connection c, String isin) throws SQLException {
        if (isin == null) return null;
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM instrument WHERE isin=?")) {
            ps.setString(1, isin);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private Long bySymbol(Connection c, String exch, String sym) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM instrument i JOIN instrument_identifier ii ON ii.instrument_id=i.instrument_id " +
                        "WHERE ii.exchange_code=? AND ii.symbol=?")) {
            ps.setString(1, exch);
            ps.setString(2, sym);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private Long byDerivKey(Connection c, String exch, String sym, String expiry,
                            java.math.BigDecimal strike, String opt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM instrument WHERE exchange_code=? AND tradingsymbol=? AND expiry_date=? AND strike_price=? AND option_type=?")) {
            ps.setString(1, exch);
            ps.setString(2, sym);
            ps.setString(3, expiry);
            ps.setBigDecimal(4, strike == null ? java.math.BigDecimal.ZERO : strike);
            ps.setString(5, opt);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private long insertStub(Connection c, String exch, String sym, String type, String isin) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO instrument(instrument_type,exchange_code,tradingsymbol,isin,created_at,updated_at) " +
                        "VALUES(?,?,?,?,NOW(),NOW())", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setString(2, exch);
            ps.setString(3, sym);
            ps.setString(4, isin);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
