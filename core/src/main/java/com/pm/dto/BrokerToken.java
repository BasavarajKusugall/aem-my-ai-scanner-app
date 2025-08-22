package com.pm.dto;

import java.time.LocalDateTime;

public class BrokerToken {
    private long id;
    private long userId;
    private long brokerAccountId;
    private String requestToken;
    private String apiKey;
    private String apiSecrete;
    private String accessToken;

    private LocalDateTime tokenExpiry;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public long getBrokerAccountId() { return brokerAccountId; }
    public void setBrokerAccountId(long brokerAccountId) { this.brokerAccountId = brokerAccountId; }

    public String getRequestToken() { return requestToken; }
    public void setRequestToken(String requestToken) { this.requestToken = requestToken; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiSecrete() { return apiSecrete; }
    public void setApiSecrete(String apiSecrete) { this.apiSecrete = apiSecrete; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public LocalDateTime getTokenExpiry() { return tokenExpiry; }
    public void setTokenExpiry(LocalDateTime tokenExpiry) { this.tokenExpiry = tokenExpiry; }
}
