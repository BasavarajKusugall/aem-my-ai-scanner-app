package com.aem.ai.pm.config;


import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "Dhan API Configuration",
        description = "Configuration for Dhan HQ API integration"
)
public @interface DhanConfig {

    @AttributeDefinition(
            name = "Enable Dhan API",
            description = "Enable or disable the Dhan API"
    )
    boolean enable() default true;

    @AttributeDefinition(
            name = "Base URL",
            description = "Dhan API base URL (e.g. https://api.dhan.co/v2)"
    )
    String baseUrl() default "https://api.dhan.co/v2";

    @AttributeDefinition(
            name = "App ID",
            description = "App ID issued by Dhan (if required for partner auth)"
    )
    String appId() default "";

    @AttributeDefinition(
            name = "App Secret",
            description = "App Secret issued by Dhan (if required for partner auth)"
    )
    String appSecret() default "";

    @AttributeDefinition(
            name = "Request Timeout (ms)",
            description = "HTTP request timeout in milliseconds"
    )
    int timeoutMs() default 3000;

    @AttributeDefinition(
            name = "Holdings Endpoint",
            description = "API endpoint for fetching holdings"
    )
    String holdingsEndpoint() default "/holdings";

    @AttributeDefinition(
            name = "Positions Endpoint",
            description = "API endpoint for fetching positions"
    )
    String positionsEndpoint() default "/positions";

    @AttributeDefinition(
            name = "Funds Endpoint",
            description = "API endpoint for fetching funds / cash summary"
    )
    String fundsEndpoint() default "/fundlimit";
}
