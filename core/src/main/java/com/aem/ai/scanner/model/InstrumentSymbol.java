package com.aem.ai.scanner.model;


import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import java.util.Objects;

@Model(adaptables = org.apache.sling.api.resource.Resource.class,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class InstrumentSymbol {

    @ValueMapValue
    private String symbol;

    @ValueMapValue
    private String bestStrategy;

    @ValueMapValue(name = "instrument_key")
    private String instrumentKey;

    // Default constructor required for Sling Models
    public InstrumentSymbol() {
    }

    public InstrumentSymbol(String symbol, String instrumentKey) {
        this.symbol = symbol;
        this.instrumentKey = instrumentKey;
    }

    public InstrumentSymbol(String symbol, String bestStrategy, String instrumentKey) {
        this.symbol = symbol;
        this.bestStrategy = bestStrategy;
        this.instrumentKey = instrumentKey;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getBestStrategy() {
        return bestStrategy;
    }

    public void setBestStrategy(String bestStrategy) {
        this.bestStrategy = bestStrategy;
    }

    public String getInstrumentKey() {
        return instrumentKey;
    }

    public void setInstrumentKey(String instrumentKey) {
        this.instrumentKey = instrumentKey;
    }

    @Override
    public String toString() {
        return "InstrumentSymbol{" +
                "symbol='" + symbol + '\'' +
                ", bestStrategy='" + bestStrategy + '\'' +
                ", instrumentKey='" + instrumentKey + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstrumentSymbol)) return false;
        InstrumentSymbol that = (InstrumentSymbol) o;
        if (instrumentKey != null && that.instrumentKey != null) {
            return instrumentKey.equals(that.instrumentKey);
        }
        return Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        if (instrumentKey != null) return instrumentKey.hashCode();
        return symbol == null ? 0 : symbol.hashCode();
    }
}
