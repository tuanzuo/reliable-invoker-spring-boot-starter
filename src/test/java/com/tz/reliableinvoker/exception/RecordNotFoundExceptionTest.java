package com.tz.reliableinvoker.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecordNotFoundException 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class RecordNotFoundExceptionTest {

    @Test
    void testMessageContainsSerialNo() {
        RecordNotFoundException ex = new RecordNotFoundException("SN123");

        assertEquals("记录不存在: SN123", ex.getMessage());
    }

    @Test
    void testIsReliableInvokerException() {
        assertTrue(new RecordNotFoundException("SN") instanceof ReliableInvokerException);
    }
}
