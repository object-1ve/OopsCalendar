package com.oops.calendar.provider;

/**
 * FMP 上游不可用(连接失败 / 超时 / 4xx / 5xx / 响应解析失败 / 错误响应体)。
 * <p>
 * 消息必须是安全、简短的中文摘要,严禁包含 FMP 原始响应体或长 FAQ URL,
 * 以便直接展示给前端 / 写入日志。
 */
public class UpstreamUnavailableException extends RuntimeException {

    public UpstreamUnavailableException(String shortMessage) {
        super(shortMessage);
    }

    public UpstreamUnavailableException(String shortMessage, Throwable cause) {
        super(shortMessage, cause);
    }
}
