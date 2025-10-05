package com.aem.ai.realtime.brokers.kite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeDetail {
    public String trade_id;
    public String order_id;
    public String exchange;
    public String tradingsymbol;
    public long quantity;
    public double average_price;
    public String fill_timestamp;
}
