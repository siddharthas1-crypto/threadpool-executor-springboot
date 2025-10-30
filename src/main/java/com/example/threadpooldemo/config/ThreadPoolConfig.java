package com.example.threadpooldemo.config;

import com.example.threadpooldemo.handler.LoggingRejectedExecutionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {

    @Value("${threadpool.corePoolSize:3}")
    private int corePoolSize;

    @Value("${threadpool.maxPoolSize:6}")
    private int maxPoolSize;

    @Value("${threadpool.keepAliveSeconds:20}")
    private int keepAliveSeconds;

    @Value("${threadpool.queueCapacity:50}")
    private int queueCapacity;

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor taskExecutor() {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueCapacity);
        ThreadFactory threadFactory = new ThreadFactory() {
            private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
            private int counter = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread t = defaultFactory.newThread(r);
                t.setName("image-processor-" + (++counter));
                t.setDaemon(false);
                return t;
            }
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                queue,
                threadFactory,
                new LoggingRejectedExecutionHandler()
        );

        // allow core threads to time out if desired
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
