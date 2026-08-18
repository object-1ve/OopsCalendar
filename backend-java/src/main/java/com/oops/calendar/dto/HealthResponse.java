package com.oops.calendar.dto;

/**
 * /api/health 响应。
 */
public class HealthResponse {

    public String status;    // "UP" | "DOWN"
    public String provider;  // "fmp" | "mock"
    public String message;   // 人类可读说明
    public String timestamp; // ISO-8601

    public HealthResponse() {
    }

    public HealthResponse(String status, String provider, String message, String timestamp) {
        this.status = status;
        this.provider = provider;
        this.message = message;
        this.timestamp = timestamp;
    }
}
