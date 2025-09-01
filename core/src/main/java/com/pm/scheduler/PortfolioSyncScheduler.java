package com.pm.scheduler;

import com.pm.dao.DataSourcePoolProviderService;
import com.pm.dao.impl.DataSourcePoolProviderServiceImpl;
import com.pm.services.PortfolioSyncService;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

@Designate(ocd = PortfolioSyncScheduler.Config.class)
@Component(service = Runnable.class, immediate = true)
public class PortfolioSyncScheduler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSyncScheduler.class);

    @Reference
    private PortfolioSyncService sync;

    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderServiceImpl;

    private Config config;

    @ObjectClassDefinition(
            name = "BSK Portfolio Sync Scheduler",
            description = "Scheduler that syncs user portfolios from all brokers"
    )
    public @interface Config {
        @AttributeDefinition(
                name = "Enable scheduler",
                description = "Enable or disable portfolio sync job"
        )
        boolean enable() default true;

        @AttributeDefinition(
                name = "Cron expression",
                description = "CRON expression for when to run (default: every 5 minutes)"
        )
        String scheduler_expression() default "0 0/5 * * * ?";

        @AttributeDefinition(
                name = "Allow concurrent execution",
                description = "Whether job can run in parallel"
        )
        boolean scheduler_concurrent() default false;
    }

    @Activate
    protected void activate(Config config) {
        this.config = config;
        log.info("✅ PortfolioSyncScheduler activated with cron={}, enabled={}",
                config.scheduler_expression(), config.enable());
    }

    @Modified
    protected void modified(Config config) {
        this.config = config;
        log.info("🔄 PortfolioSyncScheduler config modified: cron={}, enabled={}",
                config.scheduler_expression(), config.enable());
    }

    @Override
    public void run() {
        if (!config.enable()) {
            log.debug("⚠️ PortfolioSyncScheduler disabled. Skipping run.");
            return;
        }
        try {
            log.info("▶️ Running Portfolio Sync job...");
            sync.syncAllBrokersOnce();
            log.info("✅ Portfolio Sync job completed successfully.");
        } catch (Exception e) {
            log.error("❌ Error during portfolio sync: {}", e.getMessage(), e);
        }
    }
}
