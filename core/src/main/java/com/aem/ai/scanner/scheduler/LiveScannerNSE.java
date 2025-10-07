package com.aem.ai.scanner.scheduler;

import com.aem.GenericeConstants;
import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.dao.WatchlistDao;
import com.aem.ai.scanner.model.*;
import com.aem.ai.scanner.scanner.OHLStrategyScanner;
import com.aem.ai.scanner.services.GeminiService;
import com.aem.ai.scanner.services.StrategyEngine;
import com.aem.ai.scanner.services.TelegramService;
import com.aem.ai.scanner.services.impl.NSEMarketOpenStatusService;
import com.aem.ai.scanner.utils.Timeframes;
import com.aem.ai.scanner.utils.Utils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
//todo : Check Pivot levels before the entry.
// Implement trailing SL and Target
// Similarly check the target with pivot levels.
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


        @AttributeDefinition(name = "Trades cut off time. hh:mm in 24hr format", description = "E.g. 15:20")
        String trade_cutoff_time() default "14:00";


    }

    public  String stocksTradeTable;

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

    @Reference
    private OHLStrategyScanner ohlStrategyScanner;

    @Reference
    private NSEMarketOpenStatusService nseMarketOpenStatusService;

    private int cutOffHour = 14;
    private int cutOffMinute = 0;

    @Reference
    private Scheduler scheduler;
    private final Map<String, MarketDataService> servicesByBroker = new ConcurrentHashMap<>();
    private static final Map<String, PivotLevels> DAILY_PIVOT_LEVELS = new ConcurrentHashMap<>();
    private static LocalDateTime lastPivotCalculation = null;

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

    public String getStocksTradeTable() {
        return stocksTradeTable;
    }

    public void setStocksTradeTable(String stocksTradeTable) {
        this.stocksTradeTable = stocksTradeTable;
    }

    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    @Activate
    @Modified
    protected void activate(Config cfg) {
        this.config = cfg;
        String tradeCutoffTime = cfg.trade_cutoff_time();
        if (StringUtils.isNotEmpty(tradeCutoffTime) && tradeCutoffTime.contains(":")){
            String[] parts = tradeCutoffTime.split(":");
            try {
                cutOffHour = Integer.parseInt(parts[0]);
                cutOffMinute = Integer.parseInt(parts[1]);
            }catch (Exception e){
                log.error("Invalid trade cutoff time format, using default 14:00");
                cutOffHour = 14;
                cutOffMinute = 0;
            }

        }
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        stocksTradeTable = cfg.trades_table();
        // Schedule the job
        try { scheduler.unschedule("LiveScannerNSE"); } catch (Exception ignore) {}
        scheduleJob(cfg);

        // Register self-healing monitor
        log.info("✅ LiveScannerNSE activated: cron={} retries={}", cfg.scheduler_expression(), cfg.retries());
    }
    private void scheduleJob(Config cfg) {
        if (!cfg.enable()) {
            log.info("Scheduler is disabled in config.");
            return;
        }
        try {
            boolean unschedule = scheduler.unschedule(cfg.scheduler_name());// remove old instance
            log.info("Scheduler unscheduled: {}", unschedule);
            ScheduleOptions options = scheduler.EXPR(cfg.scheduler_expression());
            options.name(cfg.scheduler_name());
            options.canRunConcurrently(cfg.scheduler_concurrent());
            boolean schedule = scheduler.schedule(this, options);
            if (!schedule) {
                log.error("Failed to schedule job with name: {}", cfg.scheduler_name());
                return;
            }
            log.info("Scheduler registered with cron {}", cfg.scheduler_expression());
        } catch (Exception e) {
            log.error("Failed to register scheduler", e);
        }
    }

    @Deactivate
    protected void deactivate() {
        log.info("🛑 LiveScannerNSE deactivated.");
        try { scheduler.unschedule("LiveScannerNSE"); } catch (Exception ignore) {}
    }
    private static final String SCHEDULER_KEY = "LiveScannerNSE";

    @Override
    public void run() {
        try {
            boolean isMarketClose = Utils.isMarketClose();
            if (isMarketClose){
                log.error(" Market is closed. Skipping this run.");
                return;
            }
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

    private void doRun() throws Exception {
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
        MarketStatusResult marketStatus = nseMarketOpenStatusService.getMarketStatus();
        if (marketStatus == null || !StringUtils.equalsIgnoreCase(marketStatus.getMarketStatus(), GenericeConstants.OPEN)) {
            log.warn("Market is not open (status={})", marketStatus != null ? marketStatus.getMarketStatus() : "null");
            return;
        }

        for (Map.Entry<String, Integer> tf : tfs.entrySet()) {
            for (InstrumentSymbol symbol : symbols) {
                fetchAndProcess(svc, symbol, tf.getKey(), tf.getValue(), 0);
            }
        }
        lastPivotCalculation = LocalDateTime.now();
    }

    private void fetchAndProcess(MarketDataService svc, InstrumentSymbol symbol,
                                 String timeframe, int count, int attempt) {
        try {
            final boolean historical = Timeframes.isHistoricalBucket(timeframe);
            List<Candle> candles = svc.fetchCandles(symbol, timeframe, count, historical);
            if (candles == null || candles.isEmpty()) {
                throw new RuntimeException("No candles returned");
            }
            // ✅ Calculate daily pivots only once per day
            boolean recalcRequired = lastPivotCalculation == null ||
                    !lastPivotCalculation.toLocalDate().equals(LocalDateTime.now().toLocalDate());
            if (StringUtils.equalsIgnoreCase(timeframe,"1d")){
                if (recalcRequired){
                    PivotLevels pivots = Utils.calculatePivotLevels(candles);
                    if (pivots != null) {
                        DAILY_PIVOT_LEVELS.put(symbol.getSymbol(), pivots);
                        log.info("📊 Daily Pivot for {}: {}", symbol.getSymbol(), pivots);
                    }

                }
            }
            tradesMonitor(symbol, candles);
            log.info("Fetched {} candles for {} {} (attempt={})",
                    candles.size(), symbol.getSymbol(), timeframe, attempt + 1);

            List<String> ohlTimeFrameList = ohlStrategyScanner.getOHLTimeFrameList();
            if (ohlTimeFrameList.contains(timeframe)){
                log.info( " Check for the OHL scanner " );
                Optional<Signal> ohlcSignal = ohlStrategyScanner.evaluateLatest(candles, timeframe,  symbol);
                if (null != ohlcSignal && ohlcSignal.isPresent()) {
                    log.info("OHL Strategy signal for {} {}: {} (score={})",
                            symbol.getSymbol(), timeframe,
                            ohlcSignal.get().getSide(), ohlcSignal.get().getScore());
                    handleSignal(symbol, timeframe,
                            new StrategyConfig("OHL Strategy"),
                            ohlcSignal.get());
                }
            }

            List<StrategyConfig> strategies = parseStrategiesCached(symbol);

            // ✅ Collect signals for all strategies
            List<SignalResult> results = new ArrayList<>();
            PivotLevels pivots = LiveScannerNSE.getPivotLevels(symbol.getSymbol());
            for (StrategyConfig sc : strategies) {
                Optional<Signal> opt = strategyEngine.evaluate(sc, candles, symbol, timeframe, pivots);
                opt.ifPresent(signal -> results.add(new SignalResult(sc, signal)));
            }

            // ✅ Pick the best signal (based on your ranking logic)
            if (results.isEmpty()) {
                log.info("No signals generated for {} {} (candles={}, strategies={})",
                        symbol.getSymbol(), timeframe, candles.size(), strategies.size());
                return;
            }
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
            //updateLtpPnlForceClosedTrades(symbol, ltp); todo : Implement trade Monitor scheduler
            for (TradeModel t : openTrades) {
                daoFactory.updateLtp(t, ltp, config.trades_table());
                boolean hitTarget = (t.getSide() == Signal.Side.BUY && ltp >= t.getTarget())
                        || (t.getSide() == Signal.Side.SELL && ltp <= t.getTarget());
                boolean hitStop = (t.getSide() == Signal.Side.BUY && ltp <= t.getStopLoss())
                        || (t.getSide() == Signal.Side.SELL && ltp >= t.getStopLoss());


                MarketStatusResult marketStatus = nseMarketOpenStatusService.getMarketStatus();
                boolean closed = marketStatus != null && StringUtils.equalsIgnoreCase(marketStatus.getMarketStatus(), GenericeConstants.CLOSED);

                boolean misClose = closed && StringUtils.equalsIgnoreCase(t.getOrderType(), GenericeConstants.ORDER_TYPE_MIS);
                if (hitTarget || hitStop || misClose) {
                    double exitPrice = t.getTarget();
                    if (hitStop){
                        exitPrice =  t.getStopLoss();
                    }else if (misClose){
                        exitPrice = ltp;
                        t.setReason("Market closed -FORCE  MIS exit");
                    }
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

    private void updateLtpPnlForceClosedTrades(InstrumentSymbol symbol, double ltp) throws SQLException {
        //todo: Stop update LTP for force-closed trades after 3.30pm o the exit day
        List<TradeModel> forceClosedTradesForSymbol = daoFactory.listForceClosedTradesForSymbol(symbol.getSymbol(), config.trades_table());
        forceClosedTradesForSymbol.forEach(t -> {
            try {
                daoFactory.updateLtp(t, ltp, config.trades_table());
                log.info("🔴 Force-closed trade {} at LTP={}", symbol.getSymbol(), ltp);
            } catch (Exception e) {
                log.error("Failed to force-close trade {}: {}", t.getTradeId(), e.getMessage(), e);
            }
        });
    }

    private List<StrategyConfig> parseStrategiesCached(InstrumentSymbol symbol) {
        String json = symbol.getBestStrategy();
        if (json == null || json.isEmpty()) return Collections.emptyList();

        if (StringUtils.equalsIgnoreCase(json, "SCANNER")) {
            return Collections.emptyList();
        }

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
            //daoFactory.appendOpenTradeComment(symbol, signal.getSide(), signalMsg, config.trades_table());
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
        PivotLevels pivots = LiveScannerNSE.getPivotLevels(symbol.getSymbol());
        if (pivots != null) {
            signalMsg += String.format("\nPivots: P=%.2f R1=%.2f S1=%.2f",
                    pivots.getPivot(), pivots.getR1(), pivots.getS1());
        }
        TradeAnalysis tradeAnalysis = geminiService.tradeSignalAnalysis(
                Utils.formatTradeSignalMessage(symbol, timeframe, sc, signal, signalMsg, pivots)
        );

        // Check if MIS order and time is after 2 PM
        if ("MIS".equalsIgnoreCase(trade.getOrderType())) {
            LocalTime now = LocalTime.now();
            LocalTime cutoff = LocalTime.of(cutOffHour, cutOffMinute); // 2:00 PM
            if (now.isAfter(cutoff)) {
                log.info("Skipping MIS trade for {} as current time {} is after cutoff {}", symbol.getSymbol(), now, cutoff);
                return;
            }
        }
        // Insert trade into database
        telegram.sendMessageDailyStocksAlerts(signalMsg);
        daoFactory.insertTrade(trade, tradeAnalysis, config.trades_table(), pivots);
        daoFactory.appendOpenTradeComment(symbol, signal.getSide(), signalMsg, config.trades_table());

        // Log beautifully formatted signal
        log.debug("\n{}", Utils.formatTradeSignalMessage(symbol, timeframe, sc, signal, signalMsg));
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



    public static PivotLevels getPivotLevels(String symbol) {
        return DAILY_PIVOT_LEVELS.get(symbol);
    }
}
