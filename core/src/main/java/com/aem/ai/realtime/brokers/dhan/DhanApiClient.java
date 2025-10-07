package com.aem.ai.realtime.brokers.dhan;

import com.aem.ai.exception.ApiException;
import com.aem.ai.pm.connectors.BrokerConnector;
import com.aem.ai.pm.dto.BrokerAccountRef;
import com.aem.ai.pm.connectors.BrokerConnector; // ensure available
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component(service = DhanApiClient.class, immediate = true)
@Designate(ocd = DhanApiClient.Config.class)
public class DhanApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(DhanApiClient.class);

    @ObjectClassDefinition(name = "Dhan API Client Config", description = "Dhan API client configuration")
    public @interface Config {
        @AttributeDefinition(name = "API Base URL")
        String api_base() default "https://api.dhan.co";
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

    protected void bindBrokerConnector(BrokerConnector connector) {
        brokerConnectors.add(connector);
    }

    protected void unbindBrokerConnector(BrokerConnector connector) {
        brokerConnectors.remove(connector);
    }

    @Activate
    public void activate(Config config) {
        this.apiBase = config.api_base();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = new ObjectMapper();
        this.mapper.findAndRegisterModules();
        LOG.info("[DhanApiClient] Activated with base={}", apiBase);
    }

    @Deactivate
    public void deactivate() {
        LOG.info("[DhanApiClient] Deactivated");
    }

    private BrokerConnector getDhanBroker() {
        for (BrokerConnector c : brokerConnectors) {
            if ("DHAN".equalsIgnoreCase(c.brokerCode())) return c;
        }
        return null;
    }

    private HttpRequest.Builder baseRequest(String path, BrokerAccountRef ref) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiBase + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("access-token", ref != null ? ref.getAccessToken() : "");
    }

    private <T> T send(HttpRequest request, Class<T> respClass) throws ApiException {
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

    // Generic wrappers that iterate across DHAN accounts similar to Kite client
    public <T> List<T> post(String path, Object body, Class<T> respClass) throws ApiException {
        BrokerConnector connector = getDhanBroker();
        if (connector == null) throw new IllegalStateException("DHAN BrokerConnector not found");
        List<BrokerAccountRef> accounts = connector.discoverAccounts();
        List<T> results = new ArrayList<>();
        for (BrokerAccountRef ref : accounts) {
            try {
                String json = mapper.writeValueAsString(body);
                HttpRequest req = baseRequest(path, ref)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                T r = send(req, respClass);
                results.add(r);
            } catch (Exception e) {
                LOG.error("[Account:{}] POST {} failed: {}", ref.getBrokerAccountRef(), path, e.getMessage());
            }
        }
        return results;
    }

    public <T> List<T> put(String path, Object body, Class<T> respClass) throws ApiException {
        BrokerConnector connector = getDhanBroker();
        if (connector == null) throw new IllegalStateException("DHAN BrokerConnector not found");
        List<BrokerAccountRef> accounts = connector.discoverAccounts();
        List<T> results = new ArrayList<>();
        for (BrokerAccountRef ref : accounts) {
            try {
                String json = mapper.writeValueAsString(body);
                HttpRequest req = baseRequest(path, ref)
                        .PUT(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                T r = send(req, respClass);
                results.add(r);
            } catch (Exception e) {
                LOG.error("[Account:{}] PUT {} failed: {}", ref.getBrokerAccountRef(), path, e.getMessage());
            }
        }
        return results;
    }

    public <T> List<T> delete(String path, Class<T> respClass) throws ApiException {
        BrokerConnector connector = getDhanBroker();
        if (connector == null) throw new IllegalStateException("DHAN BrokerConnector not found");
        List<BrokerAccountRef> accounts = connector.discoverAccounts();
        List<T> results = new ArrayList<>();
        for (BrokerAccountRef ref : accounts) {
            try {
                HttpRequest req = baseRequest(path, ref).DELETE().build();
                T r = send(req, respClass);
                results.add(r);
            } catch (Exception e) {
                LOG.error("[Account:{}] DELETE {} failed: {}", ref.getBrokerAccountRef(), path, e.getMessage());
            }
        }
        return results;
    }

    public <T> List<T> get(String path, Class<T> respClass) throws ApiException {
        BrokerConnector connector = getDhanBroker();
        if (connector == null) throw new IllegalStateException("DHAN BrokerConnector not found");
        List<BrokerAccountRef> accounts = connector.discoverAccounts();
        List<T> results = new ArrayList<>();
        for (BrokerAccountRef ref : accounts) {
            try {
                HttpRequest req = baseRequest(path, ref).GET().build();
                T r = send(req, respClass);
                results.add(r);
            } catch (Exception e) {
                LOG.error("[Account:{}] GET {} failed: {}", ref.getBrokerAccountRef(), path, e.getMessage());
            }
        }
        return results;
    }
}