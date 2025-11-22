package com.app.estudiaapp.utils;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

public class RetryUtils {

    public static <T> T runWithRetries(Callable<T> op, int attempts, long baseDelayMs, Predicate<Exception> shouldRetry) throws Exception {
        Random rnd = new Random();
        Exception lastEx = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return op.call();
            } catch (Exception e) {
                lastEx = e;
                boolean retry = true;
                try { retry = shouldRetry == null ? true : shouldRetry.test(e); } catch (Exception ignored) {}
                if (!retry) throw e;
                // exponential backoff with jitter
                long delay = baseDelayMs * (1L << i);
                long jitter = (long)(rnd.nextDouble() * baseDelayMs);
                long sleepMs = delay + jitter;
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }
        throw lastEx;
    }
}
