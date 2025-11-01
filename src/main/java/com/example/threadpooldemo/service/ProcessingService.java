package com.example.threadpooldemo.service;

import com.example.threadpooldemo.config.RetryConfig;
import com.example.threadpooldemo.dto.TaskStatusDto;
import com.example.threadpooldemo.model.TaskRequest;
import com.example.threadpooldemo.processor.ImageProcessorTask;
import com.example.threadpooldemo.repository.TaskRepositoryPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
    private final TaskRepositoryPort repository;
    private final RetryConfig retryConfig;
    private final Map<String, TaskHandle> runningTasks = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(0);

    public ProcessingService(ThreadPoolExecutor executor, TaskRepositoryPort repository, RetryConfig retryConfig) {
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
        // Add to running tasks only if initial save was successful.
        if (runningTasks.containsKey(id)) {
            logger.error("Task ID collision detected for {}", id);
            throw new IllegalStateException("Task ID collision");
        }

        // Create a TaskHandle placeholder so cancel() can see the task immediately.
        TaskHandle handle = new TaskHandle(task);
        runningTasks.put(id, handle);

        // Build the wrapper runnable that performs the retry loop and ensures cleanup.
        Runnable wrapper = () -> {
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
                            try {
                                TimeUnit.MILLISECONDS.sleep(task.getRetryDelay());
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                repository.updateStatus(task.getId(), "INTERRUPTED", threadName);
                                return;
                            }
                            continue;
                        } else {
                            repository.updateStatus(task.getId(), "FAILED_PERMANENTLY", threadName);
                            logger.error("Task {} exhausted retries and failed permanently", task.getId());
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Unexpected error in retry loop for {}: {}", task.getId(), e.getMessage());
                repository.updateStatus(task.getId(), "FAILED_PERMANENTLY", threadName);
            } finally {
                // Ensure we don't leak memory by removing the handle when done
                runningTasks.remove(task.getId());
            }
        };

        // Store wrapper in handle (useful for removal from queue if needed)
        handle.setWrapper(wrapper);

        try {
            // submit returns a Future; keep it so cancel() can cancel queued or running tasks
            java.util.concurrent.Future<?> f = executor.submit(wrapper);
            handle.setFuture(f);
            logger.info("Submitted task id={} file={} to executor", id, request.getFileName());
        } catch (RuntimeException e) {
            // Submission failed - remove placeholder and mark as rejected
            runningTasks.remove(id);
            if (repository.compareAndUpdateStatus(id, "QUEUED", "REJECTED", null)) {
                logger.error("Failed to submit task {}: {}", id, e.getMessage(), e);
            }
            throw e;
        }
        return id;
    }




    public Optional<TaskStatusDto> getStatus(String id) {
        return Optional.ofNullable(repository.find(id));
    }

    public Collection<TaskStatusDto> listAll() {
        return repository.findAll();
    }

    public boolean cancel(String id) {
        TaskHandle handle = runningTasks.get(id);
        if (handle != null) {
            // mark the logical task as cancelled
            handle.getTask().cancel();
            boolean removedFromQueue = false;

            // attempt to remove the wrapper from the executor queue
            Runnable wrapper = handle.getWrapper();
            if (wrapper != null) {
                removedFromQueue = executor.remove(wrapper);
            }

            // also cancel the future to prevent execution or interrupt if running
            java.util.concurrent.Future<?> f = handle.getFuture();
            if (f != null) {
                f.cancel(true);
            }

            // update repository
            repository.updateStatus(id, "CANCELLATION_REQUESTED", null);
            logger.info("Cancellation requested for {} removedFromQueue={}", id, removedFromQueue);

            // ✅ Remove from running task registry (fix for test failure)
            runningTasks.remove(id);

            return true;
        }
        return false;
    }


    /**
     * Expose running task ids for tests/monitoring to detect leaks.
     */
    public Set<String> getRunningTaskIds() {
        return Collections.unmodifiableSet(runningTasks.keySet());
    }

    /**
     * Simple holder describing a task that has been submitted to the executor.
     * Holds the logical task, the wrapper Runnable used for submission and the
     * Future returned by the executor so we can cancel/remove it later.
     */
    private static class TaskHandle {
        private final ImageProcessorTask task;
        private Runnable wrapper;
        private java.util.concurrent.Future<?> future;

        TaskHandle(ImageProcessorTask task) {
            this.task = task;
        }

        public ImageProcessorTask getTask() { return task; }
        public Runnable getWrapper() { return wrapper; }
        public void setWrapper(Runnable wrapper) { this.wrapper = wrapper; }
        public java.util.concurrent.Future<?> getFuture() { return future; }
        public void setFuture(java.util.concurrent.Future<?> future) { this.future = future; }
    }

    @PreDestroy
    public void shutdownExecutor() {
        logger.info("ProcessingService shutting down executor...");
        try {
            // request an orderly shutdown
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Executor did not terminate within timeout; forcing shutdownNow()");
                executor.shutdownNow();
                executor.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        } catch (Exception e) {
            logger.error("Error while shutting down executor: {}", e.getMessage(), e);
        }
        logger.info("ProcessingService executor shutdown complete");
    }
}
