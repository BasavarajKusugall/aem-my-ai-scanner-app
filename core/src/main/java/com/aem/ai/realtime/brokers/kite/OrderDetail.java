package com.aem.ai.realtime.brokers.kite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDetail {
    public String order_id;
    public String status;
    public String tradingsymbol;
    public String exchange;
    public String order_type;
    public String transaction_type;
    public String variety;
    public long quantity;
    public long filled_quantity;
    public long pending_quantity;
    public double price;
    public double average_price;
    public Map<String,Object> meta;

    // Optional: Add these to prevent future errors
    public String account_id;
    public String placed_by;
    public String guid;

    // Getters & Setters (keep your existing ones)


    public String getOrder_id() {
        return order_id;
    }

    public void setOrder_id(String order_id) {
        this.order_id = order_id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTradingsymbol() {
        return tradingsymbol;
    }

    public void setTradingsymbol(String tradingsymbol) {
        this.tradingsymbol = tradingsymbol;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getOrder_type() {
        return order_type;
    }

    public void setOrder_type(String order_type) {
        this.order_type = order_type;
    }

    public String getTransaction_type() {
        return transaction_type;
    }

    public void setTransaction_type(String transaction_type) {
        this.transaction_type = transaction_type;
    }

    public String getVariety() {
        return variety;
    }

    public void setVariety(String variety) {
        this.variety = variety;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getFilled_quantity() {
        return filled_quantity;
    }

    public void setFilled_quantity(long filled_quantity) {
        this.filled_quantity = filled_quantity;
    }

    public long getPending_quantity() {
        return pending_quantity;
    }

    public void setPending_quantity(long pending_quantity) {
        this.pending_quantity = pending_quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getAverage_price() {
        return average_price;
    }

    public void setAverage_price(double average_price) {
        this.average_price = average_price;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public String getAccount_id() {
        return account_id;
    }

    public void setAccount_id(String account_id) {
        this.account_id = account_id;
    }

    public String getPlaced_by() {
        return placed_by;
    }

    public void setPlaced_by(String placed_by) {
        this.placed_by = placed_by;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }
}
