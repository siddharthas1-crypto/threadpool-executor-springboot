package com.example.threadpooldemo.repository;

import com.example.threadpooldemo.dto.TaskStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(prefix = "app.persistence", name = "enabled", havingValue = "true")
public class PersistentTaskRepository implements TaskRepositoryPort {
    private static final Logger logger = LoggerFactory.getLogger(PersistentTaskRepository.class);

    private final JpaTaskRepository jpa;

    public PersistentTaskRepository(JpaTaskRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(TaskStatusDto dto) {
        logger.info("PersistentTaskRepository.save() called for id={}", dto.getId());
        if (jpa.existsById(dto.getId())) {
            logger.warn("Task {} already exists, not overwriting", dto.getId());
            return;
        }
        TaskEntity e = new TaskEntity(dto.getId(), dto.getFileName(), dto.getStatus(), dto.getAssignedThread());
        jpa.saveAndFlush(e);
        logger.info("PersistentTaskRepository.save() flushed id={}", dto.getId());
    }

    @Override
    public TaskStatusDto find(String id) {
        return jpa.findById(id).map(e -> new TaskStatusDto(e.getId(), e.getFileName(), e.getStatus(), e.getAssignedThread())).orElse(null);
    }

    @Override
    public Collection<TaskStatusDto> findAll() {
        return jpa.findAll().stream()
                .map(e -> new TaskStatusDto(e.getId(), e.getFileName(), e.getStatus(), e.getAssignedThread()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean updateStatus(String id, String status, String threadName) {
        return jpa.findById(id).map(e -> {
            e.setStatus(status);
            e.setAssignedThread(threadName);
            jpa.saveAndFlush(e);
            return true;
        }).orElse(false);
    }

    @Override
    public boolean compareAndUpdateStatus(String id, String expectedStatus, String newStatus, String threadName) {
        return jpa.findById(id).map(e -> {
            if (!expectedStatus.equals(e.getStatus())) return false;
            e.setStatus(newStatus);
            e.setAssignedThread(threadName);
            jpa.saveAndFlush(e);
            return true;
        }).orElse(false);
    }
}
