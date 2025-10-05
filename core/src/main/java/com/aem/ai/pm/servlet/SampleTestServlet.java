package com.aem.ai.pm.servlet;

import com.aem.ai.pm.connectors.smartangel.SmartApiLoginService;
import com.aem.ai.realtime.brokers.kite.OrderService;
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
                "sling.servlet.paths=/bin/sample/test",
                "sling.servlet.methods=GET"
        }
)
public class SampleTestServlet extends SlingAllMethodsServlet {

    @Reference
    private OrderService orderService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {


    }
}
