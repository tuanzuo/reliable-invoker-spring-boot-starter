package com.tz.reliableinvoker.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ParamsTooLargeException 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ParamsTooLargeExceptionTest {

    @Test
    void testMessageContainsSizes() {
        ParamsTooLargeException ex = new ParamsTooLargeException(1024, 512);

        assertEquals("参数过大: 1024 字节，最大允许 512 字节", ex.getMessage());
    }

    @Test
    void testIsReliableInvokerException() {
        assertTrue(new ParamsTooLargeException(1, 1) instanceof ReliableInvokerException);
    }
}
