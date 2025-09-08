package com.aem.ai.pm.dao.impl;

import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.pm.utils.OsgiUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;


@Component(service = DataSourcePoolProviderService.class, immediate = true)
public class DataSourcePoolProviderServiceImpl implements DataSourcePoolProviderService {

    private static final Logger log = LoggerFactory.getLogger(DataSourcePoolProviderServiceImpl.class);

    private BundleContext bundleContext;




    // ANSI colors
    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";

    @Activate
    protected void activate(ComponentContext ctx) {
        this.bundleContext = ctx.getBundleContext();
    }

    /**
     * Lookup a DataSource by its OSGi property (datasource.name).
     */
    @SuppressWarnings("unchecked")
    public DataSource getDataSourceByName(String name) {
        try {
            ServiceReference<DataSource>[] refs =
                    (ServiceReference<DataSource>[]) bundleContext.getServiceReferences(DataSource.class.getName(), null);

            if (refs == null || refs.length == 0) {
                log.warn("{}⚠️ No DataSource services registered in OSGi!{}", RED, RESET);
                return null;
            }

            for (ServiceReference<DataSource> ref : refs) {
                log.debug("{}🔍 Checking DataSource properties...{}", CYAN, RESET);
                OsgiUtils.printDataSourceProps(ref);

                if (OsgiUtils.hasDataSourceName(ref, name)) {
                    DataSource ds = bundleContext.getService(ref);
                    log.info("{}✅ Found matching DataSource: {} = {}{}", GREEN, name, ds, RESET);
                    return ds;
                }
            }

            log.warn("{}⚠️ No matching DataSource found for name={}{}", RED, name, RESET);

        } catch (InvalidSyntaxException e) {
            log.error("{}❌ Invalid OSGi filter while looking up datasource: {}{}", RED, e.getMessage(), RESET, e);
            throw new RuntimeException("Invalid filter for datasource lookup", e);
        }
        return null;
    }

}
