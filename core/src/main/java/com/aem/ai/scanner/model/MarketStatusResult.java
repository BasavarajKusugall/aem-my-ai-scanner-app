package com.aem.ai.scanner.model;


public class MarketStatusResult {
    private String exchange;
    private String marketStatus;
    private String currentTime;
    private String marketOpenTime;
    private String marketCloseTime;
    private String adjustedCloseTime;

    // Getters and Setters
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getMarketStatus() { return marketStatus; }
    public void setMarketStatus(String marketStatus) { this.marketStatus = marketStatus; }

    public String getCurrentTime() { return currentTime; }
    public void setCurrentTime(String currentTime) { this.currentTime = currentTime; }

    public String getMarketOpenTime() { return marketOpenTime; }
    public void setMarketOpenTime(String marketOpenTime) { this.marketOpenTime = marketOpenTime; }

    public String getMarketCloseTime() { return marketCloseTime; }
    public void setMarketCloseTime(String marketCloseTime) { this.marketCloseTime = marketCloseTime; }

    public void setAdjustedCloseTime(String adjustedCloseTime) {
        this.adjustedCloseTime = adjustedCloseTime;
    }

    public String getAdjustedCloseTime() {
        return adjustedCloseTime;
    }
}

