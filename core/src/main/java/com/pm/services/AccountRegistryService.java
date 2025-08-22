package com.pm.services;


import com.pm.dto.BrokerAccountRef;
import com.pm.dto.UserBrokerAccount;
import com.pm.dto.UserRegistrationRequest;

import java.util.List;
import java.util.Optional;

public interface AccountRegistryService {
    /** Return all active accounts for this broker with fresh access tokens. */
    List<BrokerAccountRef> findActiveAccounts(String brokerCode);
    UserBrokerAccount registerOrUpdate(UserRegistrationRequest account);
    Optional<UserBrokerAccount> findById(long accountId);
}
