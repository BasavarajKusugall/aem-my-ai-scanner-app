package com.aem.ai.scanner.model;

public class BestStrategyModel {
    private String broker;
    private String symbol;
    private String timeframe;
    private String strategyName;
    private String configJson;
    private double totalProfit;
    private double profitLossPct;
    private int profitableTrades;
    private int losingTrades;
    private int breakEvenTrades;

    // getters, setters, toString...'

    public String getBroker() {
        return broker;
    }

    public void setBroker(String broker) {
        this.broker = broker;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public double getTotalProfit() {
        return totalProfit;
    }

    public void setTotalProfit(double totalProfit) {
        this.totalProfit = totalProfit;
    }

    public double getProfitLossPct() {
        return profitLossPct;
    }

    public void setProfitLossPct(double profitLossPct) {
        this.profitLossPct = profitLossPct;
    }

    public int getProfitableTrades() {
        return profitableTrades;
    }

    public void setProfitableTrades(int profitableTrades) {
        this.profitableTrades = profitableTrades;
    }

    public int getLosingTrades() {
        return losingTrades;
    }

    public void setLosingTrades(int losingTrades) {
        this.losingTrades = losingTrades;
    }

    public int getBreakEvenTrades() {
        return breakEvenTrades;
    }

    public void setBreakEvenTrades(int breakEvenTrades) {
        this.breakEvenTrades = breakEvenTrades;
    }

    @Override
    public String toString() {
        return "BestStrategyModel{" +
                "broker='" + broker + '\'' +
                ", symbol='" + symbol + '\'' +
                ", timeframe='" + timeframe + '\'' +
                ", strategyName='" + strategyName + '\'' +
                ", configJson='" + configJson + '\'' +
                ", totalProfit=" + totalProfit +
                ", profitLossPct=" + profitLossPct +
                ", profitableTrades=" + profitableTrades +
                ", losingTrades=" + losingTrades +
                ", breakEvenTrades=" + breakEvenTrades +
                '}';
    }
}

