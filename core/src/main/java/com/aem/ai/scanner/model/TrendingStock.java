package com.aem.ai.scanner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// === Specialized Stock Types ===
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrendingStock extends StockPick {
    @JsonProperty("trend_type")
    private String trendType; // Uptrend / Downtrend / Sideways

    public String getTrendType() { return trendType; }
    public void setTrendType(String trendType) { this.trendType = trendType; }
}
