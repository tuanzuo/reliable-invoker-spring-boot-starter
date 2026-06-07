package com.tz.reliableinvoker.exception;

/**
 * 方法执行异常
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public class ExecutionException extends ReliableInvokerException {
    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
