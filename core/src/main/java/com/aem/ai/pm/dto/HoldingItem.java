package com.aem.ai.pm.dto;

import java.math.BigDecimal;

public class HoldingItem {
    public String symbol;       // e.g. "TCS", "SBIN"
    public String isin;         // optional
    public String exchange;     // NSE/BSE
    public String instrumentType; // EQUITY/ETF/MF
    public BigDecimal quantity;
    public BigDecimal avgCost;  // per unit
}
