package com.example.threadpooldemo.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public class LoggingRejectedExecutionHandler implements RejectedExecutionHandler {
    private static final Logger logger = LoggerFactory.getLogger(LoggingRejectedExecutionHandler.class);

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        logger.warn("Task rejected: {} | active: {} | queue: {}",
                r, executor.getActiveCount(), executor.getQueue().size());
        try {
            Runnable polled = executor.getQueue().poll();
            if (polled != null) {
                logger.info("Dropped oldest queued task: {} to accept new task: {}", polled, r);
            }
            boolean offered = executor.getQueue().offer(r);
            if (!offered) {
                logger.error("Could not enqueue task after dropping oldest. Dropping new task: {}", r);
            }
        } catch (Exception e) {
            logger.error("Error handling rejected execution", e);
        }
    }
}
