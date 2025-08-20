package com.pm.utils;

import javax.sql.DataSource;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class OsgiUtils {

    private static final Logger log = LoggerFactory.getLogger(OsgiUtils.class);

    // ANSI colors
    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";

    /**
     * Print all DataSource service properties with colored logging
     */
    public static void printDataSourceProps(ServiceReference<DataSource> ref) {
        Arrays.stream(ref.getPropertyKeys()).forEach(key -> {
            Object val = ref.getProperty(key);
            log.info("{}[DS PROP]{} {}{}{} = {}{}{}",
                    CYAN, RESET, YELLOW, key, RESET, GREEN, val, RESET);
        });
    }

    /**
     * Check if a DataSource has a matching datasource.name property
     */
    public static boolean hasDataSourceName(ServiceReference<DataSource> ref, String targetName) {
        boolean result = Arrays.stream(ref.getPropertyKeys())
                .filter(key -> targetName.equals(key))
                .map(ref::getProperty)
                .anyMatch(val -> targetName.equals(val));
        if (result) {
            log.info("{}[MATCH]{} Found datasource.name={}{}", GREEN, RESET, targetName, RESET);
        } else {
            log.debug("{}[NO MATCH]{} Expected={}",
                    RED, RESET, targetName );
        }

        return result;
    }
}
