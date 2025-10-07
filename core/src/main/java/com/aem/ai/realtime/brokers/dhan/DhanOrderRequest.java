package com.aem.ai.realtime.brokers.dhan;

import com.aem.ai.realtime.brokers.kite.OrderRequest;
import com.aem.ai.scanner.model.InstrumentSymbol;

public class DhanOrderRequest {
    // fields as per Dhan docs
    public String dhanClientId;
    public String correlationId;
    public String transactionType;
    public String exchangeSegment;
    public String productType;
    public String orderType;
    public String validity;
    public String tradingSymbol;
    public String securityId;
    public Integer quantity;
    public Integer disclosedQuantity;
    public Double price;
    public Double triggerPrice;
    public Boolean afterMarketOrder;
    public String amoTime;
    public Double boProfitValue;
    public Double boStopLossValue;
    public String drvExpiryDate;
    public String drvOptionType;
    public Double drvStrikePrice;

    public String orderId; // used for modify

    public static DhanOrderRequest fromGeneric(OrderRequest req) {
        DhanOrderRequest d = new DhanOrderRequest();
        d.correlationId = req.tag;
        d.transactionType = req.transaction_type;
        d.exchangeSegment = mapExchange(req.exchange);
        d.productType = mapProduct(req.product);
        d.orderType = mapOrderType(req.order_type);
        d.validity = req.validity;
        d.tradingSymbol = req.tradingsymbol;
        d.securityId = req.instrument != null ? String.valueOf(req.instrument.getSecurityId()) : null;
        d.quantity = req.quantity != null ? req.quantity.intValue() : null;
        d.price = req.price;
        d.triggerPrice = req.trigger_price;
        d.afterMarketOrder = req.autoslice != null ? req.autoslice : false;
        return d;
    }

    // minimal mapping helpers - extend as needed
    private static String mapExchange(String kiteExchange) {
        if (kiteExchange == null) return null;
        switch (kiteExchange.toUpperCase()) {
            case "NSE": return "NSE_EQ";
            case "FNO": return "NSE_FNO";
            case "CUR": return "NSE_CURRENCY";
            default: return kiteExchange;
        }
    }

    private static String mapProduct(String product) {
        if (product == null) return null;
        switch (product.toUpperCase()) {
            case "CNC": return "CNC";
            case "MIS": return "INTRADAY";
            default: return product;
        }
    }

    private static String mapOrderType(String orderType) {
        if (orderType == null) return null;
        switch (orderType.toUpperCase()) {
            case "MARKET": return "MARKET";
            case "LIMIT": return "LIMIT";
            case "SL": return "STOP_LOSS";
            case "SLM": return "STOP_LOSS_MARKET";
            default: return orderType;
        }
    }

    public void setOrderId(String id) { this.orderId = id; }
}