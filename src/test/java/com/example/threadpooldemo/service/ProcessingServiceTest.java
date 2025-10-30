package com.example.threadpooldemo.service;

import com.example.threadpooldemo.dto.TaskStatusDto;
import com.example.threadpooldemo.model.TaskRequest;
import com.example.threadpooldemo.repository.TaskRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@SpringBootTest
public class ProcessingServiceTest {

    private static ThreadPoolExecutor executor;
    private static ProcessingService service;
    private static TaskRepository repository;

    @BeforeAll
    public static void setup() {
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
        repository = new TaskRepository();
        service = new ProcessingService(executor, repository);
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
    
    @Test
    public void testCancelAfterCompletion_shouldMarkAsCancelled() {
        String id = service.submit(new TaskRequest("late-cancel.jpg", 1));
        Awaitility.await().atMost(Duration.ofSeconds(3))
                  .until(() -> "COMPLETED".equals(repository.find(id).getStatus()));
        boolean cancelled = service.cancel(id);
        Assertions.assertTrue(cancelled);
        Assertions.assertEquals("CANCELLED", repository.find(id).getStatus());
    }

}
