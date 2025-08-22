package com.aem.ai.scanner.services;


import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface HttpService {
    String get(String url, Map<String, String> headers) throws Exception;
    String get(String url) throws Exception;
    String postJson(String url, String jsonBody) throws Exception;
    String postJson(String url, Map<String, String> headers, String jsonBody) throws Exception;
    CompletableFuture<String> getAsync(String url);
}
