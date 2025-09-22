package com.aem.ai.scanner.scheduler;

import com.aem.GenericeConstants;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.model.*;
import com.aem.ai.scanner.services.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

@Component(
        service = Runnable.class,
        immediate = true
)
@Designate(ocd = GeminiStockScannerScheduler.Config.class)
public class GeminiStockScannerScheduler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(GeminiStockScannerScheduler.class);

    @Reference
    private GeminiService geminiService;

    @Reference
    private DAOFactory daoFactory;

    private volatile boolean enabled = true;

    private static final ObjectMapper mapper = new ObjectMapper();

    @ObjectClassDefinition(name = "BSK Gemini Stock Scanner Scheduler Config", description = "Scheduler for Gemini AI Stock Scanner")
    public @interface Config {
        @AttributeDefinition(name = "Scheduler Enabled", description = "Enable or Disable Scheduler")
        boolean scheduler_enabled() default true;

        @AttributeDefinition(name="Cron expression")
        String scheduler_expression() default "0 0,30 9-13 ? * MON-FRI";

        @AttributeDefinition(name = "Allow concurrent execution")
        boolean scheduler_concurrent() default false;
    }

    private String cronExpression;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.enabled = config.scheduler_enabled();
        log.info("Gemini Stock Scanner Scheduler activated. Enabled: {}, Cron: {}", enabled, cronExpression);
    }

    @Override
    public void run() {
        if (!enabled) {
            log.info("Gemini Stock Scanner Scheduler is disabled. Skipping execution.");
            return;
        }

        log.info("Running Gemini Stock Scanner Scheduler at {}", LocalDateTime.now());

        try {
            StockScannerResult result = geminiService.runStockScanner();

            // Collect all NSE symbols
            Set<String> symbols = new HashSet<>();
            collectSymbols(result, symbols);
            if (symbols.isEmpty()) {
                log.warn("No stock picks found by Gemini Stock Scanner. Exiting.");
                return;
            }

            // Get instrument keys
            Map<String, String> instrumentMap = daoFactory.getInstrumentKeys(symbols.toArray(new String[0]));

            if (instrumentMap.isEmpty()) {
                log.warn("No instrument keys found for symbols: {}. Exiting.", symbols);
                return;
            }
            // Enrich and persist
            persistPicks(result.getTopIntradayPicks(), "INTRADAY", result.getMarketBias(), instrumentMap);
            persistPicks(result.getTopBtstPicks(), "BTST", result.getMarketBias(), instrumentMap);
            persistPicks(result.getTrendingStocks(), "TRENDING", result.getMarketBias(), instrumentMap);
            persistPicks(result.getVolumeBuildUpStocks(), "VOLUME", result.getMarketBias(), instrumentMap);
            persistPicks(result.getNewsBasedStocks(), "NEWS", result.getMarketBias(), instrumentMap);

            log.info("Stock scanner result persisted successfully. Market Bias: {}", result.getMarketBias());

        } catch (Exception e) {
            log.error("Error running Gemini Stock Scanner Scheduler", e);
        }

        log.info("Finished Gemini Stock Scanner Scheduler at {}", LocalDateTime.now());
    }

    private void collectSymbols(StockScannerResult result, Set<String> symbols) {
        if (result.getTopIntradayPicks() != null) {
            result.getTopIntradayPicks().forEach(p -> symbols.add(p.getNseSymbol()));
        }
        if (result.getTopBtstPicks() != null) {
            result.getTopBtstPicks().forEach(p -> symbols.add(p.getNseSymbol()));
        }
        if (result.getTrendingStocks() != null) {
            result.getTrendingStocks().forEach(p -> symbols.add(p.getNseSymbol()));
        }
        if (result.getVolumeBuildUpStocks() != null) {
            result.getVolumeBuildUpStocks().forEach(p -> symbols.add(p.getNseSymbol()));
        }
        if (result.getNewsBasedStocks() != null) {
            result.getNewsBasedStocks().forEach(p -> symbols.add(p.getNseSymbol()));
        }
    }

    private void persistPicks(List<? extends StockPick> picks, String strategy,
                              String marketBias, Map<String, String> instrumentMap) {
        if (picks == null) return;

        picks.forEach(p -> {
            try {
                String symbol = p.getNseSymbol();
                String instrumentKey = instrumentMap.getOrDefault(symbol, p.getInstrumentKey());
                if (instrumentKey == null || instrumentKey.isEmpty()) {
                    log.warn("No instrument key found for symbol: {}. Skipping.", symbol);
                    return;
                }
                instrumentKey = instrumentKey.replaceFirst("^NSE_EQ\\|", "");
                p.setInstrumentKey(instrumentKey);

                daoFactory.upsertWatchlistEntry(
                        symbol,
                        instrumentKey,
                        "SCANNER",
                        strategy,
                        String.valueOf(p.getConfidenceScore()),
                        marketBias
                );
            } catch (Exception ex) {
                log.error("Failed to upsert {} pick: {}", strategy, p.getNseSymbol(), ex);
            }
        });
    }
}
