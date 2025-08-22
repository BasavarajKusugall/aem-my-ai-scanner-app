package com.aem.ai.scanner.brokers;

import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.model.Candle;
import com.aem.ai.scanner.model.InstrumentSymbol;
import com.aem.ai.scanner.services.HttpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;

import java.time.Instant;
import java.util.*;

@Component(service = MarketDataService.class, immediate = true)
@Designate(ocd = DeltaService.Config.class)
public class DeltaService extends BaseService {

    private static final ObjectMapper om = new ObjectMapper();

    @ObjectClassDefinition(name="Delta Exchange MarketData Service")
    public @interface Config {
        @AttributeDefinition(name="Base URL")
        String base_url() default "https://api.india.delta.exchange";
        @AttributeDefinition(name="Max Retries")
        int retries() default 3;
        @AttributeDefinition(name="Backoff Ms")
        long backoff_ms() default 500;
    }

    private Config cfg;

    @Reference
    private HttpService httpService;

    @Activate @Modified
    protected void activate(Config cfg) { this.cfg = cfg; }

    @Override public String brokerCode() { return "DELTA"; }

    @Override
    public List<Candle> fetchCandles(InstrumentSymbol symbol, String timeframe, int count, boolean historical) throws Exception {
        // Delta uses seconds epoch for start/end, resolution like 5m, 15m, 1h, 1d
        long secondsPer = secondsFor(timeframe);
        long end = Instant.now().getEpochSecond();
        long start = end - (secondsPer * count);
        String url = String.format("%s/v2/history/candles?resolution=%s&symbol=%s&start=%d&end=%d",
                cfg.base_url(), timeframe, symbol.getSymbol(), start, end);

        String body = withRetry(cfg.retries(), cfg.backoff_ms(), () -> {
            try { return httpService.get(url, null); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        List<Candle> out = new ArrayList<>();
        JsonNode root = om.readTree(body);
        JsonNode arr = root.has("result") ? root.path("result") : root.path("data");
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                // Flexible parsing by key presence
                Instant t = n.has("time") ? Instant.ofEpochSecond(n.path("time").asLong()) :
                        (n.has("start") ? Instant.ofEpochSecond(n.path("start").asLong()) : Instant.now());
                double o = n.path("open").asDouble(0);
                double h = n.path("high").asDouble(0);
                double l = n.path("low").asDouble(0);
                double c = n.path("close").asDouble(0);
                double v = n.path("volume").asDouble(0);
                out.add(new Candle(t,o,h,l,c,v));
            }
        }
        return out;
    }

    private static long secondsFor(String tf) {
        if (tf.endsWith("m")) return 60L * Long.parseLong(tf.substring(0, tf.length()-1));
        if (tf.endsWith("h")) return 3600L * Long.parseLong(tf.substring(0, tf.length()-1));
        if (tf.endsWith("d")) return 86400L * Long.parseLong(tf.substring(0, tf.length()-1));
        if (tf.endsWith("w")) return 7L*86400L * Long.parseLong(tf.substring(0, tf.length()-1));
        return 300L;
    }
}
