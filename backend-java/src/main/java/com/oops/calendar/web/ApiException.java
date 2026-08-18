package com.oops.calendar.web;

/**
 * 业务异常,由全局异常处理器转为带中文信息的 HTTP 响应。
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
