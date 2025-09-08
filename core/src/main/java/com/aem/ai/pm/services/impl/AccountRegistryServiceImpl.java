package com.aem.ai.pm.services.impl;

import com.GenericeConstants;
import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.pm.dto.BrokerAccountRef;
import com.aem.ai.pm.dto.BrokerToken;
import com.aem.ai.pm.dto.UserBrokerAccount;
import com.aem.ai.pm.dto.UserRegistrationRequest;
import com.aem.ai.pm.services.AccountRegistryService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component(service = AccountRegistryService.class, immediate = true)
public class AccountRegistryServiceImpl implements AccountRegistryService {

    private static final Logger log = LoggerFactory.getLogger(AccountRegistryServiceImpl.class);

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
    public UserBrokerAccount registerOrUpdateUserBrokersAccounts(UserRegistrationRequest request) {
        UserBrokerAccount account = request.getAccount();
        BrokerToken token = request.getToken();
        if (account == null) {
            log.error(RED + "❌ No UserBrokerAccount provided in request" + RESET);
            throw new IllegalArgumentException("UserBrokerAccount must not be null");
        }
        log.info(BLUE + "🟦 Starting registerOrUpdate() for userId={} brokerId={} ref={}" + RESET,
                account.getUserId(), account.getBrokerId(), account.getBrokerAccountRef());

        DataSource dataSource = dataSourcePoolProviderService.getDataSourceByName(
                GenericeConstants.MYSQL_PORTFOLIO_MGMT
        );

        String sql = "INSERT INTO user_broker_account " +
                "(user_id, broker_id, broker_account_ref, account_alias, base_currency, status, api_key, api_secret, request_token, broker_name) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "broker_account_ref=VALUES(broker_account_ref), " +
                "api_key=VALUES(api_key), " +
                "api_secret=VALUES(api_secret), " +
                "request_token=VALUES(request_token), " +
                "broker_name=VALUES(broker_name)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, account.getUserId());
            ps.setLong(2, account.getBrokerId());
            ps.setString(3, account.getBrokerAccountRef());
            ps.setString(4, account.getAccountAlias());
            ps.setString(5, account.getBaseCurrency());
            ps.setString(6, account.getStatus());
            ps.setString(7, token.getApiKey());
            ps.setString(8, token.getApiSecrete());
            ps.setString(9, token.getRequestToken());
            ps.setString(10, account.getBrokerName());

            int rows = ps.executeUpdate();
            log.info(GREEN + "✅ registerOrUpdate executed successfully. Rows affected: {}" + RESET, rows);
            conn.commit();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long generatedId = rs.getLong(1);
                    account.setAccountId(generatedId);
                    log.info(GREEN + "🆔 New AccountId generated: {}" + RESET, generatedId);
                } else {
                    // Record already existed → fetch existing account_id
                    String lookupSql = "SELECT account_id FROM user_broker_account WHERE user_id=? AND broker_id=?";
                    try (PreparedStatement ps2 = conn.prepareStatement(lookupSql)) {
                        ps2.setLong(1, account.getUserId());
                        ps2.setLong(2, account.getBrokerId());
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            if (rs2.next()) {
                                long existingId = rs2.getLong("account_id");
                                account.setAccountId(existingId);
                                log.info(YELLOW + "ℹ️ Existing AccountId found: {}" + RESET, existingId);
                            } else {
                                throw new SQLException("Unable to retrieve account_id after update.");
                            }
                        }
                    }
                }
            }

            return account;

        } catch (SQLException e) {
            log.error(RED + "❌ SQL Error in registerOrUpdate: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("Error saving user_broker_account", e);
        }
    }


    @Override
    public Optional<UserBrokerAccount> findById(long accountId) {
        log.info(BLUE + "🔎 Searching for accountId={}" + RESET, accountId);

        DataSource dataSource = dataSourcePoolProviderService.getDataSourceByName(
                GenericeConstants.MYSQL_PORTFOLIO_MGMT
        );
        String sql = "SELECT * FROM user_broker_account WHERE account_id=?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, accountId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UserBrokerAccount acc = new UserBrokerAccount();
                    acc.setAccountId(rs.getLong("account_id"));
                    acc.setUserId(rs.getLong("user_id"));
                    acc.setBrokerId(rs.getLong("broker_id"));
                    acc.setBrokerAccountRef(rs.getString("broker_account_ref"));
                    acc.setAccountAlias(rs.getString("account_alias"));
                    acc.setBaseCurrency(rs.getString("base_currency"));
                    acc.setStatus(rs.getString("status"));

                    log.info(GREEN + "✅ Account found: {}" + RESET, acc);
                    return Optional.of(acc);
                } else {
                    log.warn(YELLOW + "⚠️ No account found for accountId={}" + RESET, accountId);
                }
            }
        } catch (SQLException e) {
            log.error(RED + "❌ SQL Error in findById: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("Error finding user_broker_account", e);
        }
        return Optional.empty();
    }

    @Override
    public List<BrokerAccountRef> findActiveAccounts(String brokerCode) {
        log.info(BLUE + "🔍 Looking up active accounts for broker: {}" + RESET, brokerCode);

        String sql =
                "SELECT * " +
                        "FROM user_broker_account uba " +
                        "JOIN broker b ON b.broker_id = uba.broker_id " +
                        "JOIN broker_token tok ON tok.broker_account_id = uba.account_id " +
                        "WHERE b.code = ? " +
                        "AND uba.status = 'ACTIVE' " +
                        "AND (tok.token_expiry IS NULL OR tok.token_expiry > NOW())";

        DataSource dataSource = dataSourcePoolProviderService.getDataSourceByName(
                GenericeConstants.MYSQL_PORTFOLIO_MGMT
        );

        List<BrokerAccountRef> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, brokerCode);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long accountId = rs.getLong("account_id");
                    long userId = rs.getLong("user_id");
                    String brokerAccountRef = rs.getString("broker_account_ref");
                    String accessToken = rs.getString("access_token");
                    String requestToken = rs.getString("request_token");
                    String apiSecret = rs.getString("api_secret");
                    String apiKey = rs.getString("api_key");

                    BrokerAccountRef ref = new BrokerAccountRef(userId, accountId, brokerCode, brokerAccountRef, accessToken, requestToken);
                    ref.setApiKey(apiKey);
                    ref.setApiSecrete(apiSecret);
                    ref.setBrokerAccountRef(brokerAccountRef);

                    out.add(ref);

                    log.info(GREEN + "✅ Active account found: accountId={}, userId={}, ref={}" + RESET,
                            accountId, userId, brokerAccountRef);
                }
            }

            if (out.isEmpty()) {
                log.warn(YELLOW + "⚠️ No active accounts found for broker: {}" + RESET, brokerCode);
            } else {
                log.info(GREEN + "📊 Total active accounts: {}" + RESET, out.size());
            }

        } catch (SQLException e) {
            log.error(RED + "❌ SQL Error in findActiveAccounts: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("Account discovery failed", e);
        }

        return out;
    }
}
