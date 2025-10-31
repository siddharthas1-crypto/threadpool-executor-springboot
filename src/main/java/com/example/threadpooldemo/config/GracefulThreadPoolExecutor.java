package com.example.threadpooldemo.config;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * Small subclass that exposes a graceful shutdown helper which will be
 * invoked by Spring's destroyMethod to await termination.
 */
public class GracefulThreadPoolExecutor extends ThreadPoolExecutor {

    public GracefulThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                      TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                      ThreadFactory threadFactory,
                                      RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /**
     * Shutdown and wait for termination for up to the given timeout (seconds).
     * If tasks don't terminate in time, force shutdownNow().
     *
     * This method is intentionally simple and conservative: it first calls
     * shutdown(), then waits up to 5 seconds before forcing shutdown.
     */
    public void shutdownGracefully() {
        shutdown();
        try {
            if (!awaitTermination(5, TimeUnit.SECONDS)) {
                // Try a more forceful shutdown
                shutdownNow();
                awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownNow();
        }
    }
}
