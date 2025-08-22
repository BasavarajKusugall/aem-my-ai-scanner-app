package com.pm.services;

import com.pm.dto.UserBrokerAccount;
import com.pm.dto.UserRegistrationRequest;

public interface UserRegistrationService {
    UserBrokerAccount registerUserAndAccount(UserRegistrationRequest request);
}
