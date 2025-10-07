package com.aem.ai.realtime.brokers.upstox;

import com.aem.ai.realtime.brokers.kite.OrderRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class UpstoxOrderRequest {
    public String correlation_id;
    public int quantity;
    public String product;
    public String validity;
    public double price;
    public String instrument_token;
    public String order_type;
    public String transaction_type;
    public int disclosed_quantity;
    public double trigger_price;
    public boolean is_amo;

    public static UpstoxOrderRequest fromGeneric(OrderRequest req) {
        UpstoxOrderRequest r = new UpstoxOrderRequest();
        r.correlation_id = req.tag;
        r.quantity = req.quantity != null ? req.quantity.intValue() : 0;
        r.product = req.product;
        r.validity = req.validity;
        r.price = req.price != null ? req.price : 0;
        r.instrument_token = req.tradingsymbol;
        r.order_type = req.order_type;
        r.transaction_type = req.transaction_type;
        r.disclosed_quantity = 0;
        r.trigger_price = req.trigger_price != null ? req.trigger_price : 0;
        r.is_amo = false;
        return r;
    }
}