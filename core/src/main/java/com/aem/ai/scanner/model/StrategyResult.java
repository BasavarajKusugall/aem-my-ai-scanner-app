package com.aem.ai.scanner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategyResult {
    private int id;
    private String name;
    private String symbol;
    private String timeframe;
    private List<StrategyConfig.RuleConfig> rules;

    private double winRate;
    private double pnl;
    private double drawdown;

    // ✅ Needed for Jackson
    public StrategyResult() {}

    public StrategyResult(StrategyConfig cfg, double winRate, double pnl, double drawdown) {
        this.name = cfg.getName();
        this.symbol = cfg.getSymbol();
        this.timeframe = cfg.getTimeframe();
        this.rules = cfg.getRules();

        this.winRate = winRate;
        this.pnl = pnl;
        this.drawdown = drawdown;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public List<StrategyConfig.RuleConfig> getRules() { return rules; }
    public void setRules(List<StrategyConfig.RuleConfig> rules) { this.rules = rules; }

    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }

    public double getPnl() { return pnl; }
    public void setPnl(double pnl) { this.pnl = pnl; }

    public double getDrawdown() { return drawdown; }
    public void setDrawdown(double drawdown) { this.drawdown = drawdown; }
}

