package com.aem.ai.scanner.model;


import java.time.Instant;

public class Candle {
    public Instant time;
    public double open;
    public double high;
    public double low;
    public double close;
    public double volume;

    public Candle() {}

    public Candle(Instant time, double open, double high, double low, double close, double volume) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    @Override
    public String toString() {
        return "Candle{" +
                "time=" + time +
                ", o=" + open +
                ", h=" + high +
                ", l=" + low +
                ", c=" + close +
                ", v=" + volume +
                '}';
    }
}
