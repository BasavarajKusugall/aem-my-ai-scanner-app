package com.aem.ai.scanner.scheduler;


import com.aem.ai.scanner.model.StockScannerResult;
import com.aem.ai.scanner.services.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.*;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component(
        service = Runnable.class,
        immediate = true

)
@Designate(ocd = GeminiStockScannerScheduler.Config.class)
public class GeminiStockScannerScheduler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(GeminiStockScannerScheduler.class);

    @Reference
    private GeminiService geminiService;

    @Reference
    private Scheduler scheduler;

    private volatile boolean enabled = true;

    private static final ObjectMapper mapper = new ObjectMapper();

    @ObjectClassDefinition(name = "BSK Gemini Stock Scanner Scheduler Config", description = "Scheduler for Gemini AI Stock Scanner")
    public @interface Config {
        @AttributeDefinition(name = "Scheduler Enabled", description = "Enable or Disable Scheduler")
        boolean scheduler_enabled() default true;

        @AttributeDefinition(name="Cron expression")
        String scheduler_expression() default "0 0/3 * * * ?";

        @AttributeDefinition(name = "Allow concurrent execution")
        boolean scheduler_concurrent() default false;
    }

    private String cronExpression;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.enabled = config.scheduler_enabled();
        log.info("Gemini Stock Scanner Scheduler activated. Enabled: {}, Cron: {}", enabled, cronExpression);
    }

    @Override
    public void run() {
        if (!enabled) {
            log.info("Gemini Stock Scanner Scheduler is disabled. Skipping execution.");
            return;
        }

        log.info("Running Gemini Stock Scanner Scheduler at {}", LocalDateTime.now());

        try {
            // Call the new service method
            StockScannerResult result = geminiService.runStockScanner();

            log.info("Stock scanner result parsed successfully. Market Bias: {}", result.getMarketBias());
            log.debug("Full JSON: {}", mapper.writeValueAsString(result));

            // TODO: persist result or trigger downstream workflow

        } catch (Exception e) {
            log.error("Error running Gemini Stock Scanner Scheduler", e);
        }

        log.info("Finished Gemini Stock Scanner Scheduler at {}", LocalDateTime.now());
    }
}
