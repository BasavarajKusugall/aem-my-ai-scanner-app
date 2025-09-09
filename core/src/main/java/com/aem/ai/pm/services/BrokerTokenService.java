package com.aem.ai.pm.services;

import com.aem.ai.pm.dto.BrokerToken;
import com.aem.ai.pm.dto.UserBrokerAccount;

import java.util.Optional;

public interface BrokerTokenService {
    UserBrokerAccount findUserBrokerAccountByBrokerAccountRef( String brokerAccountRef);
    BrokerToken findBrokerTokenByRef(String brokerAccountRef);
    BrokerToken saveOrUpdate(UserBrokerAccount userBrokerAccount, BrokerToken token);
    Optional<BrokerToken> findByAccount(long brokerAccountId);
    long registerOrUpdate(String code, String name, String apiBaseUrl);
    UserBrokerAccount findUserBrokerAccount(String email, String brokerName, String brokerAccountRef);
    BrokerToken updateAccessTokenByRef(String brokerAccountRef, String newAccessToken);
}
