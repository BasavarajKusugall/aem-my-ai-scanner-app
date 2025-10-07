package com.aem.ai.realtime.brokers.dhan;

import com.aem.ai.exception.ApiException;
import com.aem.ai.scanner.services.TelegramService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component(service = DhanOrderManagementService.class, immediate = true)
class DhanOrderManagementServiceImpl implements DhanOrderManagementService {

    private static final Logger LOG = LoggerFactory.getLogger(DhanOrderManagementServiceImpl.class);
    private boolean dryRun;
    private boolean enable;

    @ObjectClassDefinition(name = "Dhan Order Service Config", description = "Configuration for Dhan Order Service")
    public @interface Config {
        @AttributeDefinition(name = "Enable")
        boolean enable() default false;
        @AttributeDefinition(name = "Dry run")
        boolean dryRun() default false;
    }

    @Reference
    private DhanApiClient client;

    @Reference
    private TelegramService telegramService;

    private ObjectMapper mapper;

    @Activate
    public void activate(Config config) {
        this.mapper = new ObjectMapper();
        this.mapper.findAndRegisterModules();
        this.enable = config.enable();
        this.dryRun = config.dryRun();
        LOG.info("[DhanOrderService] Activated | enable={} dryRun={}", enable, dryRun);
    }

    @Deactivate
    public void deactivate() {
        LOG.info("[DhanOrderService] Deactivated");
    }

    private void sendTelegramMessage(String action, DhanOrderResponse resp) {
        String msg = String.format("📌 *Dhan Order Alert*\nAction: %s\nOrder ID: %s\nStatus: %s",
                action, resp.getOrderId(), resp.getOrderStatus());
        telegramService.sendMessageKiteAlerts(msg);
    }

    @Override
    public boolean isEnable() { return enable; }

    @Override
    public boolean isDryRun() { return dryRun; }

    private <T> T measure(String op, OperationSupplier<T> supplier) throws ApiException {
        long start = System.currentTimeMillis();
        try {
            LOG.info("[DhanOrderService] ▶ {}...", op);
            return supplier.get();
        } finally {
            LOG.info("[DhanOrderService] ⏱ {} done in {} ms", op, System.currentTimeMillis() - start);
        }
    }

    @Override
    public com.aem.ai.realtime.brokers.kite.OrderResponse placeOrder(String variety, com.aem.ai.realtime.brokers.kite.OrderRequest req) throws ApiException {
        return measure("placeOrder", () -> {
            // convert Kite OrderRequest to DhanOrderRequest
            DhanOrderRequest dhanOrderRequest = DhanOrderRequest.fromGeneric(req);
            if (dryRun) {
                LOG.warn("Dry run - not sending to DHAN: {}", dhanOrderRequest);
            }
            List<DhanOrderResponse> wrappers = client.post("/orders", dhanOrderRequest, DhanOrderResponse.class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from DHAN");
            DhanOrderResponse first = wrappers.get(0);
            sendTelegramMessage("PLACE", first);
            // map to generic response
            com.aem.ai.realtime.brokers.kite.OrderResponse out = new com.aem.ai.realtime.brokers.kite.OrderResponse(first.getOrderId());
            out.setStatus(first.getOrderStatus());
            return out;
        });
    }

    @Override
    public com.aem.ai.realtime.brokers.kite.OrderResponse modifyOrder(String variety, String orderId, com.aem.ai.realtime.brokers.kite.OrderRequest req) throws ApiException {
        return measure("modifyOrder", () -> {
            DhanOrderRequest dr = DhanOrderRequest.fromGeneric(req);
            dr.setOrderId(orderId);
            List<DhanOrderResponse> wrappers = client.put("/orders/" + URLEncoder.encode(orderId, StandardCharsets.UTF_8), dr, DhanOrderResponse.class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from DHAN");
            DhanOrderResponse r = wrappers.get(0);
            sendTelegramMessage("MODIFY", r);
            com.aem.ai.realtime.brokers.kite.OrderResponse out = new com.aem.ai.realtime.brokers.kite.OrderResponse(r.getOrderId());
            out.setStatus(r.getOrderStatus());
            return out;
        });
    }

    @Override
    public com.aem.ai.realtime.brokers.kite.OrderResponse cancelOrder(String variety, String orderId) throws ApiException {
        return measure("cancelOrder", () -> {
            List<DhanOrderResponse> wrappers = client.delete("/orders/" + URLEncoder.encode(orderId, StandardCharsets.UTF_8), DhanOrderResponse.class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from DHAN");
            DhanOrderResponse r = wrappers.get(0);
            sendTelegramMessage("CANCEL", r);
            com.aem.ai.realtime.brokers.kite.OrderResponse out = new com.aem.ai.realtime.brokers.kite.OrderResponse(r.getOrderId());
            out.setStatus(r.getOrderStatus());
            return out;
        });
    }

    @Override
    public List<com.aem.ai.realtime.brokers.kite.OrderDetail> listOrders() throws ApiException {
        return measure("listOrders", () -> {
            List<DhanOrderDetail[]> wrappers = client.get("/orders", DhanOrderDetail[].class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from DHAN");
            DhanOrderDetail[] arr = wrappers.get(0);
            List<com.aem.ai.realtime.brokers.kite.OrderDetail> out = new ArrayList<>();
            for (DhanOrderDetail d : arr) {
                out.add(d.toGeneric());
            }
            return out;
        });
    }

    @Override
    public List<com.aem.ai.realtime.brokers.kite.OrderDetail> getOrderHistory(String orderId) throws ApiException {
        return measure("getOrderHistory", () -> {
            List<DhanOrderDetail[]> wrappers = client.get("/orders/" + URLEncoder.encode(orderId, StandardCharsets.UTF_8), DhanOrderDetail[].class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from DHAN");
            DhanOrderDetail[] arr = wrappers.get(0);
            List<com.aem.ai.realtime.brokers.kite.OrderDetail> out = new ArrayList<>();
            for (DhanOrderDetail d : arr) out.add(d.toGeneric());
            return out;
        });
    }

    @Override
    public List<com.aem.ai.realtime.brokers.kite.TradeDetail> listTrades() throws ApiException {
        return measure("listTrades", () -> {
            List<DhanTradeDetail[]> wrappers = client.get("/trades", DhanTradeDetail[].class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from DHAN");
            DhanTradeDetail[] arr = wrappers.get(0);
            List<com.aem.ai.realtime.brokers.kite.TradeDetail> out = new ArrayList<>();
            for (DhanTradeDetail d : arr) out.add(d.toGeneric());
            return out;
        });
    }

    @Override
    public List<com.aem.ai.realtime.brokers.kite.TradeDetail> getOrderTrades(String orderId) throws ApiException {
        return measure("getOrderTrades", () -> {
            List<DhanTradeDetail[]> wrappers = client.get("/trades/" + URLEncoder.encode(orderId, StandardCharsets.UTF_8), DhanTradeDetail[].class);
            if (wrappers == null || wrappers.isEmpty()) throw new ApiException("No response from DHAN");
            DhanTradeDetail[] arr = wrappers.get(0);
            List<com.aem.ai.realtime.brokers.kite.TradeDetail> out = new ArrayList<>();
            for (DhanTradeDetail d : arr) out.add(d.toGeneric());
            return out;
        });
    }

    @FunctionalInterface
    interface OperationSupplier<T> { T get() throws ApiException; }
}