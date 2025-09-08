package com.aem.ai.pm.services;


import com.aem.ai.pm.dto.BrokerAccountRef;
import com.aem.ai.pm.dto.UserBrokerAccount;
import com.aem.ai.pm.dto.UserRegistrationRequest;

import java.util.List;
import java.util.Optional;

public interface AccountRegistryService {
    /** Return all active accounts for this broker with fresh access tokens. */
    List<BrokerAccountRef> findActiveAccounts(String brokerCode);
    UserBrokerAccount registerOrUpdateUserBrokersAccounts(UserRegistrationRequest account);
    Optional<UserBrokerAccount> findById(long accountId);
}
