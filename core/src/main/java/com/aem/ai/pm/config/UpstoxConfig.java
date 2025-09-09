package com.aem.ai.pm.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name="BSK Upstox Connector Config")
public @interface UpstoxConfig {
    @AttributeDefinition(
            name = "Enable Upstox API",
            description = "Enable or disable the Upstox API"
    )
    boolean enable() default true;
    @AttributeDefinition(name="Client ID") String clientId();
    @AttributeDefinition(name="Client Secret") String clientSecret();
    @AttributeDefinition(name="Base URL") String baseUrl() default "https://api.upstox.com/v2";
    @AttributeDefinition(name="Rate Limit QPS") int qps() default 3;

    @AttributeDefinition(
            name = "Holdings Endpoint",
            description = "API endpoint for fetching holdings"
    )
    String holdingsEndpoint() default "/portfolio/long-term-holdings";

    @AttributeDefinition(
            name = "Positions Endpoint",
            description = "API endpoint for fetching positions"
    )
    String positionsEndpoint() default "/portfolio/short-term-positions";

    @AttributeDefinition(
            name = "Funds Endpoint",
            description = "API endpoint for fetching funds / cash summary"
    )
    String fundsEndpoint() default "/user/get-funds-and-margin";
}
