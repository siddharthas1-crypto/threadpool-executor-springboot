package com.example.threadpooldemo.repository;

import com.example.threadpooldemo.dto.TaskStatusDto;

import java.util.Collection;

public interface TaskRepositoryPort {
    void save(TaskStatusDto dto);
    TaskStatusDto find(String id);
    Collection<TaskStatusDto> findAll();
    boolean updateStatus(String id, String status, String threadName);
    boolean compareAndUpdateStatus(String id, String expectedStatus, String newStatus, String threadName);
}
