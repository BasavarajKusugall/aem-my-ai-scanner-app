package com.aem.ai.pm.servlet;

import com.aem.ai.pm.connectors.kite.KiteAuthService;
import com.aem.ai.pm.dto.UserBrokerAccount;
import com.aem.ai.pm.services.BrokerTokenService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;

@Component(
        service = Servlet.class, immediate = true,
        property = {
                "sling.servlet.paths=/bin/kite/callback",
                "sling.servlet.methods=GET"
        }
)
public class KiteCallbackServlet extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(KiteCallbackServlet.class);

    @Reference
    private KiteAuthService kiteAuthService;

    @Reference
    private BrokerTokenService brokerTokenService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        // Extract request_token and account identifiers
        String requestToken = request.getParameter("request_token");
        String brokerAccountRef = request.getParameter("broker_account_ref");

        // Validate parameters
        if (requestToken == null || brokerAccountRef == null) {
            response.setStatus(400);
            response.getWriter().write("Missing required parameters");
            log.warn("Callback missing parameters: request_token={}, broker_account_ref={}, ",
                    requestToken, brokerAccountRef);
            return;
        }
        UserBrokerAccount userBrokerAccount = brokerTokenService.findUserBrokerAccountByBrokerAccountRef(brokerAccountRef);
        if (null == userBrokerAccount) {
            response.setStatus(400);
            response.getWriter().write("Invalid broker_account_ref");
            log.warn("Callback with invalid broker_account_ref={}", brokerAccountRef);
            return;
        }


        log.info("Received request_token={} for broker_account_ref={} ",
                requestToken, brokerAccountRef);

        // Option 1: Pass apiKey and apiSecret directly (if known here)
        String apiKey    = userBrokerAccount.getApiKey();    // Optional: from request param
        String apiSecret = userBrokerAccount.getApiSecrete(); // Optional: from request param

        // Option 2: If apiKey/apiSecret stored in broker_accounts, KiteAuthService can fetch internally

        // Exchange request_token for access_token and store in DB
        String success = kiteAuthService.getAccessTokenAndStoreToken(
                requestToken,
                userBrokerAccount,
                brokerAccountRef,
                apiKey,        // pass null if KiteAuthService handles fetching from DB
                apiSecret ,
                "ZERODHA",
                userBrokerAccount.getUserId()
        );

        // Respond
        response.getWriter().write("{\"status\":\"success\",\"message\":\"Token updated\"}");
    }
}
