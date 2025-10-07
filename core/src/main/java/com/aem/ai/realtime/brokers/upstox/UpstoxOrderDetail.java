package com.aem.ai.realtime.brokers.upstox;

import com.aem.ai.realtime.brokers.kite.OrderDetail;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class UpstoxOrderDetail {
    public String exchange;
    public String product;
    public double price;
    public int quantity;
    public String status;
    public String trading_symbol;
    public String order_type;
    public String validity;
    public String transaction_type;
    public double average_price;
    public int filled_quantity;
    public String order_id;

    public com.aem.ai.realtime.brokers.kite.OrderDetail toGeneric() {
        com.aem.ai.realtime.brokers.kite.OrderDetail o = new com.aem.ai.realtime.brokers.kite.OrderDetail();
        o.order_id = this.order_id;
        o.status = this.status;
        o.tradingsymbol = this.trading_symbol;
        o.exchange = this.exchange;
        o.order_type = this.order_type;
        o.transaction_type = this.transaction_type;
        o.quantity = this.quantity;
        o.price = this.price;
        o.filled_quantity = this.filled_quantity;
        return o;
    }
}