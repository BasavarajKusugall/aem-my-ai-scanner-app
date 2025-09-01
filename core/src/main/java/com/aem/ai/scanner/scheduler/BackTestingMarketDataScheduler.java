package com.aem.ai.scanner.scheduler;

import com.GenericeConstants;
import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.dao.WatchlistDao;
import com.aem.ai.scanner.factory.StrategyFactoryService;
import com.aem.ai.scanner.model.*;
import com.aem.ai.scanner.utils.Ansi;
import com.aem.ai.scanner.utils.RollingBarSeries;
import com.aem.ai.scanner.utils.Timeframes;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.*;
import org.ta4j.core.Bar;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.pnl.ReturnCriterion;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;

import static com.aem.ai.scanner.model.TradeModel.IST_ZONE;

@Designate(ocd = BackTestingMarketDataScheduler.Config.class)
@Component(service = Runnable.class, immediate = true)
public class BackTestingMarketDataScheduler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BackTestingMarketDataScheduler.class);

    @ObjectClassDefinition(name="BSK Backtest Market Data Fetch Scheduler")
    public @interface Config {
        @AttributeDefinition(name="Enable")
        boolean enable() default true;

        @AttributeDefinition(name="Cron (Quartz)")
        String scheduler_expression() default "0 0/5 * * * ?";

        @AttributeDefinition(name="Timeframes spec e.g. 5m:120,15m:64,1h:48,1d:365")
        String timeframes() default "5m:120,15m:64,1h:48,1d:365";

        @AttributeDefinition(name="Parallelism per broker")
        int parallelism() default 4;

        @AttributeDefinition(name="Retries")
        int retries() default 3;

        @AttributeDefinition(name="Backoff Ms")
        long backoff_ms() default 500;
    }

    private Config cfg;

    @Reference(cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    private volatile List<MarketDataService> services = new CopyOnWriteArrayList<>();

    @Reference
    private WatchlistDao watchlistDao;

    private ExecutorService schedulerPool;

    @Reference
    private DAOFactory daoFactory;

    @Reference
    private StrategyFactoryService strategyFactoryService;


    @Activate
    @Modified
    protected void activate(Config cfg) {
        this.cfg = cfg;
        restartExecutor(cfg.parallelism());
        log.info(Ansi.GREEN + "✅ MarketDataScheduler activated. cron={} enable={} timeframes='{}'" + Ansi.RESET,
                cfg.scheduler_expression(), cfg.enable(), cfg.timeframes());
    }

    @Deactivate
    protected void deactivate() {
        try {
            shutdownExecutor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info(Ansi.YELLOW + "🛑 MarketDataScheduler deactivated." + Ansi.RESET);
    }

    @Override
    public void run() {
        if (!cfg.enable()) {
            log.debug(Ansi.YELLOW + "⚠️ Scheduler disabled." + Ansi.RESET);
            return;
        }
        Map<String,Integer> tfs = Timeframes.parse(cfg.timeframes());
        if (tfs.isEmpty()) {
            log.warn(Ansi.YELLOW + "⚠️ No timeframes configured." + Ansi.RESET);
            return;
        }

        for (MarketDataService svc : services) {
            String code = svc.brokerCode();
            List<InstrumentSymbol> symbols = List.of();
            if (GenericeConstants.UPSTOX.equalsIgnoreCase(code)) symbols = watchlistDao.symbolsForUpstox();
            else if (GenericeConstants.DELTA.equalsIgnoreCase(code)) symbols = watchlistDao.symbolsForDelta();
            else {
                log.info("Skipping unknown broker {}", code);
                continue;
            }

            if (symbols.isEmpty()) {
                log.warn(Ansi.YELLOW + "⚠️ No symbols for broker {}" + Ansi.RESET, code);
                continue;
            }

            for (InstrumentSymbol symbol : symbols) {
                for (Map.Entry<String,Integer> tf : tfs.entrySet()) {
                    final String timeframe = tf.getKey();
                    final int count = tf.getValue();
                    final boolean historical = StringUtils.equals(code, GenericeConstants.UPSTOX) && Timeframes.isHistoricalBucket(timeframe);

                    schedulerPool.submit(() -> {
                        Instant t0 = Instant.now();
                        try {
                            List<Candle> candles = svc.fetchCandles(symbol, timeframe, count, historical);
                            Duration took = Duration.between(t0, Instant.now());
                            log.info(Ansi.GREEN + "✅ {} {} {} candles={} took={}ms" + Ansi.RESET,
                                    svc.brokerCode(), symbol, timeframe, candles.size(), took.toMillis());
                            if (!candles.isEmpty()){
                                evaluateStrategies(svc.brokerCode(),candles, symbol, timeframe);
                            }
                        } catch (Exception e) {
                            log.error(Ansi.RED + "❌ {} {} {} failed: {}" + Ansi.RESET,
                                    svc.brokerCode(), symbol, timeframe, e.getMessage(), e);
                        } finally {
                            svc.interCallDelay();
                        }
                    });
                }
            }
        }
    }
    /** Map timeframe string to Duration */
    public static Duration mapTimeframe(String tf) {
        switch (tf) {
            case "1m": return Duration.ofMinutes(1);
            case "3m": return Duration.ofMinutes(3);
            case "5m": return Duration.ofMinutes(5);
            case "15m": return Duration.ofMinutes(15);
            case "30m": return Duration.ofMinutes(30);
            case "1h": return Duration.ofHours(1);
            case "4h": return Duration.ofHours(4);
            case "1d": return Duration.ofDays(1);
            default: throw new IllegalArgumentException("Unsupported timeframe: " + tf);
        }
    }
    /** Safe Num creation */
    private static Num num(double v) { return DecimalNum.valueOf(Double.toString(v)); }
    private static Bar toBar(Candle c, String timeframe) {
        ZonedDateTime endTime = c.getTime()
                .atZone(ZoneId.systemDefault()).withZoneSameInstant(IST_ZONE);


        Num open   = num(c.getOpen());
        Num high   = num(c.getHigh());
        Num low    = num(c.getLow());
        Num close  = num(c.getClose());
        Num volume = num(c.getVolume());
        Num amount = volume.multipliedBy(close);
        long trades = 0L;

        return new BaseBar(
                mapTimeframe(timeframe),
                endTime,
                open,
                high,
                low,
                close,
                volume,
                amount,
                trades
        );
    }


    private void evaluateStrategies(String brokerCode,
                                    List<Candle> candles,
                                    InstrumentSymbol symbol,
                                    String timeframe) throws Exception {
        log.info("Evaluating strategies for {} {} with {} candles",
                symbol, timeframe, candles.size());

        // === Build BarSeries ===
        RollingBarSeries rolling = new RollingBarSeries(symbol.getSymbol() + "_" + timeframe, candles.size());
        for (Candle c : candles) {
            Bar bar = toBar(c, timeframe);
            rolling.addBar(bar);
        }
        BarSeries series = rolling.series();

        // === Load strategies ===
        List<StrategyConfig> strategyConfigs = daoFactory.loadActiveStrategies();
        if (strategyConfigs.isEmpty()) {
            log.warn(Ansi.YELLOW + "⚠️ No active strategies found." + Ansi.RESET);
            return;
        }

        // === Metrics ===
        Map<String, Double> winRateMap = new HashMap<>();
        Map<String, Double> pnlMap     = new HashMap<>();
        Map<String, Double> drawdownMap= new HashMap<>();

        List<StrategyResult> results = new ArrayList<>();

        for (StrategyConfig strategyTemplate : strategyConfigs) {
            List<StrategyConfig> variations = generateVariations(strategyTemplate);

            for (StrategyConfig dynamicStrategy : variations) {
                try {
                    Strategy strategy = strategyFactoryService.buildStrategy(dynamicStrategy, series);
                    BarSeriesManager mgr = new BarSeriesManager(series);
                    TradingRecord record = mgr.run(strategy);

                    double winRate = computeWinRate(series, record);
                    double pnl     = new ReturnCriterion().calculate(series, record).doubleValue();
                    double dd      = new MaximumDrawdownCriterion().calculate(series, record).doubleValue();

                    boolean add = results.add(new StrategyResult(dynamicStrategy, winRate, pnl, dd));

                    log.info("📊 Strategy {} -> WinRate={} PnL={} MaxDD={}",
                            dynamicStrategy.getName(), winRate, pnl, dd);
                } catch (Exception e) {
                    log.error("❌ Error evaluating strategy {}: {}", dynamicStrategy.getName(), e.getMessage(), e);
                }
            }
        }

// === Pick top 3 results ===
        List<StrategyResult> topResults = results.stream()
                .sorted(Comparator
                        .comparingDouble(StrategyResult::getWinRate).reversed()
                        .thenComparingDouble(StrategyResult::getDrawdown)
                        .thenComparingDouble(StrategyResult::getPnl).reversed())
                .limit(3)
                .toList();

// === Persist winners (with metrics) ===
        String watchListTable = watchlistDao.upstoxTable();
        if (StringUtils.equalsIgnoreCase(brokerCode, GenericeConstants.DELTA)) {
            watchListTable = watchlistDao.deltaTable();
            log.info("Using Delta watchlist table {}", watchListTable);
        }

        daoFactory.persistBestStrategies(watchListTable, symbol.getSymbol(), topResults);
    }

    // Manage executor safely
    private synchronized void restartExecutor(int parallelism) {
        try {
            shutdownExecutor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        schedulerPool = Executors.newFixedThreadPool(Math.max(1, parallelism));
    }

    private synchronized void shutdownExecutor() throws InterruptedException {
        if (schedulerPool != null) {
            schedulerPool.shutdownNow();
            if (!schedulerPool.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("⚠️ Executor did not terminate cleanly.");
            }
            schedulerPool = null;
        }
    }
    private double computeWinRate(BarSeries series, TradingRecord record) {
        if (record == null || record.getTrades().isEmpty()) return 0.0;

        int wins = 0;
        int total = record.getTrades().size();

        for (Trade trade : record.getTrades()) {
            // For a Trade in TA4J 0.17, the "index" refers to the entry bar index
            int entryIndex = trade.getIndex();
            Num entryPrice = trade.getPricePerAsset(series);

            // Try to get the exit bar index: it's entryIndex + 1 or later
            // but in TA4J 0.17, Trade itself doesn’t directly expose exit
            // -> You need to evaluate by comparing entry with the next bar close
            // or use record.getCurrentTrade() for ongoing trades.
            int exitIndex = Math.min(series.getEndIndex(), entryIndex + 1);
            Num exitPrice = series.getBar(exitIndex).getClosePrice();

            boolean profitable;
            if (trade.isBuy()) {
                profitable = exitPrice.isGreaterThan(entryPrice);
            } else { // SELL
                profitable = exitPrice.isLessThan(entryPrice);
            }

            if (profitable) {
                wins++;
            }
        }

        return (double) wins / total;
    }
    private List<StrategyConfig> generateVariations(StrategyConfig cfg) {
        List<StrategyConfig> list = new ArrayList<>();

        // Loop over each RuleConfig
        for (StrategyConfig.RuleConfig rule : cfg.getRules()) {
            for (Condition cond : rule.getConditions()) {
                String ind = cond.indicator.toUpperCase();
                switch (ind) {
                    case "EMA":
                    case "EMA_CROSS": {
                        int[] fastRange = {5, 9, 12};
                        int[] slowRange = {21, 26, 50};
                        for (int f : fastRange) {
                            for (int s : slowRange) {
                                if (f >= s) continue;
                                StrategyConfig clone = cfg.copy();
                                clone.setName(cfg.getName() + "_EMA_" + f + "x" + s);
                                Condition c = clone.getRules().get(0).getConditions().get(0);
                                c.fast = f;
                                c.slow = s;
                                list.add(clone);
                            }
                        }
                        break;
                    }
                    case "RSI": {
                        int[] periods = {7, 14, 21};
                        for (int p : periods) {
                            StrategyConfig clone = cfg.copy();
                            clone.setName(cfg.getName() + "_RSI_" + p);
                            Condition c = clone.getRules().get(0).getConditions().get(0);
                            c.period = p;
                            list.add(clone);
                        }
                        break;
                    }
                    case "MACD": {
                        int[] fastVals = {10, 12, 15};
                        int[] slowVals = {20, 26, 30};
                        int[] sigVals  = {7, 9, 12};
                        for (int f : fastVals) {
                            for (int s : slowVals) {
                                for (int sig : sigVals) {
                                    if (f >= s) continue;
                                    StrategyConfig clone = cfg.copy();
                                    clone.setName(cfg.getName() + "_MACD_" + f + "-" + s + "-" + sig);
                                    Condition c = clone.getRules().get(0).getConditions().get(0);
                                    c.fast = f;
                                    c.slow = s;
                                    c.signal = sig;
                                    list.add(clone);
                                }
                            }
                        }
                        break;
                    }
                    default:
                        list.add(cfg.copy()); // no variations
                }
            }
        }
        return list;
    }



    // DS dynamic bind/unbind
    protected void addMarketDataService(MarketDataService s) { services.add(s); }
    protected void removeMarketDataService(MarketDataService s) { services.remove(s); }
}
