package com.aem.ai.pm.connectors.dhan;


import com.aem.ai.pm.config.DhanConfig;
import com.aem.ai.pm.connectors.BrokerConnector;
import com.aem.ai.pm.dto.*;
import com.aem.ai.pm.net.HttpClientService;
import com.aem.ai.pm.services.AccountRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(service = BrokerConnector.class, immediate = true)
public class DhanConnector implements BrokerConnector {

    private static final Logger log = LoggerFactory.getLogger(DhanConnector.class);

    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    @Reference
    private HttpClientService http;

    @Reference
    private AccountRegistryService registry;

    private volatile DhanConfig cfg;
    private final ObjectMapper om = new ObjectMapper();

    @Activate @Modified
    protected void activate(DhanConfig cfg) {
        this.cfg = cfg;
        log.info(CYAN + "🔌 Dhan Connector activated with baseUrl=" + cfg.baseUrl() + RESET);
    }

    @Override
    public String brokerCode() { return "DHAN"; }

    @Override
    public List<BrokerAccountRef> discoverAccounts() {
        log.info(CYAN + "🔍 Discovering Dhan accounts..." + RESET);
        List<BrokerAccountRef> accounts = registry.findActiveAccounts(brokerCode());
        log.info(GREEN + "✅ Found {} active accounts for Dhan" + RESET, accounts.size());
        return accounts;
    }

    @Override
    public PortfolioSnapshot fetchPortfolio(BrokerAccountRef acc) throws BrokerException {
        log.info(YELLOW + "📥 Fetching portfolio for accountId={} broker={}" + RESET, acc.userBrokerAccountId, brokerCode());

        try {
            // Dhan expects header: access-token: <JWT>
            Map<String, String> headers = new HashMap<>();
            headers.put("access-token", acc.getAccessToken() != null ? acc.getAccessToken() : acc.accessToken);
            headers.put("Content-Type", "application/json");

            // 1) Holdings
            String holdingsJson = http.get(cfg.baseUrl() + "/portfolio/holdings", headers, cfg.timeoutMs());
            List<HoldingItem> holdings = mapDhanHoldings(holdingsJson);
            log.info(GREEN + "✅ Holdings fetched: {} instruments" + RESET, holdings.size());

            // 2) Positions
            String positionsJson = http.get(cfg.baseUrl() + "/portfolio/positions", headers, cfg.timeoutMs());
            List<PositionItem> positions = mapDhanPositions(positionsJson);
            log.info(GREEN + "✅ Positions fetched: {} items" + RESET, positions.size());

            // 3) Funds / Margins - endpoint name may vary; adapt if needed
            String fundsJson = http.get(cfg.baseUrl() + "/funds/summary", headers, cfg.timeoutMs());
            CashSummary cash = mapDhanFunds(fundsJson);
            log.info(GREEN + "✅ Cash summary fetched. Available={} Used={}" + RESET, cash.available, cash.used);

            PortfolioSnapshot snapshot = new PortfolioSnapshot(holdings, positions, cash, Instant.now());
            snapshot.setHoldingsJson(holdingsJson);
            snapshot.setPositionsJson(positionsJson);
            return snapshot;

        } catch (BrokerException be) {
            log.error(RED + "❌ BrokerException while fetching portfolio: {}" + RESET, be.getMessage());
            throw be;
        } catch (Exception e) {
            log.error(RED + "❌ Error fetching Dhan portfolio: {}" + RESET, e.getMessage(), e);
            throw new BrokerException("Dhan fetch failed: " + e.getMessage(), -1, e);
        }
    }

    // ---------- Mapping helpers (adapt to real Dhan responses) ----------
    private List<HoldingItem> mapDhanHoldings(String json) throws Exception {
        List<HoldingItem> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        JsonNode root = om.readTree(json);
        // Dhan docs use a top-level "data" object — defensive code below
        JsonNode arr = root.path("data");
        if (arr.isMissingNode() || arr.isNull()) arr = root;

        for (JsonNode n : arr) {
            // try to support both array-of-objects or object with "holdings" array
            JsonNode entries = n.isArray() ? n : n.path("holdings");
            if (entries.isArray()) {
                for (JsonNode hnode : entries) {
                    HoldingItem h = new HoldingItem();
                    h.symbol = hnode.path("symbol").asText(hnode.path("tradingsymbol").asText(null));
                    h.exchange = hnode.path("exchange").asText("NSE");
                    h.instrumentType = hnode.path("instrument_type").asText("EQUITY");
                    h.quantity = new BigDecimal(hnode.path("quantity").asText("0"));
                    h.avgCost = new BigDecimal(hnode.path("avg_price").asText(hnode.path("average_price").asText("0")));
                    h.isin = hnode.path("isin").asText(null);
                    out.add(h);
                }
                break; // mapped holdings
            }
        }

        // fallback: if root.data is array-of-holdings
        if (out.isEmpty() && root.path("data").isArray()) {
            for (JsonNode hnode : root.path("data")) {
                HoldingItem h = new HoldingItem();
                h.symbol = hnode.path("symbol").asText(hnode.path("tradingsymbol").asText(null));
                h.exchange = hnode.path("exchange").asText("NSE");
                h.instrumentType = hnode.path("instrument_type").asText("EQUITY");
                h.quantity = new BigDecimal(hnode.path("quantity").asText("0"));
                h.avgCost = new BigDecimal(hnode.path("average_price").asText("0"));
                h.isin = hnode.path("isin").asText(null);
                out.add(h);
            }
        }

        return out;
    }

    private List<PositionItem> mapDhanPositions(String json) throws Exception {
        List<PositionItem> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        JsonNode root = om.readTree(json);
        JsonNode data = root.path("data");
        // handle common shapes: data.net or data.positions or data (array)
        JsonNode arr = data.path("net");
        if (!arr.isArray()) arr = data.path("positions");
        if (!arr.isArray()) arr = data;

        if (arr.isArray()) {
            for (JsonNode n : arr) {
                PositionItem p = new PositionItem();
                p.symbol = n.path("tradingsymbol").asText(n.path("symbol").asText(null));
                p.exchange = n.path("exchange").asText("NSE");
                // infer instrument type
                if (n.hasNonNull("option_type")) p.instrumentType = "OPT";
                else if (n.hasNonNull("expiry")) p.instrumentType = "FUT";
                else p.instrumentType = "EQUITY";

                p.expiry = n.path("expiry").asText(null);
                p.strike = new BigDecimal(n.path("strike_price").asText(n.path("strike").asText("0")));
                p.optionType = n.path("option_type").asText(null);
                p.side = n.path("quantity").asInt() >= 0 ? "LONG" : "SHORT";
                p.quantity = new BigDecimal(n.path("quantity").asText("0"));
                p.avgPrice = new BigDecimal(n.path("average_price").asText(n.path("avg_price").asText("0")));
                p.pnlRealized = new BigDecimal(n.path("realized_pnl").asText(n.path("realized_profit").asText("0")));
                p.pnlUnrealized = new BigDecimal(n.path("unrealised_pnl").asText(n.path("unrealized_profit").asText("0")));
                out.add(p);
            }
        }
        return out;
    }

    private CashSummary mapDhanFunds(String json) throws Exception {
        CashSummary c = new CashSummary();
        if (json == null || json.isEmpty()) return c;
        JsonNode root = om.readTree(json);
        JsonNode d = root.path("data");
        if (d.isMissingNode() || d.isNull()) d = root;

        // common fields - adapt if Dhan returns different names
        c.available = new BigDecimal(d.path("available_balance").asText(d.path("available").path("cash").asText("0")));
        c.used = new BigDecimal(d.path("used_margin").asText(d.path("utilised").asText("0")));
        c.pnlRealizedToday = new BigDecimal(d.path("pnl_today").asText("0"));
        c.pnlUnrealized = new BigDecimal(d.path("unrealised_pnl").asText("0"));
        return c;
    }
}
