package com.tz.reliableinvoker.exception;

/**
 * 记录不存在异常
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public class RecordNotFoundException extends ReliableInvokerException {
    public RecordNotFoundException(String serialNo) {
        super("记录不存在: " + serialNo);
    }
}
