package com.aem.ai.pm.connectors.upstox;

import com.aem.ai.pm.config.UpstoxConfig;
import com.aem.ai.pm.connectors.BrokerConnector;
import com.aem.ai.pm.dto.*;
import com.aem.ai.pm.net.HttpClientService;
import com.aem.ai.pm.services.AccountRegistryService;
import com.aem.ai.pm.utils.Retry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component(service = BrokerConnector.class, immediate = true)
public class UpstoxConnector implements BrokerConnector {

    private static final Logger log = LoggerFactory.getLogger(UpstoxConnector.class);

    // ANSI colors
    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";

    @Reference private HttpClientService http;
    @Reference private AccountRegistryService registry;

    private volatile UpstoxConfig cfg;
    private final ObjectMapper om = new ObjectMapper();

    @Activate @Modified
    protected void activate(UpstoxConfig cfg) {
        this.cfg = cfg;
        log.info(GREEN + "✅ UpstoxConnector activated with baseUrl={} " + RESET, cfg.baseUrl());
    }

    @Override public String brokerCode() { return "UPSTOX"; }

    @Override
    public List<BrokerAccountRef> discoverAccounts() {
        log.info(CYAN + "🔍 Discovering UPSTOX accounts from registry..." + RESET);
        List<BrokerAccountRef> accounts = registry.findActiveAccounts("UPSTOX");
        log.info(GREEN + "✅ {} accounts discovered for UPSTOX" + RESET, accounts.size());
        return accounts;
    }

    @Override
    public PortfolioSnapshot fetchPortfolio(BrokerAccountRef acc) throws BrokerException {
        log.info(CYAN + "📡 Fetching portfolio for accountId={} broker={}" + RESET, acc.userBrokerAccountId, brokerCode());
        try {
            String auth = "Bearer " + acc.accessToken;

            HttpResponse<String> h = Retry.exec(3, 300, () ->
                    unchecked(() -> http.get(cfg.baseUrl()+ cfg.holdingsEndpoint(), auth, 15000)));
            ensure2xx(h, "holdings");
            List<HoldingItem> holdings = mapHoldings(h.body());
            log.info(GREEN + "✅ Holdings fetched: {} items" + RESET, holdings.size());

            HttpResponse<String> p = Retry.exec(3, 300, () ->
                    unchecked(() -> http.get(cfg.baseUrl()+ cfg.positionsEndpoint(), auth, 15000)));
            ensure2xx(p, "positions");
            List<PositionItem> positions = mapPositions(p.body());
            log.info(GREEN + "✅ Positions fetched: {} items" + RESET, positions.size());

            HttpResponse<String> f = Retry.exec(3, 300, () ->
                    unchecked(() -> http.get(cfg.baseUrl()+ cfg.fundsEndpoint(), auth, 15000)));
            ensure2xx(f, "funds");
            CashSummary cash = mapFunds(f.body());
            log.info(GREEN + "✅ Cash summary fetched: available={} used={}" + RESET, cash.available, cash.used);

            return new PortfolioSnapshot(holdings, positions, cash, Instant.now());
        } catch (BrokerException be) {
            log.error(RED + "❌ BrokerException while fetching portfolio: {}" + RESET, be.getMessage());
            throw be;
        } catch (Exception e) {
            log.error(RED + "❌ Unexpected error in Upstox fetch: {}" + RESET, e.getMessage(), e);
            throw new BrokerException("Upstox fetch failed: " + e.getMessage(), -1, e);
        }
    }

    private static <T> T unchecked(CallableEx<T> c){ try { return c.call(); } catch(Exception e){ throw new RuntimeException(e); } }
    @FunctionalInterface private interface CallableEx<T>{ T call() throws Exception; }

    private void ensure2xx(HttpResponse<?> r, String ctx) throws BrokerException {
        if (r.statusCode() / 100 != 2) {
            log.warn(YELLOW + "⚠️ Non-2xx response for {}: HTTP {} body={}" + RESET, ctx, r.statusCode(), r.body());
            throw new BrokerException("Upstox "+ctx+" HTTP "+r.statusCode()+": "+r.body(), r.statusCode(), null);
        }
    }

    private List<HoldingItem> mapHoldings(String json) throws Exception {
        List<HoldingItem> out = new ArrayList<>();
        JsonNode arr = om.readTree(json).path("data");
        for (JsonNode n : arr) {
            HoldingItem h = new HoldingItem();
            // Symbol / Trading symbol
            h.symbol = n.path("tradingsymbol").asText(n.path("trading_symbol").asText());
            // Exchange (default NSE if missing)
            h.exchange = n.path("exchange").asText("NSE");
            // Instrument type - always equity here
            h.instrumentType = "EQUITY";
            // Quantity
            h.quantity = new BigDecimal(n.path("quantity").asText("0"));
            // Average cost
            h.avgCost = new BigDecimal(n.path("average_price").asText("0"));
            // ISIN
            h.isin = n.path("isin").asText(null);
            // Company name (optional in your DTO - keep for debugging/logging)
            h.companyName = n.path("company_name").asText(null);
            // PnL
            h.pnl = new BigDecimal(n.path("pnl").asText("0"));
            // LTP
            h.lastPrice = new BigDecimal(n.path("last_price").asText("0"));
            // Close price
            h.closePrice = new BigDecimal(n.path("close_price").asText("0"));
            // T1 quantity
            h.t1Quantity = new BigDecimal(n.path("t1_quantity").asText("0"));
            // Collateral info (if your HoldingItem supports it)
            h.collateralQuantity = new BigDecimal(n.path("collateral_quantity").asText("0"));
            h.collateralType = n.path("collateral_type").asText(null);

            out.add(h);
        }
        return out;
    }

    private List<PositionItem> mapPositions(String json) throws Exception {
        List<PositionItem> out = new ArrayList<>();
        JsonNode arr = om.readTree(json).path("data");
        for (JsonNode n : arr) {
            PositionItem p = new PositionItem();

            // Symbol
            p.symbol = n.path("tradingsymbol").asText(n.path("trading_symbol").asText());

            // Exchange
            p.exchange = n.path("exchange").asText("NSE");

            // Instrument type (infer based on tradingsymbol / exchange)
            p.instrumentType = inferInstrFromSymbol(p.symbol, p.exchange);

            // Expiry, strike, option type (if available inside symbol name)
            p.expiry = null; // could be parsed from symbol like BANKNIFTY23OCT38000PE if needed
            p.strike = BigDecimal.ZERO; // optional parsing from symbol
            p.optionType = null; // optional parsing from symbol

            // Side: LONG if quantity > 0 else SHORT
            int qty = n.path("quantity").asInt(0);
            p.side = qty >= 0 ? "LONG" : "SHORT";
            p.quantity = new BigDecimal(qty);

            // Prices
            p.avgPrice = new BigDecimal(n.path("average_price").asText("0"));
            p.buyPrice = new BigDecimal(n.path("buy_price").asText("0"));
            p.sellPrice = new BigDecimal(n.path("sell_price").asText("0"));
            p.lastPrice = new BigDecimal(n.path("last_price").asText("0"));
            p.closePrice = new BigDecimal(n.path("close_price").asText("0"));

            // P&L
            p.pnlRealized = new BigDecimal(n.path("realised").asText("0"));
            p.pnlUnrealized = new BigDecimal(n.path("unrealised").asText("0"));
            p.pnl = new BigDecimal(n.path("pnl").asText("0"));

            // Values
            p.buyValue = new BigDecimal(n.path("buy_value").asText("0"));
            p.sellValue = new BigDecimal(n.path("sell_value").asText("0"));
            p.value = new BigDecimal(n.path("value").asText("0"));

            out.add(p);
        }
        return out;
    }

    /**
     * Simple inference for instrument type from symbol/exchange
     */
    private String inferInstrFromSymbol(String symbol, String exchange) {
        if (symbol == null) return "EQUITY";
        if (symbol.matches(".*FUT$")) return "FUT";
        if (symbol.matches(".*(CE|PE)$")) return "OPT";
        if (exchange.equalsIgnoreCase("NSE") || exchange.equalsIgnoreCase("BSE")) return "EQUITY";
        return "DERIVATIVE";
    }


    private String inferInstr(JsonNode n){
        if (n.hasNonNull("option_type")) return "OPT";
        if (n.hasNonNull("expiry")) return "FUT";
        return "EQUITY";
    }

    private CashSummary mapFunds(String json) throws Exception {
        CashSummary c = new CashSummary();
        JsonNode d = om.readTree(json).path("data");
        c.available = new BigDecimal(d.path("available_margin").asText("0"));
        c.used = new BigDecimal(d.path("utilised_margin").asText("0"));
        c.pnlRealizedToday = BigDecimal.ZERO;
        c.pnlUnrealized = BigDecimal.ZERO;
        return c;
    }
}
