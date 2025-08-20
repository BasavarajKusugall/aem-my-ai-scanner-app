package com.pm.dto;

import java.time.Instant;
import java.util.List;

public class PortfolioSnapshot {
    public final List<HoldingItem> holdings;
    public final List<PositionItem> positions;
    public final CashSummary cash;
    public final Instant asOf;
    public PortfolioSnapshot(List<HoldingItem> h, List<PositionItem> p, CashSummary c, Instant asOf){
        this.holdings=h; this.positions=p; this.cash=c; this.asOf=asOf;
    }
}
