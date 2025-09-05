package com.aem.ai.scanner.scheduler;


import com.GenericeConstants;
import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.dao.WatchlistDao;
import com.aem.ai.scanner.model.*;
import com.aem.ai.scanner.services.StrategyEngine;
import com.aem.ai.scanner.services.TelegramService;
import com.aem.ai.scanner.utils.Timeframes;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractLiveScanner {

    protected final ObjectMapper mapper = new ObjectMapper();
    protected final Map<String, CachedStrategies> strategyCache = new ConcurrentHashMap<>();
    protected final Map<String, MarketDataService> servicesByBroker = new ConcurrentHashMap<>();
    protected final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    protected WatchlistDao watchlistDao;
    protected DAOFactory daoFactory;
    protected StrategyEngine strategyEngine;
    protected TelegramService telegram;

    protected abstract Logger getLogger();

    /** Broker code for this scanner (e.g., UPSTOX, DELTA) */
    protected abstract String brokerCode();

    /** Trade table name for this scanner */
    protected abstract String tradesTable();

    /** Symbol list fetcher */
    protected abstract List<InstrumentSymbol> fetchSymbols();

    /** Maximum retries per run */
    protected abstract int maxRetries();

    public void execute() {
        List<InstrumentSymbol> symbols = fetchSymbols();
        if (symbols == null || symbols.isEmpty()) return;

        MarketDataService svc = servicesByBroker.get(brokerCode());
        if (svc == null) {
            getLogger().warn("No MarketDataService bound for broker {}", brokerCode());
            return;
        }

        Map<String, Integer> tfs = Timeframes.parse(timeframesSpec());
        if (tfs.isEmpty()) {
            getLogger().warn("No timeframes configured for scanner {}", brokerCode());
            return;
        }

        for (Map.Entry<String, Integer> tf : tfs.entrySet()) {
            for (InstrumentSymbol symbol : symbols) {
                fetchAndProcess(svc, symbol, tf.getKey(), tf.getValue(), 0);
            }
        }
    }

    protected abstract String timeframesSpec();

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
                        getLogger().error("Signal handling failed: {}", e.getMessage(), e);
                    }
                });
            }

            getLogger().info("✅ Completed {} {} (candles={}, strategies={})",
                    symbol.getSymbol(), timeframe, candles.size(), strategies.size());

            consecutiveFailures.remove(svc.brokerCode());

        } catch (Exception e) {
            int fails = consecutiveFailures.computeIfAbsent(svc.brokerCode(), k -> new AtomicInteger()).incrementAndGet();
            getLogger().warn("❌ {} {} {} failed (attempt={}): {}", svc.brokerCode(), symbol.getSymbol(), timeframe, attempt + 1, e.getMessage());

            if (attempt < maxRetries()) {
                fetchAndProcess(svc, symbol, timeframe, count, attempt + 1);
            } else if (fails > 5) {
                getLogger().error("Too many consecutive failures for broker {}", svc.brokerCode());
            }
        }
    }

    private void tradesMonitor(InstrumentSymbol symbol, List<Candle> candles) {
        try {
            double ltp = candles.get(candles.size() - 1).getClose();
            List<TradeModel> openTrades = daoFactory.listOpenTradesForSymbol(symbol.getSymbol(), tradesTable());

            for (TradeModel t : openTrades) {
                daoFactory.updateLtp(t, ltp, tradesTable());

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

                    daoFactory.closeTradeWithPnl(t, tradesTable());
                    telegram.sendMessageDailyStocksAlerts("Closed trade: " + symbol + " pnl=" + pnl);

                    getLogger().info("🔴 Auto-closed trade {} pnl={}", symbol.getSymbol(), pnl);
                }
            }
        } catch (Exception e) {
            getLogger().warn("Trade monitor failed: {}", e.getMessage());
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
            getLogger().error("Failed to parse strategies for {}: {}", symbol.getSymbol(), e.getMessage(), e);
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

    protected abstract void onEntrySignal(InstrumentSymbol symbol, String timeframe, StrategyConfig sc, Signal signal, String comment) throws Exception;
    protected abstract void onExitSignal(InstrumentSymbol symbol, String timeframe, StrategyConfig sc, Signal signal, String comment) throws Exception;

    protected static class CachedStrategies {
        final String hash;
        final List<StrategyConfig> strategies;
        CachedStrategies(String hash, List<StrategyConfig> strategies) {
            this.hash = hash;
            this.strategies = strategies;
        }
    }
}
