package com.example.threadpooldemo.dto;

public class TaskStatusDto {
    private String id;
    private String fileName;
    private String status;
    private String assignedThread;

    public TaskStatusDto() {}

    public TaskStatusDto(String id, String fileName, String status, String assignedThread) {
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
