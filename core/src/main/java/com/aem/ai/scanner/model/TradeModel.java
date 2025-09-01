package com.aem.ai.scanner.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a Trade persisted to MySQL TradeBook (Upstox_TradeBook).
 */
public class TradeModel {

    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter IST_FORMATTER =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss").withZone(IST_ZONE);

    private String tradeId;
    private String timeFrame;
    private InstrumentSymbol symbol;
    public Signal.Side side; // BUY or SELL
    private double entryPrice;
    private double exitPrice;
    private double stopLoss;
    private double target;
    private int quantity;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private Status status; // OPEN, CLOSED, CANCELLED
    private double pnl;

    public enum Status {
        OPEN, CLOSED, CANCELLED
    }

    public String getTimeFrame() {
        return timeFrame;
    }

    public void setTimeFrame(String timeFrame) {
        this.timeFrame = timeFrame;
    }

    public TradeModel(InstrumentSymbol symbol,
                      Signal.Side side,
                      double entryPrice,
                      double stopLoss,
                      double target,
                      int quantity) {
        this.symbol = symbol;
        this.side = side;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.target = target;
        this.quantity = quantity;
        this.entryTime = LocalDateTime.now(IST_ZONE);
        this.status = Status.OPEN;
    }

    public TradeModel() {
    }

    /** Used when loading from DB */
    public TradeModel(String tradeId,
                      InstrumentSymbol symbol,
                      Signal.Side side,
                      double entryPrice,
                      double stopLoss,
                      double target,
                      int quantity,
                      LocalDateTime entryTime,
                      double exitPrice,
                      LocalDateTime exitTime,
                      Status status,
                      double pnl) {
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.side = side;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.target = target;
        this.quantity = quantity;
        this.entryTime = entryTime;
        this.exitPrice = exitPrice;
        this.exitTime = exitTime;
        this.status = status;
        this.pnl = pnl;
    }

    // ===== Business logic helpers =====

    /** Increase quantity for an already open trade */
    public void addQuantity(int qty) {
        this.quantity += qty;
    }

    /** Calculate PnL based on current price */
    public void updatePnl(double currentPrice) {
        if (this.side == Signal.Side.BUY) {
            this.pnl = (currentPrice - this.entryPrice) * this.quantity;
        } else {
            this.pnl = (this.entryPrice - currentPrice) * this.quantity;
        }
    }

    /** Close trade with final price */
    public void close(double exitPrice) {
        this.exitPrice = exitPrice;
        this.exitTime = LocalDateTime.now(IST_ZONE);
        this.status = Status.CLOSED;
        updatePnl(exitPrice);
    }

    // ===== IST Time Helpers =====
    public LocalDateTime getEntryTimeIST() {
        return entryTime != null
                ? entryTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(IST_ZONE).toLocalDateTime()
                : null;
    }

    public LocalDateTime getExitTimeIST() {
        return exitTime != null
                ? exitTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(IST_ZONE).toLocalDateTime()
                : null;
    }

    public String getEntryTimeISTFormatted() {
        return entryTime != null ? IST_FORMATTER.format(entryTime.atZone(IST_ZONE)) : null;
    }

    public String getExitTimeISTFormatted() {
        return exitTime != null ? IST_FORMATTER.format(exitTime.atZone(IST_ZONE)) : null;
    }

    // ===== Getters & Setters =====
    public String getTradeId() { return tradeId; }
    public InstrumentSymbol getSymbol() { return symbol; }
    public Signal.Side getSide() { return side; }
    public double getEntryPrice() { return entryPrice; }
    public double getExitPrice() { return exitPrice; }
    public void setExitPrice(double exitPrice) { this.exitPrice = exitPrice; }
    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }
    public double getTarget() { return target; }
    public void setTarget(double target) { this.target = target; }
    public int getQuantity() { return quantity; }
    public LocalDateTime getEntryTime() { return getEntryTimeIST(); }
    public LocalDateTime getExitTime() { return getExitTimeIST(); }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public double getPnl() { return pnl; }
    public void setPnl(double pnl) { this.pnl = pnl; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }
    public void setSide(Signal.Side side) { this.side = side; }
    public void setSymbol(InstrumentSymbol symbol) { this.symbol = symbol; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    @Override
    public String toString() {
        return "Trade{" +
                "tradeId='" + tradeId + '\'' +
                ", symbol=" + symbol +
                ", side=" + side +
                ", entryPrice=" + entryPrice +
                ", exitPrice=" + exitPrice +
                ", stopLoss=" + stopLoss +
                ", target=" + target +
                ", quantity=" + quantity +
                ", entryTimeIST=" + getEntryTimeISTFormatted() +
                ", exitTimeIST=" + getExitTimeISTFormatted() +
                ", status=" + status +
                ", pnl=" + pnl +
                '}';
    }
}
