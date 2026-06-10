package com.tz.reliableinvoker.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionException 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ExecutionExceptionTest {

    @Test
    void testMessageAndCause() {
        Throwable cause = new RuntimeException("business error");
        ExecutionException ex = new ExecutionException("execution failed", cause);

        assertEquals("execution failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void testIsReliableInvokerException() {
        assertTrue(new ExecutionException("test", null) instanceof ReliableInvokerException);
    }
}
