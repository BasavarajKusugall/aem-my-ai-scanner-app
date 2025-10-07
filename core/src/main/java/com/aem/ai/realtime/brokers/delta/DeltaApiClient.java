package com.aem.ai.realtime.brokers.delta;

import com.aem.ai.exception.ApiException;
import com.aem.ai.pm.connectors.BrokerConnector;
import com.aem.ai.pm.dto.BrokerAccountRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Component(service = DeltaApiClient.class, immediate = true)
@Designate(ocd = DeltaApiClient.Config.class)
public class DeltaApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(DeltaApiClient.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    @ObjectClassDefinition(name = "Delta API Client Config", description = "Delta API client configuration")
    public @interface Config {
        @AttributeDefinition(name = "API Base URL")
        String api_base() default "https://api.delta.exchange/v2";
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
        LOG.info("[DeltaApiClient] Activated with base={}", apiBase);
    }

    @Deactivate
    public void deactivate() {
        LOG.info("[DeltaApiClient] Deactivated");
    }

    private BrokerConnector getDeltaBroker() {
        for (BrokerConnector c : brokerConnectors) {
            if ("DELTA".equalsIgnoreCase(c.brokerCode())) return c;
        }
        return null;
    }

    /**
     * NOTE: This implementation assumes BrokerAccountRef exposes methods:
     * - String getApiKey()
     * - String getApiSecret()
     * or alternatively getAccessToken() for api key and getExtra()/getSecret() for secret.
     * If your DTO differs, adapt accordingly.
     */
    private String getApiKey(BrokerAccountRef ref) {
        try {
            Method m = ref.getClass().getMethod("getApiKey");
            Object v = m.invoke(ref);
            return v != null ? v.toString() : null;
        } catch (Exception ignored) {}
        try {
            Method m = ref.getClass().getMethod("getAccessToken");
            Object v = m.invoke(ref);
            return v != null ? v.toString() : null;
        } catch (Exception ignored) {}
        LOG.warn("BrokerAccountRef does not expose getApiKey() or getAccessToken(); API key unavailable for account={}", ref.getBrokerAccountRef());
        return null;
    }

    private String getApiSecret(BrokerAccountRef ref) {
        try {
            Method m = ref.getClass().getMethod("getApiSecret");
            Object v = m.invoke(ref);
            return v != null ? v.toString() : null;
        } catch (Exception ignored) {}
        try {
            Method m = ref.getClass().getMethod("getSecret");
            Object v = m.invoke(ref);
            return v != null ? v.toString() : null;
        } catch (Exception ignored) {}
        LOG.warn("BrokerAccountRef does not expose getApiSecret()/getSecret(); API secret unavailable for account={}", ref.getBrokerAccountRef());
        return null;
    }

    private String sign(String secret, String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        if (secret == null) throw new IllegalStateException("API secret is null - cannot sign request");
        Mac sha256_HMAC = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        sha256_HMAC.init(secret_key);
        byte[] mac = sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : mac) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private <T> T send(HttpRequest request, Class<T> respClass) throws ApiException {
        try {
            LOG.debug("Sending request: {} {}", request.method(), request.uri());
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.debug("Response status: {} body: {}", resp.statusCode(), resp.body());
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

    // Generic executor which signs requests per-account and supports parallel accounts (like Dhan client)
    private <T> List<T> execute(String path, String method, Object body, Class<T> respClass) throws ApiException {
        BrokerConnector connector = getDeltaBroker();
        if (connector == null) throw new IllegalStateException("DELTA BrokerConnector not found");
        List<BrokerAccountRef> accounts = connector.discoverAccounts();
        List<T> results = new ArrayList<>();

        for (BrokerAccountRef ref : accounts) {
            String apiKey = getApiKey(ref);
            String apiSecret = getApiSecret(ref);
            if (apiKey == null || apiSecret == null) {
                LOG.error("Skipping account {} - missing apiKey or apiSecret", ref.getBrokerAccountRef());
                continue;
            }
            try {
                String fullPath = apiBase + path;
                String bodyJson = (body != null) ? mapper.writeValueAsString(body) : "";
                String timestamp = String.valueOf(Instant.now().toEpochMilli());
                // Signature payload: timestamp + method + path + body
                String relativePath = path; // keep query params if present
                String payloadToSign = timestamp + method.toUpperCase() + relativePath + bodyJson;
                String signature = sign(apiSecret, payloadToSign);

                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(fullPath))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("api-key", apiKey)
                        .header("timestamp", timestamp)
                        .header("signature", signature);

                switch (method.toUpperCase()) {
                    case "POST":
                        builder.POST(HttpRequest.BodyPublishers.ofString(bodyJson));
                        break;
                    case "PUT":
                        builder.PUT(HttpRequest.BodyPublishers.ofString(bodyJson));
                        break;
                    case "DELETE":
                        if (!bodyJson.isEmpty()) builder.method("DELETE", HttpRequest.BodyPublishers.ofString(bodyJson));
                        else builder.DELETE();
                        break;
                    default:
                        builder.GET();
                }

                HttpRequest req = builder.build();
                T r = send(req, respClass);
                results.add(r);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                LOG.error("Signing failed for account {}: {}", ref.getBrokerAccountRef(), e.getMessage());
            } catch (Exception e) {
                LOG.error("[Account:{}] {} {} failed: {}", ref.getBrokerAccountRef(), method, path, e.getMessage());
            }
        }

        return results;
    }

    // Public wrappers
    public <T> List<T> get(String path, Class<T> respClass) throws ApiException { return execute(path, "GET", null, respClass); }
    public <T> List<T> post(String path, Object body, Class<T> respClass) throws ApiException { return execute(path, "POST", body, respClass); }
    public <T> List<T> put(String path, Object body, Class<T> respClass) throws ApiException { return execute(path, "PUT", body, respClass); }
    public <T> List<T> delete(String path, Object body, Class<T> respClass) throws ApiException { return execute(path, "DELETE", body, respClass); }
}
