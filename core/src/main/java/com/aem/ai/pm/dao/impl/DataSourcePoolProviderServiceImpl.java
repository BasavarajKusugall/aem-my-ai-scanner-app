package com.aem.ai.pm.dao.impl;

import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.pm.utils.OsgiUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

@Component(service = DataSourcePoolProviderService.class, immediate = true)
public class DataSourcePoolProviderServiceImpl implements DataSourcePoolProviderService {

    private static final Logger log = LoggerFactory.getLogger(DataSourcePoolProviderServiceImpl.class);

    private ServiceTracker<DataSource, DataSource> tracker;
    private BundleContext bundleContext;

    // ANSI colors for logs
    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";

    @Activate
    protected void activate(ComponentContext ctx) {
        this.bundleContext = ctx.getBundleContext();

        // Initialize ServiceTracker
        tracker = new ServiceTracker<>(bundleContext, DataSource.class, null);
        tracker.open();
        log.info("{}✅ ServiceTracker for DataSource started{}", GREEN, RESET);
    }

    @Deactivate
    protected void deactivate() {
        if (tracker != null) {
            tracker.close();
            tracker = null;
            log.info("{}🛑 ServiceTracker for DataSource stopped{}", YELLOW, RESET);
        }
    }

    @Override
    public DataSource getDataSourceByName(String name) {
        // Primary approach: use ServiceTracker if initialized
        if (tracker != null) {
            ServiceReference<DataSource>[] refs = tracker.getServiceReferences();
            if (refs != null) {
                for (ServiceReference<DataSource> ref : refs) {
                    if (ref != null && OsgiUtils.hasDataSourceName(ref, name)) {
                        DataSource ds = tracker.getService((ServiceReference<DataSource>) ref);
                        if (ds != null) {
                            log.info("{}✅ Found DataSource via ServiceTracker: {}{}", GREEN, name, RESET);
                            return ds;
                        }
                    }
                }
            } else {
                log.warn("{}⚠️ No DataSources registered in ServiceTracker{}", YELLOW, RESET);
            }
        } else {
            log.warn("{}⚠️ ServiceTracker not initialized. Falling back to BundleContext lookup{}", RED, RESET);
        }
        if (bundleContext == null || bundleContext.getBundle() == null
                || bundleContext.getBundle().getState() != Bundle.ACTIVE) {
            log.error("❌ BundleContext is invalid (bundle not active). Skipping DataSource lookup.");
            return null;
        }

        // Fallback: use direct BundleContext lookup
        if (bundleContext != null) {
            try {
                @SuppressWarnings("unchecked")
                ServiceReference<DataSource>[] refs =
                        (ServiceReference<DataSource>[]) bundleContext.getServiceReferences(DataSource.class.getName(), null);

                if (refs != null) {
                    for (ServiceReference<DataSource> ref : refs) {
                        log.debug("{}🔍 Checking DataSource properties...{}", CYAN, RESET);
                        OsgiUtils.printDataSourceProps(ref);

                        if (OsgiUtils.hasDataSourceName(ref, name)) {
                            DataSource ds = bundleContext.getService(ref);
                            log.info("{}✅ Found DataSource via BundleContext fallback: {}{}", GREEN, name, RESET);
                            return ds;
                        }
                    }
                } else {
                    log.warn("{}⚠️ No DataSource services registered in OSGi!{}", RED, RESET);
                }

            } catch (InvalidSyntaxException e) {
                log.error("{}❌ Invalid OSGi filter while looking up datasource: {}{}", RED, e.getMessage(), RESET, e);
            }
        } else {
            log.error("{}❌ BundleContext is null. Cannot lookup DataSource{}", RED, RESET);
        }

        log.warn("{}⚠️ No DataSource found with name={}{}", RED, name, RESET);
        return null;
    }
}
