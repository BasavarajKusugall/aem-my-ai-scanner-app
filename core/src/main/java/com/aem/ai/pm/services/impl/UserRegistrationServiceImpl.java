package com.aem.ai.pm.services.impl;

import com.aem.ai.pm.dto.AppUser;
import com.aem.ai.pm.dto.BrokerToken;
import com.aem.ai.pm.dto.UserBrokerAccount;
import com.aem.ai.pm.dto.UserRegistrationRequest;
import com.aem.ai.pm.services.AccountRegistryService;
import com.aem.ai.pm.services.BrokerTokenService;
import com.aem.ai.pm.services.UserRegistrationService;
import com.aem.ai.pm.services.UserService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = UserRegistrationService.class, immediate = true)
public class UserRegistrationServiceImpl implements UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationServiceImpl.class);

    // ANSI Colors
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";

    @Reference private UserService userService;
    @Reference private AccountRegistryService accountService;
    @Reference private BrokerTokenService tokenService;

    @Override
    public UserBrokerAccount registerUserAndAccount(UserRegistrationRequest request) {
        log.info(BLUE + "🟦 Starting user + account registration process..." + RESET);

        try {
            // 1. Ensure Broker exists
            UserBrokerAccount userBrokerAccount = request.getAccount();
            long brokerId = tokenService.registerOrUpdate(
                    userBrokerAccount.getBrokerCode(),
                    userBrokerAccount.getBrokerName(),
                    userBrokerAccount.getApiBaseUrl()
            );
            userBrokerAccount.setBrokerId(brokerId);
            log.info(GREEN + "🟩 Broker registered/updated successfully. brokerId={}" + RESET, brokerId);

            // Register or update user
            AppUser user = userService.registerOrUpdate(request.getUser());
            log.info(GREEN + "👤 User registered/updated successfully. userId={}" + RESET, user.getUserId());

            // Register or update account
            userBrokerAccount.setUserId(user.getUserId());
            UserBrokerAccount savedAccount = accountService.registerOrUpdateUserBrokersAccounts(request);
            log.info(GREEN + "💼 Account registered/updated successfully. accountId={} for userId={}" + RESET,
                    savedAccount.getAccountId(), user.getUserId());

            // Register token if present
            if (request.getToken() != null) {
                BrokerToken token = request.getToken();
                token.setUserId(user.getUserId());
                token.setBrokerAccountId(savedAccount.getAccountId());
                log.info(CYAN + "🔑 Token provided in request. Preparing to save for accountId={}..." + RESET,
                        savedAccount.getAccountId());

                // Uncomment when token saving is enabled
                 tokenService.saveOrUpdate(savedAccount,token);
                log.debug(YELLOW + "⚠️ Token saving is currently commented out (disabled)." + RESET);
            } else {
                log.warn(YELLOW + "⚠️ No token provided in registration request. Skipping token save." + RESET);
            }

            log.info(GREEN + "✅ Registration process completed successfully for userId={} accountId={}" + RESET,
                    user.getUserId(), savedAccount.getAccountId());

            return savedAccount;

        } catch (Exception e) {
            log.error(RED + "❌ Registration failed: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("User + Account registration failed", e);
        }
    }
}
