package com.example.threadpooldemo.processor;

import com.example.threadpooldemo.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageProcessorTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ImageProcessorTask.class);

    private final String id;
    private final String fileName;
    private final int complexity;
    private final TaskRepository repository;
    private final int maxRetryAttempts;
    private final long retryDelayMillis;
    private volatile boolean cancelled = false;
    private int currentAttempt = 1;

    public ImageProcessorTask(String id, String fileName, int complexity, TaskRepository repository, 
                            int maxRetryAttempts, long retryDelayMillis) {
        this.id = id;
        this.fileName = fileName;
        this.complexity = complexity;
        this.repository = repository;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelayMillis = retryDelayMillis;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean shouldRetry() {
        return currentAttempt < maxRetryAttempts;
    }

    public long getRetryDelay() {
        return retryDelayMillis;
    }

    public int getCurrentAttempt() {
        return currentAttempt;
    }

    public String getId() {
        return id;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        try {
            repository.updateStatus(id, String.format("ATTEMPT_%d_OF_%d", currentAttempt, maxRetryAttempts), threadName);
            logger.info("Started processing {} (id={}) on {} attempt {}/{}", fileName, id, threadName, currentAttempt, maxRetryAttempts);

            // Even invalid complexity should trigger retry mechanism
            if (complexity <= 0) {
                throw new IllegalArgumentException("Invalid complexity value: " + complexity);
            }

            for (int i = 0; i < complexity; i++) {
                if (Thread.currentThread().isInterrupted() || cancelled) {
                    repository.updateStatus(id, "CANCELLED", threadName);
                    logger.warn("Task {} cancelled/interrupted on attempt {}", id, currentAttempt);
                    return;
                }
                Thread.sleep(200L + (long) (Math.random() * 200));
            }

            repository.updateStatus(id, "COMPLETED", threadName);
            logger.info("Task {} completed successfully after attempt {}", id, currentAttempt);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            repository.updateStatus(id, "INTERRUPTED", threadName);
            throw new RuntimeException("Task interrupted", e);
        } catch (Exception e) {
            handleTaskFailure(e, threadName);
            throw new RuntimeException("Task failed", e);
        }
    }

    private void handleTaskFailure(Exception e, String threadName) {
        boolean canRetry = currentAttempt < maxRetryAttempts;
        if (canRetry) {
            repository.updateStatus(id, String.format("FAILED_ATTEMPT_%d_RETRYING", currentAttempt), threadName);
            logger.warn("Task {} failed on attempt {} with error: {}. Retrying...", id, currentAttempt, e.getMessage());
            currentAttempt++;
        } else {
            repository.updateStatus(id, "FAILED_PERMANENTLY", threadName);
            logger.error("Task {} failed permanently after {} attempts. Error: {}", id, currentAttempt, e.getMessage());
        }
    }


    @Override
    public String toString() {
        return "ImageProcessorTask{" + id + ":" + fileName + "}";
    }
}
