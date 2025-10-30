package com.example.threadpooldemo.processor;

import com.example.threadpooldemo.dto.TaskStatusDto;
import com.example.threadpooldemo.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageProcessorTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ImageProcessorTask.class);

    private final String id;
    private final String fileName;
    private final int complexity;
    private final TaskRepository repository;
    private volatile boolean cancelled = false;

    public ImageProcessorTask(String id, String fileName, int complexity, TaskRepository repository) {
        this.id = id;
        this.fileName = fileName;
        this.complexity = complexity;
        this.repository = repository;
    }

    public void cancel() {
        cancelled = true;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        repository.updateStatus(id, "RUNNING", threadName);
        logger.info("Started processing {} (id={}) on {} with complexity={}", fileName, id, threadName, complexity);
        try {
            int chunks = Math.max(1, complexity);
            for (int i = 0; i < chunks; i++) {
                if (Thread.currentThread().isInterrupted() || cancelled) {
                    repository.updateStatus(id, "CANCELLED", threadName);
                    logger.warn("Task {} cancelled/interrupted while processing {}", id, fileName);
                    return;
                }
                Thread.sleep(200L + (long) (Math.random() * 200));
            }
            repository.updateStatus(id, "COMPLETED", threadName);
            logger.info("Completed processing {} (id={}) on {}", fileName, id, threadName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            repository.updateStatus(id, "INTERRUPTED", threadName);
            logger.warn("Task {} interrupted: {}", id, e.getMessage());
        } catch (Exception e) {
            repository.updateStatus(id, "FAILED", threadName);
            logger.error("Task {} failed: {}", id, e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return "ImageProcessorTask{" + id + ":" + fileName + "}";
    }
}
