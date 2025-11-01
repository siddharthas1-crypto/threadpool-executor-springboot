package com.example.threadpooldemo.repository;

import com.example.threadpooldemo.dto.TaskStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository implementation.
 * This bean is active when property app.persistence.enabled is not true (default false).
 */
@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "enabled", havingValue = "false", matchIfMissing = true)
public class TaskRepository implements TaskRepositoryPort {
    private static final Logger logger = LoggerFactory.getLogger(TaskRepository.class);
    private final Map<String, TaskStatusDto> store = new ConcurrentHashMap<>();

    /**
     * Atomically saves a new task status.
     */
    @Override
    public void save(TaskStatusDto dto) {
        TaskStatusDto existing = store.putIfAbsent(dto.getId(), dto);
        if (existing != null) {
            logger.warn("Task {} already exists with status {}, not overwriting with status {}", 
                dto.getId(), existing.getStatus(), dto.getStatus());
        }
    }

    /**
     * Returns the current task status, or null if not found.
     */
    @Override
    public TaskStatusDto find(String id) {
        return store.get(id);
    }

    /**
     * Returns an immutable snapshot of all task statuses.
     */
    @Override
    public Collection<TaskStatusDto> findAll() {
        return store.values();
    }

    /**
     * Atomically updates task status. Uses compute() to ensure atomic read-modify-write.
     * Returns true if the status was updated, false if the task was not found.
     */
    @Override
    public boolean updateStatus(String id, String status, String threadName) {
        TaskStatusDto updated = store.compute(id, (key, existing) -> {
            if (existing == null) {
                logger.warn("Attempted to update non-existent task {}", id);
                return null;
            }
            return existing.withStatusAndThread(status, threadName);
        });
        return updated != null;
    }

    /**
     * Atomically compares and updates task status.
     * Returns true if the status was updated, false if either the task was not found
     * or the expected status did not match.
     */
    @Override
    public boolean compareAndUpdateStatus(String id, String expectedStatus, String newStatus, String threadName) {
        TaskStatusDto current = find(id);
        if (current == null || !expectedStatus.equals(current.getStatus())) {
            return false;
        }
        TaskStatusDto updated = new TaskStatusDto(id, current.getFileName(), newStatus, threadName);
        return store.replace(id, current, updated);
    }
}
