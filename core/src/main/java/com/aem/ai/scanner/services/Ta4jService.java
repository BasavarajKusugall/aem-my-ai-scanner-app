package com.aem.ai.scanner.services;


import com.aem.ai.scanner.model.Candle;
import org.ta4j.core.BarSeries;

import java.util.List;

public interface Ta4jService {

    BarSeries buildSeries(String name, List<Candle> candles,String timeframe);

    double rsi(BarSeries series, int barIndex, int timeFrame);

    IndicatorsSnapshot computeIndicators(BarSeries series);

    class IndicatorsSnapshot {
        public final double rsi;
        public final double macd;
        public final double macdSignal;
        public final double emaFast;
        public final double emaSlow;
        public final double atr;
        public final double vwap;

        public IndicatorsSnapshot(double rsi, double macd, double macdSignal,
                                  double emaFast, double emaSlow,
                                  double atr, double vwap) {
            this.rsi = rsi;
            this.macd = macd;
            this.macdSignal = macdSignal;
            this.emaFast = emaFast;
            this.emaSlow = emaSlow;
            this.atr = atr;
            this.vwap = vwap;
        }
    }
}
