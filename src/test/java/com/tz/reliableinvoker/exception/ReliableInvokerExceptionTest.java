package com.tz.reliableinvoker.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReliableInvokerException 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerExceptionTest {

    @Test
    void testMessageConstructor() {
        ReliableInvokerException ex = new ReliableInvokerException("error message");

        assertEquals("error message", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void testMessageAndCauseConstructor() {
        Throwable cause = new RuntimeException("root cause");
        ReliableInvokerException ex = new ReliableInvokerException("error message", cause);

        assertEquals("error message", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void testIsRuntimeException() {
        assertTrue(new ReliableInvokerException("test") instanceof RuntimeException);
    }
}
