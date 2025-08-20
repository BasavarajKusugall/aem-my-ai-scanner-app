package com.pm.connectors.kite;

import com.fasterxml.jackson.databind.*;
import com.pm.config.KiteConfig;
import com.pm.connectors.BrokerConnector;
import com.pm.dto.*;
import com.pm.net.HttpClientService;
import com.pm.services.AccountRegistryService;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.*;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = BrokerConnector.class, immediate = true)
public class KiteConnector implements BrokerConnector {

    private static final Logger log = LoggerFactory.getLogger(KiteConnector.class);

    // ANSI color codes
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    @Reference
    private HttpClientService http;
    @Reference
    private AccountRegistryService registry;

    @Reference
    private KiteAuthService kiteAuthService;

    private volatile KiteConfig cfg;
    private final ObjectMapper om = new ObjectMapper();

    @Activate @Modified
    protected void activate(KiteConfig cfg) {
        this.cfg = cfg;
        log.info(CYAN + "🔌 KiteConnector activated with baseUrl=" + cfg.baseUrl() + RESET);
    }

    @Override
    public String brokerCode() { return "ZERODHA"; }

    @Override
    public List<BrokerAccountRef> discoverAccounts() {
        log.info(CYAN + "🔍 Discovering Zerodha accounts..." + RESET);
        List<BrokerAccountRef> accounts = registry.findActiveAccounts(brokerCode());
        log.info(GREEN + "✅ Found {} active accounts for Zerodha" + RESET, accounts.size());
        return accounts;
    }


    @Override
    public PortfolioSnapshot fetchPortfolio(BrokerAccountRef acc) throws BrokerException {
        log.info(YELLOW + "📥 Fetching portfolio for accountId={}..." + RESET, acc.userBrokerAccountId);

        try {
            // 1️⃣ Exchange request_token for access_token via KiteAuthService
            String accessToken = kiteAuthService.getAccessTokenAndStoreToken(
                    acc.requestToken,
                    "ZERODHA",
                    acc.getBrokerAccountRef(),
                    acc.getApiKey(),
                    acc.getApiSecrete()
            );

            if (StringUtils.isEmpty(accessToken)) {
                log.error(RED + "❌ Failed to fetch access token for accountId={}" + RESET, acc.userBrokerAccountId);
                throw new BrokerException("Failed to fetch access token for accountId=" + acc.userBrokerAccountId, -1, null);
            }

            log.info(GREEN + "✅ Access token fetched successfully for accountId={}" + RESET, acc.userBrokerAccountId);

            // 2️⃣ Fetch holdings via REST
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Kite-Version", "3");
            headers.put("Authorization", "token " + acc.getApiKey() + ":" + accessToken);

            String holdingsJson = http.get(cfg.baseUrl() + "/portfolio/holdings", headers,1000);
            List<HoldingItem> holdings = mapKiteHoldings(holdingsJson);
            log.info(GREEN + "✅ Holdings fetched: {} instruments" + RESET, holdings.size());

// 3️⃣ Fetch positions via REST
            String positionsJson = http.get(cfg.baseUrl() + "/portfolio/positions", headers,1000);
            List<PositionItem> positions = mapKitePositions(positionsJson);
            log.info(GREEN + "✅ Positions fetched: {} items" + RESET, positions.size());

// 4️⃣ Fetch margins/funds via REST
            String marginJson = http.get(cfg.baseUrl() + "/portfolio/margins/equity", headers,1000);
            CashSummary cash = mapKiteFunds(marginJson);
            log.info(GREEN + "✅ Cash summary fetched. Available={} Used={}" + RESET, cash.available, cash.used);


            return new PortfolioSnapshot(holdings, positions, cash, Instant.now());

        } catch (Exception e) {
            log.error(RED + "❌ Error fetching portfolio: {}" + RESET, e.getMessage(), e);
            throw new BrokerException("Kite fetch failed: " + e.getMessage(), -1, e);
        }
    }


    private String kiteInferInstr(String product, String optionType, String expiry){
        if (StringUtils.isNotEmpty(optionType)) return "OPT";
        if (StringUtils.isNotEmpty(expiry) || "FUT".equalsIgnoreCase(product)) return "FUT";
        return "EQUITY";
    }


    private static <T> T unchecked(CallableEx<T> c){ try { return c.call(); } catch(Exception e){ throw new RuntimeException(e); } }
    @FunctionalInterface private interface CallableEx<T>{ T call() throws Exception; }

    private void ensure2xx(HttpResponse<?> r, String ctx) throws BrokerException {
        if (r.statusCode() / 100 != 2) {
            log.warn(RED + "⚠️ Kite {} HTTP {} -> {}" + RESET, ctx, r.statusCode(), r.body());
            throw new BrokerException("Kite "+ctx+" HTTP "+r.statusCode()+": "+r.body(), r.statusCode(), null);
        }
    }

    private List<HoldingItem> mapKiteHoldings(String json) throws Exception {
        List<HoldingItem> out = new ArrayList<>();
        JsonNode root = om.readTree(json).path("data");
        for (JsonNode n : root) {
            HoldingItem h = new HoldingItem();
            h.symbol = n.path("tradingsymbol").asText();
            h.exchange = n.path("exchange").asText("NSE");
            h.instrumentType = "EQUITY";
            h.quantity = BigDecimal.valueOf(Integer.valueOf(n.path("quantity").asText("0")));
            h.avgCost = new BigDecimal(n.path("average_price").asText("0"));
            h.isin = n.path("isin").asText(null);
            out.add(h);
        }
        return out;
    }

    private List<PositionItem> mapKitePositions(String json) throws Exception {
        List<PositionItem> out = new ArrayList<>();
        JsonNode net = om.readTree(json).path("data").path("net");
        for (JsonNode n : net) {
            PositionItem p = new PositionItem();
            p.symbol = n.path("tradingsymbol").asText();
            p.exchange = n.path("exchange").asText("NSE");
            p.instrumentType = kiteInferInstr(n);
            p.expiry = n.path("expiry").asText(null);
            p.strike = new BigDecimal(n.path("strike").asText("0"));
            p.optionType = n.path("option_type").asText(null);
            p.side = n.path("quantity").asInt() >= 0 ? "LONG" : "SHORT";
            p.quantity = new BigDecimal(n.path("quantity").asText("0"));
            p.avgPrice = new BigDecimal(n.path("average_price").asText("0"));
            p.pnlRealized = new BigDecimal(n.path("pnl").asText("0"));
            p.pnlUnrealized = new BigDecimal(n.path("unrealised").asText("0"));
            out.add(p);
        }
        return out;
    }

    private String kiteInferInstr(JsonNode n){
        String product = n.path("product").asText("");
        String opt = n.path("option_type").asText("");
        if (!opt.isEmpty()) return "OPT";
        if (n.hasNonNull("expiry") || "FUT".equalsIgnoreCase(product)) return "FUT";
        return "EQUITY";
    }

    private CashSummary mapKiteFunds(String json) throws Exception {
        CashSummary c = new CashSummary();
        JsonNode d = om.readTree(json).path("data").path("equity");
        c.available = new BigDecimal(d.path("available").path("cash").asText("0"));
        c.used = new BigDecimal(d.path("utilised").path("debits").asText("0"));
        c.pnlRealizedToday = new BigDecimal(d.path("pnl").asText("0"));
        c.pnlUnrealized = BigDecimal.ZERO;
        return c;
    }
}
