package com.pm.connectors.upstox;

import com.fasterxml.jackson.databind.*;
import com.pm.config.UpstoxConfig;
import com.pm.connectors.BrokerConnector;
import com.pm.dto.*;
import com.pm.net.HttpClientService;
import com.pm.services.AccountRegistryService;
import com.pm.utils.Retry;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;

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
                    unchecked(() -> http.get(cfg.baseUrl()+"/portfolio/holdings", auth, 15000)));
            ensure2xx(h, "holdings");
            List<HoldingItem> holdings = mapHoldings(h.body());
            log.info(GREEN + "✅ Holdings fetched: {} items" + RESET, holdings.size());

            HttpResponse<String> p = Retry.exec(3, 300, () ->
                    unchecked(() -> http.get(cfg.baseUrl()+"/portfolio/positions", auth, 15000)));
            ensure2xx(p, "positions");
            List<PositionItem> positions = mapPositions(p.body());
            log.info(GREEN + "✅ Positions fetched: {} items" + RESET, positions.size());

            HttpResponse<String> f = Retry.exec(3, 300, () ->
                    unchecked(() -> http.get(cfg.baseUrl()+"/funds/summary", auth, 15000)));
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
            h.symbol = n.path("symbol").asText();
            h.exchange = n.path("exchange").asText("NSE");
            h.instrumentType = "EQUITY";
            h.quantity = new BigDecimal(n.path("quantity").asText("0"));
            h.avgCost = new BigDecimal(n.path("average_price").asText("0"));
            h.isin = n.path("isin").asText(null);
            out.add(h);
        }
        return out;
    }

    private List<PositionItem> mapPositions(String json) throws Exception {
        List<PositionItem> out = new ArrayList<>();
        JsonNode arr = om.readTree(json).path("data");
        for (JsonNode n : arr) {
            PositionItem p = new PositionItem();
            p.symbol = n.path("tradingsymbol").asText(n.path("symbol").asText());
            p.exchange = n.path("exchange").asText("NSE");
            p.instrumentType = inferInstr(n);
            p.expiry = n.path("expiry").asText(null);
            p.strike = new BigDecimal(n.path("strike_price").asText("0"));
            p.optionType = n.path("option_type").asText(null);
            p.side = n.path("net_qty").asInt() >= 0 ? "LONG" : "SHORT";
            p.quantity = new BigDecimal(n.path("net_qty").asText("0"));
            p.avgPrice = new BigDecimal(n.path("avg_price").asText("0"));
            p.pnlRealized = new BigDecimal(n.path("realized_profit").asText("0"));
            p.pnlUnrealized = new BigDecimal(n.path("unrealized_profit").asText("0"));
            out.add(p);
        }
        return out;
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
