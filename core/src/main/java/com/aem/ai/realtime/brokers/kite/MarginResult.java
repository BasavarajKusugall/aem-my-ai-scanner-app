package com.aem.ai.realtime.brokers.kite;
public class MarginResult {
    public final double marginRequired;
    public final long finalQuantity;

    public MarginResult(double marginRequired, long finalQuantity) {
        this.marginRequired = marginRequired;
        this.finalQuantity = finalQuantity;
    }
}
