package com.example.threadpooldemo.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks")
public class TaskEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String status;

    @Column
    private String assignedThread;

    public TaskEntity() { }

    public TaskEntity(String id, String fileName, String status, String assignedThread) {
        this.id = id;
        this.fileName = fileName;
        this.status = status;
        this.assignedThread = assignedThread;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignedThread() {
        return assignedThread;
    }

    public void setAssignedThread(String assignedThread) {
        this.assignedThread = assignedThread;
    }
}
