package com.aem.ai.pm.services.impl;

import com.GenericeConstants;
import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.pm.dto.BrokerToken;
import com.aem.ai.pm.services.BrokerTokenService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

@Component(service = BrokerTokenService.class, immediate = true)
public class BrokerTokenServiceImpl implements BrokerTokenService {

    private static final Logger log = LoggerFactory.getLogger(BrokerTokenServiceImpl.class);

    // ANSI Colors
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";

    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;

    @Override
    public BrokerToken saveOrUpdate(BrokerToken token) {
        log.info(BLUE + "🟦 Starting saveOrUpdate() for brokerAccountId={} userId={}" + RESET,
                token.getBrokerAccountId(), token.getUserId());

        // Note: token_expiry is always NOW()
        String sql = "INSERT INTO broker_token " +
                "(user_id, broker_account_id, request_token, api_key, api_secret, access_token, token_expiry) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW()) " +
                "ON DUPLICATE KEY UPDATE " +
                "request_token=VALUES(request_token), " +
                "api_key=VALUES(api_key), " +
                "api_secret=VALUES(api_secret), " +
                "access_token=VALUES(access_token), " +
                "token_expiry=NOW()";

        DataSource dataSource = dataSourcePoolProviderService.getDataSourceByName(
                GenericeConstants.MYSQL_PORTFOLIO_MGMT
        );

        if (dataSource == null) {
            log.error(RED + "❌ DataSource not found: {}" + RESET, GenericeConstants.MYSQL_PORTFOLIO_MGMT);
            throw new RuntimeException("DataSource not found for name: " + GenericeConstants.MYSQL_PORTFOLIO_MGMT);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            log.debug(CYAN + "📥 Preparing SQL Insert/Update for broker_token" + RESET);

            ps.setLong(1, token.getUserId());
            ps.setLong(2, token.getBrokerAccountId());
            ps.setString(3, token.getRequestToken());
            ps.setString(4, token.getApiKey());
            ps.setString(5, token.getApiSecrete());
            ps.setString(6, token.getAccessToken());

            int rows = ps.executeUpdate();
            log.info(GREEN + "✅ saveOrUpdate executed successfully. Rows affected: {}" + RESET, rows);

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long newId = rs.getLong(1);
                    token.setId(newId);
                    log.info(GREEN + "🆔 New BrokerToken ID generated: {}" + RESET, newId);
                } else {
                    log.debug(YELLOW + "ℹ️ No new ID generated (record updated)." + RESET);
                }
            }

            // force token expiry in DTO also to now
            token.setTokenExpiry(java.time.LocalDateTime.now());

            return token;

        } catch (SQLException e) {
            log.error(RED + "❌ SQL Error in saveOrUpdate: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("Error saving broker_token", e);
        }
    }

    @Override
    public Optional<BrokerToken> findByAccount(long brokerAccountId) {
        log.info(BLUE + "🔍 Looking up BrokerToken for brokerAccountId={}" + RESET, brokerAccountId);

        String sql = "SELECT * FROM broker_token WHERE broker_account_id=?";
        DataSource dataSource = dataSourcePoolProviderService.getDataSourceByName(
                GenericeConstants.MYSQL_PORTFOLIO_MGMT
        );

        if (dataSource == null) {
            log.error(RED + "❌ DataSource not found: {}" + RESET, GenericeConstants.MYSQL_PORTFOLIO_MGMT);
            throw new RuntimeException("DataSource not found for name: " + GenericeConstants.MYSQL_PORTFOLIO_MGMT);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, brokerAccountId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BrokerToken t = new BrokerToken();
                    t.setId(rs.getLong("id"));
                    t.setUserId(rs.getLong("user_id"));
                    t.setBrokerAccountId(rs.getLong("broker_account_id"));
                    t.setRequestToken(rs.getString("request_token"));
                    t.setApiKey(rs.getString("api_key"));
                    t.setApiSecrete(rs.getString("api_secret"));
                    t.setAccessToken(rs.getString("access_token"));

                    Timestamp ts = rs.getTimestamp("token_expiry");
                    if (ts != null) {
                        t.setTokenExpiry(ts.toLocalDateTime());
                        log.debug(CYAN + "⏳ token_expiry loaded: {}" + RESET, ts.toLocalDateTime());
                    }

                    log.info(GREEN + "✅ BrokerToken found: id={} brokerAccountId={} userId={}" + RESET,
                            t.getId(), t.getBrokerAccountId(), t.getUserId());

                    return Optional.of(t);
                } else {
                    log.warn(YELLOW + "⚠️ No BrokerToken found for brokerAccountId={}" + RESET, brokerAccountId);
                }
            }
        } catch (SQLException e) {
            log.error(RED + "❌ SQL Error in findByAccount: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("Error finding broker_token", e);
        }

        return Optional.empty();
    }
    @Override
    public long registerOrUpdate(String code, String name, String apiBaseUrl) {
        String sql = "INSERT INTO broker (code, name, api_base_url, status, created_at) " +
                "VALUES (?, ?, ?, 'ACTIVE', NOW()) " +
                "ON DUPLICATE KEY UPDATE " +
                "name = VALUES(name), " +
                "api_base_url = VALUES(api_base_url), " +
                "status = VALUES(status)";

        DataSource ds = dataSourcePoolProviderService.getDataSourceByName(
                GenericeConstants.MYSQL_PORTFOLIO_MGMT
        );

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, apiBaseUrl);

            int rows = ps.executeUpdate();
            log.info("✅ Broker upsert executed. Rows affected: {}", rows);

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long brokerId = rs.getLong(1);
                    log.info("🆔 New broker_id created: {}", brokerId);
                    return brokerId;
                }
            }

            // If not new, fetch broker_id by code
            try (PreparedStatement ps2 = conn.prepareStatement("SELECT broker_id FROM broker WHERE code=?")) {
                ps2.setString(1, code);
                try (ResultSet rs2 = ps2.executeQuery()) {
                    if (rs2.next()) {
                        return rs2.getLong(1);
                    }
                }
            }

            throw new RuntimeException("Failed to resolve broker_id for code=" + code);

        } catch (SQLException e) {
            throw new RuntimeException("Error saving broker", e);
        }
    }
}
