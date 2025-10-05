package com.aem.ai.pm.dto;

import java.math.BigDecimal;

public class CashSummary {

    public BigDecimal available = BigDecimal.ZERO;
    public BigDecimal used = BigDecimal.ZERO;
    public BigDecimal pnlRealizedToday = BigDecimal.ZERO;
    public BigDecimal pnlUnrealized = BigDecimal.ZERO;
    public BigDecimal net = BigDecimal.ZERO;

    public CashSummary() {}

    public CashSummary(BigDecimal available, BigDecimal used,
                       BigDecimal pnlRealizedToday, BigDecimal pnlUnrealized,
                       BigDecimal net) {
        this.available = available;
        this.used = used;
        this.pnlRealizedToday = pnlRealizedToday;
        this.pnlUnrealized = pnlUnrealized;
        this.net = net;
    }

    // ===== Standard Getters/Setters =====

    public BigDecimal getAvailable() { return available; }
    public void setAvailable(BigDecimal available) {
        this.available = available != null ? available : BigDecimal.ZERO;
    }

    public BigDecimal getUsed() { return used; }
    public void setUsed(BigDecimal used) {
        this.used = used != null ? used : BigDecimal.ZERO;
    }

    public BigDecimal getPnlRealizedToday() { return pnlRealizedToday; }
    public void setPnlRealizedToday(BigDecimal pnlRealizedToday) {
        this.pnlRealizedToday = pnlRealizedToday != null ? pnlRealizedToday : BigDecimal.ZERO;
    }

    public BigDecimal getPnlUnrealized() { return pnlUnrealized; }
    public void setPnlUnrealized(BigDecimal pnlUnrealized) {
        this.pnlUnrealized = pnlUnrealized != null ? pnlUnrealized : BigDecimal.ZERO;
    }

    public BigDecimal getNet() { return net; }
    public void setNet(BigDecimal net) {
        this.net = net != null ? net : BigDecimal.ZERO;
    }

    // ===== Fluent Builder Methods =====

    public CashSummary withAvailable(BigDecimal available) {
        setAvailable(available);
        return this;
    }

    public CashSummary withUsed(BigDecimal used) {
        setUsed(used);
        return this;
    }

    public CashSummary withPnlRealizedToday(BigDecimal pnlRealizedToday) {
        setPnlRealizedToday(pnlRealizedToday);
        return this;
    }

    public CashSummary withPnlUnrealized(BigDecimal pnlUnrealized) {
        setPnlUnrealized(pnlUnrealized);
        return this;
    }

    public CashSummary withNet(BigDecimal net) {
        setNet(net);
        return this;
    }

    // ===== toString =====

    @Override
    public String toString() {
        return "CashSummary{" +
                "available=" + available +
                ", used=" + used +
                ", pnlRealizedToday=" + pnlRealizedToday +
                ", pnlUnrealized=" + pnlUnrealized +
                ", net=" + net +
                '}';
    }
}
