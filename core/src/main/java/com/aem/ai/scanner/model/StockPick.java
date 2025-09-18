package com.aem.ai.scanner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StockPick {
    @JsonProperty("stock")
    private String stock;

    @JsonProperty("nse_symbol")
    private String nseSymbol;

    @JsonProperty("instrument_key")
    private String instrumentKey;

    @JsonProperty("confidence_score")
    private int confidenceScore;

    @JsonProperty("live_price")
    private double livePrice;

    // Getters & Setters
    public String getStock() { return stock; }
    public void setStock(String stock) { this.stock = stock; }

    public String getNseSymbol() { return nseSymbol; }
    public void setNseSymbol(String nseSymbol) { this.nseSymbol = nseSymbol; }

    public String getInstrumentKey() { return instrumentKey; }
    public void setInstrumentKey(String instrumentKey) { this.instrumentKey = instrumentKey; }

    public int getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }

    public double getLivePrice() { return livePrice; }
    public void setLivePrice(double livePrice) { this.livePrice = livePrice; }
}
