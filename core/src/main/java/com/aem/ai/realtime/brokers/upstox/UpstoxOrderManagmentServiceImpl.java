package com.aem.ai.realtime.brokers.upstox;

import com.aem.ai.exception.ApiException;
import com.aem.ai.pm.connectors.BrokerConnector;
import com.aem.ai.pm.dto.BrokerAccountRef;
import com.aem.ai.realtime.brokers.kite.OrderDetail;
import com.aem.ai.realtime.brokers.kite.OrderRequest;
import com.aem.ai.realtime.brokers.kite.OrderResponse;
import com.aem.ai.scanner.services.TelegramService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Component(service = UpstoxOrderManagmentService.class, immediate = true)
public class UpstoxOrderManagmentServiceImpl implements UpstoxOrderManagmentService {

    private static final Logger LOG = LoggerFactory.getLogger(UpstoxOrderManagmentServiceImpl.class);
    private boolean dryRun;
    private boolean enable;

    @ObjectClassDefinition(name = "Upstox Order Service Config", description = "Configuration for Upstox Order Service")
    public @interface Config {
        @AttributeDefinition(name = "Enable") boolean enable() default false;
        @AttributeDefinition(name = "Dry run") boolean dryRun() default false;
    }

    @Reference private UpstoxApiClient client;
    @Reference private TelegramService telegramService;

    private ObjectMapper mapper;

    @Activate
    public void activate(Config config) {
        this.mapper = new ObjectMapper();
        this.mapper.findAndRegisterModules();
        this.enable = config.enable();
        this.dryRun = config.dryRun();
        LOG.info("[UpstoxOrderService] Activated | enable={} dryRun={}", enable, dryRun);
    }

    private void sendTelegramMessage(String action, String orderId, String status) {
        String msg = String.format("📌 *Upstox Order Alert*\nAction: %s\nOrder ID: %s\nStatus: %s", action, orderId, status);
        telegramService.sendMessageKiteAlerts(msg);
    }

    @Override public boolean isEnable() { return enable; }
    @Override public boolean isDryRun() { return dryRun; }

    @Override
    public List<OrderDetail> listOrders() throws ApiException {
        List<UpstoxOrderDetail[]> responses = client.get("/order/retrieve-all", UpstoxOrderDetail[].class);
        if (responses == null || responses.isEmpty()) throw new ApiException("No response from Upstox");
        List<com.aem.ai.realtime.brokers.kite.OrderDetail> result = new ArrayList<>();
        for (UpstoxOrderDetail d : responses.get(0)) result.add(d.toGeneric());
        return result;
    }

    @Override
    public List<com.aem.ai.realtime.brokers.kite.OrderDetail> getOrderHistory(String orderId) throws ApiException {
        List<UpstoxOrderDetail[]> responses = client.get("/order/history?order_id=" + orderId, UpstoxOrderDetail[].class);
        if (responses == null || responses.isEmpty()) throw new ApiException("No response from Upstox");
        List<com.aem.ai.realtime.brokers.kite.OrderDetail> result = new ArrayList<>();
        for (UpstoxOrderDetail d : responses.get(0)) result.add(d.toGeneric());
        return result;
    }

    @Override
    public List<com.aem.ai.realtime.brokers.kite.TradeDetail> listTrades() throws ApiException {
        List<UpstoxTradeDetail[]> responses = client.get("/order/trades/get-trades-for-day", UpstoxTradeDetail[].class);
        if (responses == null || responses.isEmpty()) throw new ApiException("No response from Upstox");
        List<com.aem.ai.realtime.brokers.kite.TradeDetail> result = new ArrayList<>();
        for (UpstoxTradeDetail d : responses.get(0)) result.add(d.toGeneric());
        return result;
    }

    @Override
    public List<com.aem.ai.realtime.brokers.kite.TradeDetail> getOrderTrades(String orderId) throws ApiException {
        List<UpstoxTradeDetail[]> responses = client.get("/order/trades?order_id=" + orderId, UpstoxTradeDetail[].class);
        if (responses == null || responses.isEmpty()) throw new ApiException("No response from Upstox");
        List<com.aem.ai.realtime.brokers.kite.TradeDetail> result = new ArrayList<>();
        for (UpstoxTradeDetail d : responses.get(0)) result.add(d.toGeneric());
        return result;
    }

    @Override
    public OrderResponse placeOrder(String variety, OrderRequest req) throws ApiException {
        UpstoxOrderRequest upReq = UpstoxOrderRequest.fromGeneric(req);
        if (dryRun) {
            LOG.warn("Dry run enabled - not placing order: {}", upReq);
            return new OrderResponse(UUID.randomUUID().toString());
        }
        List<UpstoxOrderResponse> res = client.post("/order/place", upReq, UpstoxOrderResponse.class);
        UpstoxOrderResponse first = res.get(0);
        sendTelegramMessage("PLACE", first.getOrderId(), first.getStatus());
        OrderResponse out = new OrderResponse(first.getOrderId());
        out.setStatus(first.getStatus());
        return out;
    }
    @Override
    public OrderResponse modifyOrder(String variety, String orderId, OrderRequest req) throws ApiException {
        UpstoxOrderRequest upReq = UpstoxOrderRequest.fromGeneric(req);
        List<UpstoxOrderResponse> res = client.put("/order/modify?order_id=" + URLEncoder.encode(orderId, StandardCharsets.UTF_8), upReq, UpstoxOrderResponse.class);
        UpstoxOrderResponse first = res.get(0);
        sendTelegramMessage("MODIFY", first.getOrderId(), first.getStatus());
        OrderResponse out = new OrderResponse(first.getOrderId());
        out.setStatus(first.getStatus());
        return out;
    }
    @Override
    public OrderResponse cancelOrder(String variety, String orderId) throws ApiException {
        List<UpstoxOrderResponse> res = client.delete("/order/cancel?order_id=" + URLEncoder.encode(orderId, StandardCharsets.UTF_8), UpstoxOrderResponse.class);
        UpstoxOrderResponse first = res.get(0);
        sendTelegramMessage("CANCEL", first.getOrderId(), first.getStatus());
        OrderResponse out = new OrderResponse(first.getOrderId());
        out.setStatus(first.getStatus());
        return out;
    }
    // --- NEW IMPLEMENTATIONS ---

    public String placeMultiOrder(List<Map<String, Object>> orders) throws ApiException {
        BrokerConnector connector = getUpstoxBroker();
        if (connector == null) throw new IllegalStateException("UPSTOX BrokerConnector not found");
        BrokerAccountRef ref = connector.discoverAccounts().get(0);
        try {
            String body = mapper.writeValueAsString(orders);
            HttpRequest req = client.baseRequest("/order/multi/place", ref)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return client.send(req, String.class);
        } catch (Exception e) {
            throw new ApiException("Failed to place multi order: " + e.getMessage(), e);
        }
    }

    public String cancelMultiOrder(String segment, String tag) throws ApiException {
        BrokerConnector connector = getUpstoxBroker();
        if (connector == null) throw new IllegalStateException("UPSTOX BrokerConnector not found");
        BrokerAccountRef ref = connector.discoverAccounts().get(0);
        String path = "/order/multi/cancel";
        if (segment != null || tag != null) {
            List<String> params = new ArrayList<>();
            if (segment != null) params.add("segment=" + segment);
            if (tag != null) params.add("tag=" + tag);
            path += "?" + String.join("&", params);
        }
        HttpRequest req = client.baseRequest(path, ref)
                .DELETE()
                .build();
        return client.send(req, String.class);
    }

    public String exitAllPositions(String segment, String tag) throws ApiException {
        BrokerConnector connector = getUpstoxBroker();
        if (connector == null) throw new IllegalStateException("UPSTOX BrokerConnector not found");
        BrokerAccountRef ref = connector.discoverAccounts().get(0);
        String path = "/order/positions/exit";
        if (segment != null || tag != null) {
            List<String> params = new ArrayList<>();
            if (segment != null) params.add("segment=" + segment);
            if (tag != null) params.add("tag=" + tag);
            path += "?" + String.join("&", params);
        }
        HttpRequest req = client.baseRequest(path, ref)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(req, String.class);
    }
    private BrokerConnector getUpstoxBroker() {
        for (BrokerConnector c : brokerConnectors) {
            if ("UPSTOX".equalsIgnoreCase(c.brokerCode())) return c;
        }
        return null;
    }

    @Reference(
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            bind = "bindBrokerConnector",
            unbind = "unbindBrokerConnector"
    )
    private volatile List<BrokerConnector> brokerConnectors = new CopyOnWriteArrayList<>();

    protected void bindBrokerConnector(BrokerConnector connector) { brokerConnectors.add(connector); }
    protected void unbindBrokerConnector(BrokerConnector connector) { brokerConnectors.remove(connector); }


}