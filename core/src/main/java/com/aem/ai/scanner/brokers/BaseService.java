package com.aem.ai.scanner.brokers;

import com.aem.ai.scanner.api.MarketDataService;
import com.aem.ai.scanner.utils.Backoff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public abstract class BaseService implements MarketDataService {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected <T> T withRetry(int attempts, long backoffMs, Supplier<T> call) {
        return Backoff.retry(attempts, backoffMs, call);
    }
}
