package com.pm.services;


import com.pm.dto.BrokerAccountRef;
import java.util.List;

public interface AccountRegistryService {
    /** Return all active accounts for this broker with fresh access tokens. */
    List<BrokerAccountRef> findActiveAccounts(String brokerCode);
}
