package com.aem.ai.pm.connectors.dhan.impl;


import com.aem.ai.pm.config.DhanConfig;
import com.aem.ai.pm.connectors.dhan.DhanAuthService;
import com.aem.ai.pm.dto.BrokerException;
import com.aem.ai.pm.net.HttpClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Component(service = DhanAuthService.class, immediate = true)
public class DhanAuthServiceImpl implements DhanAuthService {

    private static final Logger log = LoggerFactory.getLogger(DhanAuthServiceImpl.class);

    @Reference
    private HttpClientService http;

    private final ObjectMapper om = new ObjectMapper();
    private volatile DhanConfig cfg;

    @Activate
    protected void activate(DhanConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * Exchange request token for an access token (if Dhan partner API provides this).
     */
    public String exchangeForAccessToken(String requestToken) throws BrokerException {
        try {
            String url = cfg.baseUrl() + "/oauth/token"; // ⚠️ verify actual endpoint in docs
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            String body = String.format(
                    "{\"appId\":\"%s\",\"appSecret\":\"%s\",\"requestToken\":\"%s\"}",
                    cfg.appId(), cfg.appSecret(), requestToken
            );

            String resp = http.post(url, headers, body, cfg.timeoutMs());
            JsonNode node = om.readTree(resp);

            if (node.has("accessToken")) {
                return node.get("accessToken").asText();
            } else {
                throw new BrokerException("No accessToken in Dhan response: " + resp);
            }
        } catch (Exception e) {
            throw new BrokerException("Failed to exchange token with Dhan: " + e.getMessage(), -1, e);
        }
    }
}
