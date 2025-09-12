package com.aem.ai.scanner.services.impl;

import com.aem.ai.scanner.factory.StrategyFactoryService;
import com.aem.ai.scanner.model.*;
import com.aem.ai.scanner.services.StrategyEngine;
import com.aem.ai.scanner.services.Ta4jService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Component(service = StrategyEngine.class, immediate = true)
public class StrategyEngineImpl implements StrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyEngineImpl.class);

    // ANSI color codes for console logging
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";

    @Reference
    private StrategyFactoryService strategyFactory;

    @Reference
    private Ta4jService ta4jService;

    @Override
    public Optional<Signal> evaluate(StrategyConfig cfg,
                                     List<Candle> candles,
                                     InstrumentSymbol symbol,
                                     String timeframe) {
        long start = System.nanoTime();
        if (candles == null || candles.isEmpty()) {
            log.debug(YELLOW + "[{} {}] No candles available" + RESET, symbol, timeframe);
            return Optional.empty();
        }

        try {
            long stepStart;

            // Step 1: Build series
            stepStart = System.nanoTime();
            String seriesName = symbol.getSymbol() + "-" + timeframe;
            BarSeries series = ta4jService.buildSeries(seriesName, candles, timeframe);
            log.info(CYAN + "[{} {}] Step 1: BarSeries built in {} ms" + RESET,
                    symbol, timeframe, (System.nanoTime() - stepStart) / 1_000_000);

            // Step 2: Build strategy
            stepStart = System.nanoTime();
            Strategy taStrategy = strategyFactory.buildStrategy(cfg, series);
            if (taStrategy == null) {
                log.warn(RED + "[{} {}] No strategy returned from factory" + RESET, symbol, cfg.getName());
                return Optional.empty();
            }
            log.info(CYAN + "[{} {}] Step 2: Strategy built in {} ms" + RESET,
                    symbol, cfg.getName(), (System.nanoTime() - stepStart) / 1_000_000);

            // Step 3: Evaluate last bar
            stepStart = System.nanoTime();
            int lastIndex = series.getEndIndex();
            boolean entry = taStrategy.shouldEnter(lastIndex);
            boolean exit = taStrategy.shouldExit(lastIndex);
            log.info(CYAN + "[{} {}] Step 3: Strategy evaluated in {} ms" + RESET,
                    symbol, cfg.getName(), (System.nanoTime() - stepStart) / 1_000_000);

            // Primary rule
            StrategyConfig.RuleConfig primaryRule = null;
            if (cfg.getRules() != null && !cfg.getRules().isEmpty()) {
                primaryRule = cfg.getRules().get(0);
            }

            // Step 4: Build signal
            stepStart = System.nanoTime();
            if (entry) {
                Signal.Side side = Signal.Side.BUY;
                if (primaryRule != null && "SELL".equalsIgnoreCase(primaryRule.getAction())) {
                    side = Signal.Side.SELL;
                }

                double entryPrice = candles.get(candles.size() - 1).getClose();
                boolean isBuy = true; // or false for short

                double[] sltp = StopLossTargetCalculator.computeSLTP(series,
                        14,   // ATR period
                        10,   // Swing lookback
                        1.0,  // ATR buffer
                        entryPrice,
                        isBuy);

                double stopLoss = sltp[0];
                double target   = sltp[1];
               /* double stopLoss = computeStopLoss(primaryRule, entryPrice, side, series);
                double target   = computeTarget(primaryRule, entryPrice, side, series);*/
                double confidence = computeConfidence(cfg, series, entryPrice, stopLoss, target);
                double score = computeScore(entryPrice, stopLoss, target);

                Signal s = new Signal(symbol, side, entryPrice, stopLoss, target, timeframe, confidence);
                s.setScore(score);

                // ✅ compute % distance from entry
                if (!Double.isNaN(stopLoss) && entryPrice > 0) {
                    double slPct = ((entryPrice - stopLoss) / entryPrice) * 100.0;
                    if (side == Signal.Side.SELL) {
                        slPct = ((stopLoss - entryPrice) / entryPrice) * 100.0;
                    }
                    s.setStopLossPercent(slPct);
                }

                if (!Double.isNaN(target) && entryPrice > 0) {
                    double tpPct = ((target - entryPrice) / entryPrice) * 100.0;
                    if (side == Signal.Side.SELL) {
                        tpPct = ((entryPrice - target) / entryPrice) * 100.0;
                    }
                    s.setTargetPercent(tpPct);
                }
                log.info(GREEN + "[{} {}] Step 4: Entry signal generated in {} ms" + RESET,
                        symbol, cfg.getName(), (System.nanoTime() - stepStart) / 1_000_000);
                log.info(GREEN + "[{} {}] Signal: {}" + RESET, symbol, cfg.getName(), s);
                log.info(CYAN + "[{} {}] Total evaluation time: {} ms" + RESET,
                        symbol, cfg.getName(), (System.nanoTime() - start) / 1_000_000);
                return Optional.of(s);

            } else if (exit) {
                double exitPrice = candles.get(candles.size() - 1).getClose();
                boolean isBuy = false; // or false for short
                double[] sltp = StopLossTargetCalculator.computeSLTP(series,
                        14,   // ATR period
                        10,   // Swing lookback
                        1.0,  // ATR buffer
                        exitPrice,
                        isBuy);
                double stopLoss = sltp[0];
                double target = sltp[1];
                double confidence = computeConfidence(cfg, series, exitPrice, stopLoss, target);
                Signal s = new Signal(symbol, Signal.Side.SELL, exitPrice, stopLoss, target, timeframe, confidence);

                log.info(YELLOW + "[{} {}] Step 4: Exit signal generated in {} ms" + RESET,
                        symbol, cfg.getName(), (System.nanoTime() - stepStart) / 1_000_000);
                log.info(YELLOW + "[{} {}] Signal: {}" + RESET, symbol, cfg.getName(), s);
                log.info(CYAN + "[{} {}] Total evaluation time: {} ms" + RESET,
                        symbol, cfg.getName(), (System.nanoTime() - start) / 1_000_000);
                return Optional.of(s);
            }

        } catch (Exception e) {
            log.error(RED + "[{} {}] Strategy evaluation failed: {}" + RESET,
                    symbol, cfg.getName(), e.getMessage(), e);
        }

        log.info(CYAN + "[{} {}] Total evaluation time: {} ms" + RESET,
                symbol, cfg.getName(), (System.nanoTime() - start) / 1_000_000);
        return Optional.empty();
    }

    @Override
    public String format(Signal signal,
                         StrategyConfig cfg,
                         InstrumentSymbol symbol,
                         String timeframe) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Strategy: ").append(cfg.getName()).append("\n");
        sb.append("Symbol: ").append(symbol.getSymbol()).append("\n");
        sb.append("Side: ").append(signal.getSide()).append("\n");
        sb.append("Entry: ").append(signal.getEntryPrice()).append("\n");
        if (!Double.isNaN(signal.getStopLoss())) sb.append("StopLoss: ").append(signal.getStopLoss()).append("\n");
        if (!Double.isNaN(signal.getTarget())) sb.append("Target: ").append(signal.getTarget()).append("\n");
        sb.append("Timeframe: ").append(timeframe).append("\n");
        sb.append("Confidence: ").append(signal.getConfidence()).append("\n");
        sb.append("Generated: ").append(ZonedDateTime.now()).append("\n");
        if (!Double.isNaN(signal.getStopLoss())) {
            sb.append("StopLoss: ").append(signal.getStopLoss());
            sb.append(" (").append(String.format("%.2f", signal.getStopLossPercent())).append("%)").append("\n");
        }
        if (!Double.isNaN(signal.getTarget())) {
            sb.append("Target: ").append(signal.getTarget());
            sb.append(" (").append(String.format("%.2f", signal.getTargetPercent())).append("%)").append("\n");
        }
        return sb.toString();
    }

    // --- Helpers (same as your original code) ---
    private double computeScore(double entryPrice, double stopLoss, double target) {
        if (Double.isNaN(stopLoss) || Double.isNaN(target)) return 0.0;
        double risk = Math.abs(entryPrice - stopLoss);
        double reward = Math.abs(target - entryPrice);
        if (risk <= 0) return 0.0;
        double rr = reward / risk;
        return Math.min(1.0, rr / 5.0);
    }

    private double computeConfidence(StrategyConfig cfg, org.ta4j.core.BarSeries series,
                                     double entryPrice, double stopLoss, double target) {
        double confidence = 0.5;
        double risk = Math.abs(entryPrice - stopLoss);
        double reward = Math.abs(target - entryPrice);

        if (!Double.isNaN(risk) && !Double.isNaN(reward) && risk > 0) {
            double rr = reward / risk;
            if (rr >= 2) confidence += 0.2;
            else if (rr < 1) confidence -= 0.2;
        }

        try {
            Ta4jService.IndicatorsSnapshot snap = ta4jService.computeIndicators(series);
            if (snap != null && !Double.isNaN(snap.atr)) {
                double atr = snap.atr;
                double candleRange = series.getLastBar().getHighPrice().doubleValue()
                        - series.getLastBar().getLowPrice().doubleValue();
                confidence += (candleRange < atr * 1.2) ? 0.1 : -0.1;
            }
        } catch (Exception e) {
            log.debug("Confidence ATR calc failed: {}", e.getMessage());
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private double computeStopLoss(StrategyConfig.RuleConfig rule, double entryPrice, Signal.Side side, org.ta4j.core.BarSeries series) {
        if (rule == null) return Double.NaN;

        if (rule.getStopLossPoints() != null) return (side == Signal.Side.BUY)
                ? entryPrice - rule.getStopLossPoints() : entryPrice + rule.getStopLossPoints();
        if (rule.getStopLossPercent() != null) {
            double factor = rule.getStopLossPercent() / 100.0;
            return (side == Signal.Side.BUY) ? entryPrice * (1 - factor) : entryPrice * (1 + factor);
        }
        if (rule.getStopLossAtrMultiplier() != null) {
            try {
                Ta4jService.IndicatorsSnapshot snap = ta4jService.computeIndicators(series);
                double atr = snap != null ? snap.atr : Double.NaN;
                if (!Double.isNaN(atr)) return (side == Signal.Side.BUY) ? entryPrice - atr * rule.getStopLossAtrMultiplier()
                        : entryPrice + atr * rule.getStopLossAtrMultiplier();
            } catch (Exception e) {
                log.debug("ATR compute failed: {}", e.getMessage());
            }
        }
        return Double.NaN;
    }

    private double computeTarget(StrategyConfig.RuleConfig rule, double entryPrice, Signal.Side side, org.ta4j.core.BarSeries series) {
        if (rule == null) return Double.NaN;

        if (rule.getTakeProfitPoints() != null) return (side == Signal.Side.BUY)
                ? entryPrice + rule.getTakeProfitPoints() : entryPrice - rule.getTakeProfitPoints();
        if (rule.getTakeProfitPercent() != null) {
            double factor = rule.getTakeProfitPercent() / 100.0;
            return (side == Signal.Side.BUY) ? entryPrice * (1 + factor) : entryPrice * (1 - factor);
        }
        if (rule.getTakeProfitAtrMultiplier() != null) {
            try {
                Ta4jService.IndicatorsSnapshot snap = ta4jService.computeIndicators(series);
                double atr = snap != null ? snap.atr : Double.NaN;
                if (!Double.isNaN(atr)) return (side == Signal.Side.BUY) ? entryPrice + atr * rule.getTakeProfitAtrMultiplier()
                        : entryPrice - atr * rule.getTakeProfitAtrMultiplier();
            } catch (Exception e) {
                log.debug("ATR compute failed: {}", e.getMessage());
            }
        }
        return Double.NaN;
    }
}
