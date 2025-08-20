package com.pm.dto;

import java.math.BigDecimal;

public class PositionItem {
    public String symbol;
    public String exchange;
    public String instrumentType; // FUT/OPT/FX/COMMODITY or INTRADAY_EQUITY
    public String expiry;         // yyyy-MM-dd for derivatives
    public BigDecimal strike;     // if OPT
    public String optionType;     // CE/PE
    public String side;           // LONG/SHORT
    public BigDecimal quantity;   // net open
    public BigDecimal avgPrice;
    public BigDecimal pnlRealized;   // realized till now (if broker provides)
    public BigDecimal pnlUnrealized; // optional
}
