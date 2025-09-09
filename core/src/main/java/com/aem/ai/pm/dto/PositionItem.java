package com.aem.ai.pm.dto;

import java.math.BigDecimal;

public class PositionItem {
    public String symbol;
    public String exchange;
    public String instrumentType;   // FUT/OPT/FX/COMMODITY/EQUITY
    public String expiry;           // yyyy-MM-dd for derivatives (optional parsing)
    public BigDecimal strike;       // if OPT
    public String optionType;       // CE/PE
    public String side;             // LONG/SHORT

    // Quantities
    public BigDecimal quantity;     // net open

    // Prices
    public BigDecimal avgPrice;     // average holding price
    public BigDecimal buyPrice;     // last buy price
    public BigDecimal sellPrice;    // last sell price
    public BigDecimal lastPrice;    // current market LTP
    public BigDecimal closePrice;   // previous close

    // PnL
    public BigDecimal pnl;             // overall pnl from broker
    public BigDecimal pnlRealized;     // realized till now
    public BigDecimal pnlUnrealized;   // unrealized (mark-to-market)

    // Values
    public BigDecimal buyValue;     // buy notional
    public BigDecimal sellValue;    // sell notional
    public BigDecimal value;        // net position value

    @Override
    public String toString() {
        return "PositionItem{" +
                "symbol='" + symbol + '\'' +
                ", exchange='" + exchange + '\'' +
                ", instrumentType='" + instrumentType + '\'' +
                ", expiry='" + expiry + '\'' +
                ", strike=" + strike +
                ", optionType='" + optionType + '\'' +
                ", side='" + side + '\'' +
                ", quantity=" + quantity +
                ", avgPrice=" + avgPrice +
                ", buyPrice=" + buyPrice +
                ", sellPrice=" + sellPrice +
                ", lastPrice=" + lastPrice +
                ", closePrice=" + closePrice +
                ", pnl=" + pnl +
                ", pnlRealized=" + pnlRealized +
                ", pnlUnrealized=" + pnlUnrealized +
                ", buyValue=" + buyValue +
                ", sellValue=" + sellValue +
                ", value=" + value +
                '}';
    }
}
