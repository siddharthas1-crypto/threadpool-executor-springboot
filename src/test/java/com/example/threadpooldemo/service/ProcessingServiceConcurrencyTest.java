package com.example.threadpooldemo.service;

import com.example.threadpooldemo.config.RetryConfig;
import com.example.threadpooldemo.dto.TaskStatusDto;
import com.example.threadpooldemo.model.TaskRequest;
import com.example.threadpooldemo.repository.TaskRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ProcessingServiceConcurrencyTest {

    private static ThreadPoolExecutor executor;
    private static ProcessingService service;
    private static TaskRepository repository;
    private static RetryConfig retryConfig;
    private static ExecutorService testExecutor;

    @BeforeAll
    public static void setup() {
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
        repository = new TaskRepository();
        retryConfig = Mockito.mock(RetryConfig.class);
        Mockito.when(retryConfig.getMaxRetryAttempts()).thenReturn(3);
        Mockito.when(retryConfig.getRetryDelayMillis()).thenReturn(100L);
        service = new ProcessingService(executor, repository, retryConfig);
        testExecutor = Executors.newFixedThreadPool(20); // For concurrent test execution
    }

    @AfterAll
    public static void tearDown() {
        executor.shutdownNow();
        testExecutor.shutdownNow();
    }

    @Test
    public void testConcurrentTaskSubmission() throws Exception {
        int numTasks = 100;
        Set<String> allIds = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Submit tasks concurrently
        for (int i = 0; i < numTasks; i++) {
            int complexity = 1 + (i % 3); // Vary complexity between 1-3
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String id = service.submit(new TaskRequest("test.jpg", complexity));
                assertTrue(allIds.add(id), "Duplicate task ID detected: " + id);
            }, testExecutor);
            futures.add(future);
        }

        // Wait for all submissions to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        // Verify unique IDs
        assertEquals(numTasks, allIds.size(), "Expected unique task IDs");

        // Wait for all tasks to complete or fail
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> repository.findAll().stream()
                .map(TaskStatusDto::getStatus)
                .allMatch(status -> Set.of("COMPLETED", "FAILED_PERMANENTLY").contains(status)));

        // Verify final states
        Collection<TaskStatusDto> results = repository.findAll();
        assertEquals(numTasks, results.size(), "All tasks should be tracked");

        // Count final states
        Map<String, Long> statusCounts = results.stream()
            .collect(Collectors.groupingBy(TaskStatusDto::getStatus, Collectors.counting()));
        
        System.out.println("Final status counts: " + statusCounts);
        
        assertTrue(statusCounts.getOrDefault("COMPLETED", 0L) > 0, 
            "Some tasks should complete successfully");
    }

    @Test
    public void testConcurrentStatusUpdates() throws Exception {
        String id = service.submit(new TaskRequest("concurrent-updates.jpg", 5));
        int numUpdates = 50;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Perform concurrent updates
        for (int i = 0; i < numUpdates; i++) {
            final int updateNum = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                repository.updateStatus(id, "UPDATE_" + updateNum, "Thread-" + updateNum);
            }, testExecutor);
            futures.add(future);
        }

        // Wait for all updates to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);

        // Verify the task exists and has a valid status
        TaskStatusDto finalStatus = repository.find(id);
        assertNotNull(finalStatus, "Task should exist");
        assertTrue(finalStatus.getStatus().startsWith("UPDATE_"), 
            "Status should be from our updates");
    }

    @Test
    public void testConcurrentRetries() throws Exception {
        // Configure for multiple retry attempts
        Mockito.when(retryConfig.getMaxRetryAttempts()).thenReturn(5);
        Mockito.when(retryConfig.getRetryDelayMillis()).thenReturn(50L);

        // Submit multiple tasks that will need retries
        int numTasks = 20;
        List<String> taskIds = IntStream.range(0, numTasks)
            .mapToObj(i -> service.submit(new TaskRequest("retry-test-" + i + ".jpg", 0)))
            .collect(Collectors.toList());

        // Wait for all tasks to reach final state
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> taskIds.stream()
                .map(repository::find)
                .allMatch(status -> status != null && 
                    status.getStatus().equals("FAILED_PERMANENTLY")));

        // Verify each task went through the expected states
        for (String taskId : taskIds) {
            TaskStatusDto status = repository.find(taskId);
            assertNotNull(status);
            assertEquals("FAILED_PERMANENTLY", status.getStatus(), 
                "Task " + taskId + " should have failed permanently");
        }
    }
}