package com.aem.ai.pm.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name="BSK Zerodha (Kite) Connector Config")
public @interface KiteConfig {
    @AttributeDefinition(
            name = "Enable Zerodha API",
            description = "Enable or disable the Zerodha API"
    )
    boolean enable() default true;
    @AttributeDefinition(name="API Key") String apiKey();
    @AttributeDefinition(name="API Secret") String apiSecret();
    @AttributeDefinition(name="Base URL") String baseUrl() default "https://api.kite.trade";
    @AttributeDefinition(name="Login URL") String loginUrl() default "https://kite.zerodha.com/connect/login?v=3&api_key=";
    @AttributeDefinition(name="Rate Limit QPS") int qps() default 3;

    @AttributeDefinition(
            name = "Holdings Endpoint",
            description = "API endpoint for fetching holdings"
    )
    String holdingsEndpoint() default "/portfolio/holdings";

    @AttributeDefinition(
            name = "Positions Endpoint",
            description = "API endpoint for fetching positions"
    )
    String positionsEndpoint() default "/portfolio/positions";

    @AttributeDefinition(
            name = "Funds Endpoint",
            description = "API endpoint for fetching funds / cash summary"
    )
    String fundsEndpoint() default "/user/margins/equity";
}

