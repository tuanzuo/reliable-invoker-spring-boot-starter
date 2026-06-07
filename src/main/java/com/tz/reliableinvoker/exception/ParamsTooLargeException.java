package com.tz.reliableinvoker.exception;

/**
 * 参数过大异常
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public class ParamsTooLargeException extends ReliableInvokerException {
    public ParamsTooLargeException(int actualBytes, int maxBytes) {
        super(String.format("参数过大: %d 字节，最大允许 %d 字节", actualBytes, maxBytes));
    }
}
