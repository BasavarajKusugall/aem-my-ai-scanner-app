package com.aem.ai.realtime.brokers.upstox;

import com.aem.ai.realtime.brokers.kite.TradeDetail;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UpstoxTradeDetail {
    public String exchange;
    public String order_id;
    public String trade_id;
    public String trading_symbol;
    public double average_price;
    public int quantity;

    public TradeDetail toGeneric() {
        TradeDetail t = new TradeDetail();
        t.trade_id = this.trade_id;
        t.order_id = this.order_id;
        t.exchange = this.exchange;
        t.tradingsymbol = this.trading_symbol;
        t.average_price = this.average_price;
        t.quantity = this.quantity;
        return t;
    }
}