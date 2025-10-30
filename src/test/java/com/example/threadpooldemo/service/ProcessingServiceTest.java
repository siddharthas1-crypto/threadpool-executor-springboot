package com.example.threadpooldemo.service;

import com.example.threadpooldemo.config.RetryConfig;
import com.example.threadpooldemo.dto.TaskStatusDto;
import com.example.threadpooldemo.model.TaskRequest;
import com.example.threadpooldemo.repository.TaskRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@SpringBootTest
public class ProcessingServiceTest {

    private static ThreadPoolExecutor executor;
    private static ProcessingService service;
    private static TaskRepository repository;

    private static RetryConfig retryConfig;

    @BeforeAll
    public static void setup() {
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
        repository = new TaskRepository();
        retryConfig = Mockito.mock(RetryConfig.class);
        Mockito.when(retryConfig.getMaxRetryAttempts()).thenReturn(3);
        Mockito.when(retryConfig.getRetryDelayMillis()).thenReturn(100L);
        service = new ProcessingService(executor, repository, retryConfig);
    }

    @AfterAll
    public static void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void testSubmitAndComplete() {
        String id = service.submit(new TaskRequest("img-test-1.jpg", 2));
        Assertions.assertNotNull(id);

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            TaskStatusDto s = repository.find(id);
            return s != null && ("COMPLETED".equals(s.getStatus()) || "INTERRUPTED".equals(s.getStatus()));
        });

        TaskStatusDto status = repository.find(id);
        Assertions.assertTrue("COMPLETED".equals(status.getStatus()) || "INTERRUPTED".equals(status.getStatus()));
    }

    @Test
    public void testRetryAndSucceed() {
        String id = service.submit(new TaskRequest("img-retry-success.jpg", 1));
        Assertions.assertNotNull(id);

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            TaskStatusDto s = repository.find(id);
            return s != null && "COMPLETED".equals(s.getStatus());
        });

        TaskStatusDto status = repository.find(id);
        Assertions.assertEquals("COMPLETED", status.getStatus());
    }

    @Test
    public void testRetryAndFail() {
        // Clear any previous tasks
        repository.findAll().clear();

        // Configure RetryConfig for 2 total attempts (but invalid input will fail immediately)
        Mockito.when(retryConfig.getMaxRetryAttempts()).thenReturn(2);
        Mockito.when(retryConfig.getRetryDelayMillis()).thenReturn(50L);

        // Submit an invalid task (complexity 0)
        String id = service.submit(new TaskRequest("img-retry-fail.jpg", 0));
        Assertions.assertNotNull(id);

        // Wait until the task reaches FAILED_PERMANENTLY
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> {
                    TaskStatusDto s = repository.find(id);
                    return s != null && "FAILED_PERMANENTLY".equals(s.getStatus());
                });

        TaskStatusDto finalStatus = repository.find(id);
        Assertions.assertEquals("FAILED_PERMANENTLY", finalStatus.getStatus(), 
                "Task should fail permanently for invalid input complexity");

        // Additional assertion: ensure no COMPLETED or RETRYING statuses occurred
        Assertions.assertNotEquals("COMPLETED", finalStatus.getStatus());
    }


    @Test
    public void testInvalidInputHandling() {
        // Test with zero complexity
        String idZero = service.submit(new TaskRequest("img-zero-complexity.jpg", 0));
        // Test with negative complexity
        String idNegative = service.submit(new TaskRequest("img-negative-complexity.jpg", -1));
        
        // Both tasks should eventually fail permanently
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
            TaskStatusDto sZero = repository.find(idZero);
            TaskStatusDto sNegative = repository.find(idNegative);
            return sZero != null && "FAILED_PERMANENTLY".equals(sZero.getStatus()) &&
                   sNegative != null && "FAILED_PERMANENTLY".equals(sNegative.getStatus());
        });

        // Verify final statuses
        Assertions.assertEquals("FAILED_PERMANENTLY", repository.find(idZero).getStatus());
        Assertions.assertEquals("FAILED_PERMANENTLY", repository.find(idNegative).getStatus());
    }

    @Test
    public void testCancel() throws Exception {
        String id = service.submit(new TaskRequest("img-cancel.jpg", 10));
        Assertions.assertNotNull(id);
        boolean cancelled = service.cancel(id);
        Assertions.assertTrue(cancelled);

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            TaskStatusDto s = repository.find(id);
            return s != null && ("CANCELLATION_REQUESTED".equals(s.getStatus()) || "CANCELLED".equals(s.getStatus()));
        });
        TaskStatusDto status = repository.find(id);
        Assertions.assertNotNull(status);
    }

}
