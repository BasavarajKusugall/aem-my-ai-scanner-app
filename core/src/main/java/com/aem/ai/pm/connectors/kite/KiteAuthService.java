package com.aem.ai.pm.connectors.kite;

import com.aem.ai.pm.dto.UserBrokerAccount;

public interface KiteAuthService {
    /**
     * Exchanges the request_token for an access_token and stores in DB
     *
     * @param requestToken      the request_token returned by Kite Connect login
     * @param userBrokerAccount
     * @return true if success, false otherwise
     */
    String getAccessTokenAndStoreToken(String requestToken, UserBrokerAccount userBrokerAccount, String accountNumber, String apiKey, String apiSecret,String brokerName,long userId) ;
}
