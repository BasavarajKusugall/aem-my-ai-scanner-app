package com.aem.ai.realtime.brokers.delta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.aem.ai.realtime.brokers.kite.*;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DeltaOrderRequest {
    public Integer product_id;
    public String product_symbol;
    public String limit_price;
    public Integer size;
    public String side; // buy/sell
    public String order_type; // limit_order, market_order
    public String stop_order_type;
    public String stop_price;
    public String client_order_id;

    public static DeltaOrderRequest fromGeneric(OrderRequest r) {
        DeltaOrderRequest d = new DeltaOrderRequest();
        d.product_symbol = r.tradingsymbol;
        d.size = r.quantity != null ? r.quantity.intValue() : null;
        d.limit_price = r.price != null ? String.valueOf(r.price) : null;
        d.side = r.transaction_type != null ? r.transaction_type.toLowerCase() : null;
        d.order_type = (r.order_type != null && r.order_type.equalsIgnoreCase("MARKET")) ? "market_order" : "limit_order";
        d.client_order_id = r.tag;
        return d;
    }
}