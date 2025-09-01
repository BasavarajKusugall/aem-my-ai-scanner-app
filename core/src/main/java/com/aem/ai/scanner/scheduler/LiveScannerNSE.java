package com.aem.ai.scanner.scheduler;

import com.GenericeConstants;
import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.dao.WatchlistDao;
import com.aem.ai.scanner.factory.StrategyFactoryService;
import com.aem.ai.scanner.model.Candle;
import com.aem.ai.scanner.model.InstrumentSymbol;
import com.aem.ai.scanner.utils.Ansi;
import com.aem.ai.scanner.utils.Timeframes;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Designate(ocd = LiveScannerNSE.Config.class)
@Component(service = Runnable.class, immediate = true)
public class LiveScannerNSE implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(LiveScannerNSE.class);

    @ObjectClassDefinition(name="BSK NSE UPSTOX Live Scanner Scheduler",
            description="Fetch market data for NSE UPSTOX symbols on a schedule")
    public @interface Config {
        @AttributeDefinition(name="Enable")
        boolean enable() default true;

        @AttributeDefinition(name="Cron (Quartz)")
        String scheduler_expression() default "0 0/5 9-15 ? * MON-FRI";

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




    // DS dynamic bind/unbind
    protected void addMarketDataService(MarketDataService s) { services.add(s); }
    protected void removeMarketDataService(MarketDataService s) { services.remove(s); }
}
