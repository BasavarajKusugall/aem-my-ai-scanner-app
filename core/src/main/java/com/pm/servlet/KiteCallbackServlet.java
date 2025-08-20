package com.pm.servlet;

import com.pm.connectors.kite.KiteAuthService;
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
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/kite/callback",
                "sling.servlet.methods=GET"
        }
)
public class KiteCallbackServlet extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(KiteCallbackServlet.class);

    @Reference
    private KiteAuthService kiteAuthService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        // Extract request_token and account identifiers
        String requestToken = request.getParameter("request_token");
        String userId       = request.getParameter("user_id");
        String brokerName   = request.getParameter("broker_name");
        String accountNumber= request.getParameter("account_number");

        // Validate parameters
        if (requestToken == null || userId == null || brokerName == null || accountNumber == null) {
            response.setStatus(400);
            response.getWriter().write("Missing required parameters");
            log.warn("Callback missing parameters: request_token={}, user_id={}, broker_name={}, account_number={}",
                    requestToken, userId, brokerName, accountNumber);
            return;
        }

        log.info("Received request_token={} for user_id={} / broker={} / account={}",
                requestToken, userId, brokerName, accountNumber);

        // Option 1: Pass apiKey and apiSecret directly (if known here)
        String apiKey    = request.getParameter("api_key");    // Optional: from request param
        String apiSecret = request.getParameter("api_secret"); // Optional: from request param

        // Option 2: If apiKey/apiSecret stored in broker_accounts, KiteAuthService can fetch internally

        // Exchange request_token for access_token and store in DB
        String success = kiteAuthService.getAccessTokenAndStoreToken(
                requestToken,
                brokerName,
                accountNumber,
                apiKey,        // pass null if KiteAuthService handles fetching from DB
                apiSecret      // pass null if KiteAuthService handles fetching from DB
        );

        // Respond
        response.getWriter().write(success);
    }
}
