package com.aem.ai.scanner.utils;

import java.util.function.Supplier;

public class Backoff {
    public static <T> T retry(int attempts, long initialBackoffMs, Supplier<T> call) {
        RuntimeException last = null;
        long back = initialBackoffMs;
        for (int i = 0; i < attempts; i++) {
            try { return call.get(); }
            catch (RuntimeException e) {
                last = e;
                try { Thread.sleep(back); } catch (InterruptedException ie) { Thread.currentThread().interrupt();}
                back *= 2;
            }
        }
        throw last;
    }
}
