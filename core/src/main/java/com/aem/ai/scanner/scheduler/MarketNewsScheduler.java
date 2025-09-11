package com.aem.ai.scanner.scheduler;

import com.aem.ai.scanner.services.GeminiService;
import com.aem.ai.scanner.services.TelegramService;
import com.aem.ai.scanner.services.impl.FinanceNewsService;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduler that fetches Market Finance News every X minutes
 */
@Designate(ocd = MarketNewsScheduler.Config.class)
@Component(service = Runnable.class, immediate = true)
public class MarketNewsScheduler implements Runnable {

    @Reference
    private GeminiService geminiService;

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketNewsScheduler.class);

    @ObjectClassDefinition(
            name = "BSK Market News Fetch Scheduler",
            description = "Fetches latest market finance news updates from Perplexity AI API"
    )
    public @interface Config {
        @AttributeDefinition(name = "Enable scheduler")
        boolean enable() default true;

        @AttributeDefinition(name = "Cron expression",
                description = "CRON format, default: every 5 minutes")
        String scheduler_expression() default "0 9,14,18 * * * ?";
        @AttributeDefinition(name = "Misfire Policy", description = "Reschedule on misfire")
        String scheduler_misfire_policy() default "REPLACE";


        @AttributeDefinition(name = "Allow concurrent execution")
        boolean scheduler_concurrent() default false;

        @AttributeDefinition(name = "Perplexity API Key")
        String perplexity_api_key();
    }


    @Reference
    private FinanceNewsService financeNewsService;

    @Reference
    private TelegramService telegramService;

    private Config config;

    @Activate
    protected void activate(Config config) {
        this.config = config;
        LOGGER.info("✅ MarketNewsScheduler activated with cron: {}, enabled={}",
                config.scheduler_expression(), config.enable());
    }

    @Modified
    protected void modified(Config config) {
        this.config = config;
        LOGGER.info("🔄 MarketNewsScheduler config modified: cron={}, enabled={}",
                config.scheduler_expression(), config.enable());
    }

    @Override
    public void run() {
        if (!config.enable()) {
            LOGGER.debug("⚠️ MarketNewsScheduler is disabled. Skipping run.");
            return;
        }
        try {
            String todayNewsUpdates = geminiService.todayNewsUpdates();
            LOGGER.info("📈 Gemini todayNewsUpdates: {}", todayNewsUpdates);
            if (StringUtils.isNotEmpty(todayNewsUpdates)){
                telegramService.sendMessageDailyNews(todayNewsUpdates);
            }

        } catch (Exception e) {
            LOGGER.error(e.getMessage(),e);
            throw new RuntimeException(e);
        }

    }
}
