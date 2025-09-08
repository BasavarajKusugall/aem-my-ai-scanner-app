package com.aem.ai.pm.config;


import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "Dhan API Configuration",
        description = "Configuration for Dhan HQ API integration"
)
public @interface DhanConfig {

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
}
