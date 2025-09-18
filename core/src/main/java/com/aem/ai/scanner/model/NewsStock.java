package com.aem.ai.scanner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsStock extends StockPick {
    @JsonProperty("news_type")
    private String newsType; // Earnings / Corporate / Macro

    @JsonProperty("expected_impact")
    private String expectedImpact; // Positive / Negative / Neutral

    public String getNewsType() { return newsType; }
    public void setNewsType(String newsType) { this.newsType = newsType; }

    public String getExpectedImpact() { return expectedImpact; }
    public void setExpectedImpact(String expectedImpact) { this.expectedImpact = expectedImpact; }
}