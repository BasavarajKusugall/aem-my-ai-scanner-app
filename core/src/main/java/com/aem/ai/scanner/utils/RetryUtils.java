package com.aem.ai.scanner.utils;


import java.util.concurrent.Callable;

/**
 * Retry with exponential backoff.
 */
public class RetryUtils {

    public static <T> T executeWithExponentialBackoff(Callable<T> task, int maxAttempts, long baseMillis) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                return task.call();
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxAttempts) throw e;
                long sleep = baseMillis * (1L << (attempt - 1));
                Thread.sleep(sleep);
            }
        }
    }
}
