package com.aem.ai.scanner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.inject.Inject;

@Model(
        adaptables = {Resource.class},
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeAnalysis {

    @ValueMapValue
    public String symbol;

    @ValueMapValue
    private String date;

    @ValueMapValue
    private String side;

    @Inject
    private FundamentalScore fundamental_score;

    @ValueMapValue
    private int confidence_score;

    @ValueMapValue
    private String global_news_sentiment;

    @ValueMapValue
    private String market_trend;

    @ValueMapValue
    private String open_interest_build_up;

    @ValueMapValue
    private String recommended_trade_timeframe;

    @ValueMapValue
    private String can_take_trade;

    @ValueMapValue
    private String final_verdict;

    @ValueMapValue
    private String responseJson;

    // Getters
    public String getSymbol() { return symbol; }
    public String getDate() { return date; }
    public String getSide() { return side; }
    public FundamentalScore getFundamental_score() { return fundamental_score; }
    public int getConfidence_score() { return confidence_score; }
    public String getGlobal_news_sentiment() { return global_news_sentiment; }
    public String getMarket_trend() { return market_trend; }
    public String getOpen_interest_build_up() { return open_interest_build_up; }
    public String getRecommended_trade_timeframe() { return recommended_trade_timeframe; }
    public String getCan_take_trade() { return can_take_trade; }
    public String getFinal_verdict() { return final_verdict; }
    public String getResponseJson() { return responseJson; }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public void setFundamental_score(FundamentalScore fundamental_score) {
        this.fundamental_score = fundamental_score;
    }

    public void setConfidence_score(int confidence_score) {
        this.confidence_score = confidence_score;
    }

    public void setGlobal_news_sentiment(String global_news_sentiment) {
        this.global_news_sentiment = global_news_sentiment;
    }

    public void setMarket_trend(String market_trend) {
        this.market_trend = market_trend;
    }

    public void setOpen_interest_build_up(String open_interest_build_up) {
        this.open_interest_build_up = open_interest_build_up;
    }

    public void setRecommended_trade_timeframe(String recommended_trade_timeframe) {
        this.recommended_trade_timeframe = recommended_trade_timeframe;
    }

    public void setCan_take_trade(String can_take_trade) {
        this.can_take_trade = can_take_trade;
    }

    public void setFinal_verdict(String final_verdict) {
        this.final_verdict = final_verdict;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    @Model(
            adaptables = {Resource.class},
            defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
    )
    public static class FundamentalScore {

        @ValueMapValue
        private String pe_ratio;

        @ValueMapValue
        private String eps_growth;

        @ValueMapValue
        private String revenue_growth;

        @ValueMapValue
        private String debt_to_equity;

        @ValueMapValue
        private String overall_fundamental_rating;

        public String getPe_ratio() { return pe_ratio; }
        public String getEps_growth() { return eps_growth; }
        public String getRevenue_growth() { return revenue_growth; }
        public String getDebt_to_equity() { return debt_to_equity; }
        public String getOverall_fundamental_rating() { return overall_fundamental_rating; }

        @Override
        public String toString() {
            return "FundamentalScore{" +
                    "pe_ratio='" + pe_ratio + '\'' +
                    ", eps_growth='" + eps_growth + '\'' +
                    ", revenue_growth='" + revenue_growth + '\'' +
                    ", debt_to_equity='" + debt_to_equity + '\'' +
                    ", overall_fundamental_rating='" + overall_fundamental_rating + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "TradeAnalysis{" +
                "symbol='" + symbol + '\'' +
                ", date='" + date + '\'' +
                ", side='" + side + '\'' +
                ", fundamental_score=" + fundamental_score +
                ", confidence_score=" + confidence_score +
                ", global_news_sentiment='" + global_news_sentiment + '\'' +
                ", market_trend='" + market_trend + '\'' +
                ", open_interest_build_up='" + open_interest_build_up + '\'' +
                ", recommended_trade_timeframe='" + recommended_trade_timeframe + '\'' +
                ", can_take_trade='" + can_take_trade + '\'' +
                ", final_verdict='" + final_verdict + '\'' +
                '}';
    }
}
