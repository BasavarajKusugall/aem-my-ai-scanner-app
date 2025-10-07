// === com/aem/ai/realtime/brokers/upstox/UpstoxApiClient.java ===
package com.aem.ai.realtime.brokers.upstox;

import com.aem.ai.exception.ApiException;
import com.aem.ai.pm.connectors.BrokerConnector;
import com.aem.ai.pm.dto.BrokerAccountRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Component(service = UpstoxApiClient.class, immediate = true)
@Designate(ocd = UpstoxApiClient.Config.class)
public class UpstoxApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(UpstoxApiClient.class);

    @ObjectClassDefinition(name = "Upstox API Client Config", description = "Upstox API client configuration")
    public @interface Config {
        @AttributeDefinition(name = "API Base URL")
        String api_base() default "https://api.upstox.com/v2";
    }

    private HttpClient client;
    private ObjectMapper mapper;
    private String apiBase;

    @Reference(
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            bind = "bindBrokerConnector",
            unbind = "unbindBrokerConnector"
    )
    private volatile List<BrokerConnector> brokerConnectors = new CopyOnWriteArrayList<>();

    protected void bindBrokerConnector(BrokerConnector connector) { brokerConnectors.add(connector); }
    protected void unbindBrokerConnector(BrokerConnector connector) { brokerConnectors.remove(connector); }

    @Activate
    public void activate(Config config) {
        this.apiBase = config.api_base();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = new ObjectMapper();
        this.mapper.findAndRegisterModules();
        LOG.info("[UpstoxApiClient] Activated with base={}", apiBase);
    }

    @Deactivate
    public void deactivate() {
        LOG.info("[UpstoxApiClient] Deactivated");
    }

    private BrokerConnector getUpstoxBroker() {
        for (BrokerConnector c : brokerConnectors) {
            if ("UPSTOX".equalsIgnoreCase(c.brokerCode())) return c;
        }
        return null;
    }

    public HttpRequest.Builder baseRequest(String path, BrokerAccountRef ref) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiBase + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + (ref != null ? ref.getAccessToken() : ""));
    }

    <T> T send(HttpRequest request, Class<T> respClass) throws ApiException {
        try {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            String body = resp.body();
            if (status >= 200 && status < 300) {
                if (respClass == Void.class) return null;
                return mapper.readValue(body, respClass);
            }
            throw new ApiException("HTTP " + status + " : " + body);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request failed: " + e.getMessage(), e);
        }
    }

    // ===================== Generic Helper =====================
    private <T> List<T> execute(String path, String method, Object body, Class<T> respClass) throws ApiException {
        BrokerConnector connector = getUpstoxBroker();
        if (connector == null)
            throw new IllegalStateException("UPSTOX BrokerConnector not found");

        List<BrokerAccountRef> accounts = connector.discoverAccounts();
        List<T> results = new ArrayList<>();

        for (BrokerAccountRef ref : accounts) {
            try {
                HttpRequest.Builder builder = baseRequest(path, ref);
                if (body != null) {
                    String json = mapper.writeValueAsString(body);
                    switch (method) {
                        case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(json));
                        case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(json));
                        default -> builder.method(method, HttpRequest.BodyPublishers.ofString(json));
                    }
                } else if (method.equals("DELETE")) {
                    builder.DELETE();
                } else {
                    builder.GET();
                }

                HttpRequest req = builder.build();
                T response = send(req, respClass);
                results.add(response);
            } catch (Exception e) {
                LOG.error("[Account:{}] {} {} failed: {}", ref.getBrokerAccountRef(), method, path, e.getMessage());
            }
        }

        return results;
    }

    // ===================== Public Wrappers =====================
    public <T> List<T> get(String path, Class<T> respClass) throws ApiException {
        return execute(path, "GET", null, respClass);
    }

    public <T> List<T> post(String path, Object body, Class<T> respClass) throws ApiException {
        return execute(path, "POST", body, respClass);
    }

    public <T> List<T> put(String path, Object body, Class<T> respClass) throws ApiException {
        return execute(path, "PUT", body, respClass);
    }

    public <T> List<T> delete(String path, Class<T> respClass) throws ApiException {
        return execute(path, "DELETE", null, respClass);
    }
}
