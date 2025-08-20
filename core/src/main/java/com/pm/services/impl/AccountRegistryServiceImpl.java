package com.pm.services.impl;

import com.GenericeConstants;
import com.pm.dao.DataSourcePoolProviderService;
import com.pm.dto.BrokerAccountRef;
import com.pm.services.AccountRegistryService;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Component(service = AccountRegistryService.class, immediate = true)
public class AccountRegistryServiceImpl implements AccountRegistryService {

    private static final Logger log = LoggerFactory.getLogger(AccountRegistryServiceImpl.class);

    // ANSI Colors for logging
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";

    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;

    @Override
    public List<BrokerAccountRef> findActiveAccounts(String brokerCode) {
        String sql =
                "SELECT * " +
                        "FROM user_broker_account uba " +
                        "JOIN broker b ON b.broker_id = uba.broker_id " +
                        "JOIN broker_token tok ON tok.broker_account_id = uba.account_id " +
                        "WHERE b.code = ? " +
                        "AND uba.status = 'ACTIVE' "+
                       "AND (tok.token_expiry IS NULL OR tok.token_expiry > NOW())";

        log.info(BLUE + "🔍 Looking up active accounts for broker: " + RESET + brokerCode);
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
                    String api_secret = rs.getString("api_secret");
                    String api_key = rs.getString("api_key");
                    String brokerAccountRefStr = rs.getString("broker_account_ref");
                    BrokerAccountRef newBrokerAccountRef = new BrokerAccountRef(userId, accountId, brokerCode, brokerAccountRef, accessToken, requestToken);
                    newBrokerAccountRef.setApiKey(api_key);
                    newBrokerAccountRef.setApiSecrete(api_secret);
                    newBrokerAccountRef.setBrokerAccountRef(brokerAccountRefStr);
                    out.add(newBrokerAccountRef);

                    log.info(GREEN + "✅ Found active account: " + RESET +
                            "AccountId=" + accountId + ", UserId=" + userId + ", Ref=" + brokerAccountRef);
                }
            }

            if (out.isEmpty()) {
                log.warn(YELLOW + "⚠️ No active accounts found for broker: " + RESET + brokerCode);
            } else {
                log.info(GREEN + "📊 Total active accounts found: " + RESET + out.size());
            }

        } catch (SQLException e) {
            log.error(RED + "❌ Account discovery failed for broker: " + brokerCode + " | Error: " + e.getMessage() + RESET, e);
            throw new RuntimeException("Account discovery failed: " + e.getMessage(), e);
        }

        return out;
    }
}
