package com.example.threadpooldemo.dto;

public final class TaskStatusDto {
    private final String id;
    private final String fileName;
    private final String status;
    private final String assignedThread;

    public TaskStatusDto(String id, String fileName, String status, String assignedThread) {
        this.id = id;
        this.fileName = fileName;
        this.status = status;
        this.assignedThread = assignedThread;
    }

    public String getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStatus() {
        return status;
    }

    public String getAssignedThread() {
        return assignedThread;
    }

    public TaskStatusDto withStatus(String newStatus) {
        return new TaskStatusDto(this.id, this.fileName, newStatus, this.assignedThread);
    }

    public TaskStatusDto withThread(String newThread) {
        return new TaskStatusDto(this.id, this.fileName, this.status, newThread);
    }

    public TaskStatusDto withStatusAndThread(String newStatus, String newThread) {
        return new TaskStatusDto(this.id, this.fileName, newStatus, newThread);
    }

    @Override
    public String toString() {
        return String.format("TaskStatusDto{id='%s', fileName='%s', status='%s', thread='%s'}", 
            id, fileName, status, assignedThread);
    }
}
