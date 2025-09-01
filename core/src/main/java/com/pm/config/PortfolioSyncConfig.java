package com.pm.config;


import org.osgi.service.metatype.annotations.*;

@ObjectClassDefinition(name = "BSK Portfolio Sync Scheduler")
public @interface PortfolioSyncConfig {
    @AttributeDefinition(name = "Enabled") boolean enabled() default true;
    @AttributeDefinition(name = "Cron (Quartz)") String cron() default "0 0/5 * * * ?"; // every 5 min
    @AttributeDefinition(name = "Max Parallel Accounts") int parallelism() default 4;
    @AttributeDefinition(name = "HTTP Timeout ms") int httpTimeoutMs() default 15000;
    @AttributeDefinition(name = "Max Retries") int maxRetries() default 3;
    @AttributeDefinition(name = "Initial Backoff ms") int backoffMs() default 500;
}
