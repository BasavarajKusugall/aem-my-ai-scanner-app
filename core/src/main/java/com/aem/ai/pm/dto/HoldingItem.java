package com.aem.ai.pm.dto;

import java.math.BigDecimal;

public class HoldingItem {
    public String symbol;          // e.g. "TCS", "SBIN"
    public String isin;            // e.g. INE528G01035
    public String exchange;        // NSE/BSE
    public String instrumentType;  // EQUITY/ETF/MF

    // Quantities
    public BigDecimal quantity;        // total holding quantity
    public BigDecimal t1Quantity;      // T+1 unsettled quantity
    public BigDecimal collateralQuantity; // pledged quantity (if any)

    // Prices
    public BigDecimal avgCost;     // average acquisition price
    public BigDecimal lastPrice;   // current LTP
    public BigDecimal closePrice;  // previous close

    // PnL
    public BigDecimal pnl;         // total profit/loss on holding

    // Extra Info
    public String companyName;     // e.g. "YES BANK LTD."
    public String collateralType;  // e.g. "WC" if pledged

    @Override
    public String toString() {
        return "HoldingItem{" +
                "symbol='" + symbol + '\'' +
                ", isin='" + isin + '\'' +
                ", exchange='" + exchange + '\'' +
                ", instrumentType='" + instrumentType + '\'' +
                ", quantity=" + quantity +
                ", t1Quantity=" + t1Quantity +
                ", collateralQuantity=" + collateralQuantity +
                ", avgCost=" + avgCost +
                ", lastPrice=" + lastPrice +
                ", closePrice=" + closePrice +
                ", pnl=" + pnl +
                ", companyName='" + companyName + '\'' +
                ", collateralType='" + collateralType + '\'' +
                '}';
    }
}
