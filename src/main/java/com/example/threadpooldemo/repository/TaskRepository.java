package com.example.threadpooldemo.repository;

import com.example.threadpooldemo.dto.TaskStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TaskRepository {
    private static final Logger logger = LoggerFactory.getLogger(TaskRepository.class);
    private final Map<String, TaskStatusDto> store = new ConcurrentHashMap<>();

    /**
     * Atomically saves a new task status.
     */
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
    public TaskStatusDto find(String id) {
        return store.get(id);
    }

    /**
     * Returns an immutable snapshot of all task statuses.
     */
    public Collection<TaskStatusDto> findAll() {
        return store.values();
    }

    /**
     * Atomically updates task status. Uses compute() to ensure atomic read-modify-write.
     * Returns true if the status was updated, false if the task was not found.
     */
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
    public boolean compareAndUpdateStatus(String id, String expectedStatus, String newStatus, String threadName) {
        return store.replace(id, 
            new TaskStatusDto(id, find(id).getFileName(), expectedStatus, find(id).getAssignedThread()),
            new TaskStatusDto(id, find(id).getFileName(), newStatus, threadName));
    }
}
