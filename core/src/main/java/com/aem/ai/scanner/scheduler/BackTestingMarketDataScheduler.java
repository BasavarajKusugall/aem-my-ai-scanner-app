package com.aem.ai.scanner.scheduler;

import com.GenericeConstants;
import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.dao.WatchlistDao;
import com.aem.ai.scanner.factory.StrategyFactoryService;
import com.aem.ai.scanner.model.*;
import com.aem.ai.scanner.services.ReportStorageService;
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
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.PerformanceReport;
import org.ta4j.core.reports.PositionStatsReport;
import org.ta4j.core.reports.TradingStatement;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
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
        String scheduler_expression() default "0 0/50 * * * ?";
        @AttributeDefinition(name = "Allow concurrent execution")
        boolean scheduler_concurrent() default false;

        @AttributeDefinition(name = "Misfire Policy", description = "Reschedule on misfire")
        String scheduler_misfire_policy() default "REPLACE";

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

    @Reference
    private ReportStorageService reportStorageService;

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

        // Collect all strategies (including variations)
        List<Strategy> strategies = new ArrayList<>();
        List<StrategyConfig> strategyMapping = new ArrayList<>(); // parallel mapping for persistence

        for (StrategyConfig template : strategyConfigs) {
            template.setTimeframe(timeframe);
            List<StrategyConfig> variations = generateVariations(template, series, timeframe);

            for (StrategyConfig cfg : variations) {
                try {
                    Strategy s = strategyFactoryService.buildStrategy(cfg, series);
                    strategies.add(s);
                    strategyMapping.add(cfg);
                } catch (Exception e) {
                    log.error("❌ Error building strategy {}: {}", cfg.getName(), e.getMessage(), e);
                }
            }
        }

        if (strategies.isEmpty()) {
            log.warn("⚠️ No valid strategies to test.");
            return;
        }

        // ✅ Use BacktestExecutor instead of BarSeriesManager
        BacktestExecutor executor = new BacktestExecutor(series);
        List<TradingStatement> tradingStatements = executor.execute(
                strategies,
                DecimalNum.valueOf(50), // position size (adjust as needed)
                Trade.TradeType.BUY
        );

        // ✅ Convert results into StrategyResult
        List<StrategyResult> results = new ArrayList<>();
        for (int i = 0; i < tradingStatements.size(); i++) {
            TradingStatement ts = tradingStatements.get(i);
            StrategyConfig cfg = strategyMapping.get(i);

            double pnl = ts.getPerformanceReport().getTotalProfitLossPercentage().doubleValue();
            double winRate = (ts.getPositionStatsReport().getProfitCount().intValue() == 0)
                    ? 0.0
                    : (double) ts.getPositionStatsReport().getProfitCount().intValue()
                    / (ts.getPositionStatsReport().getProfitCount().intValue() + ts.getPositionStatsReport().getLossCount().intValue());
// assuming 'record' is the TradingRecord returned by mgr.run(strategy)
            double dd = 0.0;
            if (pnl > 1 && ts.getPositionStatsReport().getProfitCount().intValue() > ts.getPositionStatsReport().getLossCount().intValue() ){
                results.add(new StrategyResult(cfg, winRate, pnl, dd));
            }
        }

        // ✅ Store reports
        try {
            BacktestReportGenerator reportGen = new BacktestReportGenerator(tradingStatements);

            reportStorageService.storeReport(
                    "/var/mytrades",
                    symbol.getSymbol() + "_" + timeframe + ".csv",
                    reportGen.generateCsv(),
                    "text/csv"
            );

            reportStorageService.storeReport(
                    "/var/mytrades",
                    symbol.getSymbol() + "_" + timeframe + ".json",
                    reportGen.generateJson(),
                    "application/json"
            );

            reportStorageService.storeReport(
                    "/var/mytrades",
                    symbol.getSymbol() + "_" + timeframe + ".html",
                    reportGen.generateHtml(),
                    "text/html"
            );

            log.info("✅ Reports stored in CRX for {} {}", symbol, timeframe);
        } catch (Exception e) {
            log.error("❌ Failed to store reports for {} {}: {}", symbol, timeframe, e.getMessage(), e);
        }

        // ✅ Still print summary to logs

        // ✅ Print reports
        log.info(printReport(tradingStatements));

        // ✅ Best strategy summary
        Optional<TradingStatement> bestStatement = tradingStatements.stream()
                .max(Comparator.comparing(ts -> ts.getPerformanceReport().getTotalProfitLossPercentage()));

        if (bestStatement.isPresent()) {
            TradingStatement best = bestStatement.get();
            log.info("\n\n========= BEST STRATEGY SUMMARY =========");
            log.info("Name: {}", best.getStrategy().getName());
            log.info("Total Profit: {}", best.getPerformanceReport().getTotalProfit());
            log.info("Total P/L %: {}", best.getPerformanceReport().getTotalProfitLossPercentage());
            log.info("Profitable Trades: {}", best.getPositionStatsReport().getProfitCount());
            log.info("Losing Trades: {}", best.getPositionStatsReport().getLossCount());
            log.info("Break-even Trades: {}", best.getPositionStatsReport().getBreakEvenCount());
            log.info("=========================================");
        }

        // ✅ Persist results
        String watchListTable = GenericeConstants.DELTA.equalsIgnoreCase(brokerCode)
                ? watchlistDao.deltaTable()
                : watchlistDao.upstoxTable();
        daoFactory.persistBestStrategies(watchListTable, symbol.getSymbol(), results);
    }


    /** ========= Helper reporting methods ========= */

    private static String printReport(List<TradingStatement> tradingStatements) {
        StringJoiner resultJoiner = new StringJoiner(System.lineSeparator());
        for (TradingStatement statement : tradingStatements) {
            resultJoiner.add(printStatementReport(statement).toString());
        }
        return resultJoiner.toString();
    }

    private static StringBuilder printStatementReport(TradingStatement statement) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("\n######### ")
                .append(statement.getStrategy().getName())
                .append(" #########")
                .append(System.lineSeparator())
                .append(printPerformanceReport(statement.getPerformanceReport()))
                .append(System.lineSeparator())
                .append(printPositionStats(statement.getPositionStatsReport()))
                .append(System.lineSeparator())
                .append("###########################");
        return resultBuilder;
    }

    private static StringBuilder printPerformanceReport(PerformanceReport report) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("--------- performance report ---------")
                .append(System.lineSeparator())
                .append("total loss: ").append(report.getTotalLoss()).append(System.lineSeparator())
                .append("total profit: ").append(report.getTotalProfit()).append(System.lineSeparator())
                .append("total profit loss: ").append(report.getTotalProfitLoss()).append(System.lineSeparator())
                .append("total profit loss percentage: ").append(report.getTotalProfitLossPercentage()).append(System.lineSeparator())
                .append("---------------------------");
        return resultBuilder;
    }

    private static StringBuilder printPositionStats(PositionStatsReport report) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("--------- trade statistics report ---------")
                .append(System.lineSeparator())
                .append("loss trade count: ").append(report.getLossCount()).append(System.lineSeparator())
                .append("profit trade count: ").append(report.getProfitCount()).append(System.lineSeparator())
                .append("break even trade count: ").append(report.getBreakEvenCount()).append(System.lineSeparator())
                .append("---------------------------");
        return resultBuilder;
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

    /**
     * Convert a timeframe string like "5m", "1h", "1d" into minutes
     */
    private static int timeframeToMinutes(String tf) {
        if (tf == null || tf.isEmpty()) throw new IllegalArgumentException("Timeframe cannot be null or empty");

        tf = tf.trim().toLowerCase();
        int multiplier;
        if (tf.endsWith("m")) {
            multiplier = 1;
        } else if (tf.endsWith("h")) {
            multiplier = 60;
        } else if (tf.endsWith("d")) {
            multiplier = 1440; // 24 * 60
        } else {
            throw new IllegalArgumentException("Unsupported timeframe: " + tf);
        }

        String numberPart = tf.substring(0, tf.length() - 1);
        int value = Integer.parseInt(numberPart);
        return value * multiplier;
    }
    private List<StrategyConfig> generateVariations(StrategyConfig cfg, BarSeries series, String timeframe) {
        List<StrategyConfig> list = new ArrayList<>();
        int minutes = timeframeToMinutes(timeframe);

        // Determine the timeframe from the series
        Duration tfDuration = series.getBar(0).getTimePeriod();
        //int minutes = (int) tfDuration.toMinutes();

        // Define ranges dynamically based on timeframe
        int[] emaFastRange;
        int[] emaSlowRange;
        int[] rsiPeriods;
        int[] macdFastRange;
        int[] macdSlowRange;
        int[] macdSignalRange;

        if (minutes <= 5) { // very short-term
            emaFastRange = new int[]{3,5,8};
            emaSlowRange = new int[]{13,21,34};
            rsiPeriods = new int[]{5,7,9};
            macdFastRange = new int[]{5,8,10};
            macdSlowRange = new int[]{13,21,26};
            macdSignalRange = new int[]{5,7,9};
        } else if (minutes <= 15) { // short-term
            emaFastRange = new int[]{5,9,12};
            emaSlowRange = new int[]{21,26,50};
            rsiPeriods = new int[]{7,14,21};
            macdFastRange = new int[]{10,12,15};
            macdSlowRange = new int[]{20,26,30};
            macdSignalRange = new int[]{7,9,12};
        } else { // longer-term (1h, 1d)
            emaFastRange = new int[]{10,15,20};
            emaSlowRange = new int[]{50,60,100};
            rsiPeriods = new int[]{14,21,28};
            macdFastRange = new int[]{12,15,18};
            macdSlowRange = new int[]{26,30,35};
            macdSignalRange = new int[]{9,12,15};
        }

        for (StrategyConfig.RuleConfig rule : cfg.getRules()) {
            for (Condition cond : rule.getConditions()) {
                switch (cond.indicator.toUpperCase()) {
                    case "EMA":
                        for (int f : emaFastRange) {
                            for (int s : emaSlowRange) {
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
                    case "RSI":
                        for (int p : rsiPeriods) {
                            StrategyConfig clone = cfg.copy();
                            clone.setName(cfg.getName() + "_RSI_" + p);
                            clone.getRules().get(0).getConditions().get(0).period = p;
                            list.add(clone);
                        }
                        break;
                    case "MACD":
                        for (int f : macdFastRange)
                            for (int s : macdSlowRange)
                                for (int sig : macdSignalRange) {
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
