package com.example.threadpooldemo.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

@Component
public class ThreadPoolMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolMonitor.class);

    private final ThreadPoolExecutor executor;
    private final int monitorIntervalSeconds;

    public ThreadPoolMonitor(ThreadPoolExecutor executor,
                             @Value("${threadpool.monitorIntervalSeconds:5}") int monitorIntervalSeconds) {
        this.executor = executor;
        this.monitorIntervalSeconds = monitorIntervalSeconds;
    }

    @Scheduled(fixedDelayString = "${threadpool.monitorIntervalSeconds:5}000")
    public void report() {
        logger.info("ThreadPool stats - core={}, active={}, max={}, completed={}, queued={}",
                executor.getCorePoolSize(),
                executor.getActiveCount(),
                executor.getMaximumPoolSize(),
                executor.getCompletedTaskCount(),
                executor.getQueue().size());
    }
}
