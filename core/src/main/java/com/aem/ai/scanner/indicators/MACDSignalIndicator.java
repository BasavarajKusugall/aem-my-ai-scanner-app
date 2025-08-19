package com.aem.ai.scanner.indicators;


import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;

/**
 * MACDSignalIndicator
 *
 * Simple wrapper that computes the MACD signal line as an EMA of the MACD values.
 * This class provides a default constructor using signal period = 9 (common convention)
 * and also allows a custom signal period.
 *
 * It extends ta4j's EMAIndicator so it can be used anywhere an Indicator<Num> is expected.
 */
public class MACDSignalIndicator extends EMAIndicator {

    /**
     * Create MACD signal indicator using the provided MACD indicator and signal period.
     *
     * @param macdIndicator the MACDIndicator producing MACD values
     * @param signalPeriod  the EMA period used for the signal line (commonly 9)
     */
    public MACDSignalIndicator(MACDIndicator macdIndicator, int signalPeriod) {
        super(macdIndicator, signalPeriod);
    }

    /**
     * Create MACD signal indicator with default signal period = 9.
     *
     * @param macdIndicator the MACDIndicator producing MACD values
     */
    public MACDSignalIndicator(MACDIndicator macdIndicator) {
        super(macdIndicator, 9);
    }
}
