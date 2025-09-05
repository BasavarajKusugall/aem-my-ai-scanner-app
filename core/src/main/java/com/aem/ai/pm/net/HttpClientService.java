package com.aem.ai.pm.net;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component(service = HttpClientService.class, immediate = true)
public class HttpClientService {

    private static final Logger log = LoggerFactory.getLogger(HttpClientService.class);

    // ANSI Colors for console logs
    private static final String RESET = "\u001B[0m";
    private static final String BLUE = "\u001B[34m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";

    private HttpClient http;

    @Activate
    protected void activate() {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_2)
                .build();
        log.info(GREEN + "✅ HttpClientService activated with HTTP/2 and 10s connect timeout." + RESET);
    }

    public HttpResponse<String> get(String url, String authHeader, int timeoutMs) throws Exception {
        log.info(BLUE + "🌐 Sending HTTP GET Request: " + RESET + url +
                (authHeader != null ? " " + YELLOW + "[Auth Header Present]" + RESET : " " + RED + "[No Auth]" + RESET));

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", authHeader == null ? "" : authHeader)
                .header("Accept", "application/json")
                .GET().build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() / 100 == 2) {
            log.info(GREEN + "✅ Response " + res.statusCode() + " received successfully for: " + RESET + url);
        } else {
            log.warn(RED + "⚠️ Response " + res.statusCode() + " for: " + RESET + url +
                    " | Body: " + res.body());
        }

        return res;
    }
    public String get(String url, Map<String, String> headers, int timeoutMs) throws Exception {
        log.info(BLUE + "🌐 Sending HTTP GET Request: " + RESET + url);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET();

        if (headers != null) {
            headers.forEach((k, v) -> reqBuilder.header(k, v));
        }

        HttpRequest req = reqBuilder.build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() / 100 == 2) {
            log.info(GREEN + "✅ Response " + res.statusCode() + " received successfully for: " + RESET + url);
        } else {
            log.warn(RED + "⚠️ Response " + res.statusCode() + " for: " + RESET + url +
                    " | Body: " + res.body());
        }

        return res.body();
    }
    public String post(String url, Map<String, String> headers, String body, int timeoutMs) throws Exception {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(body));

        // Ensure Content-Type for form-urlencoded
        reqBuilder.header("Content-Type", "application/x-www-form-urlencoded");

        // Add additional headers if provided
        if (headers != null) {
            headers.forEach(reqBuilder::header);
        }

        HttpRequest req = reqBuilder.build();

        log.info(BLUE + "🌐 Sending HTTP POST Request: " + RESET + url +
                (headers != null && headers.containsKey("Authorization") ? " " + YELLOW + "[Auth Header Present]" + RESET : " " + RED + "[No Auth]" + RESET));

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() / 100 == 2) {
            log.info(GREEN + "✅ POST Response " + res.statusCode() + " received for: " + RESET + url);
        } else {
            log.warn(RED + "⚠️ POST Response " + res.statusCode() + " for: " + RESET + url + " | Body: " + res.body());
        }

        return res.body(); // return JSON string only
    }


}
