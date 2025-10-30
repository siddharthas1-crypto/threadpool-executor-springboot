package com.example.threadpooldemo.repository;

import com.example.threadpooldemo.dto.TaskStatusDto;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TaskRepository {
    private final Map<String, TaskStatusDto> store = new ConcurrentHashMap<>();

    public void save(TaskStatusDto dto) {
        store.put(dto.getId(), dto);
    }

    public TaskStatusDto find(String id) {
        return store.get(id);
    }

    public Collection<TaskStatusDto> findAll() {
        return store.values();
    }

    public void updateStatus(String id, String status, String threadName) {
        TaskStatusDto dto = store.get(id);
        if (dto != null) {
            dto.setStatus(status);
            dto.setAssignedThread(threadName);
        }
    }
}
