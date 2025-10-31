package com.example.threadpooldemo.service;

import com.example.threadpooldemo.config.RetryConfig;
import com.example.threadpooldemo.dto.TaskStatusDto;
import com.example.threadpooldemo.model.TaskRequest;
import com.example.threadpooldemo.processor.ImageProcessorTask;
import com.example.threadpooldemo.repository.TaskRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessingService.class);

    private final ThreadPoolExecutor executor;
    private final TaskRepository repository;
    private final RetryConfig retryConfig;
    private final Map<String, ImageProcessorTask> runningTasks = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(0);

    public ProcessingService(ThreadPoolExecutor executor, TaskRepository repository, RetryConfig retryConfig) {
        this.executor = executor;
        this.repository = repository;
        this.retryConfig = retryConfig;
    }

    @PostConstruct
    public void init() {
        logger.info("ProcessingService initialized with core={} max={} queue={}",
                executor.getCorePoolSize(), executor.getMaximumPoolSize(), executor.getQueue().size());
    }

    public String submit(TaskRequest request) {
        String id = String.valueOf(idGenerator.incrementAndGet());
        TaskStatusDto dto = new TaskStatusDto(id, request.getFileName(), "QUEUED", null);
        repository.save(dto);

        ImageProcessorTask task = new ImageProcessorTask(id, request.getFileName(), 
            request.getComplexity(), repository, 
            retryConfig.getMaxRetryAttempts(), retryConfig.getRetryDelayMillis());
        
        // Add to running tasks only if initial save was successful
        if (!runningTasks.containsKey(id)) {
            runningTasks.put(id, task);
            try {
                submitWithRetry(task);
                logger.info("Submitted task id={} file={} to executor", id, request.getFileName());
            } catch (Exception e) {
                // Atomic update with expected status check
                if (repository.compareAndUpdateStatus(id, "QUEUED", "REJECTED", null)) {
                    runningTasks.remove(id);
                    logger.error("Failed to submit task {}: {}", id, e.getMessage(), e);
                    throw e;
                }
            }
        } else {
            logger.error("Task ID collision detected for {}", id);
            throw new IllegalStateException("Task ID collision");
        }
        return id;
    }

    private void submitWithRetry(ImageProcessorTask task) {
        executor.execute(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                while (true) {
                    try {
                        task.run();
                        return; // success
                    } catch (RuntimeException e) {
                        if (task.getCurrentAttempt() < task.getMaxRetryAttempts()) {
                            logger.warn("Retrying task {} after failure (attempt {}/{})", 
                                task.getId(), task.getCurrentAttempt(), task.getMaxRetryAttempts());
                            TimeUnit.MILLISECONDS.sleep(task.getRetryDelay());
                            continue;
                        } else {
                            repository.updateStatus(task.getId(), "FAILED_PERMANENTLY", threadName);
                            logger.error("Task {} exhausted retries and failed permanently", task.getId());
                            return;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                repository.updateStatus(task.getId(), "INTERRUPTED", threadName);
            } catch (Exception e) {
                logger.error("Unexpected error in retry loop for {}: {}", task.getId(), e.getMessage());
                repository.updateStatus(task.getId(), "FAILED_PERMANENTLY", threadName);
            }
        });
    }


    public Optional<TaskStatusDto> getStatus(String id) {
        return Optional.ofNullable(repository.find(id));
    }

    public Collection<TaskStatusDto> listAll() {
        return repository.findAll();
    }

    public boolean cancel(String id) {
        ImageProcessorTask task = runningTasks.get(id);
        if (task != null) {
            task.cancel();
            boolean removed = executor.remove(task);
            repository.updateStatus(id, "CANCELLATION_REQUESTED", null);
            logger.info("Cancellation requested for {} removedFromQueue={}", id, removed);
            return true;
        }
        return false;
    }
}
