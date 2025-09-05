package com.aem.ai.pm.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name="BSK Upstox Connector Config")
public @interface UpstoxConfig {
    @AttributeDefinition(name="Client ID") String clientId();
    @AttributeDefinition(name="Client Secret") String clientSecret();
    @AttributeDefinition(name="Base URL") String baseUrl() default "https://api.upstox.com/v2";
    @AttributeDefinition(name="Rate Limit QPS") int qps() default 3;
}
