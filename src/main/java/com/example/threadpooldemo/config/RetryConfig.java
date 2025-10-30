package com.example.threadpooldemo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RetryConfig {
    @Value("${threadpool.retry.maxAttempts:3}")
    private int maxRetryAttempts;

    @Value("${threadpool.retry.delayMillis:1000}")
    private long retryDelayMillis;

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public long getRetryDelayMillis() {
        return retryDelayMillis;
    }
}