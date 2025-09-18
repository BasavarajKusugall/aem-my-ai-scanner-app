package com.aem.ai.scanner.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VolumeStock extends StockPick {
    @JsonProperty("volume_change_percent")
    private double volumeChangePercent;

    @JsonProperty("price_action")
    private String priceAction; // Breakout / Breakdown / Gap Up / Gap Down

    public double getVolumeChangePercent() { return volumeChangePercent; }
    public void setVolumeChangePercent(double volumeChangePercent) { this.volumeChangePercent = volumeChangePercent; }

    public String getPriceAction() { return priceAction; }
    public void setPriceAction(String priceAction) { this.priceAction = priceAction; }
}