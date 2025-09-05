package com.aem.ai.pm.services.impl;

import com.GenericeConstants;
import com.aem.ai.pm.config.PortfolioSyncConfig;
import com.aem.ai.pm.connectors.BrokerConnector;
import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.pm.dao.PortfolioDao;
import com.aem.ai.pm.dto.BrokerAccountRef;
import com.aem.ai.pm.dto.PortfolioSnapshot;
import com.aem.ai.pm.services.PortfolioSyncService;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

@Component(service = PortfolioSyncService.class, immediate = true)
public class PortfolioSyncServiceImpl implements PortfolioSyncService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSyncServiceImpl.class);

    // ANSI Color Codes
    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";

    private DataSource dataSource;

    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    private volatile List<BrokerConnector> connectors;

    @Reference
    private PortfolioDao dao;

    private volatile PortfolioSyncConfig cfg;

    @Activate
    @Modified
    protected void activate(PortfolioSyncConfig cfg) {
        this.cfg = cfg;
        log.info(CYAN + "PortfolioSyncService activated, enabled={} " + RESET, cfg.enabled());
    }

    @Override
    public void syncAllBrokersOnce() {
        if (!cfg.enabled()) {
            log.info(YELLOW + "Portfolio sync disabled" + RESET);
            return;
        }

        dataSource = dataSourcePoolProviderService.getDataSourceByName(GenericeConstants.MYSQL_PORTFOLIO_MGMT);
        if (dataSource == null) {
            log.error(RED + "DataSource not found! Cannot perform portfolio sync." + RESET);
            return;
        }

        log.info(CYAN + "Starting portfolio sync with {} connectors..." + RESET, connectors.size());

        for (BrokerConnector bc : connectors) {
            try {
                List<BrokerAccountRef> accounts = bc.discoverAccounts();
                log.info(GREEN + "Discovered {} accounts for broker={}" + RESET,
                        accounts.size(), bc.brokerCode());

                for (BrokerAccountRef acc : accounts) {
                    syncAccount(bc, acc); // sequential execution
                }
            } catch (Exception e) {
                log.error(RED + "Error discovering accounts for broker={}: {}" + RESET,
                        bc.brokerCode(), e.getMessage(), e);
            }
        }
    }

    private void syncAccount(BrokerConnector bc, BrokerAccountRef acc) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            PortfolioSnapshot snap = bc.fetchPortfolio(acc);
            dao.updateUserBrokerAccountJson(c, acc, snap);
            dao.upsertAccountSnapshot(c, acc, snap);
            c.commit();

            log.info(GREEN + "Synced broker={} account={} (UBA:{})" + RESET,
                    bc.brokerCode(), acc.externalAccountId, acc.userBrokerAccountId);
        } catch (Exception e) {
            log.error(RED + "Sync failed for broker={} uba={} : {}" + RESET,
                    bc.brokerCode(), acc.userBrokerAccountId, e.getMessage(), e);
        }
    }
}
