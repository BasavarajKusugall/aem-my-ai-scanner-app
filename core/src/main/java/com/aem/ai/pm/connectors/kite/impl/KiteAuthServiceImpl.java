package com.aem.ai.pm.connectors.kite.impl;

import com.GenericeConstants;
import com.aem.ai.pm.connectors.kite.KiteAuthService;
import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.pm.net.HttpClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

@Component(service = KiteAuthService.class, immediate = true)
public class KiteAuthServiceImpl implements KiteAuthService {

    private static final Logger log = LoggerFactory.getLogger(KiteAuthServiceImpl.class);

    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;

    @Reference
    private HttpClientService http;

    private static final ObjectMapper om = new ObjectMapper();
    private String sql;

    private DataSource getDataSource() {
        return dataSourcePoolProviderService.getDataSourceByName(GenericeConstants.MYSQL_PORTFOLIO_MGMT);
    }

    public void refreshTokensFromBrokerAccounts() {
        String sql = "SELECT user_id, broker_name, broker_account_ref, api_key, api_secret, request_token " +
                "FROM user_broker_account WHERE request_token IS NOT NULL";

        try (Connection con = getDataSource().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String userId = rs.getString("user_id");
                String brokerName = rs.getString("broker_name");
                String brokerAccountRef = rs.getString("broker_account_ref");
                String apiKey = rs.getString("api_key");
                String apiSecret = rs.getString("api_secret");
                String requestToken = rs.getString("request_token");

                if (StringUtils.isNotBlank(requestToken)) {
                    String accessToken = getAccessTokenAndStoreToken(requestToken, brokerName, brokerAccountRef, apiKey, apiSecret);
                    if (StringUtils.isNotEmpty(accessToken)) {
                        log.info("✅ Token refreshed for user={} broker={} account={}", userId, brokerName, brokerAccountRef);
                    } else {
                        log.warn("⚠️ Token refresh failed for user={} broker={} account={}", userId, brokerName, brokerAccountRef);
                    }
                }
            }

        } catch (Exception e) {
            log.error("❌ Error refreshing tokens from broker_accounts", e);
        }
    }

    @Override
    public String getAccessTokenAndStoreToken(String requestToken, String brokerName,
                                              String brokerAccountRef, String apiKey, String apiSecret) {
        try {
            // 1️⃣ Check if a valid access token exists
            String existingToken = fetchExistingAccessToken(brokerName, brokerAccountRef);
            if (StringUtils.isNotEmpty(existingToken) && isAccessTokenValid(existingToken, apiKey)) {
                log.info("✅ Using existing valid access token for broker={} / account={}", brokerName, brokerAccountRef);
                return existingToken;
            }

            // 2️⃣ If no valid token, request new access token
            log.info("Requesting new access token for broker={} / account={}", brokerName, brokerAccountRef);

            String url = "https://api.kite.trade/session/token";
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");

            String body = String.format("api_key=%s&request_token=%s&checksum=%s",
                    apiKey, requestToken, generateChecksum(apiKey, requestToken, apiSecret));

            String responseJson = http.post(url, headers, body, 5000);
            JsonNode userNode = om.readTree(responseJson).path("data");
            String accessToken = userNode.path("access_token").asText();
            String publicToken = userNode.path("public_token").asText();
            long tokenExpiry = 24 * 60 * 60;

            Long brokerAccountId = getBrokerAccountId(userNode.path("user_id").asText(), brokerName, brokerAccountRef);
            if (brokerAccountId == null) {
                log.error("Broker account not found for user {} / broker {} / account {}",
                        userNode.path("user_id").asText(), brokerName, brokerAccountRef);
                return null;
            }

            // 3️⃣ Save new token in DB
            upsertBrokerToken(brokerAccountId, String.valueOf(brokerAccountId), brokerName,
                    accessToken, publicToken, String.valueOf(tokenExpiry));

            log.info("✅ New access token stored successfully for broker={} / account={}", brokerName, brokerAccountRef);
            return accessToken;

        } catch (Exception e) {
            log.error("Exception during access token fetch: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }


    private String generateChecksum(String apiKey, String requestToken, String apiSecret) {
        // Kite expects SHA256(apiKey + requestToken + apiSecret)
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((apiKey + requestToken + apiSecret).getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate checksum", e);
        }
    }

    private Long getBrokerAccountId(String userId, String brokerName, String brokerAccountRef) {
        if (StringUtils.isBlank(userId)) {
            log.error("UserId is empty, cannot fetch broker account for broker={} / account={}", brokerName, brokerAccountRef);
            return null;
        }

        try (Connection con = getDataSource().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT *\n" +
                             "FROM user_broker_account uba" +
                             "INNER JOIN broker_token bt" +
                             "    ON bt.id = uba.account_id" +
                             "WHERE bt.broker_name = ?" +
                             "  AND uba.broker_account_ref = ?"
             )) {
            ps.setString(1, brokerName);
            ps.setString(2, brokerAccountRef);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("user_id");
                log.warn("Broker account not found for user {} / broker {} / account {}", userId, brokerName, brokerAccountRef);
            }
        } catch (NumberFormatException nfe) {
            log.error("Invalid userId '{}' for broker={} / account={}", userId, brokerName, brokerAccountRef, nfe);
        } catch (Exception e) {
            log.error("Error fetching broker_account_id: {}", e.getMessage(), e);
        }
        return null;
    }


    private void upsertBrokerToken(Long brokerAccountId, String userId, String brokerName,
                                   String accessToken, String refreshToken, String tokenExpiry) {
        try (Connection con = getDataSource().getConnection()) {
            // Create table if not exists
            String createTableSQL = "CREATE TABLE IF NOT EXISTS broker_token (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT NOT NULL," +
                    "broker_account_id BIGINT NOT NULL," +
                    "broker_name VARCHAR(50)," +
                    "access_token VARCHAR(512)," +
                    "refresh_token VARCHAR(512)," +
                    "token_expiry VARCHAR(128)," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uq_broker_account (broker_account_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            try (PreparedStatement ps = con.prepareStatement(createTableSQL)) { ps.execute(); }

            // Upsert token
            String upsertSQL = "INSERT INTO broker_token " +
                    "(user_id, broker_account_id, broker_name, access_token, refresh_token, token_expiry) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "access_token=VALUES(access_token), " +
                    "refresh_token=VALUES(refresh_token), " +
                    "token_expiry=VALUES(token_expiry), " +
                    "updated_at=CURRENT_TIMESTAMP";

            try (PreparedStatement ps = con.prepareStatement(upsertSQL)) {
                ps.setLong(1, Long.parseLong(userId));
                ps.setLong(2, brokerAccountId);
                ps.setString(3, brokerName);
                ps.setString(4, accessToken);
                ps.setString(5, refreshToken);
                ps.setString(6, tokenExpiry);
                ps.executeUpdate();
                log.info("Broker token upserted for broker_account_id {}", brokerAccountId);
            }

        } catch (Exception e) {
            log.error("Exception during broker_token insert/update: {}", e.getMessage(), e);
        }
    }

    // Fetch existing access token from DB
    private String fetchExistingAccessToken(String brokerName, String brokerAccountRef) {
        sql = "SELECT bt.access_token FROM broker_token bt " +
                "INNER JOIN user_broker_account uba ON bt.broker_account_id = uba.account_id " +
                "WHERE bt.broker_name = ? AND uba.broker_account_ref = ?";
        try (Connection con = getDataSource().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     sql
             )) {
            ps.setString(1, brokerName);
            ps.setString(2, brokerAccountRef);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("bt.access_token");
            }
        } catch (Exception e) {
            log.error("Error fetching existing access token: {}", e.getMessage(), e);
        }
        return null;
    }

    // Validate token by calling Kite API
    private boolean isAccessTokenValid(String accessToken, String apiKey) {
        try {
            String url = "https://api.kite.trade/user/profile";
            Map<String, String> headers = Map.of(
                    "X-Kite-Version", "3",
                    "Authorization", "token " + apiKey + ":" + accessToken
            );
            String response = http.get(url, headers,  3000);  // Empty body for GET-like POST
            JsonNode root = om.readTree(response);
            return root.path("status").asText("").equalsIgnoreCase("success");
        } catch (Exception e) {
            log.warn("Access token validation failed: {}", e.getMessage());
            return false;
        }
    }

}
