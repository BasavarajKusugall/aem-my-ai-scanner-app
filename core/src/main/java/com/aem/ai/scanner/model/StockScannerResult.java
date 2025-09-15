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
@JsonIgnoreProperties(ignoreUnknown = true)
class StockPick {
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

// === Specialized Stock Types ===
@JsonIgnoreProperties(ignoreUnknown = true)
class TrendingStock extends StockPick {
    @JsonProperty("trend_type")
    private String trendType; // Uptrend / Downtrend / Sideways

    public String getTrendType() { return trendType; }
    public void setTrendType(String trendType) { this.trendType = trendType; }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class VolumeStock extends StockPick {
    @JsonProperty("volume_change_percent")
    private double volumeChangePercent;

    @JsonProperty("price_action")
    private String priceAction; // Breakout / Breakdown / Gap Up / Gap Down

    public double getVolumeChangePercent() { return volumeChangePercent; }
    public void setVolumeChangePercent(double volumeChangePercent) { this.volumeChangePercent = volumeChangePercent; }

    public String getPriceAction() { return priceAction; }
    public void setPriceAction(String priceAction) { this.priceAction = priceAction; }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class NewsStock extends StockPick {
    @JsonProperty("news_type")
    private String newsType; // Earnings / Corporate / Macro

    @JsonProperty("expected_impact")
    private String expectedImpact; // Positive / Negative / Neutral

    public String getNewsType() { return newsType; }
    public void setNewsType(String newsType) { this.newsType = newsType; }

    public String getExpectedImpact() { return expectedImpact; }
    public void setExpectedImpact(String expectedImpact) { this.expectedImpact = expectedImpact; }
}
