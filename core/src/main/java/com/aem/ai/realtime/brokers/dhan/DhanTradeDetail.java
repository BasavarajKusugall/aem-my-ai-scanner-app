package com.aem.ai.realtime.brokers.dhan;

import com.aem.ai.realtime.brokers.kite.TradeDetail;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DhanTradeDetail {
    public String dhanClientId;
    public String orderId;
    public String exchangeOrderId;
    public String exchangeTradeId;
    public String transactionType;
    public String exchangeSegment;
    public String productType;
    public String orderType;
    public String tradingSymbol;
    public String securityId;
    public int tradedQuantity;
    public double tradedPrice;

    public TradeDetail toGeneric() {
        TradeDetail td = new TradeDetail();
        td.trade_id = this.exchangeTradeId;
        td.order_id = this.orderId;
        td.exchange = this.exchangeSegment;
        td.tradingsymbol = this.tradingSymbol;
        td.quantity = this.tradedQuantity;
        td.average_price = this.tradedPrice;
        return td;
    }
}