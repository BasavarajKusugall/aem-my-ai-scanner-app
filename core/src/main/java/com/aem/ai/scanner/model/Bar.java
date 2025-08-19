package com.aem.ai.scanner.model;


import java.time.ZonedDateTime;

/**
 * Simple OHLCV bar.
 */
public class Bar {
    private final ZonedDateTime time;
    private final double open, high, low, close;
    private final long volume;

    private String symbol; // optional, can be set later
    public Bar(ZonedDateTime time, double open, double high, double low, double close, long volume) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public ZonedDateTime getTime() { return time; }
    public double getOpen() { return open; }
    public double getHigh() { return high; }
    public double getLow() { return low; }
    public double getClose() { return close; }
    public long getVolume() { return volume; }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        return "Bar{" + time + ", o=" + open + ", h=" + high + ", l=" + low + ", c=" + close + ", v=" + volume + '}';
    }
}
