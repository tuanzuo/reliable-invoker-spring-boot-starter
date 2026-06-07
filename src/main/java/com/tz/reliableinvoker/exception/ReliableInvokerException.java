package com.tz.reliableinvoker.exception;

/**
 * 可靠调用组件基础异常
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerException extends RuntimeException {
    public ReliableInvokerException(String message) {
        super(message);
    }

    public ReliableInvokerException(String message, Throwable cause) {
        super(message, cause);
    }
}
