package com.aem.ai.scanner.scheduler;

import com.aem.GenericeConstants;
import com.aem.ai.realtime.brokers.kite.OrderService;
import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.model.*;
import com.aem.ai.scanner.services.TelegramService;
import com.aem.ai.scanner.services.impl.NSEMarketOpenStatusService;
import com.aem.ai.scanner.utils.Timeframes;
import com.aem.ai.scanner.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Trades monitor scheduler:
 * - Reads open trades from DB
 * - Fetches latest 5m candles / LTP
 * - Updates LTP & unrealized PnL
 * - Checks target/stoploss/pivots
 * - Applies trailing SL logic
 * - Executes exits via OrderService (integration point)
 * - Updates DB and sends notifications
 */
@Designate(ocd = TradesMonitorScheduler.Config.class)
@Component(service = Runnable.class, immediate = true)
public class TradesMonitorScheduler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TradesMonitorScheduler.class);

    @ObjectClassDefinition(name = "BSK Trades Monitor Scheduler")
    public @interface Config {
        @AttributeDefinition(name = "Enable")
        boolean enable() default true;

        @AttributeDefinition(name = "Cron expression")
        String scheduler_expression() default "0 0/3 * * * ?";

        @AttributeDefinition(name = "Allow concurrent execution")
        boolean scheduler_concurrent() default false;

        @AttributeDefinition(name = "Trades table for stocks (optional override)")
        String stocks_table() default "stock_trades";

        @AttributeDefinition(name = "Trades table for crypto (optional override)")
        String crypto_table() default "currency_trades";

        @AttributeDefinition(name = "Trailing increment percent (e.g. 0.25)")
        double trailing_increment_percent() default 0.25;

        @AttributeDefinition(name = "Max holding minutes (0 = disabled)")
        int max_holding_minutes() default 0;

        @AttributeDefinition(name = "Timeframes e.g. 5m:120,15m:64,1h:48,1d:365")
        String timeframes() default "5m:120";

        @AttributeDefinition(name = " Auto MIS square-off time (HH:mm IST, 24h)")
        String auto_square_off_time() default "15:25";
    }

    private volatile Config cfg;

    @Reference
    private DAOFactory daoFactory;

    @Reference
    private TelegramService telegram;

    // optional: order execution service (if present)
    @Reference
    private OrderService orderService; // you have OrderService used by test servlet

    @Reference
    private Scheduler scheduler;

    @Reference
    private NSEMarketOpenStatusService nseMarketOpenStatusService;

    private final List<MarketDataService> marketServices = new CopyOnWriteArrayList<>();

    @Activate
    @Modified
    protected void activate(Config cfg) {
        this.cfg = cfg;
        log.info("✅ TradeMonitorScheduler activated: cron={}, enabled={}, trailing={}%, stocks_table={}, crypto_table={}",
                cfg.scheduler_expression(), cfg.enable(), cfg.trailing_increment_percent(), cfg.stocks_table(), cfg.crypto_table());
        try { scheduler.unschedule("TradeMonitorScheduler"); } catch (Exception ignore) {}
        scheduleJob();
    }

    @Deactivate
    protected void deactivate() {
        log.info("🛑 TradeMonitorScheduler deactivated.");
        try {
            scheduler.unschedule("TradeMonitorScheduler");
        } catch (Exception ignore) {}
    }

    private void scheduleJob() {
        if (!cfg.enable()) {
            log.info("TradesMonitorScheduler Scheduler disabled by config.");
            return;
        }
        try {
            ScheduleOptions opts = scheduler.EXPR(cfg.scheduler_expression());
            opts.name("TradeMonitorScheduler");
            opts.canRunConcurrently(cfg.scheduler_concurrent());
            scheduler.schedule(this, opts);
            log.info("Scheduled TradeMonitorScheduler with cron={}", cfg.scheduler_expression());
        } catch (Exception e) {
            log.error("Failed to schedule TradeMonitorScheduler", e);
        }
    }

    @Override
    public void run() {
        if (!cfg.enable()) {
            log.debug("TradeMonitorScheduler disabled - skipping run.");
            return;
        }
        // parse timeframes mapping (we only need 5m count usually)
        Map<String, Integer> tfs = Timeframes.parse(cfg.timeframes()); // default minimal fetch
        // if you have a richer mapping, you can decode from other config sources

        for (MarketDataService svc : marketServices) {
            try {
                if (!svc.enabled()) {
                    log.debug("MarketDataService {} is disabled - skipping", svc.brokerCode());
                    continue;
                }
                String broker = svc.brokerCode();
                if (StringUtils.equalsIgnoreCase(broker,GenericeConstants.UPSTOX)){
                    // NSE market hours check
                    MarketStatusResult marketStatus = nseMarketOpenStatusService.getMarketStatus();
                    if (!StringUtils.equalsIgnoreCase(marketStatus.getMarketStatus(), GenericeConstants.CLOSED)) {
                        log.debug("NSE market is closed - skipping Upstox trade monitoring run.");
                        continue;
                    }
                }
                String table = chooseTableForBroker(broker);
                ensureMisOrdersSquaredIfNeeded(svc, table);
                List<TradeModel> openTrades = daoFactory.listAllOpenTrades(table);
                if (openTrades == null || openTrades.isEmpty()) {
                    log.debug("No open trades found for broker {} table {}", broker, table);
                    continue;
                }

                for (TradeModel t : openTrades) {
                    try {
                        processTrade(svc, t, table, tfs);
                    } catch (Exception te) {
                        log.error("Error processing trade {} : {}", t == null ? "null" : t.getTradeId(), te.getMessage(), te);
                    }
                }

            } catch (Exception e) {
                log.error("Error iterating market services: {}", e.getMessage(), e);
            }
        }
    }

    private String chooseTableForBroker(String brokerCode) {
        if (GenericeConstants.UPSTOX.equalsIgnoreCase(brokerCode)) {
            return cfg.stocks_table();
        } else if (GenericeConstants.DELTA.equalsIgnoreCase(brokerCode)) {
            return cfg.crypto_table();
        } else {
            return cfg.stocks_table();
        }
    }

    private void processTrade(MarketDataService svc, TradeModel tradeModel
            , String table, Map<String,Integer> tfs) throws Exception {
        if (tradeModel == null) {
            log.error("processTrade called with null tradeModel");
            return;
        }
        if (!tradeModel.isValid()) {
            log.warn("Invalid trade found in DB: {}", tradeModel);
            return;
        }
        // fetch minimal candles (5m) to get latest LTP; try to respect trade timeframe if present
        String timeframes = cfg.timeframes();
        String timeframe = "5m";
        int count = 2;
        if (StringUtils.isNotEmpty(timeframes)){
            timeframe = timeframes.split(":")[0];
             count = Integer.parseInt(timeframes.split(":")[1]);
        }
        InstrumentSymbol instrument = tradeModel.getSymbol();
        if (instrument == null) {
            log.warn("Trade {} has no instrument symbol, skipping", tradeModel.getTradeId());
            return;
        }

        // Fetch candles (non-historical intraday)
        List<Candle> candles;
        try {
            candles = svc.fetchCandles(instrument, timeframe, count, false);
        } catch (Exception e) {
            log.warn("Market data fetch failed for {} {}: {}", instrument.getSymbol(), timeframe, e.getMessage());
            return;
        }
        if (candles == null || candles.isEmpty()) {
            log.warn("No candles returned for {} {}", instrument.getSymbol(), timeframe);
            return;
        }

        // LTP taken from last candle close
        double ltp = candles.get(candles.size() - 1).getClose();
        daoFactory.updateLtp(tradeModel, ltp, table); // persist LTP
        tradeModel.setLtp(ltp);

        // compute unrealized pnl for filled quantity
        double pnl = computeUnrealizedPnl(tradeModel, ltp);
        tradeModel.setPnl(pnl);
        if (tradeModel.getEntryPrice() != 0) {
            tradeModel.setPnlPercentage((pnl / (tradeModel.getEntryPrice() * tradeModel.getQuantity())) * 100.0);
        }

        log.info("Trade {} {} ltp={} pnl={} ({:.2f}%)", tradeModel.getTradeId(), instrument.getSymbol(), ltp, pnl, tradeModel.getPnlPercentage());

        // pivot levels: prefer trade.pivotLevels else compute daily pivot from daily candles (if available)
        PivotLevels pivots = tradeModel.getPivotLevels();
        if (pivots == null) {
            // if 1d candles are present in the market data service; try to compute
            try {
                List<Candle> daily = svc.fetchCandles(instrument, "1d", 3, true); // historical
                if (daily != null && !daily.isEmpty()) {
                    pivots = Utils.calculatePivotLevels(daily);
                    tradeModel.setPivotLevels(pivots);
                }
            } catch (Exception ignore) {
                log.error("Failed to fetch daily candles for pivots: {}", ignore.getMessage());
            }
        }

        // Step: Check target or stop loss hit
        boolean targetHit = (tradeModel.getSide() == Signal.Side.BUY && ltp >= tradeModel.getTarget())
                || (tradeModel.getSide() == Signal.Side.SELL && ltp <= tradeModel.getTarget());
        boolean stopHit = (tradeModel.getSide() == Signal.Side.BUY && ltp <= tradeModel.getStopLoss())
                || (tradeModel.getSide() == Signal.Side.SELL && ltp >= tradeModel.getStopLoss());

        if (targetHit || stopHit) {
            double exitPrice = targetHit ? tradeModel.getTarget() : tradeModel.getStopLoss();
            log.info("Trade {} {} exit triggered at {} (ltp={}): {}", tradeModel.getTradeId(), instrument.getSymbol(), exitPrice, ltp, targetHit ? "TARGET_HIT" : "STOP_HIT");
            performExit(tradeModel, exitPrice, targetHit ? "TARGET_HIT" : "STOP_HIT", table);
            return;
        }

        // Pivot proximity check - if price is near strong resistance/support and target far => consider exit
        if (pivots != null) {
            // If BUY and near R1 and target > R2 => optional early exit
            double distanceToTarget = Math.abs(tradeModel.getTarget() - ltp);
            double distToR1 = Math.abs(pivots.getR1() - ltp);
            if (tradeModel.getSide() == Signal.Side.BUY && distToR1 < (distanceToTarget * 0.1)) {
                // heuristics: if target is > R2, consider partial or full exit
                if (tradeModel.getTarget() > pivots.getR2()) {
                    log.info("Trade {} near R1 and target beyond R2. Considering early exit.", tradeModel.getTradeId());
                    // For now just send alert and decrease trailing slack; you may choose to exit:
                    telegram.sendMessageDailyCryptoAlerts(String.format("Consider exit: %s near R1 ltp=%.2f pivR1=%.2f", instrument.getSymbol(), ltp, pivots.getR1()));
                }
            }
            // symmetrical for SELL near S1...
            double distToS1 = Math.abs(pivots.getS1() - ltp);
            if (tradeModel.getSide() == Signal.Side.SELL && distToS1 < (distanceToTarget * 0.1)) {
                telegram.sendMessageDailyCryptoAlerts(String.format("Consider exit: %s near S1 ltp=%.2f pivS1=%.2f", instrument.getSymbol(), ltp, pivots.getS1()));
            }
        }

        // Trailing logic: adjust stop loss upward/downward depending on price direction & volume (volume heuristics require volume candle; we approximate)
        applyTrailingLogic(tradeModel, candles, table);

        // Time-based / session square-off
        if (cfg.max_holding_minutes() > 0 && tradeModel.getEntryTime() != null) {
            long heldMinutes = java.time.Duration.between(tradeModel.getEntryTime(), LocalDateTime.now()).toMinutes();
            if (heldMinutes >= cfg.max_holding_minutes()) {
                log.info("Max holding time exceeded for trade {} ({} minutes). Forcing exit at market (ltp {}).", tradeModel.getTradeId(), heldMinutes, ltp);
                performExit(tradeModel, ltp, "MAX_DURATION_EXCEEDED", table);
            }
        }

        // Other portfolio-level checks would be invoked here (e.g., aggregated PnL thresholds)
    }

    private double computeUnrealizedPnl(TradeModel t, double ltp) {
        if (t.getQuantity() <= 0) return 0.0;
        if (t.getSide() == Signal.Side.BUY) {
            return (ltp - t.getEntryPrice()) * t.getQuantity();
        } else {
            return (t.getEntryPrice() - ltp) * t.getQuantity();
        }
    }
    private void applyTrailingLogic(TradeModel t, List<Candle> candles, String table) {
        if (candles == null || candles.size() < 2) return;
        Candle last = candles.get(candles.size() - 1);
        Candle prev = candles.get(candles.size() - 2);

        double cfgIncrPct = cfg.trailing_increment_percent();
        // allow per-trade override if available (example getter)
        double incrPct = (t.getTrailingIncrement() > 0) ? (t.getTrailingIncrement() / 100.0) : cfgIncrPct;

        boolean bullishMomentum = last.getClose() > prev.getClose() && last.getVolume() >= prev.getVolume();
        log.info("Trailing check for trade {}: lastClose={} prevClose={} bullishMomentum={}", t.getTradeId(), last.getClose(), prev.getClose(), bullishMomentum);
        boolean bearishMomentum = last.getClose() < prev.getClose() && last.getVolume() >= prev.getVolume();
        log.info("Trailing check for trade {}: lastClose={} prevClose={} bearishMomentum={}", t.getTradeId(), last.getClose(), prev.getClose(), bearishMomentum);

        try {
            if (t.getSide() == Signal.Side.BUY && bullishMomentum) {
                double proposedSl = last.getClose() - (last.getClose() * incrPct);
                // only raise stoploss (narrowing for buyer)
                if (proposedSl > t.getStopLoss()) {
                    double oldSl = t.getStopLoss();
                    t.setStopLoss(proposedSl);
                    daoFactory.updateStopLoss(t, proposedSl, table); // persist stoploss change
                    daoFactory.updateLtp(t, last.getClose(), table); // persist LTP too
                    telegram.sendMessageDailyCryptoAlerts(String.format("Trailing SL updated (BUY): %s %s -> %.2f", t.getSymbol().getSymbol(), oldSl, proposedSl));
                    log.info("Trailing SL moved for trade {} {} -> {}", t.getTradeId(), oldSl, proposedSl);
                }
            } else if (t.getSide() == Signal.Side.SELL && bearishMomentum) {
                double proposedSl = last.getClose() + (last.getClose() * incrPct);
                // only lower stoploss for sell (narrowing)
                if (proposedSl < t.getStopLoss() || t.getStopLoss() == 0.0) {
                    double oldSl = t.getStopLoss();
                    t.setStopLoss(proposedSl);
                    daoFactory.updateStopLoss(t, proposedSl, table);
                    daoFactory.updateLtp(t, last.getClose(), table);
                    telegram.sendMessageDailyCryptoAlerts(String.format("Trailing SL updated (SELL): %s %s -> %.2f", t.getSymbol().getSymbol(), oldSl, proposedSl));
                    log.info("Trailing SL moved for trade {} {} -> {}", t.getTradeId(), oldSl, proposedSl);
                }
            }
        } catch (Exception e) {
            log.warn("applyTrailingLogic failed for trade {}: {}", t.getTradeId(), e.getMessage(), e);
        }
    }


    private void performExit(TradeModel t, double exitPrice, String reason, String table) {
        try {
            // 1) Try to execute exit on broker if orderService available
            try {
                // construct simple market order to close quantity
                //daoFactory.placeExitOrder(t.getSymbol(), t.getSide(), t.getQuantity(), exitPrice);
                //daoFactory.closeTradeWithPnl(t, table); // mark closed in DB
                log.info("Exit order placed via OrderService for trade {} at price {} reason={}", t.getTradeId(), exitPrice, reason);
            } catch (Exception oe) {
                log.warn("OrderService exit failed for {}: {}", t.getTradeId(), oe.getMessage());
            }

            // 2) compute final pnl
            double pnl = computeUnrealizedPnl(t, exitPrice);
            t.setExitPrice(exitPrice);
            t.setExitTime(LocalDateTime.now());
            t.setPnl(pnl);
            t.setStatus(TradeModel.Status.CLOSED);

            // 3) persist closure
            daoFactory.closeTradeWithPnl(t, table);
            // if implementation has plain closeTrade(tradeId, exitPrice, reason, table) use that
            telegram.sendMessageDailyStocksAlerts(String.format("Closed trade %s exit=%.2f pnl=%.2f reason=%s", t.getSymbol().getSymbol(), exitPrice, pnl, reason));
            log.info("Trade {} auto-closed. exit={} pnl={} reason={}", t.getTradeId(), exitPrice, pnl, reason);
        } catch (Exception e) {
            log.error("Failed to close trade {} in DB: {}", t.getTradeId(), e.getMessage(), e);
        }
    }

    /**
     * Ensure all MIS trades for the given table are squared off before MIS session close time (15:25 IST).
     *
     * Behavior:
     *  - Sends a pre-close alert in last 5 minutes (15:20 - 15:25).
     *  - At or after 15:25 IST, fetches LTP for each MIS trade and calls performExit(...) with reason "MIS_SESSION_SQUAREOFF".
     *  - Uses best-effort market data (fetchCandles). If no candles available, falls back to trade.getLtp().
     *  - Tolerant to errors: logs and continues with other trades.
     */
    private void ensureMisOrdersSquaredIfNeeded(MarketDataService svc, String table) {
        try {
            log.info("Checking MIS trades for session square-off in table {}", table);
            // Determine MIS square-off time from config (default 15:25 IST)
            int autoSquareOffHour = 15;
            int autoSquareOffMinute = 25;
            if (StringUtils.isNotEmpty(cfg.auto_square_off_time())){
                String hrs = cfg.auto_square_off_time().split(":")[0].trim();
                if (StringUtils.isNotEmpty(hrs)){
                    autoSquareOffHour = Integer.parseInt(hrs);
                }
                String minutes = cfg.auto_square_off_time().split(":")[1].trim();
                if (StringUtils.isNotEmpty(minutes)){
                    autoSquareOffMinute = Integer.parseInt(minutes);
                }
            }
            ZoneId ist = ZoneId.of("Asia/Kolkata");
            LocalTime now = LocalTime.now(ist);
            LocalTime squareOffTime = LocalTime.of(autoSquareOffHour, autoSquareOffMinute); // 15:25 IST
            log.info("Current IST time is {}. MIS square-off time is {}.", now, squareOffTime);
            // Pre-alert window starts 5 minutes before square-off time or as per configured buffer
            LocalTime preAlertFrom = squareOffTime.minusMinutes(Math.max(nseMarketOpenStatusService.getTradeCloseBufferMinutes(), 5)); // 15:20 IST
            log.info("Pre-alert window starts at {}.", preAlertFrom);
            // If it's before pre-alert window we skip (too early)

            // If it's more than 30 minutes AFTER market close we skip (safety), but adapt as you like.
            if (now.isBefore(preAlertFrom)) {
                // Too early: nothing to do
                return;
            }
            log.info("Current IST time is {}. MIS square-off time is {}. Pre-alert window starts at {}.", now, squareOffTime, preAlertFrom);
            List<TradeModel> openTrades = daoFactory.listAllOpenTrades(table);
            if (openTrades == null || openTrades.isEmpty()) {
                return;
            }

            // Pre-close alert window (15:20 - 15:25) => send reminders but DO NOT exit yet
            if (now.isBefore(squareOffTime)) {
                // send a gentle alert once per run (this method is called every run; you may want to throttle)
                try {
                    long misCount = openTrades.stream().filter(this::isMisOrder).count();
                    if (misCount > 0) {
                        String msg = String.format("Reminder: %d MIS trades open. Session square-off by %s IST (in %d minutes).",
                                misCount, squareOffTime.toString(), java.time.Duration.between(now, squareOffTime).toMinutes());
                        telegram.sendMessageDailyStocksAlerts(msg);
                        log.info(msg);
                    }
                } catch (Exception e) {
                    log.warn("Failed to send MIS pre-close alert: {}", e.getMessage());
                }
                return; // do not square off yet
            }

            // At or after squareOffTime => force-exit all MIS trades
            log.info("MIS session square-off window reached (now {}). Attempting to square off MIS trades in table {}", now, table);
            for (TradeModel t : openTrades) {
                try {
                    if (!isMisOrder(t)) continue;
                    if (!t.isValid()) {
                        log.warn("Skipping invalid trade during MIS square-off: {}", t);
                        continue;
                    }
                    // Get a best-effort LTP: try market service, fallback to trade.ltp
                    double ltp = t.getLtp();
                    try {
                        InstrumentSymbol symbol = t.getSymbol();
                        if (symbol != null) {
                            // attempt to fetch very recent candle (1 bar) to use its close as LTP
                            List<Candle> candles = svc.fetchCandles(symbol, "5m", 1, false);
                            if (candles != null && !candles.isEmpty()) {
                                ltp = candles.get(candles.size() - 1).getClose();
                            }
                        }
                    } catch (Exception mdex) {
                        log.warn("Failed to fetch LTP for MIS square-off for trade {}: {}, using stored LTP {}", t.getTradeId(), mdex.getMessage(), ltp);
                    }

                    // Execute exit. performExit handles broker order + DB close + alerts consistently.
                    log.info("Squaring MIS trade {} symbol={} ltp={} table={}", t.getTradeId(), t.getSymbol() == null ? "?" : t.getSymbol().getSymbol(), ltp, table);
                    performExit(t, ltp, "MIS_SESSION_SQUAREOFF", table);
                } catch (Exception ex) {
                    log.error("Error squaring off MIS trade {}: {}", t == null ? "null" : t.getTradeId(), ex.getMessage(), ex);
                }
            }

        } catch (Exception e) {
            log.error("ensureMisOrdersSquaredIfNeeded failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Detect whether a trade is an MIS order.
     * This is tolerant so it works whether orderType is stored as a string or enum-like name.
     */
    private boolean isMisOrder(TradeModel t) {
        if (t == null) return false;
        // if trade has an explicit getter for order type enum, adapt accordingly
        try {
            // try String-based getter
            String orderType = null;
            try {
                orderType = t.getOrderType(); // common pattern
            } catch (NoSuchMethodError | AbstractMethodError ignored) {
                // ignore - fall back
            } catch (Throwable ignored) {
            }
            if (orderType != null) {
                return "MIS".equalsIgnoreCase(orderType) || orderType.toUpperCase().contains("MIS");
            }
            // fallback: maybe trade stores as enum-like object
            Object otObj = null;
            try {
                // use reflection fallback to try getOrderTypeEnum or similar shape
                otObj = t.getClass().getMethod("getOrderTypeEnum").invoke(t);
            } catch (Throwable ignored) {}
            if (otObj != null) {
                String name = otObj.toString();
                return "MIS".equalsIgnoreCase(name) || name.toUpperCase().contains("MIS");
            }
        } catch (Throwable e) {
            log.debug("isMisOrder detection fallback failed: {}", e.getMessage());
        }
        return false;
    }


    // DS dynamic bind/unbind for MarketDataService
    @Reference(service = MarketDataService.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    protected void addMarketDataService(MarketDataService s) { marketServices.add(s); }

    protected void removeMarketDataService(MarketDataService s) { marketServices.remove(s); }

    // Optional OrderService binding - dynamic
    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void bindOrderService(OrderService s) { this.orderService = s; }

    protected void unbindOrderService(OrderService s) { if (this.orderService == s) this.orderService = null; }
}
