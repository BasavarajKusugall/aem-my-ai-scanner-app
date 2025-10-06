package com.aem.ai.scanner.scheduler;

import com.aem.GenericeConstants;
import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.dao.WatchlistDao;
import com.aem.ai.scanner.factory.StrategyFactoryService;
import com.aem.ai.scanner.model.TradeModel;
import com.aem.ai.scanner.utils.Timeframes;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Designate(ocd = TradesMonitorScheduler.Config.class)
@Component(
        service = Runnable.class,
        immediate = true

)
public class TradesMonitorScheduler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TradesMonitorScheduler.class);

    @ObjectClassDefinition(name="BSK Trades Monitor Scheduler")
    public @interface Config {
        @AttributeDefinition(name="Enable")
        boolean enable() default true;

        @AttributeDefinition(name="Cron expression")
        String scheduler_expression() default "0 0/5 * * * ?";
        @AttributeDefinition(name = "Allow concurrent execution")
        boolean scheduler_concurrent() default false;

        @AttributeDefinition(name = "Misfire Policy", description = "Reschedule on misfire")
        String scheduler_misfire_policy() default "REPLACE";

        @AttributeDefinition(name = "Scheduler name")
        String scheduler_name() default "TradesMonitorScheduler";

        @AttributeDefinition(name="Timeframes e.g. 5m:120,15m:64,1h:48,1d:365")
        String timeframes() default "5m:120,15m:64,1h:48,1d:365";
    }

    private volatile Config cfg;

    @Reference
    private WatchlistDao watchlistDao;

    @Reference
    private DAOFactory daoFactory;


    @Reference
    private LiveScannerNSE liveScannerNSE;

    @Reference
    private LiveScannerDeltaExchange liveScannerDeltaExchange;

    @Reference
    private StrategyFactoryService strategyFactoryService;


    private final List<MarketDataService> services = new CopyOnWriteArrayList<>();

    @Activate
    @Modified
    protected void activate(Config cfg) {
        this.cfg = cfg;
        log.info("✅ TradesMonitorScheduler activated. cron={} enable={} timeframes={}",
                cfg.scheduler_expression(), cfg.enable(), cfg.timeframes());
    }

    @Deactivate
    protected void deactivate() {
        log.info("🛑 TradesMonitorScheduler deactivated.");
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
            List<TradeModel> openTradesList;
            if (GenericeConstants.UPSTOX.equalsIgnoreCase(code)) {
                String stocksTradeTable = GenericeConstants.STOCK_TRADES_TABLE;
                if (liveScannerNSE != null){
                     stocksTradeTable = liveScannerNSE.getStocksTradeTable();
                }
                log.info("Using stocksTradeTable {} for broker {}", stocksTradeTable, code);
                openTradesList = daoFactory.listAllOpenTrades(stocksTradeTable);
            } else if (GenericeConstants.DELTA.equalsIgnoreCase(code)) {
                String cryptoTradeTable = GenericeConstants.CURRENCY_TRADES_TABLE;
                if (liveScannerDeltaExchange != null){
                    cryptoTradeTable = liveScannerDeltaExchange.getCryptoTradeTable();
                }
                log.info("Using cryptoTradeTable {} for broker {}", cryptoTradeTable, code);
                openTradesList = daoFactory.listAllOpenTrades(cryptoTradeTable);
            } else {
                log.info("Skipping unknown broker {}", code);
                continue;
            }

            if (openTradesList.isEmpty()) {
                log.warn("⚠️ No openTradesList for broker {}", code);
                continue;
            }

            for (TradeModel trade : openTradesList) {
                    log.info("Processing trade: {}", trade);
                    String timeFrame = trade.getTimeFrame();
                    int barCount = tfs.get(timeFrame);
                    //todo iterate trades
                    //todo get the timeframe from the trade and extract the barCount from tf for that timeframe
                    //todo fetch candles for 5min and count
                    //todo build series
            }
        }
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
