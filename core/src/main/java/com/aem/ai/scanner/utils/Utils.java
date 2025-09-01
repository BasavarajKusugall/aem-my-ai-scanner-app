package com.aem.ai.scanner.utils;

import com.aem.ai.scanner.model.Candle;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
}
