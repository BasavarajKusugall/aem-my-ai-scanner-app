package com.aem.ai.pm.servlet;


import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.pm.dto.BrokerToken;
import com.aem.ai.pm.dto.UserBrokerAccount;
import com.aem.ai.pm.services.BrokerTokenService;
import com.aem.ai.pm.services.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

/**
 * Updates broker_token access_token based on email + brokerAccountRef
 */
@Component(
        service = Servlet.class,
        immediate = true,
        property = {
                "sling.servlet.paths=/bin/broker/update-token",
                "sling.servlet.methods=POST"
        }
)
public class UpdateBrokerTokenServlet extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(UpdateBrokerTokenServlet.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Reference
    private UserService userService;

    @Reference
    private BrokerTokenService brokerTokenService;

    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        try {
            JsonNode json = mapper.readTree(request.getInputStream());
            String email = json.path("email").asText();
            String brokerAccountRef = json.path("brokerAccountRef").asText();
            String brokerName = json.path("brokerName").asText();
            String accessToken = json.path("accessToken").asText();

            if ( brokerAccountRef.isEmpty() || accessToken.isEmpty()) {
                response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);

                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Missing required fields:  brokerAccountRef,  accessToken\"}");
                return;
            }

            // 1. Resolve user by email
            /*var userOpt = userService.findByIdOrEmail(email); // implement/find existing service
            if (userOpt.isEmpty()) {
                response.sendError(404, "User not found for email: " + email);
                return;
            }
            long userId = userOpt.get().getUserId();

            // 2. Resolve accountId from brokerAccountRef
            UserBrokerAccount account = findUserBrokerAccount(email, brokerName, brokerAccountRef);
            if (account == null) {
                response.sendError(404, "Broker account not found for ref: " + brokerAccountRef);
                return;
            }

            // 3. Update token
            BrokerToken token = new BrokerToken();
            token.setUserId(userId);
            token.setBrokerAccountId(account.getAccountId());
            token.setAccessToken(accessToken);*/

            brokerTokenService.updateAccessTokenByRef(brokerAccountRef, accessToken);

            response.setContentType("application/json");
            response.setStatus(SC_OK);
            response.getWriter().write("{\"status\":\"success\",\"message\":\"Token updated\"}");
            log.info("✅ Token updated for email={} brokerAccountRef={} brokerName={}", email, brokerAccountRef, brokerName);

        } catch (Exception e) {
            log.error("❌ Error updating broker token: {}", e.getMessage(), e);
            response.sendError(SC_INTERNAL_SERVER_ERROR, "Internal Server Error: " + e.getMessage());
        }
    }

    private UserBrokerAccount findUserBrokerAccount(String email, String brokerName, String brokerAccountRef) {
        UserBrokerAccount userBrokerAccount = brokerTokenService.findUserBrokerAccount(email, brokerName, brokerAccountRef);
        if (null != userBrokerAccount){
            return userBrokerAccount;
        }
        return  null;

    }
}

