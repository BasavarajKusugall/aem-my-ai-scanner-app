package com.aem.ai.realtime.brokers.delta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class DeltaFillResponse {
    public Long id;
    public Long order_id;
    public String side;
    public Integer size;
    public String price;
    public String created_at;

    public com.aem.ai.realtime.brokers.kite.TradeDetail toGeneric() {
        com.aem.ai.realtime.brokers.kite.TradeDetail t = new com.aem.ai.realtime.brokers.kite.TradeDetail();
        t.trade_id = String.valueOf(this.id);
        t.order_id = String.valueOf(this.order_id);
        t.tradingsymbol = null; // delta fill doesn't always include symbol here
        try {
            t.average_price = Double.parseDouble(this.price);
        } catch (Exception ignored) {
        }
        t.quantity = this.size != null ? this.size : 0;
        return t;
    }
}
