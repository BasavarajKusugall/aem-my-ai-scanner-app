package com.pm.utils;


import java.util.function.Supplier;

public class Retry {
    public static <T> T exec(int attempts, long backoffMs, Supplier<T> call) {
        RuntimeException last = null;
        for (int i=0;i<attempts;i++){
            try { return call.get(); } catch (RuntimeException e) {
                last = e;
                try { Thread.sleep(backoffMs * (1L<<i)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw last;
    }
}
