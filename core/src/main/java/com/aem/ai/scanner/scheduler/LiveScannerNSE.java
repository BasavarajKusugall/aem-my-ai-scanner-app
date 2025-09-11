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
import org.apache.commons.lang3.StringUtils;
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

@Designate(ocd = LiveScannerNSE.Config.class)
@Component(
        service = Runnable.class,
        immediate = true,
        property = {
                "scheduler.name=LiveScannerNSE"
        }
)
public class LiveScannerNSE implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(LiveScannerNSE.class);

    @ObjectClassDefinition(name = "BSK NSE UPSTOX Live Scanner Scheduler",
            description = "Fetch market data for NSE UPSTOX symbols on a schedule (pure OSGi, no threads)")
    public @interface Config {
        @AttributeDefinition(name = "Enable")
        boolean enable() default true;

        @AttributeDefinition(name = "Cron expression")
        String scheduler_expression() default "0 0/5 9-15 ? * MON-FRI";

        @AttributeDefinition(name = "Allow concurrent execution")
        boolean scheduler_concurrent() default false;

        @AttributeDefinition(name = "Misfire Policy", description = "Reschedule on misfire")
        String scheduler_misfire_policy() default "REPLACE";

        @AttributeDefinition(name = "Scheduler name")
        String scheduler_name() default "LiveScannerNSE";

        @AttributeDefinition(name = "Timeframes e.g. 5m:120,15m:64,1h:48,1d:365")
        String timeframes() default "5m:120,15m:64,1h:48,1d:365";

        @AttributeDefinition(name = "Retries (per run)")
        int retries() default 1;

        @AttributeDefinition(name = "Trades table name")
        String trades_table() default "stock_trades";
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
        log.info("✅ LiveScannerNSE activated: cron={} retries={}", cfg.scheduler_expression(), cfg.retries());
    }

    @Deactivate
    protected void deactivate() {
        log.info("🛑 LiveScannerNSE deactivated.");
    }
    private static final String SCHEDULER_KEY = "LiveScannerNSE";

    @Override
    public void run() {
        try {
            log.info("💡 LiveScannerNSE scheduler triggered at {}", LocalDateTime.now());
            doRun(); // move your original run() logic into doRun()
            // reset scheduler-level failures on success
            AtomicInteger ai = consecutiveFailures.get(SCHEDULER_KEY);
            if (ai != null) ai.set(0);
        } catch (Throwable t) {
            // Catch everything so Sling doesn't unschedule the job
            log.error("❌ Unhandled error in LiveScannerNSE scheduler (kept alive): {}", t.getMessage(), t);
            consecutiveFailures.computeIfAbsent(SCHEDULER_KEY, k -> new AtomicInteger()).incrementAndGet();
            // do NOT rethrow
        }
    }

    private void doRun() {
        if (config == null || !config.enable()) {
            log.debug("Scheduler disabled");
            return;
        }

        Map<String, Integer> tfs = Timeframes.parse(config.timeframes());
        if (tfs.isEmpty()) {
            log.warn("No timeframes configured");
            return;
        }

        List<InstrumentSymbol> symbols = watchlistDao.symbolsForUpstox();
        if (symbols == null || symbols.isEmpty()) {
            log.debug("No symbols in watchlist");
            return;
        }

        MarketDataService svc = servicesByBroker.get(GenericeConstants.UPSTOX);
        if (svc == null) {
            log.warn("No MarketDataService for broker {}", GenericeConstants.UPSTOX);
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
            final boolean historical = Timeframes.isHistoricalBucket(timeframe);
            List<Candle> candles = svc.fetchCandles(symbol, timeframe, count, historical);
            if (candles == null || candles.isEmpty()) {
                throw new RuntimeException("No candles returned");
            }

            tradesMonitor(symbol, candles);

            List<StrategyConfig> strategies = parseStrategiesCached(symbol);

            // ✅ Collect signals for all strategies
            List<SignalResult> results = new ArrayList<>();
            for (StrategyConfig sc : strategies) {
                Optional<Signal> opt = strategyEngine.evaluate(sc, candles, symbol, timeframe);
                opt.ifPresent(signal -> results.add(new SignalResult(sc, signal)));
            }

            // ✅ Pick the best signal (based on your ranking logic)
            results.stream()
                    .max(Comparator.comparingDouble(r -> r.signal.getScore())) // Example: highest score
                    .ifPresent(best -> {
                        try {
                            log.info("Best signal for {} {}: {} (score={})",
                                    symbol.getSymbol(), timeframe, best.signal.getSide(), best.signal.getScore());
                            log.info("\n ********* Signal details: entry={}, sl={}, target={}  ******\n",
                                    best.signal.getEntryPrice(), best.signal.getStopLoss(), best.signal.getTarget());
                            handleSignal(symbol, timeframe, best.strategy, best.signal);
                            log.info("🏆 Best strategy selected: {}", best.strategy.getName());
                        } catch (Exception e) {
                            log.error("Signal handling failed: {}", e.getMessage(), e);
                        }
                    });

            log.info("✅ Completed {} {} (candles={}, strategies={}, signals={})",
                    symbol.getSymbol(), timeframe, candles.size(), strategies.size(), results.size());

            consecutiveFailures.remove(svc.brokerCode());

        } catch (Exception e) {
            int fails = consecutiveFailures.computeIfAbsent(svc.brokerCode(), k -> new AtomicInteger()).incrementAndGet();
            log.warn("❌ {} {} {} failed (attempt={}): {}",
                    svc.brokerCode(), symbol.getSymbol(), timeframe, attempt + 1, e.getMessage());

            if (attempt < config.retries()) {
                fetchAndProcess(svc, symbol, timeframe, count, attempt + 1);
            } else if (fails > 5) {
                log.error("Too many consecutive failures for broker {}", svc.brokerCode());
            }
        }
    }

    // ✅ Helper class to keep strategy + signal together
    private static class SignalResult {
        StrategyConfig strategy;
        Signal signal;

        SignalResult(StrategyConfig strategy, Signal signal) {
            this.strategy = strategy;
            this.signal = signal;
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
            log.error("Trade monitor failed: {}", e.getMessage(),e);
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


        if (signal.getSide() == Signal.Side.BUY) {
            onEntrySignal(symbol, timeframe, sc, signal, msg);
        } else if (signal.getSide() == Signal.Side.SELL) {
            onExitSignal(symbol, timeframe, sc, signal, msg);
        }
    }

    private void onEntrySignal(InstrumentSymbol symbol,
                               String timeframe,
                               StrategyConfig sc,
                               Signal signal,
                               String signalMsg) throws Exception {

        List<TradeModel> openTrades = daoFactory.listOpenTrades(symbol, timeframe, signal, config.trades_table());
        if (!openTrades.isEmpty()) {
            daoFactory.appendOpenTradeComment(symbol, signal.getSide(), signalMsg, config.trades_table());
            log.info("🔔 Comment appended to existing open trade: {} - {}", symbol.getSymbol(), signalMsg);
            return;
        }

        // Create new trade model
        TradeModel trade = new TradeModel(symbol, signal.getSide(),
                signal.getEntryPrice(), signal.getStopLoss(), signal.getTarget(), 1);
        trade.setEntryTime(LocalDateTime.now());
        trade.setStatus(TradeModel.Status.OPEN);
        trade.setTimeFrame(timeframe);

        if (!trade.isValid()) {
            log.warn("Trade is invalid: {}", trade);
            return;
        }
        // Generate trade analysis
        TradeAnalysis tradeAnalysis = geminiService.tradeSignalAnalysis(
                Utils.formatTradeSignalMessage(symbol, timeframe, sc, signal, signalMsg)
        );
        telegram.sendMessageDailyStocksAlerts(signalMsg);

        // Insert trade into database
        daoFactory.insertTrade(trade, tradeAnalysis, config.trades_table());
        daoFactory.appendOpenTradeComment(symbol, signal.getSide(), signalMsg, config.trades_table());

        // Log beautifully formatted signal
        log.info("\n{}", Utils.formatTradeSignalMessage(symbol, timeframe, sc, signal, signalMsg));
    }

    /**
     * Format trade signal message for logs and analysis
     */



    private void onExitSignal(InstrumentSymbol symbol, String timeframe, StrategyConfig sc, Signal signal, String signalMsg) throws Exception {
        List<TradeModel> openTrades = daoFactory.listOpenTrades(symbol, timeframe, signal, config.trades_table());
        if (openTrades.isEmpty()) return;

        TradeModel t = openTrades.get(0);
        t.setExitPrice(signal.getEntryPrice());
        t.setExitTime(LocalDateTime.now());
        double pnl = (t.getSide() == Signal.Side.BUY)
                ? (t.getExitPrice() - t.getEntryPrice())
                : (t.getEntryPrice() - t.getExitPrice());
        t.setPnl(pnl);
        telegram.sendMessageDailyStocksAlerts(signalMsg);
        daoFactory.closeTradeWithPnl(t, config.trades_table());
        daoFactory.appendOpenTradeComment(symbol, t.getSide(), "[CLOSED] " + signalMsg, config.trades_table());
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
