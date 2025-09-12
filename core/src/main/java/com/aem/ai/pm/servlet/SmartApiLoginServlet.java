package com.aem.ai.pm.servlet;

import com.aem.ai.pm.connectors.smartangel.SmartApiLoginService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/smartapi/login",
                "sling.servlet.methods=GET"
        }
)
public class SmartApiLoginServlet extends SlingAllMethodsServlet {

    @Reference
    private SmartApiLoginService loginService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        String clientId = request.getParameter("clientId");
        String password = request.getParameter("password");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (clientId == null || password == null) {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Missing clientId or password");
            mapper.writeValue(response.getWriter(), error);
            return;
        }

        try {
            Map<String, Object> tokens = loginService.login(clientId, password);
            mapper.writeValue(response.getWriter(), tokens);
        } catch (Exception e) {
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Login failed: " + e.getMessage());
            mapper.writeValue(response.getWriter(), error);
        }
    }
}
