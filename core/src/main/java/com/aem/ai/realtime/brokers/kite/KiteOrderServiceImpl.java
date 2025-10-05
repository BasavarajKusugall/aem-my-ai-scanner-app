package com.aem.ai.realtime.brokers.kite;

import com.aem.ai.exception.ApiException;
import com.aem.ai.scanner.services.TelegramService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OSGi implementation of the generic OrderService for Kite broker.
 */
@Component(service = OrderService.class, immediate = true)
public class KiteOrderServiceImpl implements OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(KiteOrderServiceImpl.class);

    private boolean dryRun;
    private boolean enable;
    @ObjectClassDefinition(
            name = "Kite Order Service Config",
            description = "Configuration for Zerodha Kite Order Service"
    )
    public @interface Config {

        @AttributeDefinition(name = "Enable", description = "Enable Kite Connect API")
        boolean enable() default false;

        @AttributeDefinition(name = "Enable Dry run", description = "Enable Dry run Kite Connect API")
        boolean dryRun() default false;
    }
    private ObjectMapper mapper;

    @Reference
    private KiteApiClient client;

    @Reference
    private TelegramService telegramService;

    @Activate
    public void activate(Config config) {
        LOG.info("\u001B[32m[KiteOrderService] ✅ Activated Order Service\u001B[0m");
        this.mapper = new ObjectMapper();
        this.mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper.findAndRegisterModules();
        this.enable = config.enable();
        this.dryRun = config.dryRun();
        if(this.enable) {
            LOG.info("\u001B[32m[KiteOrderService] 🚀 Kite Order Service is ENABLED\u001B[0m");
        } else {
            LOG.warn("\u001B[33m[KiteOrderService] ⚠️ Kite Order Service is DISABLED\u001B[0m");
        }
        if(this.dryRun) {
            LOG.warn("\u001B[33m[KiteOrderService] ⚠️ Kite Order Service is in DRY RUN mode\u001B[0m");
        } else {
            LOG.info("\u001B[32m[KiteOrderService] 🚀 Kite Order Service is in LIVE mode\u001B[0m");
        }
    }

    @Override
    public boolean isDryRun() {
        return dryRun;
    }

    @Override
    public boolean isEnable() {
        return enable;
    }


    @Deactivate
    public void deactivate() {
        LOG.info("\u001B[31m[KiteOrderService] ❌ Deactivated Order Service\u001B[0m");
    }

    private <T> T measure(String operation, OperationSupplier<T> supplier) throws ApiException {
        long start = System.currentTimeMillis();
        try {
            LOG.info("\u001B[35m[{}] ▶ Started...\u001B[0m", operation);
            return supplier.get();
        } finally {
            long duration = System.currentTimeMillis() - start;
            LOG.info("\u001B[35m[{}] ⏱ Completed in {} ms\u001B[0m", operation, duration);
        }
    }

    private void sendTelegramMessage(String action, OrderResponse response) {
        String message = String.format(
                "📌 *Kite Order Alert*\n" +
                        "Action: %s\n" +
                        "Order ID: `%s`\n" +
                        "Account: %s\n" +
                        "Status: %s",
                action,
                response.getOrder_id(),
                response.getBrokerAccountRef() != null ? response.getBrokerAccountRef().getBrokerAccountRef() : "N/A",
                response.getStatus() != null ? response.getStatus() : "PLACED"
        );
        telegramService.sendMessageKiteAlerts(message);
    }

    @Override
    public OrderResponse placeOrder(String variety, OrderRequest req) throws ApiException {
        return measure("placeOrder", () -> {
            String path = "/orders/" + encode(variety);
            List<ApiWrapper> wrappers = client.postForm(path, req, ApiWrapper.class);

            if (wrappers == null || wrappers.isEmpty()) {
                throw new ApiException("No response from any account while placing order");
            }

            ApiWrapper<Map<String, Object>> wrapper = wrappers.get(0);
            Map<String, Object> data = (Map<String, Object>) wrapper.data;
            OrderResponse out = new OrderResponse((String) data.get("order_id"));
            out.setBrokerAccountRef(wrapper.getBrokerAccountRef());
            out.setStatus((String) data.getOrDefault("status", "PLACED"));
            sendTelegramMessage("PLACE", out);
            LOG.info("\u001B[32m✔ Placed orderId={} from account\u001B[0m", out.getOrder_id());
            return out;
        });
    }

    @Override
    public OrderResponse modifyOrder(String variety, String orderId, OrderRequest req) throws ApiException {
        return measure("modifyOrder", () -> {
            String path = "/orders/" + encode(variety) + "/" + encode(orderId);
            List<ApiWrapper> wrappers = client.putForm(path, req, ApiWrapper.class);

            if (wrappers == null || wrappers.isEmpty()) {
                throw new ApiException("No response from any account while modifying order");
            }

            ApiWrapper<Map<String, Object>> wrapper = wrappers.get(0);
            Map<String, Object> data = (Map<String, Object>) wrapper.data;
            OrderResponse out = new OrderResponse((String) data.get("order_id"));
            out.setBrokerAccountRef(wrapper.getBrokerAccountRef());
            out.setStatus((String) data.getOrDefault("status", "MODIFIED"));
            sendTelegramMessage("MODIFY", out);
            LOG.info("\u001B[33m✏ Modified orderId={} from account\u001B[0m", out.getOrder_id());
            return out;
        });
    }

    @Override
    public OrderResponse cancelOrder(String variety, String orderId) throws ApiException {
        return measure("cancelOrder", () -> {
            String path = "/orders/" + encode(variety) + "/" + encode(orderId);
            List<ApiWrapper> wrappers = client.delete(path, ApiWrapper.class);

            if (wrappers == null || wrappers.isEmpty()) {
                throw new ApiException("No response from any account while cancelling order");
            }

            ApiWrapper<Map<String, Object>> wrapper = wrappers.get(0);
            Map<String, Object> data = (Map<String, Object>) wrapper.data;
            OrderResponse out = new OrderResponse((String) data.get("order_id"));
            out.setBrokerAccountRef(wrapper.getBrokerAccountRef());
            out.setStatus((String) data.getOrDefault("status", "CANCELLED"));
            sendTelegramMessage("CANCEL", out);
            LOG.info("\u001B[31m✖ Cancelled orderId={} from account\u001B[0m", out.getOrder_id());
            return out;
        });
    }

    @Override
    public List<OrderDetail> listOrders() throws ApiException {
        return measure("listOrders", () -> {
            List<ApiWrapper> wrappers = client.get("/orders", ApiWrapper.class);

            if (wrappers == null || wrappers.isEmpty()) {
                throw new ApiException("No response from any account while listing orders");
            }

            ApiWrapper<?> wrapper = wrappers.get(0);
            List<Map<String, Object>> rawList = mapper.convertValue(wrapper.data, List.class);
            List<OrderDetail> orders = new ArrayList<>();
            for (Map<String, Object> item : rawList) {
                orders.add(mapper.convertValue(item, OrderDetail.class));
            }
            return orders;
        });
    }

    @Override
    public List<OrderDetail> getOrderHistory(String orderId) throws ApiException {
        return measure("getOrderHistory", () -> {
            List<ApiWrapper> wrappers = client.get("/orders/" + encode(orderId), ApiWrapper.class);

            if (wrappers == null || wrappers.isEmpty()) {
                throw new ApiException("No response from any account while fetching order history");
            }

            ApiWrapper<?> wrapper = wrappers.get(0);
            List<Map<String, Object>> rawList = mapper.convertValue(wrapper.data, List.class);
            List<OrderDetail> history = new ArrayList<>();
            for (Map<String, Object> item : rawList) {
                history.add(mapper.convertValue(item, OrderDetail.class));
            }
            return history;
        });
    }

    @Override
    public List<TradeDetail> listTrades() throws ApiException {
        return measure("listTrades", () -> {
            List<ApiWrapper> wrappers = client.get("/trades", ApiWrapper.class);

            if (wrappers == null || wrappers.isEmpty()) {
                throw new ApiException("No response from any account while listing trades");
            }

            ApiWrapper<?> wrapper = wrappers.get(0);
            List<Map<String, Object>> rawList = mapper.convertValue(wrapper.data, List.class);
            List<TradeDetail> trades = new ArrayList<>();
            for (Map<String, Object> item : rawList) {
                trades.add(mapper.convertValue(item, TradeDetail.class));
            }
            return trades;
        });
    }

    @Override
    public List<TradeDetail> getOrderTrades(String orderId) throws ApiException {
        return measure("getOrderTrades", () -> {
            List<ApiWrapper> wrappers = client.get("/orders/" + encode(orderId) + "/trades", ApiWrapper.class);

            if (wrappers == null || wrappers.isEmpty()) {
                throw new ApiException("No response from any account while fetching order trades");
            }

            ApiWrapper<?> wrapper = wrappers.get(0);
            List<Map<String, Object>> rawList = mapper.convertValue(wrapper.data, List.class);
            List<TradeDetail> trades = new ArrayList<>();
            for (Map<String, Object> item : rawList) {
                trades.add(mapper.convertValue(item, TradeDetail.class));
            }
            return trades;
        });
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    interface OperationSupplier<T> {
        T get() throws ApiException;
    }
}
