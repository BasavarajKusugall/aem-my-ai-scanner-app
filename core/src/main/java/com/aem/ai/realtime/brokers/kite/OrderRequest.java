package com.aem.ai.realtime.brokers.kite;

import com.aem.ai.scanner.model.InstrumentSymbol;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderRequest {
    @JsonIgnore
    public InstrumentSymbol instrument; // optional - can be derived from tradingsymbol+exchange
    // common params - keep flexible and allow arbitrary meta
    public String tradingsymbol;
    public String exchange;
    public String transaction_type; // BUY/SELL
    public String order_type; // MARKET/LIMIT/SL etc.
    public String product; // CNC/MIS/NRML/MTF
    public Long quantity;
    public Double price;
    public Double trigger_price;
    public String validity; // DAY/IOC/TTL
    public Integer validity_ttl;
    public Boolean autoslice;
    public String tag;

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

    public String getTransaction_type() {
        return transaction_type;
    }

    public void setTransaction_type(String transaction_type) {
        this.transaction_type = transaction_type;
    }

    public String getOrder_type() {
        return order_type;
    }

    public void setOrder_type(String order_type) {
        this.order_type = order_type;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getTrigger_price() {
        return trigger_price;
    }

    public void setTrigger_price(Double trigger_price) {
        this.trigger_price = trigger_price;
    }

    public String getValidity() {
        return validity;
    }

    public void setValidity(String validity) {
        this.validity = validity;
    }

    public Integer getValidity_ttl() {
        return validity_ttl;
    }

    public void setValidity_ttl(Integer validity_ttl) {
        this.validity_ttl = validity_ttl;
    }

    public Boolean getAutoslice() {
        return autoslice;
    }

    public void setAutoslice(Boolean autoslice) {
        this.autoslice = autoslice;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Map<String, String> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, String> extra) {
        this.extra = extra;
    }

    public InstrumentSymbol getInstrument() {
        return instrument;
    }

    public void setInstrument(InstrumentSymbol instrument) {
        this.instrument = instrument;
    }

    public Map<String,String> extra = new HashMap<>();

    public Map<String,String> toForm() {
        Map<String,String> m = new LinkedHashMap<>();
        putIfNotNull(m, "tradingsymbol", tradingsymbol);
        putIfNotNull(m, "exchange", exchange);
        putIfNotNull(m, "transaction_type", transaction_type);
        putIfNotNull(m, "order_type", order_type);
        putIfNotNull(m, "product", product);
        putIfNotNull(m, "quantity", quantity==null?null:String.valueOf(quantity));
        putIfNotNull(m, "price", price==null?null:String.valueOf(price));
        putIfNotNull(m, "trigger_price", trigger_price==null?null:String.valueOf(trigger_price));
        putIfNotNull(m, "validity", validity);
        putIfNotNull(m, "validity_ttl", validity_ttl==null?null:String.valueOf(validity_ttl));
        putIfNotNull(m, "autoslice", autoslice==null?null:String.valueOf(autoslice));
        putIfNotNull(m, "tag", tag);
        if (extra!=null) extra.forEach((k,v) -> putIfNotNull(m,k,v));
        return m;
    }
    private void putIfNotNull(Map<String,String> m, String k, String v) {
        if (v != null) m.put(k, v);
    }
}

