package com.aem.ai.pm.dto;

import java.math.BigDecimal;

public class CashSummary {
    public BigDecimal available;
    public BigDecimal used;
    public BigDecimal pnlRealizedToday;
    public BigDecimal pnlUnrealized;

    public BigDecimal getAvailable() {
        return available;
    }

    public void setAvailable(BigDecimal available) {
        this.available = available;
    }

    public BigDecimal getUsed() {
        return used;
    }

    public void setUsed(BigDecimal used) {
        this.used = used;
    }

    public BigDecimal getPnlRealizedToday() {
        return pnlRealizedToday;
    }

    public void setPnlRealizedToday(BigDecimal pnlRealizedToday) {
        this.pnlRealizedToday = pnlRealizedToday;
    }

    public BigDecimal getPnlUnrealized() {
        return pnlUnrealized;
    }

    public void setPnlUnrealized(BigDecimal pnlUnrealized) {
        this.pnlUnrealized = pnlUnrealized;
    }

    @Override
    public String toString() {
        return "CashSummary{" +
                "available=" + available +
                ", used=" + used +
                ", pnlRealizedToday=" + pnlRealizedToday +
                ", pnlUnrealized=" + pnlUnrealized +
                '}';
    }
}
