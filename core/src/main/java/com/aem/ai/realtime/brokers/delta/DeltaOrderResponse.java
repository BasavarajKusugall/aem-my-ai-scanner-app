package com.aem.ai.realtime.brokers.delta;

import com.aem.ai.realtime.brokers.kite.OrderDetail;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class DeltaOrderResponse {
    public Long id;
    public Long user_id;
    public Integer size;
    public Integer unfilled_size;
    public String side;
    public String order_type;
    public String limit_price;
    public String stop_order_type;
    public String stop_price;
    public String client_order_id;
    public String state;
    public String created_at;
    public Integer product_id;
    public String product_symbol;

    public OrderDetail toGeneric() {
        OrderDetail o = new OrderDetail();
        o.order_id = String.valueOf(this.id);
        o.status = this.state;
        o.tradingsymbol = this.product_symbol;
        o.exchange = "DELTA";
        o.order_type = this.order_type;
        o.transaction_type = this.side != null ? this.side.toUpperCase() : null;
        o.quantity = this.size != null ? this.size : 0;
        o.filled_quantity = (this.size != null && this.unfilled_size != null) ? (this.size - this.unfilled_size) : 0;
        try {
            o.price = Double.parseDouble(this.limit_price);
        } catch (Exception ignored) {
        }
        return o;
    }
}
