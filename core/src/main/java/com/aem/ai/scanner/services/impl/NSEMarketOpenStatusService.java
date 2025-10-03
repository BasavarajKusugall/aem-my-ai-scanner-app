package com.aem.ai.scanner.services.impl;

import com.aem.ai.scanner.model.MarketStatusResult;
import com.aem.ai.scanner.scheduler.LiveScannerNSE;
import com.aem.ai.scanner.services.HttpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component(
        service = NSEMarketOpenStatusService.class,
        immediate = true
)
@Designate(ocd = NSEMarketOpenStatusService.Config.class)
public class NSEMarketOpenStatusService {
    private static final Logger LOG = LoggerFactory.getLogger(NSEMarketOpenStatusService.class);


    @Reference
    private HttpService httpService;

    private volatile int tradeCloseBufferMinutes;

    private static final String MARKET_TIMINGS_URL = "https://api.upstox.com/v2/market/timings/";

    @Activate
    @Modified
    protected void activate(Config config) {
        this.tradeCloseBufferMinutes = config.tradeCloseBufferMinutes();
    }

    public MarketStatusResult getMarketStatus() throws Exception {
        MarketStatusResult result = new MarketStatusResult();

        // 1. Prepare today’s date in yyyy-MM-dd format
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 2. Call Upstox endpoint
        String url = MARKET_TIMINGS_URL + today;
        String response = httpService.get(url);

        // 3. Parse JSON
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);

        JsonNode dataArray = root.has("data") ? root.path("data") : null;
        JsonNode nseNode = null;
        if (dataArray == null || !dataArray.isArray()) {
            LOG.info("No market timings data found in response");
            result.setMarketStatus("CLOSED");
            return result;
        }

        for (JsonNode node : dataArray) {
            if ("NSE".equalsIgnoreCase(node.path("exchange").asText())) {
                nseNode = node;
                break;
            }
        }

        if (nseNode == null) {
            throw new RuntimeException("NSE timings not found in response");
        }

        long startMillis = nseNode.path("start_time").asLong();
        long endMillis = nseNode.path("end_time").asLong();

        LocalDateTime marketOpen = Instant.ofEpochMilli(startMillis)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime();

        LocalDateTime marketClose = Instant.ofEpochMilli(endMillis)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime();

        // Apply buffer minutes to close time
        LocalDateTime adjustedClose = marketClose.minusMinutes(tradeCloseBufferMinutes);

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

        String status = (now.isAfter(marketOpen) && now.isBefore(adjustedClose))
                ? "OPEN"
                : "CLOSED";

        // 4. Return structured result
        result.setExchange("NSE");
        result.setMarketStatus(status);
        result.setCurrentTime(now.toString());
        result.setMarketOpenTime(marketOpen.toString());
        result.setMarketCloseTime(marketClose.toString());
        result.setAdjustedCloseTime(adjustedClose.toString());

        return result;
    }

    @ObjectClassDefinition(
            name = "NSE Market Open Status Service",
            description = "Checks NSE market open/close status and applies trade buffer before close"
    )
    public @interface Config {
        @AttributeDefinition(
                name = "Trade Close Buffer (minutes)",
                description = "How many minutes before official market close to treat as CLOSED"
        )
        int tradeCloseBufferMinutes() default 15;
    }
}
