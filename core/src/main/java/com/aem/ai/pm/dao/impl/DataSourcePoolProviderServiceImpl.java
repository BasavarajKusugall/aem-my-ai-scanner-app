package com.aem.ai.pm.dao.impl;

import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.pm.utils.OsgiUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;


@Component(service = DataSourcePoolProviderService.class, immediate = true)
public class DataSourcePoolProviderServiceImpl implements DataSourcePoolProviderService {

    private static final Logger log = LoggerFactory.getLogger(DataSourcePoolProviderServiceImpl.class);

    private ServiceTracker<DataSource, DataSource> tracker;

    @Activate
    protected void activate(ComponentContext ctx) {
        tracker = new ServiceTracker<>(ctx.getBundleContext(), DataSource.class, null);
        tracker.open();
        log.info("✅ ServiceTracker for DataSource started");
    }

    @Deactivate
    protected void deactivate() {
        if (tracker != null) {
            tracker.close();
            tracker = null;
            log.info("🛑 ServiceTracker for DataSource stopped");
        }
    }

    @Override
    public DataSource getDataSourceByName(String name) {
        if (tracker == null) {
            log.error("❌ ServiceTracker not initialized");
            return null;
        }

        ServiceReference<DataSource>[] refs = (ServiceReference<DataSource>[]) tracker.getServiceReferences();
        if (refs == null) {
            log.warn("⚠️ No DataSources registered");
            return null;
        }

        for (ServiceReference<DataSource> ref : refs) {
            if (OsgiUtils.hasDataSourceName(ref, name)) {
                return tracker.getService(ref);
            }
        }

        log.warn("⚠️ No DataSource found with name={}", name);
        return null;
    }
}

