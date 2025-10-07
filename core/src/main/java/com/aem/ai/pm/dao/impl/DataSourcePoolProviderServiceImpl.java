package com.aem.ai.pm.dao.impl;

import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component(service = DataSourcePoolProviderService.class, immediate = true)
public class DataSourcePoolProviderServiceImpl implements DataSourcePoolProviderService {
    private static final Logger log = LoggerFactory.getLogger(DataSourcePoolProviderServiceImpl.class);

    @Reference
    private DataSource dataSource;
    //private ServiceTracker<DataSource, DataSource> tracker;
    //private BundleContext bundleContext;


    // ANSI colors for logs
    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";

    /*@Activate
    protected void activate(ComponentContext ctx) {
        this.bundleContext = ctx.getBundleContext();

        // Initialize ServiceTracker
        tracker = new ServiceTracker<>(bundleContext, DataSource.class, null);
        tracker.open();
        log.info("{}✅ ServiceTracker for DataSource started{}", GREEN, RESET);
    }*/

   /* @Deactivate
    protected void deactivate() {
        if (tracker != null) {
            tracker.close();
            tracker = null;
            log.info("{}🛑 ServiceTracker for DataSource stopped{}", YELLOW, RESET);
        }
    }*/

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource != null) {
            Connection connection = dataSource.getConnection();
            if (connection != null || connection.isValid(2)){
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(true); // ensure commit per statement
                log.info("{}✅ Returning injected DataSource instance for name={}{}", GREEN,  RESET);
                return connection;
            }else {
                Connection newConnection = dataSource.getConnection();
                newConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(true); // ensure commit per statement
                return newConnection;
            }
        }
        log.error("{}⚠️ No DataSource found with name={}{}", RED,  RESET);
        return null;
    }
}
