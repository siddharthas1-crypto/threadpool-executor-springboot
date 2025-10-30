package com.example.threadpooldemo.handler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class LoggingRejectedExecutionHandlerTest {

    @Test
    public void testRejectedHandling() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1,1,1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1), new LoggingRejectedExecutionHandler());

        Runnable longTask = () -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        };

        executor.execute(longTask); // occupies thread
        executor.execute(longTask); // occupies queue
        // next task should be rejected and handled by LoggingRejectedExecutionHandler
        executor.execute(longTask);

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }
}
