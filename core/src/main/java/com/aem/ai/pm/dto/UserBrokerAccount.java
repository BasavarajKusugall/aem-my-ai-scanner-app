package com.aem.ai.pm.dto;

public class UserBrokerAccount {
    private long accountId;
    private long userId;
    private long brokerId;
    private String brokerCode;   // <-- Add this
    private String brokerName;   // Already present
    private String brokerAccountRef;
    private String accountAlias;
    private String baseCurrency;
    private String status;
    private String apiKey;
    private String apiSecrete;
    private String requestToken;
    private String apiBaseUrl;
    private String telegramBotUserId;
    private String portfolioPositionsJson;
    private String portfolioHoldingJson;
    private String password;


    // Getters and setters
    public long getAccountId() { return accountId; }
    public void setAccountId(long accountId) { this.accountId = accountId; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public long getBrokerId() { return brokerId; }
    public void setBrokerId(long brokerId) { this.brokerId = brokerId; }

    public String getBrokerCode() { return brokerCode; }
    public void setBrokerCode(String brokerCode) { this.brokerCode = brokerCode; }

    public String getBrokerName() { return brokerName; }
    public void setBrokerName(String brokerName) { this.brokerName = brokerName; }

    public String getBrokerAccountRef() { return brokerAccountRef; }
    public void setBrokerAccountRef(String brokerAccountRef) { this.brokerAccountRef = brokerAccountRef; }

    public String getAccountAlias() { return accountAlias; }
    public void setAccountAlias(String accountAlias) { this.accountAlias = accountAlias; }

    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiSecrete() { return apiSecrete; }
    public void setApiSecrete(String apiSecrete) { this.apiSecrete = apiSecrete; }

    public String getRequestToken() { return requestToken; }
    public void setRequestToken(String requestToken) { this.requestToken = requestToken; }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public void setTelegramBotUserId(String telegramBotUserId) {
        this.telegramBotUserId = telegramBotUserId;
    }

    public void setPortfolioPositionsJson(String portfolioPositionsJson) {
        this.portfolioPositionsJson = portfolioPositionsJson;
    }

    public void setPortfolioHoldingJson(String portfolioHoldingJson) {
        this.portfolioHoldingJson = portfolioHoldingJson;
    }

    public String getTelegramBotUserId() {
        return telegramBotUserId;
    }

    public String getPortfolioPositionsJson() {
        return portfolioPositionsJson;
    }

    public String getPortfolioHoldingJson() {
        return portfolioHoldingJson;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "UserBrokerAccount{" +
                "accountId=" + accountId +
                ", userId=" + userId +
                ", brokerId=" + brokerId +
                ", brokerCode='" + brokerCode + '\'' +
                ", brokerName='" + brokerName + '\'' +
                ", brokerAccountRef='" + brokerAccountRef + '\'' +
                ", accountAlias='" + accountAlias + '\'' +
                ", baseCurrency='" + baseCurrency + '\'' +
                ", status='" + status + '\'' +
                ", apiKey='" + apiKey + '\'' +
                ", apiSecrete='" + apiSecrete + '\'' +
                ", requestToken='" + requestToken + '\'' +
                ", apiBaseUrl='" + apiBaseUrl + '\'' +
                ", telegramBotUserId='" + telegramBotUserId + '\'' +
                ", portfolioPositionsJson='" + portfolioPositionsJson + '\'' +
                ", portfolioHoldingJson='" + portfolioHoldingJson + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
