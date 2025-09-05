package com.aem.ai.scanner.model;


import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Represents a trading signal.
 */
public class Signal {
    private double score;

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public enum Side {BUY, SELL}
    private final String id;
    private final InstrumentSymbol symbol;
    private final Side side;
    private final double entryPrice;
    private final double stopLoss;
    private final double target;
    private final ZonedDateTime time;
    private final String timeframe;
    private final double confidence;

    public Signal(InstrumentSymbol symbol, Side side, double entryPrice, double stopLoss, double target, String timeframe, double confidence) {
        this.id = UUID.randomUUID().toString();
        this.symbol = symbol;
        this.side = side;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.target = target;
        this.time = ZonedDateTime.now();
        this.timeframe = timeframe;
        this.confidence = confidence;
    }

    public static Signal buy(InstrumentSymbol symbol, double entry, double sl, double tgt, String timeframe, double conf) {
        return new Signal(symbol, Side.BUY, entry, sl, tgt, timeframe, conf);
    }

    public static Signal sell(InstrumentSymbol symbol, double entry, double sl, double tgt, String timeframe, double conf) {
        return new Signal(symbol, Side.SELL, entry, sl, tgt, timeframe, conf);
    }

    // getters...
    public String getId(){return id;}
    public InstrumentSymbol getSymbol(){return symbol;}
    public Side getSide(){return side;}
    public double getEntryPrice(){return entryPrice;}
    public double getStopLoss(){return stopLoss;}
    public double getTarget(){return target;}
    public ZonedDateTime getTime(){return time;}
    public String getTimeframe(){return timeframe;}
    public double getConfidence(){return confidence;}

    @Override
    public String toString() {
        return "Signal{" +
                "id='" + id + '\'' +
                ", symbol=" + symbol +
                ", side=" + side +
                ", entryPrice=" + entryPrice +
                ", stopLoss=" + stopLoss +
                ", target=" + target +
                ", time=" + time +
                ", timeframe='" + timeframe + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
