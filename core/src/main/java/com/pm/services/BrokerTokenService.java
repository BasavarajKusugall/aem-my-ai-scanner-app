package com.pm.services;

import com.pm.dto.BrokerToken;
import java.util.Optional;

public interface BrokerTokenService {
    BrokerToken saveOrUpdate(BrokerToken token);
    Optional<BrokerToken> findByAccount(long brokerAccountId);
    long registerOrUpdate(String code, String name, String apiBaseUrl);
}
