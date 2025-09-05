package com.aem.ai.pm.dto;



public class BrokerAccountRef {
    public  long userId;
    public  long userBrokerAccountId;
    public  String brokerCode;
    public  String externalAccountId; // e.g. clientId
    public  String accessToken;       // decrypted short-lived access
    public  String requestToken;       // decrypted short-lived access
    public  String apiKey;       // decrypted short-lived access
    public  String apiSecrete;       // decrypted short-lived access
    public  String brokerAccountRef;       // decrypted short-lived access
    public BrokerAccountRef(long userId, long ubaId, String brokerCode, String externalAccountId, String accessToken,String requestToken) {
        this.userId = userId; this.userBrokerAccountId = ubaId; this.requestToken= requestToken;
        this.brokerCode = brokerCode; this.externalAccountId = externalAccountId; this.accessToken = accessToken;
    }

    public long getUserId() {
        return userId;
    }

    public long getUserBrokerAccountId() {
        return userBrokerAccountId;
    }

    public String getBrokerCode() {
        return brokerCode;
    }

    public String getExternalAccountId() {
        return externalAccountId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRequestToken() {
        return requestToken;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecrete() {
        return apiSecrete;
    }

    public void setApiSecrete(String apiSecrete) {
        this.apiSecrete = apiSecrete;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public void setUserBrokerAccountId(long userBrokerAccountId) {
        this.userBrokerAccountId = userBrokerAccountId;
    }

    public void setBrokerCode(String brokerCode) {
        this.brokerCode = brokerCode;
    }

    public void setExternalAccountId(String externalAccountId) {
        this.externalAccountId = externalAccountId;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setRequestToken(String requestToken) {
        this.requestToken = requestToken;
    }

    public String getBrokerAccountRef() {
        return brokerAccountRef;
    }

    public void setBrokerAccountRef(String brokerAccountRef) {
        this.brokerAccountRef = brokerAccountRef;
    }

    @Override
    public String toString() {
        return "BrokerAccountRef{" +
                "userId=" + userId +
                ", userBrokerAccountId=" + userBrokerAccountId +
                ", brokerCode='" + brokerCode + '\'' +
                ", externalAccountId='" + externalAccountId + '\'' +
                ", accessToken='" + accessToken + '\'' +
                ", requestToken='" + requestToken + '\'' +
                '}';
    }
}

