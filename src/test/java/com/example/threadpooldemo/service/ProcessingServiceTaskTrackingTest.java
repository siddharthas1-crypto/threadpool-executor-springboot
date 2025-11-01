package com.example.threadpooldemo.service;

import com.example.threadpooldemo.config.RetryConfig;
import com.example.threadpooldemo.model.TaskRequest;
import com.example.threadpooldemo.repository.TaskRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessingServiceTaskTrackingTest {

    private ThreadPoolExecutor executor;
    private ProcessingService service;

    private TaskRepository repository;
    private RetryConfig retryConfig;

    private void setup(int threads) {
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads);
        repository = new TaskRepository();
        retryConfig = Mockito.mock(RetryConfig.class);
        Mockito.when(retryConfig.getMaxRetryAttempts()).thenReturn(3);
        Mockito.when(retryConfig.getRetryDelayMillis()).thenReturn(50L);
        service = new ProcessingService(executor, repository, retryConfig);
    }

    @AfterEach
    public void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testRunningTasksClearedAfterCompletion() {
        setup(2);
        String id = service.submit(new TaskRequest("img-track-ok.jpg", 1));
        Assertions.assertNotNull(id);

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
            return repository.find(id) != null && "COMPLETED".equals(repository.find(id).getStatus());
        });

        // After completion the running task map should not retain the id
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> service.getRunningTaskIds().isEmpty());
        Assertions.assertTrue(service.getRunningTaskIds().isEmpty());
    }

    @Test
    public void testCancelRemovesTaskFromTracking() {
        setup(1);
        // long running task
        String id = service.submit(new TaskRequest("img-track-cancel.jpg", 10));
        Assertions.assertNotNull(id);

        boolean cancelled = service.cancel(id);
        Assertions.assertTrue(cancelled);

        // Wait until repository has seen cancellation request or cancelled
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
            return repository.find(id) != null && (
                    "CANCELLATION_REQUESTED".equals(repository.find(id).getStatus()) ||
                    "CANCELLED".equals(repository.find(id).getStatus()) ||
                    "INTERRUPTED".equals(repository.find(id).getStatus())
            );
        });

        // Ensure it is not leaking in running task registry
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> !service.getRunningTaskIds().contains(id));
        Assertions.assertFalse(service.getRunningTaskIds().contains(id));
    }

    @Test
    public void testSubmitRejectedWhenExecutorShutdown() {
        setup(1);
        // shutdown the executor so submission will fail
        executor.shutdownNow();

        // Attempting to submit should cause runtime submission failure and repository update to REJECTED
        try {
            service.submit(new TaskRequest("img-reject.jpg", 1));
        } catch (RuntimeException ignored) {
            // expected - submission to shutdown executor
        }

        // Ensure repository contains an entry marked REJECTED for our test file
        List<String> rejected = repository.findAll().stream()
                .filter(s -> "REJECTED".equals(s.getStatus()))
                .map(s -> s.getFileName())
                .collect(Collectors.toList());

        Assertions.assertTrue(rejected.contains("img-reject.jpg"));
    }

    @Test
    public void testCancelNonExistingReturnsFalse() {
        setup(1);
        Assertions.assertFalse(service.cancel("non-existing-id-xyz"));
    }

    @Test
    public void testIdUniquenessForMultipleSubmissions() {
        setup(2);
        String id1 = service.submit(new TaskRequest("img-a.jpg", 1));
        String id2 = service.submit(new TaskRequest("img-b.jpg", 1));
        Assertions.assertNotNull(id1);
        Assertions.assertNotNull(id2);
        Assertions.assertNotEquals(id1, id2);

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
            return repository.find(id1) != null && repository.find(id2) != null &&
                    "COMPLETED".equals(repository.find(id1).getStatus()) &&
                    "COMPLETED".equals(repository.find(id2).getStatus());
        });
    }
}
