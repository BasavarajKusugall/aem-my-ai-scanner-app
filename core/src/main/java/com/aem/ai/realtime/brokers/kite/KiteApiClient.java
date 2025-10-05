package com.aem.ai.realtime.brokers.kite;

import com.aem.ai.exception.ApiException;
import com.aem.ai.pm.connectors.BrokerConnector;
import com.aem.ai.pm.connectors.kite.KiteAuthService;
import com.aem.ai.pm.dto.BrokerAccountRef;
import com.aem.ai.pm.dto.CashSummary;
import com.aem.ai.scanner.model.InstrumentSymbol;
import com.aem.ai.scanner.utils.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Component(service = KiteApiClient.class, immediate = true)
@Designate(ocd = KiteApiClient.Config.class)
public class KiteApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(KiteApiClient.class);
    public static final ArrayList<String> ALL_ORDER_PATHS = new ArrayList<String>() {{
        add("/orders/regular");
        add("/orders/amo");
        add("/orders/co");
        add("/orders/iceberg");
        add("/orders/auction");
    }};
    private HttpClient client;
    private ObjectMapper mapper;
    private String apiBase;
    @Reference
    private KiteAuthService kiteAuthService;
    @Reference(
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            bind = "bindBrokerConnector",
            unbind = "unbindBrokerConnector"
    )
    private volatile List<BrokerConnector> brokerConnectors = new ArrayList<>();

    protected void bindBrokerConnector(BrokerConnector connector) {
        brokerConnectors.add(connector);
    }

    protected void unbindBrokerConnector(BrokerConnector connector) {
        brokerConnectors.remove(connector);
    }

    @ObjectClassDefinition(
            name = "Kite Order Service Config",
            description = "Configuration for Zerodha Kite Order Service"
    )
    public @interface Config {

        @AttributeDefinition(name = "API Base URL", description = "Kite Connect API base URL")
        String api_base() default "https://api.kite.trade";
    }

    @Activate
    public void activate(Config config) {
        LOG.info("\u001B[32m[KiteApiClient] Activating...\u001B[0m");
        this.apiBase = config.api_base();

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.mapper = new ObjectMapper();
        this.mapper.findAndRegisterModules();
        if (brokerConnectors == null || brokerConnectors.isEmpty()) {
            LOG.warn("[KiteApiClient] No BrokerConnector services found during activation");
        }

        LOG.info("\u001B[32m[KiteApiClient] Activated successfully\u001B[0m");
    }

    @Deactivate
    public void deactivate() {
        LOG.info("\u001B[31m[KiteApiClient] Deactivating...\u001B[0m");
    }

    private BrokerConnector getKiteBroker(){
        for (BrokerConnector connector : brokerConnectors) {
            if ("ZERODHA".equalsIgnoreCase(connector.brokerCode())) {
                return connector;
            }
        }
        return null;
    }

    private HttpRequest.Builder baseRequest(String path, BrokerAccountRef accountRef) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiBase + path))
                .header("X-Kite-Version", "3")
                .header("Authorization", "token " + accountRef.getApiKey() + ":" + accountRef.getAccessToken())
                .timeout(Duration.ofSeconds(20));
    }

    /**
     * Calculate margin requirement for a single order for a single account.
     */
    public MarginResult calculateMarginForOrder(BrokerAccountRef ref, OrderRequest req, BigDecimal accountFunds) throws ApiException {
        if (ref == null || req == null) {
            throw new IllegalArgumentException("AccountRef and OrderRequest cannot be null");
        }

        try {
            String requestJson = Utils.buildMarginRequestJson(req);
            if (requestJson == null || requestJson.isEmpty()) {
                throw new IllegalArgumentException("Failed to build margin request JSON");
            }
            requestJson = "["+requestJson+"]"; // Kite expects an array of orders

            HttpRequest reqHttp = baseRequest("/margins/orders", ref)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .header("Content-Type", "application/json")
                    .build();

            LOG.info("[Account:{}] Calculating margin for order {}", ref.getBrokerAccountRef(), req.getInstrument());
            Map<String, Object> resp = send(reqHttp, Map.class);
            if (resp == null || !resp.containsKey("data")) {
                LOG.warn("[Account:{}] Margin response did not contain total, returning 0. Response: {}", ref.getBrokerAccountRef(), resp);
                return new MarginResult(0.0, 0);
            }
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) resp.get("data");
            if (dataList == null || dataList.isEmpty()) {
                LOG.warn("[Account:{}] Margin response data is empty, returning 0. Response: {}", ref.getBrokerAccountRef(), resp);
                return new MarginResult(0.0, 0);
            }
            Map<String, Object> first = dataList.get(0);
            if (first == null || !first.containsKey("total")) {
                LOG.warn("[Account:{}] Margin response did not contain total, returning 0. Response: {}", ref.getBrokerAccountRef(), resp);
                return new MarginResult(0.0, 0);
            }
            Object totalMargin = first.get("total");
            if (totalMargin == null) {
                LOG.warn("[Account:{}] Margin response data is empty, returning 0. Response: {}", ref.getBrokerAccountRef(), resp);
                return new MarginResult(0.0, 0);
            }
            double marginRequired;
            if (totalMargin instanceof Number) {
                marginRequired = ((Number) totalMargin).doubleValue();
            } else {
                LOG.warn("[Account:{}] Margin response did not contain total, returning 0. Response: {}", ref.getBrokerAccountRef(), resp);
                marginRequired = 0.0;
            }

            double approvedFundsFraction = ref.getApprovedFundsPercentage() / 100.0;
            InstrumentSymbol instrument = req.getInstrument();
            double instrumentFraction = instrument != null && instrument.getAllowedMarginFundsPercent() > 0
                    ? instrument.getAllowedMarginFundsPercent() / 100.0
                    : 0.5; // default 50%

            double effectiveFunds = accountFunds.doubleValue() * approvedFundsFraction * instrumentFraction;

            double marginPerUnit = marginRequired / req.getQuantity();
            long maxAllowedQuantity = (long) Math.floor(effectiveFunds / marginPerUnit);
            long finalQuantity = Math.min(req.getQuantity(), maxAllowedQuantity);

            return new MarginResult(marginRequired, maxAllowedQuantity);

        } catch (IOException e) {
            throw new ApiException("Failed to serialize request: " + e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("Error calculating margin for order: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Shared logic: Execute an API request across all active Kite accounts.
     */
    private <T> List<T> executeAcrossAccounts(String path,
                                              OrderRequest req,
                                              String method,
                                              Class<T> respClass)  {
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            if (req == null) {
                throw new IllegalArgumentException("Request body cannot be null for " + method + " " + path);
            }
            Map<String,String> form = req.toForm();
            if (!"PUT".equalsIgnoreCase(method) && (form == null || form.isEmpty())) {
                throw new IllegalArgumentException("Request form cannot be empty for " + method + " " + path);
            }
        }

        BrokerConnector kiteBroker = getKiteBroker();
        if (kiteBroker == null) {
            throw new IllegalStateException("Kite broker connector not found");
        }

        List<BrokerAccountRef> brokerAccountRefs = kiteBroker.discoverAccounts();
        if (brokerAccountRefs.isEmpty()) {
            throw new IllegalStateException("No active Kite accounts found");
        }

        List<T> results = new ArrayList<>();
        for (BrokerAccountRef ref : brokerAccountRefs) {
            String accessToken = ref.getAccessToken();
            if (StringUtils.isEmpty(accessToken)) {
                LOG.info("\u001B[33m[Account:{}] Access token missing or expired. Refreshing...\u001B[0m",
                        ref.getBrokerAccountRef());
                accessToken = kiteAuthService.getAccessTokenAndStoreToken(
                        ref.requestToken,
                        null,
                        ref.getBrokerAccountRef(),
                        ref.getApiKey(),
                        ref.getApiSecrete(),
                        "ZERODHA",
                        ref.getUserId()
                );
            }
            if (StringUtils.isEmpty(accessToken)) {
                LOG.error("\u001B[31m[Account:{}] ❌ Failed to obtain access token. Skipping account.\u001B[0m",
                        ref.getBrokerAccountRef());
                continue; // skip this account
            }
            Map<String, String> headers = Map.of(
                    "X-Kite-Version", "3",
                    "Authorization", "token " + ref.getApiKey() + ":" + accessToken
            );

            if (StringUtils.equalsAnyIgnoreCase(method,"PUT","POST")
                    && ALL_ORDER_PATHS.stream().anyMatch(path::startsWith)) {


                CashSummary fundsForAccount;
                try {
                    fundsForAccount = kiteBroker.getFundsForAccount(headers);
                } catch (Exception e) {
                    LOG.error("\u001B[31m[Account:{}] ❌ Failed to fetch funds info: {}\u001B[0m",
                            ref.getBrokerAccountRef(), e.getMessage());
                    continue; // skip account
                }

                BigDecimal accountFundsAvailable = fundsForAccount.available;
                if (accountFundsAvailable == null) {
                    LOG.warn("\u001B[33m[Account:{}] ⚠️ Skipping account due to unavailable cash info\u001B[0m",
                            ref.getBrokerAccountRef());
                    continue;
                }

                MarginResult calculatedMarginForOrder;
                try {
                    calculatedMarginForOrder = calculateMarginForOrder(ref, req,accountFundsAvailable);
                    if (calculatedMarginForOrder == null) {
                        LOG.error("\u001B[31m[Account:{}] ❌ Margin calculation returned null\u001B[0m",
                                ref.getBrokerAccountRef());
                        continue;
                    }
                    if (calculatedMarginForOrder.finalQuantity <= 0) {
                        LOG.warn("\u001B[33m[Account:{}] ⚠️ Skipping order | Calculated final quantity is zero\u001B[0m",
                                ref.getBrokerAccountRef());
                        continue;
                    }
                } catch (ApiException e) {
                    LOG.error("\u001B[31m[Account:{}] ❌ Failed to calculate margin for order: {}\u001B[0m",
                            ref.getBrokerAccountRef(), e.getMessage());
                    continue;
                }
                if (calculatedMarginForOrder.finalQuantity <= 0) {
                    LOG.warn("[Account:{}] ⚠️ Skipping order | Insufficient funds", ref.getBrokerAccountRef());
                    continue;
                }
                req.setQuantity(calculatedMarginForOrder.finalQuantity); // update request
                int approvedFundsPercentage = ref.getApprovedFundsPercentage(); // e.g., 50%
                InstrumentSymbol instrument = req.getInstrument();
                int instrumentAllowedFunds = 50; // default 50%
                if (instrument != null && instrument.getAllowedMarginFundsPercent() > 0) {
                    instrumentAllowedFunds = instrument.getAllowedMarginFundsPercent();
                }

                // Calculate effective funds
                double effectiveAccountFunds = accountFundsAvailable.doubleValue() * (approvedFundsPercentage / 100.0);
                double fundsAllowedForInstrument = effectiveAccountFunds * (instrumentAllowedFunds / 100.0);

                if (calculatedMarginForOrder.marginRequired > fundsAllowedForInstrument) {
                    LOG.warn("\u001B[33m[Account:{}] ⚠️ Skipping order for {} | Required Margin={} > Allowed Funds={} (Approved:{}%, Instrument:{}%)\u001B[0m",
                            ref.getBrokerAccountRef(),
                            instrument != null ? instrument : "UNKNOWN",
                            calculatedMarginForOrder,
                            fundsAllowedForInstrument,
                            approvedFundsPercentage,
                            instrumentAllowedFunds);
                    continue; // skip this account
                }

                LOG.info("\u001B[32m[Account:{}] ✅ Order allowed | Required Margin={} <= Allowed Funds={} (Approved:{}%, Instrument:{}%)\u001B[0m",
                        ref.getBrokerAccountRef(),
                        calculatedMarginForOrder,
                        fundsAllowedForInstrument,
                        approvedFundsPercentage,
                        instrumentAllowedFunds);


            }


            // --- Build request ---
            HttpRequest.Builder builder = baseRequest(path, ref);
            String builtForm = req != null ? buildForm(req.toForm()) : null;
            if (StringUtils.equalsIgnoreCase(method,"POST") && StringUtils.isEmpty(builtForm)) {
                LOG.error("\u001B[31m[Account:{}] ❌ Request form is empty for {} {}\u001B[0m",
                        ref.getBrokerAccountRef(), method, path);
                continue;
            }
            HttpRequest request = switch (method.toUpperCase()) {
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(builtForm))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build();
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(builtForm))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build();
                case "DELETE" -> builder.DELETE().build();
                default -> // GET
                        builder.GET().build();
            };

            // --- Send request ---
            long start = System.currentTimeMillis();
            try {
                LOG.info("\u001B[36m[Account:{}] [{}] {} \u001B[0m",
                        ref.getBrokerAccountRef(), method, path);

                if (builtForm != null && !builtForm.isEmpty()) {
                    LOG.debug("\u001B[33m[Account:{}] Request Body: {}\u001B[0m",
                            ref.getBrokerAccountRef(), truncate(builtForm, 300));
                }

                ApiWrapper resp = (ApiWrapper) send(request, respClass);
                if (resp == null) {
                    LOG.error("\u001B[31m[Account:{}] ❌ No response received for {} {}\u001B[0m",
                            ref.getBrokerAccountRef(), method, path);
                    continue;
                }
                resp.setBrokerAccountRef(ref);
                long duration = System.currentTimeMillis() - start;

                LOG.info("\u001B[32m[Account:{}] ✅ Success [{} {}] in {} ms\u001B[0m",
                        ref.getBrokerAccountRef(), method, path, duration);
                results.add((T) resp);

            } catch (ApiException ex) {
                long duration = System.currentTimeMillis() - start;
                LOG.error("\u001B[31m[Account:{}] ❌ Failed [{} {}] in {} ms | Reason: {}\u001B[0m",
                        ref.getBrokerAccountRef(), method, path, duration, ex.getMessage());
            }
        }

        return results;
    }


    private <T> T send(HttpRequest req, Class<T> respClass) throws ApiException {
        try {
            long start = System.currentTimeMillis();
            LOG.debug("\u001B[34m[HTTP] Sending {} {}\u001B[0m", req.method(), req.uri());

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;

            int code = resp.statusCode();
            String body = resp.body();

            if (code >= 200 && code < 300) {
                LOG.debug("\u001B[34m[HTTP] ✅ {} {} | Status={} | Duration={}ms | Body={}\u001B[0m",
                        req.method(), req.uri(), code, duration, truncate(body, 200));
                return mapper.readValue(body, respClass);
            } else {
                LOG.error("\u001B[31m[HTTP] ❌ {} {} | Status={} | Duration={}ms | Body={}\u001B[0m",
                        req.method(), req.uri(), code, duration, truncate(body, 200));
                Map<String,Object> err = new ConcurrentHashMap<>();//safeReadMap(body);
                throw new ApiException("HTTP " + code + " : " + err.getOrDefault("message", body));
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request failed: " + e.getMessage(), e);
        }
    }


    public <T> List<T> postForm(String path, OrderRequest req, Class<T> respClass) throws ApiException {
        return measure("POST " + path, () ->
                executeAcrossAccounts(path, req, "POST", respClass));
    }

    public <T> List<T> putForm(String path, OrderRequest form, Class<T> respClass) throws ApiException {
        return measure("PUT " + path, () ->
                executeAcrossAccounts(path, form, "PUT", respClass));
    }

    public <T> List<T> delete(String path, Class<T> respClass) throws ApiException {
        return measure("DELETE " + path, () ->
                executeAcrossAccounts(path, null, "DELETE", respClass));
    }

    public <T> List<T> get(String path, Class<T> respClass) throws ApiException {
        return measure("GET " + path, () ->
                executeAcrossAccounts(path, null, "GET", respClass));
    }
    private String buildForm(Map<String,String> form) {
        if (form == null || form.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        form.forEach((k,v) -> {
            if (v == null) return;
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private <T> T sendWithBody(HttpRequest.Builder builder, Class<T> respClass) throws ApiException {
        return send(builder.header("Content-Type","application/x-www-form-urlencoded").build(), respClass);
    }

    private Map<String,Object> safeReadMap(String json) {
        try { return mapper.readValue(json, new TypeReference<Map<String,Object>>(){}); }
        catch (Exception ex) { return Map.of("raw", json); }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private <T> T measure(String operation, OperationSupplier<T> supplier) throws ApiException {
        long start = System.currentTimeMillis();
        try {
            LOG.info("\u001B[35m[{}] Started...\u001B[0m", operation);
            return supplier.get();
        } finally {
            long duration = System.currentTimeMillis() - start;
            LOG.info("\u001B[35m[{}] Completed in {} ms\u001B[0m", operation, duration);
        }
    }

    @FunctionalInterface
    interface OperationSupplier<T> { T get() throws ApiException; }
}
