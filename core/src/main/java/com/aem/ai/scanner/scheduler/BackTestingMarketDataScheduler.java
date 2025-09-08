package com.aem.ai.scanner.scheduler;

import com.GenericeConstants;
import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.dao.WatchlistDao;
import com.aem.ai.scanner.factory.StrategyFactoryService;
import com.aem.ai.scanner.model.*;
import com.aem.ai.scanner.utils.RollingBarSeries;
import com.aem.ai.scanner.utils.Timeframes;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.aem.ai.scanner.model.TradeModel.IST_ZONE;

@Designate(ocd = BackTestingMarketDataScheduler.Config.class)
@Component(
        service = Runnable.class,
        immediate = true

)
public class BackTestingMarketDataScheduler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BackTestingMarketDataScheduler.class);

    @ObjectClassDefinition(name="BSK Backtest Market Data Fetch Scheduler")
    public @interface Config {
        @AttributeDefinition(name="Enable")
        boolean enable() default true;

        @AttributeDefinition(name="Cron expression")
        String scheduler_expression() default "0 0/5 * * * ?";
        @AttributeDefinition(name = "Allow concurrent execution")
        boolean scheduler_concurrent() default false;

        @AttributeDefinition(name = "Scheduler name")
        String scheduler_name() default "BackTestingMarketDataScheduler";
        @AttributeDefinition(name="Timeframes e.g. 5m:120,15m:64,1h:48,1d:365")
        String timeframes() default "5m:120,15m:64,1h:48,1d:365";
    }

    private volatile Config cfg;

    @Reference
    private WatchlistDao watchlistDao;

    @Reference
    private DAOFactory daoFactory;



    @Reference
    private StrategyFactoryService strategyFactoryService;

    private final List<MarketDataService> services = new CopyOnWriteArrayList<>();

    @Activate
    @Modified
    protected void activate(Config cfg) {
        this.cfg = cfg;
        log.info("✅ BackTestingMarketDataScheduler activated. cron={} enable={} timeframes={}",
                cfg.scheduler_expression(), cfg.enable(), cfg.timeframes());
    }

    @Deactivate
    protected void deactivate() {
        log.info("🛑 BackTestingMarketDataScheduler deactivated.");
    }

    @Override
    public void run() {
        if (!cfg.enable()) {
            log.debug("⚠️ Scheduler disabled.");
            return;
        }

        Map<String,Integer> tfs = Timeframes.parse(cfg.timeframes());
        if (tfs.isEmpty()) {
            log.warn("⚠️ No timeframes configured.");
            return;
        }

        for (MarketDataService svc : services) {
            String code = svc.brokerCode();
            boolean brokerEnabled = svc.enabled();
            if (!brokerEnabled){
                log.warn("⚠️ Skipping disabled broker {}", code);
                continue;
            }
            List<InstrumentSymbol> symbols;
            if (GenericeConstants.UPSTOX.equalsIgnoreCase(code)) {
                symbols = watchlistDao.symbolsForUpstox();
            } else if (GenericeConstants.DELTA.equalsIgnoreCase(code)) {
                symbols = watchlistDao.symbolsForDelta();
            } else {
                log.info("Skipping unknown broker {}", code);
                continue;
            }

            if (symbols.isEmpty()) {
                log.warn("⚠️ No symbols for broker {}", code);
                continue;
            }

            for (InstrumentSymbol symbol : symbols) {
                for (Map.Entry<String,Integer> tf : tfs.entrySet()) {
                    fetchAndEvaluate(svc, symbol, tf.getKey(), tf.getValue());
                }
            }
        }
    }

    private void fetchAndEvaluate(MarketDataService svc, InstrumentSymbol symbol,
                                  String timeframe, int count) {
        try {
            List<Candle> candles = svc.fetchCandles(symbol, timeframe, count, Timeframes.isHistoricalBucket(timeframe));
            if (candles.isEmpty()) {
                log.warn("⚠️ No candles for {} {}", symbol, timeframe);
                return;
            }

            evaluateStrategies(svc.brokerCode(), candles, symbol, timeframe);
            log.info("✅ Completed backtest {} {} candles={}", symbol, timeframe, candles.size());

        } catch (Exception e) {
            log.error("❌ {} {} {} failed: {}", svc.brokerCode(), symbol, timeframe, e.getMessage(), e);
        } finally {
            svc.interCallDelay(); // still honor service pacing
        }
    }

    private void evaluateStrategies(String brokerCode,
                                    List<Candle> candles,
                                    InstrumentSymbol symbol,
                                    String timeframe) throws Exception {
        log.info("Evaluating strategies for {} {} with {} candles",
                symbol, timeframe, candles.size());

        RollingBarSeries rolling = new RollingBarSeries(symbol.getSymbol() + "_" + timeframe, candles.size());
        for (Candle c : candles) {
            rolling.addBar(toBar(c, timeframe));
        }
        BarSeries series = rolling.series();

        List<StrategyConfig> strategyConfigs = daoFactory.loadActiveStrategies();
        if (strategyConfigs.isEmpty()) {
            log.warn("⚠️ No active strategies found.");
            return;
        }

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

                    results.add(new StrategyResult(dynamicStrategy, winRate, pnl, dd));
                    log.info("📊 Strategy {} -> WinRate={} PnL={} MaxDD={}",
                            dynamicStrategy.getName(), winRate, pnl, dd);
                } catch (Exception e) {
                    log.error("❌ Error evaluating strategy {}: {}", dynamicStrategy.getName(), e.getMessage(), e);
                }
            }
        }

        List<StrategyResult> topResults = results.stream()
                .sorted(Comparator.comparingDouble(StrategyResult::getPnl).reversed())
                .limit(3)
                .toList();

        String watchListTable = GenericeConstants.DELTA.equalsIgnoreCase(brokerCode)
                ? watchlistDao.deltaTable()
                : watchlistDao.upstoxTable();

        daoFactory.persistBestStrategies(watchListTable, symbol.getSymbol(), topResults);
    }

    /** Map timeframe string to Duration */
    private static Duration mapTimeframe(String tf) {
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

    private static Num num(double v) { return DecimalNum.valueOf(Double.toString(v)); }

    private static Bar toBar(Candle c, String timeframe) {
        ZonedDateTime endTime = c.getTime().atZone(ZoneId.systemDefault()).withZoneSameInstant(IST_ZONE);
        Num open   = num(c.getOpen());
        Num high   = num(c.getHigh());
        Num low    = num(c.getLow());
        Num close  = num(c.getClose());
        Num volume = num(c.getVolume());
        Num amount = volume.multipliedBy(close);
        return new BaseBar(mapTimeframe(timeframe), endTime, open, high, low, close, volume, amount, 0L);
    }

    private double computeWinRate(BarSeries series, TradingRecord record) {
        if (record == null || record.getTrades().isEmpty()) return 0.0;
        int wins = 0;
        for (Trade trade : record.getTrades()) {
            int entryIndex = trade.getIndex();
            Num entryPrice = trade.getPricePerAsset(series);
            int exitIndex = Math.min(series.getEndIndex(), entryIndex + 1);
            Num exitPrice = series.getBar(exitIndex).getClosePrice();

            boolean profitable = trade.isBuy()
                    ? exitPrice.isGreaterThan(entryPrice)
                    : exitPrice.isLessThan(entryPrice);
            if (profitable) wins++;
        }
        return (double) wins / record.getTrades().size();
    }

    private List<StrategyConfig> generateVariations(StrategyConfig cfg) {
        List<StrategyConfig> list = new ArrayList<>();
        for (StrategyConfig.RuleConfig rule : cfg.getRules()) {
            for (Condition cond : rule.getConditions()) {
                switch (cond.indicator.toUpperCase()) {
                    case "EMA":
                        for (int f : new int[]{5, 9, 12}) {
                            for (int s : new int[]{21, 26, 50}) {
                                if (f >= s) continue;
                                StrategyConfig clone = cfg.copy();
                                clone.setName(cfg.getName() + "_EMA_" + f + "x" + s);
                                clone.getRules().get(0).getConditions().get(0).fast = f;
                                clone.getRules().get(0).getConditions().get(0).slow = s;
                                list.add(clone);
                            }
                        }
                        break;
                    case "RSI":
                        for (int p : new int[]{7, 14, 21}) {
                            StrategyConfig clone = cfg.copy();
                            clone.setName(cfg.getName() + "_RSI_" + p);
                            clone.getRules().get(0).getConditions().get(0).period = p;
                            list.add(clone);
                        }
                        break;
                    case "MACD":
                        for (int f : new int[]{10, 12, 15})
                            for (int s : new int[]{20, 26, 30})
                                for (int sig : new int[]{7, 9, 12}) {
                                    if (f >= s) continue;
                                    StrategyConfig clone = cfg.copy();
                                    clone.setName(cfg.getName() + "_MACD_" + f + "-" + s + "-" + sig);
                                    Condition c = clone.getRules().get(0).getConditions().get(0);
                                    c.fast = f; c.slow = s; c.signal = sig;
                                    list.add(clone);
                                }
                        break;
                    default:
                        list.add(cfg.copy());
                }
            }
        }
        return list;
    }

    // DS dynamic bind/unbind
    @Reference(
            service = MarketDataService.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
    )
    protected void addMarketDataService(MarketDataService s) { services.add(s); }

    protected void removeMarketDataService(MarketDataService s) { services.remove(s); }
}
