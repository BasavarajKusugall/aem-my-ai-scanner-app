package com.aem.ai.scanner.services.impl;


import com.aem.ai.scanner.services.HttpService;
import com.aem.ai.scanner.utils.RetryUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Lightweight wrapper around Java11 HttpClient with retries.
 */
@Component(immediate = true,service = HttpService.class)
public class HttpServiceImpl implements HttpService {

    private static final Logger log = LoggerFactory.getLogger(HttpService.class);

    private HttpClient client;

    @Activate
    @Modified
    protected void activate() {
        // Initialize the HttpClient
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("HttpService activated with HttpClient timeout 10s");
    }

    private String encodeUnsafePath(String url) {
        // Example: only replace "|" with "%7C"
        return url.replace("|", "%7C");
    }


    /**
     * Perform a synchronous GET request with retry logic.
     */
    public String get(String url) throws Exception {
        String safeUrl = encodeUnsafePath(url);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(safeUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        return RetryUtils.executeWithExponentialBackoff(() -> {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("HTTP GET successful for {}", url);
                return resp.body();
            } else {
                log.error("HTTP GET failed: status={} body={}", resp.statusCode(), resp.body());
                throw new RuntimeException("HTTP " + resp.statusCode() + " for " + url + " body:" + resp.body());
            }
        }, 3, 500);
    }

    /**
     * Perform a synchronous POST request with JSON body and retry logic.
     */
    public String postJson(String url, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return RetryUtils.executeWithExponentialBackoff(() -> {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return resp.body();
            } else {
                throw new RuntimeException("HTTP " + resp.statusCode() + " for " + url + " body:" + resp.body());
            }
        }, 3, 500);
    }

    /**
     * POST request with headers
     */
    public String postJson(String url, Map<String, String> headers, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (headers != null) {
            headers.forEach(builder::header);
        }

        HttpRequest req = builder.build();

        return RetryUtils.executeWithExponentialBackoff(() -> {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return resp.body();
            } else {
                throw new RuntimeException("HTTP " + resp.statusCode() + " for " + url + " body:" + resp.body());
            }
        }, 3, 500);
    }

    /**
     * Perform an asynchronous GET request.
     */
    public CompletableFuture<String> getAsync(String url) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    log.debug("Async HTTP response received: status={}", resp.statusCode());
                    return resp.body();
                });
    }
}
