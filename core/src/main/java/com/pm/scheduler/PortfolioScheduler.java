package com.pm.scheduler;

import com.aem.ai.scanner.services.GeminiService;
import com.aem.ai.scanner.services.TelegramService;
import com.pm.dao.PortfolioDao;
import com.pm.dto.UserBrokerAccount;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Designate(ocd = PortfolioScheduler.Config.class)
@Component(service = Runnable.class, immediate = true)
public class PortfolioScheduler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(PortfolioScheduler.class);

    @Reference
    private PortfolioDao portfolioDAO;

    @Reference
    private GeminiService geminiService;

    @Reference
    private TelegramService telegramService;

    private Config config;

    @ObjectClassDefinition(
            name = "BSK Portfolio Analysis Scheduler",
            description = "Runs Gemini AI portfolio analysis and sends results to Telegram"
    )
    public @interface Config {
        @AttributeDefinition(
                name = "Enable scheduler",
                description = "Enable or disable the scheduler"
        )
        boolean enable() default true;

        @AttributeDefinition(
                name = "Cron expression",
                description = "When to run (default: every 5 minutes)"
        )
        String scheduler_expression() default "0 0/5 * * * ?";

        @AttributeDefinition(
                name = "Allow concurrent execution",
                description = "Whether multiple jobs can run in parallel"
        )
        boolean scheduler_concurrent() default false;
    }

    @Activate
    protected void activate(Config config) {
        this.config = config;
        log.info("✅ PortfolioScheduler activated with cron={}, enabled={}",
                config.scheduler_expression(), config.enable());
    }

    @Modified
    protected void modified(Config config) {
        this.config = config;
        log.info("🔄 PortfolioScheduler config modified: cron={}, enabled={}",
                config.scheduler_expression(), config.enable());
    }

    @Override
    public void run() {
        if (!config.enable()) {
            log.debug("⚠️ PortfolioScheduler disabled. Skipping run.");
            return;
        }

        try {
            log.info("▶️ Running Portfolio analysis job...");
            List<UserBrokerAccount> accounts = portfolioDAO.fetchActiveAccounts();

            for (UserBrokerAccount acc : accounts) {
                try {
                    String combinedPortfolio = "Holdings: " + acc.getPortfolioHoldingJson()
                            + "\nPositions: " + acc.getPortfolioPositionsJson();

                    String aiResponse = geminiService.analyzePortfolio(combinedPortfolio);

                    // send to specific Telegram user/group
                    telegramService.sendMessageToUser(acc.getTelegramBotUserId(),
                            "[Portfolio Update]\n" + aiResponse);

                    log.info("✅ Portfolio analysis sent for user_id={} broker_account_ref={}",
                            acc.getUserId(), acc.getBrokerAccountRef());
                } catch (Exception e) {
                    log.error("❌ Error processing account {}", acc.getAccountId(), e);
                }
            }
            log.info("✅ Portfolio analysis job completed successfully.");

        } catch (Exception e) {
            log.error("❌ PortfolioScheduler failed: {}", e.getMessage(), e);
        }
    }
}
