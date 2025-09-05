package com.aem.ai.scanner.scheduler;

import com.GenericeConstants;
import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.dao.WatchlistDao;
import com.aem.ai.scanner.model.*;
import com.aem.ai.scanner.services.GeminiService;
import com.aem.ai.scanner.services.StrategyEngine;
import com.aem.ai.scanner.services.TelegramService;
import com.aem.ai.scanner.utils.Timeframes;
import com.aem.ai.scanner.utils.Utils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Designate(ocd = LiveScannerDelta.Config.class)
@Component(
        service = Runnable.class,
        immediate = true,
        property = {
                "scheduler.name=LiveScannerDelta"
        }
)
public class LiveScannerDelta implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(LiveScannerDelta.class);

    @ObjectClassDefinition(name = "BSK  Delta Live Scanner Scheduler",
            description = "Fetch market data for  Delta symbols on a schedule (pure OSGi, no threads)")
    public @interface Config {
        @AttributeDefinition(name = "Enable")
        boolean enable() default true;

        @AttributeDefinition(name = "Cron expression")
        String scheduler_expression() default "0 0/5 * * * ?";
        @AttributeDefinition(name = "Allow concurrent execution")
        boolean scheduler_concurrent() default false;

        @AttributeDefinition(name = "Scheduler name")
        String scheduler_name() default "LiveScannerDelta";

        @AttributeDefinition(name = "Timeframes e.g. 5m:120,15m:64,1h:48,1d:365")
        String timeframes() default "5m:120,15m:64,1h:48,1d:365";

        @AttributeDefinition(name = "Retries (per run)")
        int retries() default 1;

        @AttributeDefinition(name = "Trades table name")
        String trades_table() default "currency_trades";
    }

    private volatile Config config;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CachedStrategies> strategyCache = new ConcurrentHashMap<>();

    @Reference
    private WatchlistDao watchlistDao;

    @Reference
    private DAOFactory daoFactory;

    @Reference
    private StrategyEngine strategyEngine;

    @Reference
    private TelegramService telegram;

    @Reference
    private GeminiService geminiService;


    private final Map<String, MarketDataService> servicesByBroker = new ConcurrentHashMap<>();

    @Reference(
            service = MarketDataService.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
    )
    protected void bindMarketDataService(MarketDataService svc) {
        if (svc != null) {
            servicesByBroker.put(svc.brokerCode(), svc);
            log.info("✅ Bound MarketDataService: {}", svc.brokerCode());
        }
    }

    protected void unbindMarketDataService(MarketDataService svc) {
        if (svc != null) {
            servicesByBroker.remove(svc.brokerCode());
            log.info("🛑 Unbound MarketDataService: {}", svc.brokerCode());
        }
    }

    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    @Activate
    @Modified
    protected void activate(Config cfg) {
        this.config = cfg;
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        log.info("✅ LiveScannerDelta activated: cron={} retries={}", cfg.scheduler_expression(), cfg.retries());
    }

    @Deactivate
    protected void deactivate() {
        log.info("🛑 LiveScannerDelta deactivated.");
    }

    @Override
    public void run() {
        if (config == null || !config.enable()) {
            log.debug("Scheduler disabled");
            return;
        }

        Map<String, Integer> tfs = Timeframes.parse(config.timeframes());
        if (tfs.isEmpty()) {
            log.warn("No timeframes configured");
            return;
        }

        List<InstrumentSymbol> symbols = watchlistDao.symbolsForDelta();
        if (symbols == null || symbols.isEmpty()) {
            log.debug("No symbols in watchlist");
            return;
        }

        MarketDataService svc = servicesByBroker.get(GenericeConstants.DELTA);
        if (svc == null) {
            log.warn("No MarketDataService for broker {}", GenericeConstants.DELTA);
            return;
        }

        for (Map.Entry<String, Integer> tf : tfs.entrySet()) {
            for (InstrumentSymbol symbol : symbols) {
                fetchAndProcess(svc, symbol, tf.getKey(), tf.getValue(), 0);
            }
        }
    }

    private void fetchAndProcess(MarketDataService svc, InstrumentSymbol symbol,
                                 String timeframe, int count, int attempt) {
        try {
            List<Candle> candles = svc.fetchCandles(symbol, timeframe, count, false);
            if (candles == null || candles.isEmpty()) {
                throw new RuntimeException("No candles returned");
            }

            tradesMonitor(symbol, candles);

            List<StrategyConfig> strategies = parseStrategiesCached(symbol);
            for (StrategyConfig sc : strategies) {
                Optional<Signal> opt = strategyEngine.evaluate(sc, candles, symbol, timeframe);
                opt.ifPresent(signal -> {
                    try {
                        handleSignal(symbol, timeframe, sc, signal);
                    } catch (Exception e) {
                        log.error("Signal handling failed: {}", e.getMessage(), e);
                    }
                });
            }

            log.info("✅ Completed {} {} (candles={}, strategies={})",
                    symbol.getSymbol(), timeframe, candles.size(), strategies.size());

            consecutiveFailures.remove(svc.brokerCode());

        } catch (Exception e) {
            int fails = consecutiveFailures.computeIfAbsent(svc.brokerCode(), k -> new AtomicInteger()).incrementAndGet();
            log.warn("❌ {} {} {} failed (attempt={}): {}", svc.brokerCode(), symbol.getSymbol(), timeframe, attempt + 1, e.getMessage());

            if (attempt < config.retries()) {
                fetchAndProcess(svc, symbol, timeframe, count, attempt + 1);
            } else if (fails > 5) {
                log.error("Too many consecutive failures for broker {}", svc.brokerCode());
            }
        }
    }

    private void tradesMonitor(InstrumentSymbol symbol, List<Candle> candles) {
        try {
            double ltp = candles.get(candles.size() - 1).getClose();
            List<TradeModel> openTrades = daoFactory.listOpenTradesForSymbol(symbol.getSymbol(), config.trades_table());

            for (TradeModel t : openTrades) {
                daoFactory.updateLtp(t, ltp, config.trades_table());
                boolean hitTarget = (t.getSide() == Signal.Side.BUY && ltp >= t.getTarget())
                        || (t.getSide() == Signal.Side.SELL && ltp <= t.getTarget());
                boolean hitStop = (t.getSide() == Signal.Side.BUY && ltp <= t.getStopLoss())
                        || (t.getSide() == Signal.Side.SELL && ltp >= t.getStopLoss());

                if (hitTarget || hitStop) {
                    double exitPrice = hitTarget ? t.getTarget() : t.getStopLoss();
                    t.setExitPrice(exitPrice);
                    t.setExitTime(LocalDateTime.now());
                    double pnl = (t.getSide() == Signal.Side.BUY)
                            ? (exitPrice - t.getEntryPrice()) * t.getQuantity()
                            : (t.getEntryPrice() - exitPrice) * t.getQuantity();
                    t.setPnl(pnl);

                    daoFactory.closeTradeWithPnl(t, config.trades_table());
                    telegram.sendMessageDailyStocksAlerts("Closed trade: " + symbol + " pnl=" + pnl);

                    log.info("🔴 Auto-closed trade {} pnl={}", symbol.getSymbol(), pnl);
                }
            }
        } catch (Exception e) {
            log.warn("Trade monitor failed: {}", e.getMessage());
        }
    }

    private List<StrategyConfig> parseStrategiesCached(InstrumentSymbol symbol) {
        String json = symbol.getBestStrategy();
        if (json == null || json.isEmpty()) return Collections.emptyList();

        CachedStrategies cached = strategyCache.get(symbol.getSymbol());
        if (cached != null && Objects.equals(cached.hash, Integer.toString(json.hashCode()))) {
            return cached.strategies;
        }

        try {
            JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, StrategyConfig.class);
            List<StrategyConfig> strategies = mapper.readValue(json, type);
            strategyCache.put(symbol.getSymbol(), new CachedStrategies(Integer.toString(json.hashCode()), strategies));
            return strategies;
        } catch (Exception e) {
            log.error("Failed to parse strategies for {}: {}", symbol.getSymbol(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private void handleSignal(InstrumentSymbol symbol, String timeframe, StrategyConfig sc, Signal signal) throws Exception {
        String msg = strategyEngine.format(signal, sc, symbol, timeframe);
        telegram.sendMessageDailyStocksAlerts(msg);

        if (signal.getSide() == Signal.Side.BUY) {
            onEntrySignal(symbol, timeframe, sc, signal, msg);
        } else if (signal.getSide() == Signal.Side.SELL) {
            onExitSignal(symbol, timeframe, sc, signal, msg);
        }
    }

    private void onEntrySignal(InstrumentSymbol symbol, String timeframe, StrategyConfig sc, Signal signal, String comment) throws Exception {
        List<TradeModel> openTrades = daoFactory.listOpenTrades(symbol, timeframe, signal, config.trades_table());
        if (!openTrades.isEmpty()) {
            daoFactory.appendOpenTradeComment(symbol, signal.getSide(), comment, config.trades_table());
            return;
        }

        TradeModel t = new TradeModel(symbol, signal.getSide(),
                signal.getEntryPrice(), signal.getStopLoss(), signal.getTarget(), 1);
        t.setEntryTime(LocalDateTime.now());
        t.setStatus(TradeModel.Status.OPEN);
        t.setTimeFrame(timeframe);
        // Generate trade analysis
        TradeAnalysis tradeAnalysis = geminiService.tradeSignalAnalysis(
                Utils.formatTradeSignalMessage(symbol, timeframe, sc, signal, comment)
        );
        daoFactory.insertTrade(t, tradeAnalysis, config.trades_table());
        daoFactory.appendOpenTradeComment(symbol, signal.getSide(), comment, config.trades_table());
    }

    private void onExitSignal(InstrumentSymbol symbol, String timeframe, StrategyConfig sc, Signal signal, String comment) throws Exception {
        List<TradeModel> openTrades = daoFactory.listOpenTrades(symbol, timeframe, signal, config.trades_table());
        if (openTrades.isEmpty()) return;

        TradeModel t = openTrades.get(0);
        t.setExitPrice(signal.getEntryPrice());
        t.setExitTime(LocalDateTime.now());
        double pnl = (t.getSide() == Signal.Side.BUY)
                ? (t.getExitPrice() - t.getEntryPrice())
                : (t.getEntryPrice() - t.getExitPrice());
        t.setPnl(pnl);

        daoFactory.closeTradeWithPnl(t, config.trades_table());
        daoFactory.appendOpenTradeComment(symbol, t.getSide(), "[CLOSED] " + comment, config.trades_table());
    }

    private static class CachedStrategies {
        final String hash;
        final List<StrategyConfig> strategies;
        CachedStrategies(String hash, List<StrategyConfig> strategies) {
            this.hash = hash;
            this.strategies = strategies;
        }
    }
}
