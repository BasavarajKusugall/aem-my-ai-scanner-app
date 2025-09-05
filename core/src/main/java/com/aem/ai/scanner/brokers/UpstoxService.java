package com.aem.ai.scanner.brokers;

import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.model.Candle;
import com.aem.ai.scanner.model.InstrumentSymbol;
import com.aem.ai.scanner.services.HttpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component(service = MarketDataService.class, immediate = true)
@Designate(ocd = UpstoxService.Config.class)
public class UpstoxService extends BaseService {

    @Reference
    private HttpService httpService;

    private static final Logger log = LoggerFactory.getLogger(UpstoxService.class);
    private static final ObjectMapper om = new ObjectMapper();

    @ObjectClassDefinition(name="BSK Upstox MarketData Service")
    public @interface Config {
        @AttributeDefinition(name="Base URL")
        String base_url() default "https://api.upstox.com/v3";

        @AttributeDefinition(name = "Enable")
        boolean enable() default true;

        @AttributeDefinition(name="Auth Header (Bearer ...)")
        String auth_header() default "";

        @AttributeDefinition(name="Max Retries")
        int retries() default 1;

        @AttributeDefinition(name="Backoff Ms")
        long backoff_ms() default 500;
    }

    private Config cfg;

    @Activate @Modified
    protected void activate(Config cfg) { this.cfg = cfg; }

    @Override public String brokerCode() { return "UPSTOX"; }

    @Override
    public boolean enabled() {
        return cfg.enable();
    }

    @Override
    public List<Candle> fetchCandles(InstrumentSymbol symbolOrKey, String timeframe, int count, boolean historical) throws Exception {
        if (!cfg.enable()){
            log.warn(" UpstoxService disabled in config");
            return null;
        }
        String unit = inferUnit(timeframe);
        String interval = inferInterval(timeframe);
        String url;
        if (historical) {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime from = now.minusDays(count);

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            String fromD = dateFormatter.format(from);
            String toD   = dateFormatter.format(now);
            url = String.format("%s/historical-candle/%s/%s/%d/%s/%s",
                    cfg.base_url(),
                    symbolOrKey.getInstrumentKey(),
                    unit,          // e.g. "day" or "5m"
                    1,             // unit count (depends on API spec, often 1)
                    toD,
                    fromD
            );
        } else {
            url = String.format("%s/historical-candle/intraday/%s/%s/%s",
                    cfg.base_url(), symbolOrKey.getInstrumentKey(), unit, interval);
        }

        String finalUrl = url;
        String body = withRetry(cfg.retries(), cfg.backoff_ms(), () -> {
            try {
                Map<String,String> headers = new HashMap<>();
                if (!cfg.auth_header().isBlank()) headers.put("Authorization", cfg.auth_header());
                return httpService.get(finalUrl, headers);
            } catch (Exception e) { throw new RuntimeException(e); }
        });

        List<Candle> out = new ArrayList<>();
        JsonNode arr = om.readTree(body).path("data").path("candles");
        for (JsonNode n : arr) {
            // Upstox returns [time, o,h,l,c, volume, oi]
            Instant t = OffsetDateTime.parse(n.get(0).asText()).toInstant();
            double o = n.get(1).asDouble();
            double h = n.get(2).asDouble();
            double l = n.get(3).asDouble();
            double c = n.get(4).asDouble();
            double v = n.get(5).asDouble();
            out.add(new Candle(t, o, h, l, c, v));
        }
        if (!out.isEmpty()) {
            out.sort(Comparator.comparing(Candle::getTime));
            log.debug("Fetched {} candles for {} {}", out.size(), symbolOrKey, timeframe);
        }
        return out;
    }

    private String inferUnit(String tf) {
        if (tf.endsWith("m")) return "minutes";
        if (tf.endsWith("h")) return "hours";
        if (tf.endsWith("d")) return "days";
        if (tf.endsWith("w")) return "weeks";
        return "minutes";
    }
    private  String inferInterval(String tf) {
        return tf.substring(0, tf.length()-1);
    }
    private static long minutesFor(String tf) {
        if (tf.endsWith("m")) return Long.parseLong(tf.substring(0, tf.length()-1));
        if (tf.endsWith("h")) return 60L * Long.parseLong(tf.substring(0, tf.length()-1));
        if (tf.endsWith("d")) return 60L*24L * Long.parseLong(tf.substring(0, tf.length()-1));
        if (tf.endsWith("w")) return 60L*24L*7L * Long.parseLong(tf.substring(0, tf.length()-1));
        return 5L;
    }
    private static DateTimeFormatter nowFormatter() {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    }
}
