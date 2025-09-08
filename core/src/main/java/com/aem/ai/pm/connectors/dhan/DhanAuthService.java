package com.aem.ai.pm.connectors.dhan;

import com.aem.ai.pm.dto.BrokerException;

public interface DhanAuthService {
    String exchangeForAccessToken(String requestToken) throws BrokerException;
}
