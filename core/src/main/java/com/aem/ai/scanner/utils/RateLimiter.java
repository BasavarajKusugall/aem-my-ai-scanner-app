package com.aem.ai.scanner.utils;


import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple token-bucket style RateLimiter.
 * Limits requests to a maximum rate (permits per second).
 */
public class RateLimiter {

    private final long permitsPerSecond;
    private final long intervalNanos;
    private final AtomicLong nextFreeSlot = new AtomicLong(System.nanoTime());

    public static RateLimiter create(double permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be > 0");
        }
        return new RateLimiter((long) permitsPerSecond);
    }

    private RateLimiter(long permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
        this.intervalNanos = 1_000_000_000L / permitsPerSecond;
    }

    /**
     * Blocks until a permit is available.
     */
    public void acquire() {
        while (true) {
            long now = System.nanoTime();
            long availableAt = nextFreeSlot.get();

            if (now >= availableAt) {
                long next = now + intervalNanos;
                if (nextFreeSlot.compareAndSet(availableAt, next)) {
                    return; // acquired successfully
                }
            } else {
                long sleepNanos = availableAt - now;
                try {
                    Thread.sleep(sleepNanos / 1_000_000L,
                            (int) (sleepNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
