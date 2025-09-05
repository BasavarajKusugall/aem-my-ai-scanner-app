package com.aem.ai.scanner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategyConfig {
    private String name;
    private String symbol;
    private String timeframe;
    private List<RuleConfig> rules;
    private int id;
    private double winRate;
    private double pnl;
    private double drawdown;

    public double getPnl() {
        return pnl;
    }

    public void setPnl(double pnl) {
        this.pnl = pnl;
    }

    public double getDrawdown() {
        return drawdown;
    }

    public void setDrawdown(double drawdown) {
        this.drawdown = drawdown;
    }

    public double getWinRate() {
        return winRate;
    }

    public void setWinRate(double winRate) {
        this.winRate = winRate;
    }

    // getters and setters
    // ...

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public List<RuleConfig> getRules() {
        return rules;
    }

    public void setRules(List<RuleConfig> rules) {
        this.rules = rules;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public StrategyConfig copy() {
        StrategyConfig clone = new StrategyConfig();
        clone.setName(this.name);
        clone.setSymbol(this.symbol);
        clone.setTimeframe(this.timeframe);
        clone.setId(this.id);

        if (this.rules != null) {
            List<RuleConfig> ruleClones = new ArrayList<>();
            for (RuleConfig r : this.rules) {
                RuleConfig rc = new RuleConfig();
                rc.setAction(r.getAction());
                rc.setStopLossPercent(r.getStopLossPercent());
                rc.setTakeProfitPercent(r.getTakeProfitPercent());
                rc.setStopLossPoints(r.getStopLossPoints());
                rc.setTakeProfitPoints(r.getTakeProfitPoints());
                rc.setStopLossAtrMultiplier(r.getStopLossAtrMultiplier());
                rc.setTakeProfitAtrMultiplier(r.getTakeProfitAtrMultiplier());

                if (r.getConditions() != null) {
                    List<Condition> condClones = new ArrayList<>();
                    for (Condition c : r.getConditions()) {
                        Condition cc = new Condition();
                        cc.indicator = c.indicator;
                        cc.operator  = c.operator;
                        cc.fast      = c.fast;
                        cc.slow      = c.slow;
                        cc.period    = c.period;
                        cc.signal    = c.signal;
                        cc.value     = c.value;
                        condClones.add(cc);
                    }
                    rc.setConditions(condClones);
                }
                ruleClones.add(rc);
            }
            clone.setRules(ruleClones);
        }
        return clone;
    }


    public static class RuleConfig {
        private String action;
        private List<Condition> conditions;

        // SL/TP in % and points
        private Double stopLossPercent;
        private Double takeProfitPercent;
        private Double stopLossPoints;
        private Double takeProfitPoints;

        // ✅ NEW ATR multipliers
        private Double stopLossAtrMultiplier;
        private Double takeProfitAtrMultiplier;

        // getters/setters
        public Double getStopLossAtrMultiplier() { return stopLossAtrMultiplier; }
        public void setStopLossAtrMultiplier(Double stopLossAtrMultiplier) { this.stopLossAtrMultiplier = stopLossAtrMultiplier; }

        public Double getTakeProfitAtrMultiplier() { return takeProfitAtrMultiplier; }
        public void setTakeProfitAtrMultiplier(Double takeProfitAtrMultiplier) { this.takeProfitAtrMultiplier = takeProfitAtrMultiplier; }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public List<Condition> getConditions() {
            return conditions;
        }

        public void setConditions(List<Condition> conditions) {
            this.conditions = conditions;
        }

        public Double getStopLossPercent() {
            return stopLossPercent;
        }

        public void setStopLossPercent(Double stopLossPercent) {
            this.stopLossPercent = stopLossPercent;
        }

        public Double getTakeProfitPercent() {
            return takeProfitPercent;
        }

        public void setTakeProfitPercent(Double takeProfitPercent) {
            this.takeProfitPercent = takeProfitPercent;
        }

        public Double getStopLossPoints() {
            return stopLossPoints;
        }

        public void setStopLossPoints(Double stopLossPoints) {
            this.stopLossPoints = stopLossPoints;
        }

        public Double getTakeProfitPoints() {
            return takeProfitPoints;
        }

        public void setTakeProfitPoints(Double takeProfitPoints) {
            this.takeProfitPoints = takeProfitPoints;
        }
    }
}

