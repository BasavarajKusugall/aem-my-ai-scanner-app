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

    @Activate
    @Modified
    protected void activate(DhanConfig cfg) {
        this.cfg = cfg;
        log.info(CYAN + "🔌 Dhan Connector activated with baseUrl=" + cfg.baseUrl() + RESET);
    }

    @Override
    public String brokerCode() {
        return "DHAN";
    }

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
            Map<String, String> headers = new HashMap<>();
            headers.put("access-token", acc.getAccessToken() != null ? acc.getAccessToken() : acc.accessToken);
            headers.put("Content-Type", "application/json");

            // Configurable endpoints
            String holdingsEndpoint = cfg.holdingsEndpoint() != null ? cfg.holdingsEndpoint() : "/holdings";
            String positionsEndpoint = cfg.positionsEndpoint() != null ? cfg.positionsEndpoint() : "/positions";
            String fundsEndpoint = cfg.fundsEndpoint() != null ? cfg.fundsEndpoint() : "/fundlimit";

            // 1) Holdings
            String holdingsJson = http.get(cfg.baseUrl() + holdingsEndpoint, headers, cfg.timeoutMs());
            List<HoldingItem> holdings = mapDhanHoldings(holdingsJson);
            log.info(GREEN + "✅ Holdings fetched: {} instruments" + RESET, holdings.size());

            // 2) Positions
            String positionsJson = http.get(cfg.baseUrl() + positionsEndpoint, headers, cfg.timeoutMs());
            List<PositionItem> positions = mapDhanPositions(positionsJson);
            log.info(GREEN + "✅ Positions fetched: {} items" + RESET, positions.size());

            // 3) Funds
            String fundsJson = http.get(cfg.baseUrl() + fundsEndpoint, headers, cfg.timeoutMs());
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

    // ---------- Mapping helpers ----------
    private List<HoldingItem> mapDhanHoldings(String json) throws Exception {
        List<HoldingItem> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;

        JsonNode root = om.readTree(json);

        if (root.isArray()) {
            for (JsonNode hnode : root) {
                HoldingItem h = new HoldingItem();
                h.symbol = hnode.path("tradingSymbol").asText(hnode.path("symbol").asText(null));
                h.exchange = hnode.path("exchange").asText("NSE");
                h.instrumentType = "EQUITY";
                h.quantity = new BigDecimal(hnode.path("totalQty").asText(hnode.path("quantity").asText("0")));
                h.avgCost = new BigDecimal(hnode.path("avgCostPrice").asText(hnode.path("average_price").asText("0")));
                h.isin = hnode.path("isin").asText(null);
                out.add(h);
            }
        } else {
            JsonNode dataNode = root.path("data");
            if (dataNode.isArray()) {
                for (JsonNode hnode : dataNode) {
                    HoldingItem h = new HoldingItem();
                    h.symbol = hnode.path("tradingSymbol").asText(hnode.path("symbol").asText(null));
                    h.exchange = hnode.path("exchange").asText("NSE");
                    h.instrumentType = "EQUITY";
                    h.quantity = new BigDecimal(hnode.path("totalQty").asText(hnode.path("quantity").asText("0")));
                    h.avgCost = new BigDecimal(hnode.path("avgCostPrice").asText(hnode.path("average_price").asText("0")));
                    h.isin = hnode.path("isin").asText(null);
                    out.add(h);
                }
            }
        }

        log.debug("Mapped {} holdings from Dhan JSON", out.size());
        return out;
    }

    private List<PositionItem> mapDhanPositions(String json) throws Exception {
        List<PositionItem> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;

        JsonNode root = om.readTree(json);

        if (root.isArray()) {
            for (JsonNode n : root) {
                PositionItem p = new PositionItem();
                p.symbol = n.path("tradingSymbol").asText(n.path("symbol").asText(null));
                String exchangeSegment = n.path("exchangeSegment").asText("NSE_EQ");
                p.exchange = exchangeSegment.contains("NSE") ? "NSE" : "BSE";
                if (n.hasNonNull("drvOptionType") && !n.path("drvOptionType").isNull()) p.instrumentType = "OPT";
                else if (n.hasNonNull("drvExpiryDate") && !"0001-01-01".equals(n.path("drvExpiryDate").asText())) p.instrumentType = "FUT";
                else p.instrumentType = "EQUITY";

                p.side = "LONG".equalsIgnoreCase(n.path("positionType").asText()) ? "LONG" : "SHORT";
                p.quantity = new BigDecimal(n.path("netQty").asText("0"));
                p.avgPrice = new BigDecimal(n.path("buyAvg").asText(n.path("costPrice").asText("0")));
                p.pnlRealized = new BigDecimal(n.path("realizedProfit").asText("0"));
                p.pnlUnrealized = new BigDecimal(n.path("unrealizedProfit").asText("0"));
                p.expiry = n.path("drvExpiryDate").asText(null);
                p.strike = new BigDecimal(n.path("drvStrikePrice").asText("0"));
                p.optionType = n.path("drvOptionType").asText(null);

                out.add(p);
            }
        }

        log.debug("Mapped {} positions from Dhan JSON", out.size());
        return out;
    }

    private CashSummary mapDhanFunds(String json) throws Exception {
        CashSummary c = new CashSummary();
        if (json == null || json.isEmpty()) return c;

        JsonNode root = om.readTree(json);
        JsonNode d = root.path("data");
        if (d.isMissingNode() || d.isNull()) d = root;

        // Current Dhan fields
        c.available = new BigDecimal(d.path("availabelBalance").asText(d.path("available_balance").asText("0")));
        c.used = new BigDecimal(d.path("utilizedAmount").asText(d.path("used_margin").asText("0")));
        c.pnlRealizedToday = new BigDecimal(d.path("pnlRealizedToday").asText(d.path("pnl_today").asText("0")));
        c.pnlUnrealized = new BigDecimal(d.path("pnlUnrealized").asText(d.path("unrealised_pnl").asText("0")));

        log.debug("Mapped cash summary: available={}, used={}", c.available, c.used);
        return c;
    }
}
