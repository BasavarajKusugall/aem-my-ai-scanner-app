package com.aem.ai.realtime.brokers.kite;

import com.aem.ai.exception.ApiException;

import java.util.List;

/**
 * OrderService - generic operations.
 */
public interface OrderService {
    boolean isEnable();
    boolean isDryRun();
    OrderResponse placeOrder(String variety, OrderRequest req) throws ApiException;
    OrderResponse modifyOrder(String variety, String orderId, OrderRequest req) throws ApiException;
    OrderResponse cancelOrder(String variety, String orderId) throws ApiException;
    List<OrderDetail> listOrders() throws ApiException;
    List<OrderDetail> getOrderHistory(String orderId) throws ApiException;
    List<TradeDetail> listTrades() throws ApiException;
    List<TradeDetail> getOrderTrades(String orderId) throws ApiException;
}

