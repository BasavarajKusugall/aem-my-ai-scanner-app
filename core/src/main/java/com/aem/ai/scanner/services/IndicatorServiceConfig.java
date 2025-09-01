package com.aem.ai.scanner.services;


import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "BSK Indicator Service Configuration",
        description = "Configuration for TA4J-based Indicator Service"
)
public @interface IndicatorServiceConfig {

    @AttributeDefinition(
            name = "Maximum Bars",
            description = "Maximum number of bars to keep in series"
    )
    int maxBars() default 800;

    @AttributeDefinition(
            name = "Default Resolution",
            description = "Fallback resolution if not provided (e.g. 5m, 15m, 1d)"
    )
    String defaultResolution() default "5m";
}
