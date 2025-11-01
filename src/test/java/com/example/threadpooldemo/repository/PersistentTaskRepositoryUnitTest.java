package com.example.threadpooldemo.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class PersistentTaskRepositoryUnitTest {

    @Autowired
    private JpaTaskRepository jpa;

    @Test
    public void saveAndFind() {
        PersistentTaskRepository repo = new PersistentTaskRepository(jpa);
        TaskEntity e = new TaskEntity("u1", "f.jpg", "QUEUED", null);
        repo.save(new com.example.threadpooldemo.dto.TaskStatusDto(e.getId(), e.getFileName(), e.getStatus(), e.getAssignedThread()));

        TaskEntity loaded = jpa.findById("u1").orElse(null);
        assertNotNull(loaded);
        assertEquals("QUEUED", loaded.getStatus());
    }

    @Test
    public void updateStatusAndCompare() {
        PersistentTaskRepository repo = new PersistentTaskRepository(jpa);
        jpa.saveAndFlush(new TaskEntity("u2", "g.jpg", "QUEUED", null));

        boolean updated = repo.updateStatus("u2", "RUNNING", "thread-1");
        assertTrue(updated);
        TaskEntity after = jpa.findById("u2").orElse(null);
        assertNotNull(after);
        assertEquals("RUNNING", after.getStatus());

        // compareAndUpdateStatus with wrong expected should fail
        boolean cmpFalse = repo.compareAndUpdateStatus("u2", "QUEUED", "DONE", "t2");
        assertFalse(cmpFalse);

        // correct expected should succeed
        boolean cmpTrue = repo.compareAndUpdateStatus("u2", "RUNNING", "DONE", "t2");
        assertTrue(cmpTrue);
        TaskEntity finalE = jpa.findById("u2").orElse(null);
        assertEquals("DONE", finalE.getStatus());
    }

    @Test
    public void updateMissingReturnsFalse() {
        PersistentTaskRepository repo = new PersistentTaskRepository(jpa);
        boolean res = repo.updateStatus("not-exist", "X", null);
        assertFalse(res);
    }

    @Test
    public void saveExistingDoesNotOverwrite() {
        PersistentTaskRepository repo = new PersistentTaskRepository(jpa);
        jpa.saveAndFlush(new TaskEntity("u3", "h.jpg", "QUEUED", null));
        // attempt to save same id again - repo.save should skip
        repo.save(new com.example.threadpooldemo.dto.TaskStatusDto("u3", "h2.jpg", "RUNNING", "t"));

        TaskEntity e = jpa.findById("u3").orElse(null);
        assertNotNull(e);
        // original filename and status should remain
        assertEquals("h.jpg", e.getFileName());
        assertEquals("QUEUED", e.getStatus());
    }
}
