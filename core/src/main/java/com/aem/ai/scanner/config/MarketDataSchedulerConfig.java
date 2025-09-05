package com.aem.ai.scanner.config;


import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "BSK Market Data Scheduler Configuration")
public @interface MarketDataSchedulerConfig {

    @AttributeDefinition(
            name = "Timeframe Configuration",
            description = "Configure timeframes and number of candles as comma-separated key:value pairs, e.g., 5m:50,15m:100"
    )
    String timeframeConfig() default "5m:50,15m:100,1h:200,1d:500";
}
