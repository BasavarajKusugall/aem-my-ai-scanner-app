package com.aem.ai.pm.services;

import com.aem.ai.pm.dto.UserBrokerAccount;
import com.aem.ai.pm.dto.UserRegistrationRequest;

public interface UserRegistrationService {
    UserBrokerAccount registerUserAndAccount(UserRegistrationRequest request);
}
