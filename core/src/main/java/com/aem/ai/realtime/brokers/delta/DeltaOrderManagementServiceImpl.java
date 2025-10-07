package com.aem.ai.realtime.brokers.delta;

import com.aem.ai.exception.ApiException;
import com.aem.ai.realtime.brokers.kite.*;
import com.aem.ai.scanner.services.TelegramService;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component(service = DeltaOrderManagementService.class, immediate = true)
public class DeltaOrderManagementServiceImpl implements DeltaOrderManagementService {

    private static final Logger LOG = LoggerFactory.getLogger(DeltaOrderManagementServiceImpl.class);

    @Reference private DeltaApiClient client;
    @Reference private TelegramService telegramService;

    private boolean enable;
    private boolean dryRun;

    @ObjectClassDefinition(name = "Delta Order Service Config", description = "Configuration for Delta Order Service")
    public @interface Config {
        @AttributeDefinition(name = "Enable") boolean enable() default false;
        @AttributeDefinition(name = "Dry run") boolean dryRun() default false;
    }

    @Activate
    public void activate(Config config) {
        this.enable = config.enable();
        this.dryRun = config.dryRun();
        LOG.info("[DeltaOrderService] Activated | enable={} dryRun={}", enable, dryRun);
    }

    private void sendTelegramMessage(String action, String orderId, String status) {
        try {
            String msg = String.format("📌 *Delta Order Alert*\nAction: %s\nOrder ID: %s\nStatus: %s", action, orderId, status);
            telegramService.sendMessageKiteAlerts(msg);
        } catch (Exception e) {
            LOG.warn("Failed to send telegram alert: {}", e.getMessage());
        }
    }

    @Override public boolean isEnable() { return enable; }
    @Override public boolean isDryRun() { return dryRun; }

    private <T> T measure(String op, OperationSupplier<T> supplier) throws ApiException {
        long start = System.currentTimeMillis();
        try {
            LOG.info("[DeltaOrderService] ▶ {}...", op);
            return supplier.get();
        } finally {
            LOG.info("[DeltaOrderService] ⏱ {} done in {} ms", op, System.currentTimeMillis() - start);
        }
    }

    @Override
    public OrderResponse placeOrder(String variety, OrderRequest req) throws ApiException {
        return measure("placeOrder", () -> {
            DeltaOrderRequest dor = DeltaOrderRequest.fromGeneric(req);
            if (dryRun) {
                LOG.warn("Dry run - not placing order: {}", dor);
                return new OrderResponse("DRYRUN-" + UUID.randomUUID());
            }
            List<DeltaOrderResponseWrapper> wrappers = client.post("/orders", dor, DeltaOrderResponseWrapper.class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from Delta");
            DeltaOrderResponseWrapper w = wrappers.get(0);
            DeltaOrderResponse r = w.result;
            sendTelegramMessage("PLACE", String.valueOf(r.id), r.state);
            OrderResponse out = new OrderResponse(String.valueOf(r.id));
            out.setStatus(r.state);
            return out;
        });
    }

    @Override
    public OrderResponse modifyOrder(String variety, String orderId, OrderRequest req) throws ApiException {
        return measure("modifyOrder", () -> {
            DeltaEditOrderRequest edit = DeltaEditOrderRequest.fromGeneric(orderId, req);
            List<DeltaOrderResponseWrapper> wrappers = client.put("/orders", edit, DeltaOrderResponseWrapper.class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from Delta");
            DeltaOrderResponseWrapper w = wrappers.get(0);
            DeltaOrderResponse r = w.result;
            sendTelegramMessage("MODIFY", String.valueOf(r.id), r.state);
            OrderResponse out = new OrderResponse(String.valueOf(r.id));
            out.setStatus(r.state);
            return out;
        });
    }

    @Override
    public OrderResponse cancelOrder(String variety, String orderId) throws ApiException {
        return measure("cancelOrder", () -> {
            Map<String,Object> body = new HashMap<>();
            // Delta accepts id or client_order_id; we'll use id
            body.put("id", Long.parseLong(orderId));
            List<DeltaOrderResponseWrapper> wrappers = client.delete("/orders", body, DeltaOrderResponseWrapper.class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from Delta");
            DeltaOrderResponseWrapper w = wrappers.get(0);
            DeltaOrderResponse r = w.result;
            sendTelegramMessage("CANCEL", String.valueOf(r.id), r.state);
            OrderResponse out = new OrderResponse(String.valueOf(r.id));
            out.setStatus(r.state);
            return out;
        });
    }

    @Override
    public List<OrderDetail> listOrders() throws ApiException {
        return measure("listOrders", () -> {
            List<DeltaOrderResponse[]> wrappers = client.get("/orders", DeltaOrderResponse[].class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from Delta");
            // use first account
            DeltaOrderResponse[] arr = wrappers.get(0);
            List<OrderDetail> out = new ArrayList<>();
            for (DeltaOrderResponse r : arr) out.add(r.toGeneric());
            return out;
        });
    }

    @Override
    public List<OrderDetail> getOrderHistory(String orderId) throws ApiException {
        return measure("getOrderHistory", () -> {
            List<DeltaOrderResponse[]> wrappers = client.get("/orders/history?product_ids=&", DeltaOrderResponse[].class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from Delta");
            DeltaOrderResponse[] arr = wrappers.get(0);
            List<OrderDetail> out = new ArrayList<>();
            for (DeltaOrderResponse r : arr) out.add(r.toGeneric());
            return out;
        });
    }

    @Override
    public List<com.aem.ai.realtime.brokers.kite.TradeDetail> listTrades() throws ApiException {
        return measure("listTrades", () -> {
            List<DeltaFillResponse[]> wrappers = client.get("/fills", DeltaFillResponse[].class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from Delta");
            DeltaFillResponse[] arr = wrappers.get(0);
            List<com.aem.ai.realtime.brokers.kite.TradeDetail> out = new ArrayList<>();
            for (DeltaFillResponse f : arr) out.add(f.toGeneric());
            return out;
        });
    }

    @Override
    public List<com.aem.ai.realtime.brokers.kite.TradeDetail> getOrderTrades(String orderId) throws ApiException {
        return measure("getOrderTrades", () -> {
            List<DeltaFillResponse[]> wrappers = client.get("/fills?order_id=" + URLEncoder.encode(orderId, StandardCharsets.UTF_8), DeltaFillResponse[].class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from Delta");
            DeltaFillResponse[] arr = wrappers.get(0);
            List<com.aem.ai.realtime.brokers.kite.TradeDetail> out = new ArrayList<>();
            for (DeltaFillResponse f : arr) out.add(f.toGeneric());
            return out;
        });
    }

    @FunctionalInterface
    interface OperationSupplier<T> { T get() throws ApiException; }
}