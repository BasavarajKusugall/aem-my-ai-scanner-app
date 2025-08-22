package com.pm.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pm.dto.UserBrokerAccount;
import com.pm.dto.UserRegistrationRequest;
import com.pm.services.UserRegistrationService;
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
                "sling.servlet.paths=/bin/user/register",
                "sling.servlet.methods=POST"
        }
)
public class UserRegistrationServlet extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationServlet.class);

    // ANSI Colors
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";

    @Reference
    private UserRegistrationService registrationService;

    private final ObjectMapper mapper = new ObjectMapper();


    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        long start = System.currentTimeMillis();
        log.info(BLUE + "🟦 Incoming request: POST {}" + RESET, request.getRequestPathInfo().getResourcePath());

        try {
            //todo telegram_user_id is not part of UserRegistrationRequest, need to handle it separately
            UserRegistrationRequest regReq = mapper.readValue(request.getInputStream(), UserRegistrationRequest.class);
            log.debug(CYAN + "📥 Parsed UserRegistrationRequest: UserRegistrationRequest={}" + RESET,
                    regReq);

            // Delegate to Service
            UserBrokerAccount account = registrationService.registerUserAndAccount(regReq);

            // Respond with JSON
            response.setContentType("application/json");
            mapper.writeValue(response.getWriter(), account);

            long took = System.currentTimeMillis() - start;
            log.info(GREEN + "✅ User registration successful. userId={} accountId={} took={}ms" + RESET,
                    account.getUserId(), account.getAccountId(), took);

        } catch (Exception e) {
            long took = System.currentTimeMillis() - start;
            log.error(RED + "❌ Error in UserRegistrationServlet: {} (took={}ms)" + RESET, e.getMessage(), took, e);
            response.sendError(500, "Error registering user: " + e.getMessage());
        }
    }
}
