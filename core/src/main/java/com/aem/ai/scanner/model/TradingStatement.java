package com.aem.ai.scanner.model;

public class TradingStatement {
    private String symbol;
    private String timeframe;
    private int totalTrades;
    private double totalProfit;
    private double winRate;

    // ✅ Constructors, getters, setters
    public TradingStatement() {}

    public TradingStatement(String symbol, String timeframe, int totalTrades, double totalProfit, double winRate) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.totalTrades = totalTrades;
        this.totalProfit = totalProfit;
        this.winRate = winRate;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public int getTotalTrades() { return totalTrades; }
    public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }

    public double getTotalProfit() { return totalProfit; }
    public void setTotalProfit(double totalProfit) { this.totalProfit = totalProfit; }

    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }
}
