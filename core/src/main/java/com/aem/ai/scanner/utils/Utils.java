package com.aem.ai.scanner.utils;

import com.aem.ai.scanner.model.*;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.time.*;
import java.util.List;

import static com.aem.ai.scanner.model.TradeModel.IST_ZONE;

public class Utils {

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
    public static String formatTradeSignalMessage(InstrumentSymbol symbol,
                                            String timeframe,
                                            StrategyConfig sc,
                                            Signal signal,
                                            String comment) {
        StringBuilder sb = new StringBuilder();
        sb.append("📈📊 ================= TRADE SIGNAL =================\n");
        sb.append("🛠 Strategy: ").append(sc.getName()).append("\n");
        sb.append("📌 Symbol: ").append(symbol.getSymbol()).append("\n");
        sb.append("⏱ Timeframe: ").append(timeframe).append("\n");
        sb.append("🔀 Side: ").append(signal.getSide()).append("\n");
        sb.append("💰 Entry Price: ").append(signal.getEntryPrice()).append("\n");

        if (!Double.isNaN(signal.getStopLoss())) {
            sb.append("🛡 Stop Loss: ").append(signal.getStopLoss()).append("\n");
        }

        if (!Double.isNaN(signal.getTarget())) {
            sb.append("🎯 Target: ").append(signal.getTarget()).append("\n");
        }

        sb.append("⭐ Confidence: ").append(String.format("%.2f", signal.getConfidence())).append("\n");
        sb.append("🏷 Score: ").append(String.format("%.2f", signal.getScore())).append("\n");
        sb.append("📝 Comment: ").append(comment).append("\n");
        sb.append("🕒 Generated: ").append(LocalDateTime.now()).append("\n");
        sb.append("📈📊 ==============================================\n");
        return sb.toString();
    }
    public static String formatTradeSignalMessage(InstrumentSymbol symbol,
                                                  String timeframe,
                                                  StrategyConfig sc,
                                                  Signal signal,
                                                  String baseMessage,
                                                  PivotLevels pivots) {
        StringBuilder sb = new StringBuilder();
        sb.append("📢 Signal Alert\n");
        sb.append("Symbol: ").append(symbol.getSymbol()).append("\n");
        sb.append("Timeframe: ").append(timeframe).append("\n");
        sb.append("Strategy: ").append(sc.getName()).append("\n");
        sb.append("Side: ").append(signal.getSide()).append("\n");
        sb.append("Entry: ").append(signal.getEntryPrice()).append("\n");
        sb.append("SL: ").append(signal.getStopLoss()).append("\n");
        sb.append("Target: ").append(signal.getTarget()).append("\n");

        if (pivots != null) {
            sb.append("\n📊 Pivot Levels\n");
            sb.append("Pivot: ").append(String.format("%.2f", pivots.getPivot())).append("\n");
            sb.append("R1: ").append(String.format("%.2f", pivots.getR1())).append(" | ");
            sb.append("R2: ").append(String.format("%.2f", pivots.getR2())).append(" | ");
            sb.append("R3: ").append(String.format("%.2f", pivots.getR3())).append("\n");
            sb.append("S1: ").append(String.format("%.2f", pivots.getS1())).append(" | ");
            sb.append("S2: ").append(String.format("%.2f", pivots.getS2())).append(" | ");
            sb.append("S3: ").append(String.format("%.2f", pivots.getS3())).append("\n");
        }

        if (baseMessage != null && !baseMessage.isEmpty()) {
            sb.append("\nNote: ").append(baseMessage);
        }

        return sb.toString();
    }

    public static boolean isMarketClose() {
        // Current time in IST
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));

        // Market close time
        LocalTime closeTime = LocalTime.of(15, 30);

        // Return true if current time >= 15:30
        return !now.isBefore(closeTime);
    }

    public static PivotLevels calculatePivotLevels(List<Candle> dailyCandles) {
        if (dailyCandles == null || dailyCandles.isEmpty()) return null;

        Candle last = dailyCandles.get(dailyCandles.size() - 1);

        double high = last.getHigh();
        double low = last.getLow();
        double close = last.getClose();

        double pivot = (high + low + close) / 3.0;
        double r1 = 2 * pivot - low;
        double s1 = 2 * pivot - high;
        double r2 = pivot + (high - low);
        double s2 = pivot - (high - low);
        double r3 = high + 2 * (pivot - low);
        double s3 = low - 2 * (high - pivot);

        return new PivotLevels(pivot, r1, r2, r3, s1, s2, s3);
    }
}
