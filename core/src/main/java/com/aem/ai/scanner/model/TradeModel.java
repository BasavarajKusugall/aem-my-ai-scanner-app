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
    private String orderType;
    private String timeFrame;
    private String reason;
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
    private double ltp;
    private double pnlPercentage;
    private PivotLevels pivotLevels;
    private String brokerAccountRef;
    private int liveOrderId;
    private int liveTradeId;
    private double trailingIncrement;

    public String getBrokerAccountRef() {
        return brokerAccountRef;
    }

    public void setBrokerAccountRef(String brokerAccountRef) {
        this.brokerAccountRef = brokerAccountRef;
    }

    public int getLiveOrderId() {
        return liveOrderId;
    }

    public void setLiveOrderId(int liveOrderId) {
        this.liveOrderId = liveOrderId;
    }

    public int getLiveTradeId() {
        return liveTradeId;
    }

    public void setLiveTradeId(int liveTradeId) {
        this.liveTradeId = liveTradeId;
    }

    public void setLtp(double ltp) {
        this.ltp = ltp;
    }

    public double getLtp() {
        return safeDouble(ltp);
    }

    public void setPnlPercentage(double pnlPercentage) {
        this.pnlPercentage = pnlPercentage;
    }

    public double getPnlPercentage() {
        return safeDouble(pnlPercentage);
    }

    public void setPivotLevels(PivotLevels pivotLevels) {
        this.pivotLevels = pivotLevels;
    }

    public PivotLevels getPivotLevels() {
        return pivotLevels;
    }

    public double getTrailingIncrement() {
        return trailingIncrement;
    }

    public void setTrailingIncrement(double trailingIncrement) {
        this.trailingIncrement = trailingIncrement;
    }

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

    public boolean isValid() {
        if (symbol == null || side == null) return false;

        if (isInvalidNumber(entryPrice)) return false;
        if (isInvalidNumber(stopLoss)) return false;
        if (isInvalidNumber(target)) return false;
        if (quantity <= 0) return false;

        // exitPrice can be 0 if trade is still open, so only check if set
        if (status == Status.CLOSED && isInvalidNumber(exitPrice)) return false;

        return true;
    }

    private boolean isInvalidNumber(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) || value <= 0;
    }
    private double safeDouble(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0; // or handle differently (NULL)
        }
        return value;
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
    public double getEntryPrice() { return safeDouble(entryPrice); }
    public double getExitPrice() { return safeDouble(exitPrice); }
    public void setExitPrice(double exitPrice) { this.exitPrice = exitPrice; }
    public double getStopLoss() { return safeDouble(stopLoss); }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }
    public double getTarget() { return safeDouble(target); }
    public void setTarget(double target) { this.target = target; }
    public int getQuantity() { return quantity; }
    public LocalDateTime getEntryTime() { return getEntryTimeIST(); }
    public LocalDateTime getExitTime() { return getExitTimeIST(); }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public double getPnl() { return safeDouble(pnl); }
    public void setPnl(double pnl) { this.pnl = pnl; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }
    public void setSide(Signal.Side side) { this.side = side; }
    public void setSymbol(InstrumentSymbol symbol) { this.symbol = symbol; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "TradeModel{" +
                "tradeId='" + tradeId + '\'' +
                ", orderType='" + orderType + '\'' +
                ", timeFrame='" + timeFrame + '\'' +
                ", reason='" + reason + '\'' +
                ", symbol=" + symbol +
                ", side=" + side +
                ", entryPrice=" + entryPrice +
                ", exitPrice=" + exitPrice +
                ", stopLoss=" + stopLoss +
                ", target=" + target +
                ", quantity=" + quantity +
                ", entryTime=" + entryTime +
                ", exitTime=" + exitTime +
                ", status=" + status +
                ", pnl=" + pnl +
                ", ltp=" + ltp +
                ", pnlPercentage=" + pnlPercentage +
                ", pivotLevels=" + pivotLevels +
                ", brokerAccountRef='" + brokerAccountRef + '\'' +
                ", liveOrderId=" + liveOrderId +
                ", liveTradeId=" + liveTradeId +
                '}';
    }
}
