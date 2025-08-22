package com.aem.ai.scanner.utils;

import java.util.*;
import java.util.regex.*;

public class Timeframes {
    private static final Pattern P = Pattern.compile("\\s*([0-9]+[mhdw]|[1-9][0-9]*[mhdw])\\s*:\\s*([0-9]+)\\s*");

    /** Parse config string like "5m:120, 15m:64, 1h:48, 1d:365" */
    public static Map<String,Integer> parse(String spec) {
        Map<String,Integer> out = new LinkedHashMap<>();
        if (spec == null || spec.trim().isEmpty()) return out;
        for (String part : spec.split(",")) {
            Matcher m = P.matcher(part);
            if (m.matches()) {
                out.put(m.group(1), Integer.parseInt(m.group(2)));
            }
        }
        return out;
    }

    public static boolean isHistoricalBucket(String tf) {
        // days/weeks/months => historical; intraday for m/h (except current day window logic can be handled by service)
        return tf.endsWith("d") || tf.endsWith("w");
    }
}
