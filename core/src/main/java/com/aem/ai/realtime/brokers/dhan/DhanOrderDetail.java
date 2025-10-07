package com.aem.ai.realtime.brokers.dhan;

import com.aem.ai.realtime.brokers.kite.OrderDetail;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DhanOrderDetail {
    public String dhanClientId;
    public String orderId;
    public String correlationId;
    public String orderStatus;
    public String transactionType;
    public String exchangeSegment;
    public String productType;
    public String orderType;
    public String validity;
    public String tradingSymbol;
    public String securityId;
    public int quantity;
    public int disclosedQuantity;
    public double price;
    public double triggerPrice;
    public boolean afterMarketOrder;

    public OrderDetail toGeneric() {
        OrderDetail od = new OrderDetail();
        od.order_id = this.orderId;
        od.status = this.orderStatus;
        od.tradingsymbol = this.tradingSymbol;
        od.exchange = this.exchangeSegment;
        od.order_type = this.orderType;
        od.transaction_type = this.transactionType;
        od.quantity = this.quantity;
        od.filled_quantity = 0;
        od.price = this.price;
        return od;
    }
}
