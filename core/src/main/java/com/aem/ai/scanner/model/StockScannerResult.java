package com.aem.ai.scanner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StockScannerResult {

    @JsonProperty("market_bias")
    private String marketBias;

    @JsonProperty("top_intraday_picks")
    private List<StockPick> topIntradayPicks;

    @JsonProperty("top_btst_picks")
    private List<StockPick> topBtstPicks;

    @JsonProperty("trending_stocks")
    private List<TrendingStock> trendingStocks;

    @JsonProperty("volume_build_up_stocks")
    private List<VolumeStock> volumeBuildUpStocks;

    @JsonProperty("news_based_stocks")
    private List<NewsStock> newsBasedStocks;

    @JsonProperty("overall_trade_bias")
    private String overallTradeBias;

    @JsonProperty("market_confidence_score")
    private int marketConfidenceScore;

    @JsonProperty("top_trade_confidence_score")
    private int topTradeConfidenceScore;

    @JsonProperty("risk_summary")
    private String riskSummary;

    @JsonProperty("final_recommendation")
    private String finalRecommendation;

    // === Getters and Setters ===
    public String getMarketBias() { return marketBias; }
    public void setMarketBias(String marketBias) { this.marketBias = marketBias; }

    public List<StockPick> getTopIntradayPicks() { return topIntradayPicks; }
    public void setTopIntradayPicks(List<StockPick> topIntradayPicks) { this.topIntradayPicks = topIntradayPicks; }

    public List<StockPick> getTopBtstPicks() { return topBtstPicks; }
    public void setTopBtstPicks(List<StockPick> topBtstPicks) { this.topBtstPicks = topBtstPicks; }

    public List<TrendingStock> getTrendingStocks() { return trendingStocks; }
    public void setTrendingStocks(List<TrendingStock> trendingStocks) { this.trendingStocks = trendingStocks; }

    public List<VolumeStock> getVolumeBuildUpStocks() { return volumeBuildUpStocks; }
    public void setVolumeBuildUpStocks(List<VolumeStock> volumeBuildUpStocks) { this.volumeBuildUpStocks = volumeBuildUpStocks; }

    public List<NewsStock> getNewsBasedStocks() { return newsBasedStocks; }
    public void setNewsBasedStocks(List<NewsStock> newsBasedStocks) { this.newsBasedStocks = newsBasedStocks; }

    public String getOverallTradeBias() { return overallTradeBias; }
    public void setOverallTradeBias(String overallTradeBias) { this.overallTradeBias = overallTradeBias; }

    public int getMarketConfidenceScore() { return marketConfidenceScore; }
    public void setMarketConfidenceScore(int marketConfidenceScore) { this.marketConfidenceScore = marketConfidenceScore; }

    public int getTopTradeConfidenceScore() { return topTradeConfidenceScore; }
    public void setTopTradeConfidenceScore(int topTradeConfidenceScore) { this.topTradeConfidenceScore = topTradeConfidenceScore; }

    public String getRiskSummary() { return riskSummary; }
    public void setRiskSummary(String riskSummary) { this.riskSummary = riskSummary; }

    public String getFinalRecommendation() { return finalRecommendation; }
    public void setFinalRecommendation(String finalRecommendation) { this.finalRecommendation = finalRecommendation; }
}

// === Base Stock Pick ===




