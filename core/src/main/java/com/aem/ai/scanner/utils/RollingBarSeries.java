package com.aem.ai.scanner.utils;


import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;


public class RollingBarSeries {
    private final BarSeries series;


    /** Create new series with given name and max bars */
    public RollingBarSeries(String name, int maxBars) {
        this.series = new BaseBarSeriesBuilder().withName(name).build();
        this.series.setMaximumBarCount(maxBars);
    }


    /** Wrap an existing series but enforce maxBars */
    public RollingBarSeries(BarSeries existing, int maxBars) {
        this.series = existing;
        this.series.setMaximumBarCount(maxBars);
    }


    public BarSeries series() { return series; }


    public synchronized void addBar(Bar bar) {
        if (series.isEmpty()) {
            series.addBar(bar);
            return;
        }


        Bar lastBar = series.getLastBar();
// If same end time → ignore. Upstream should only add when a new candle closes
        if (bar.getEndTime().equals(lastBar.getEndTime())) {
            System.out.println("[RollingBarSeries] Same endTime; ignoring interim/duplicate bar: " + bar.getEndTime());
            return;
        }
// Only allow forward-in-time bars
        if (bar.getEndTime().isAfter(lastBar.getEndTime())) {
            series.addBar(bar);
        } else {
            System.out.println("[RollingBarSeries] Ignored out-of-order bar: " + bar.getEndTime());
        }
    }
}