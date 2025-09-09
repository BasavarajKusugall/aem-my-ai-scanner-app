package com.aem.ai.pm.services.impl;

import com.GenericeConstants;
import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.pm.dto.BrokerToken;
import com.aem.ai.pm.dto.UserBrokerAccount;
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
    public UserBrokerAccount findUserBrokerAccountByBrokerAccountRef( String brokerAccountRef) {
        DataSource ds = dataSourcePoolProviderService.getDataSourceByName(
                GenericeConstants.MYSQL_PORTFOLIO_MGMT
        );

        if (ds == null) {
            log.error(RED + "❌ DataSource not found: {}" + RESET, GenericeConstants.MYSQL_PORTFOLIO_MGMT);
            throw new RuntimeException("DataSource not found for name: " + GenericeConstants.MYSQL_PORTFOLIO_MGMT);
        }

        // Join app_user + user_broker_account + broker_token
        String sql = "SELECT u.user_id, u.email, u.full_name, u.phone, u.status AS user_status, " +
                "uba.account_id, uba.broker_id, uba.broker_name, uba.broker_account_ref, " +
                "uba.account_alias, uba.base_currency, uba.status AS account_status, " +
                "uba.api_key, uba.api_secret, uba.request_token, " +
                "uba.telegram_bot_user_id, uba.portfolio_positions_json, uba.portfolio_holding_json, " +
                "bt.id AS broker_token_id, bt.access_token AS token_access_token, bt.token_expiry " +
                "FROM app_user u " +
                "INNER JOIN user_broker_account uba ON u.user_id = uba.user_id " +
                "INNER JOIN broker_token bt ON uba.account_id = bt.broker_account_id AND u.user_id = bt.user_id " +
                "WHERE uba.broker_account_ref=?";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brokerAccountRef);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // hydrate UserBrokerAccount
                    UserBrokerAccount uba = new UserBrokerAccount();
                    uba.setAccountId(rs.getLong("account_id"));
                    uba.setBrokerName(rs.getString("broker_name"));
                    uba.setUserId(rs.getLong("user_id"));
                    uba.setBrokerAccountRef(rs.getString("broker_account_ref"));
                    uba.setStatus(rs.getString("account_status"));
                    uba.setApiKey(rs.getString("api_key"));
                    uba.setApiSecrete(rs.getString("api_secret"));
                    uba.setRequestToken(rs.getString("request_token"));


                    log.info(GREEN + "✅ Found UserBrokerAccount with BrokerToken: accountId={} broker_account_ref={}" + RESET,
                            uba.getAccountId(),  brokerAccountRef);

                    return uba;
                } else {
                    log.warn(YELLOW + "⚠️ No UserBrokerAccount found for broker_account_ref={} " + RESET,
                           brokerAccountRef);
                }
            }
        } catch (Exception e) {
            log.error(RED + "❌ SQL error in findUserBrokerAccount: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("Error finding UserBrokerAccount", e);
        }

        return null;
    }

    @Override
    public UserBrokerAccount findUserBrokerAccount(String email, String brokerName, String brokerAccountRef) {
        DataSource ds = dataSourcePoolProviderService.getDataSourceByName(
                GenericeConstants.MYSQL_PORTFOLIO_MGMT
        );

        if (ds == null) {
            log.error(RED + "❌ DataSource not found: {}" + RESET, GenericeConstants.MYSQL_PORTFOLIO_MGMT);
            throw new RuntimeException("DataSource not found for name: " + GenericeConstants.MYSQL_PORTFOLIO_MGMT);
        }

        // Join app_user + user_broker_account + broker_token
        String sql = "SELECT u.user_id, u.email, u.full_name, u.phone, u.status AS user_status, " +
                "uba.account_id, uba.broker_id, uba.broker_name, uba.broker_account_ref, " +
                "uba.account_alias, uba.base_currency, uba.status AS account_status, " +
                "uba.api_key, uba.api_secret, uba.request_token, " +
                "uba.telegram_bot_user_id, uba.portfolio_positions_json, uba.portfolio_holding_json, " +
                "bt.id AS broker_token_id, bt.access_token AS token_access_token, bt.token_expiry " +
                "FROM app_user u " +
                "INNER JOIN user_broker_account uba ON u.user_id = uba.user_id " +
                "INNER JOIN broker_token bt ON uba.account_id = bt.broker_account_id AND u.user_id = bt.user_id " +
                "WHERE u.email=? AND uba.broker_account_ref=?";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.setString(2, brokerAccountRef);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // hydrate UserBrokerAccount
                    UserBrokerAccount uba = new UserBrokerAccount();
                    uba.setAccountId(rs.getLong("account_id"));
                    uba.setUserId(rs.getLong("user_id"));
                    uba.setBrokerId(rs.getLong("broker_id"));
                    uba.setBrokerName(rs.getString("broker_name"));
                    uba.setBrokerAccountRef(rs.getString("broker_account_ref"));
                    uba.setAccountAlias(rs.getString("account_alias"));
                    uba.setBaseCurrency(rs.getString("base_currency"));
                    uba.setStatus(rs.getString("account_status"));
                    uba.setApiKey(rs.getString("api_key"));
                    uba.setApiSecrete(rs.getString("api_secret"));
                    uba.setRequestToken(rs.getString("request_token"));
                    uba.setTelegramBotUserId(rs.getString("telegram_bot_user_id"));
                    uba.setPortfolioPositionsJson(rs.getString("portfolio_positions_json"));
                    uba.setPortfolioHoldingJson(rs.getString("portfolio_holding_json"));

                    // hydrate BrokerToken if your DTO supports it
                    BrokerToken token = new BrokerToken();
                    token.setId(rs.getLong("broker_token_id"));
                    token.setUserId(rs.getLong("user_id"));
                    token.setBrokerAccountId(rs.getLong("account_id"));
                    Timestamp ts = rs.getTimestamp("token_expiry");
                    if (ts != null) {
                        token.setTokenExpiry(ts.toLocalDateTime());
                    }

                    // 🔑 If you want to link the token into the account DTO

                    log.info(GREEN + "✅ Found UserBrokerAccount with BrokerToken: accountId={} broker={} ref={}" + RESET,
                            uba.getAccountId(), brokerName, brokerAccountRef);

                    return uba;
                } else {
                    log.warn(YELLOW + "⚠️ No UserBrokerAccount found for userId={} broker={} ref={}" + RESET,
                            email, brokerName, brokerAccountRef);
                }
            }
        } catch (Exception e) {
            log.error(RED + "❌ SQL error in findUserBrokerAccount: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("Error finding UserBrokerAccount", e);
        }

        return null;
    }

    public BrokerToken updateAccessTokenByRef(String brokerAccountRef, String newAccessToken) {
        log.info(BLUE + "🔄 Updating access_token for broker_account_ref={}" + RESET, brokerAccountRef);

        String sql = "UPDATE broker_token bt " +
                "INNER JOIN user_broker_account uba ON bt.broker_account_id = uba.account_id " +
                "SET bt.access_token = ?, bt.token_expiry = NOW() " +
                "WHERE uba.broker_account_ref = ?";

        DataSource dataSource = dataSourcePoolProviderService.getDataSourceByName(
                GenericeConstants.MYSQL_PORTFOLIO_MGMT
        );

        if (dataSource == null) {
            log.error(RED + "❌ DataSource not found: {}" + RESET, GenericeConstants.MYSQL_PORTFOLIO_MGMT);
            throw new RuntimeException("DataSource not found for name: " + GenericeConstants.MYSQL_PORTFOLIO_MGMT);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newAccessToken);
            ps.setString(2, brokerAccountRef);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                conn.commit();
                log.info(GREEN + "✅ Access token updated successfully for broker_account_ref={}" + RESET, brokerAccountRef);

                // Re-fetch the updated token to return
                return findBrokerTokenByRef(brokerAccountRef);
            } else {
                log.warn(YELLOW + "⚠️ No rows updated. Invalid broker_account_ref={}?" + RESET, brokerAccountRef);
                return null;
            }

        } catch (SQLException e) {
            log.error(RED + "❌ SQL Error in updateAccessTokenByRef: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("Error updating access token", e);
        }
    }
    /**
     * Helper method to fetch BrokerToken by broker_account_ref.
     */
    public BrokerToken findBrokerTokenByRef(String brokerAccountRef) {
        String sql = "SELECT * " +
                "FROM broker_token bt " +
                "INNER JOIN user_broker_account uba ON bt.broker_account_id = uba.account_id " +
                "WHERE uba.broker_account_ref = ?";

        DataSource dataSource = dataSourcePoolProviderService.getDataSourceByName(
                GenericeConstants.MYSQL_PORTFOLIO_MGMT
        );

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, brokerAccountRef);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BrokerToken token = new BrokerToken();
                    token.setId(rs.getLong("id"));
                    token.setUserId(rs.getLong("user_id"));
                    token.setBrokerAccountId(rs.getLong("broker_account_id"));
                    token.setAccessToken(rs.getString("access_token"));

                    Timestamp ts = rs.getTimestamp("token_expiry");
                    if (ts != null) {
                        token.setTokenExpiry(ts.toLocalDateTime());
                    }

                    return token;
                }
            }
        } catch (SQLException e) {
            log.error(RED + "❌ SQL Error in findBrokerTokenByRef: {}" + RESET, e.getMessage(), e);
        }

        return null;
    }



    @Override
    public BrokerToken saveOrUpdate(UserBrokerAccount userBrokerAccount, BrokerToken token) {
        log.info(BLUE + "🟦 Starting saveOrUpdate() for brokerAccountId={} userId={}" + RESET,
                token.getBrokerAccountId(), token.getUserId());

        // Note: token_expiry is always NOW()
        String sql = "INSERT INTO broker_token " +
                "(user_id, broker_account_id, access_token,broker_name, token_expiry) " +
                "VALUES (?, ?, ?,?, NOW()) " +
                "ON DUPLICATE KEY UPDATE " +
                "broker_account_id=VALUES(broker_account_id), " +
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
            ps.setLong(2, userBrokerAccount.getAccountId());
            ps.setString(3, token.getAccessToken());
            ps.setString(4, userBrokerAccount.getBrokerName());

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
            conn.commit();
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
