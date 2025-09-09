package com.aem.ai.scanner.model;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.num.Num;

public class StopLossTargetCalculator {

    /**
     * Compute StopLoss and Target with hybrid logic:
     * - SL: last swing low/high +/- ATR buffer
     * - TP: derived from SL with fixed RR = 1:2
     */
    public static double[] computeSLTP(BarSeries series,
                                       int atrPeriod,
                                       int swingPeriod,
                                       double atrBuffer,
                                       double entryPrice,
                                       boolean isBuy) {
        int lastIndex = series.getEndIndex();
        if (lastIndex < swingPeriod) return new double[]{Double.NaN, Double.NaN};

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ATRIndicator atr = new ATRIndicator(series, atrPeriod);

        // Swing indicators
        LowestValueIndicator swingLow = new LowestValueIndicator(new LowPriceIndicator(series), swingPeriod);
        HighestValueIndicator swingHigh = new HighestValueIndicator(new HighPriceIndicator(series), swingPeriod);

        Num atrVal = atr.getValue(lastIndex);
        double atrD = atrVal.doubleValue();

        double stopLoss;
        if (isBuy) {
            double lastSwingLow = swingLow.getValue(lastIndex).doubleValue();
            stopLoss = Math.min(entryPrice, lastSwingLow - atrBuffer * atrD);
        } else {
            double lastSwingHigh = swingHigh.getValue(lastIndex).doubleValue();
            stopLoss = Math.max(entryPrice, lastSwingHigh + atrBuffer * atrD);
        }

        // Risk
        double risk = Math.abs(entryPrice - stopLoss);

        // Target (RR = 1:2)
        double target = isBuy ? entryPrice + risk * 2 : entryPrice - risk * 2;

        return new double[]{stopLoss, target};
    }
}
