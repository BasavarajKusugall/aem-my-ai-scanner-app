package com.aem.ai.pm.dto;

import java.time.Instant;
import java.util.List;

public class PortfolioSnapshot {
    public final List<HoldingItem> holdings;
    public final List<PositionItem> positions;
    public final CashSummary cash;
    public  String positionsJson;
    public  String holdingsJson;
    public final Instant asOf;
    public PortfolioSnapshot(List<HoldingItem> h, List<PositionItem> p, CashSummary c, Instant asOf){
        this.holdings=h; this.positions=p; this.cash=c; this.asOf=asOf;
    }

    public List<HoldingItem> getHoldings() {
        return holdings;
    }

    public List<PositionItem> getPositions() {
        return positions;
    }

    public CashSummary getCash() {
        return cash;
    }

    public String getPositionsJson() {
        return positionsJson;
    }

    public void setPositionsJson(String positionsJson) {
        this.positionsJson = positionsJson;
    }

    public String getHoldingsJson() {
        return holdingsJson;
    }

    public void setHoldingsJson(String holdingsJson) {
        this.holdingsJson = holdingsJson;
    }

    public Instant getAsOf() {
        return asOf;
    }
}
