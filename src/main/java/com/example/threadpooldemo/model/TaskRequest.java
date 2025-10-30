package com.example.threadpooldemo.model;

import jakarta.validation.constraints.NotBlank;

public class TaskRequest {

    @NotBlank
    private String fileName;

    private int complexity; // 1..10 - how heavy the simulated processing is

    public TaskRequest() {}

    public TaskRequest(String fileName, int complexity) {
        this.fileName = fileName;
        this.complexity = complexity;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getComplexity() {
        return complexity;
    }

    public void setComplexity(int complexity) {
        this.complexity = complexity;
    }

    @Override
    public String toString() {
        return "TaskRequest{" +
                "fileName='" + fileName + '\'' +
                ", complexity=" + complexity +
                '}';
    }
}
