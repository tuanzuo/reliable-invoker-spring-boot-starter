package com.tz.reliableinvoker.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InvocationStatusEnum 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class InvocationStatusEnumTest {

    @Test
    void testGetCode() {
        assertEquals(0, InvocationStatusEnum.PENDING.getCode());
        assertEquals(1, InvocationStatusEnum.EXECUTING.getCode());
        assertEquals(2, InvocationStatusEnum.SUCCESS.getCode());
        assertEquals(3, InvocationStatusEnum.FAILED.getCode());
    }

    @Test
    void testFromCode() {
        assertEquals(InvocationStatusEnum.PENDING, InvocationStatusEnum.fromCode(0));
        assertEquals(InvocationStatusEnum.EXECUTING, InvocationStatusEnum.fromCode(1));
        assertEquals(InvocationStatusEnum.SUCCESS, InvocationStatusEnum.fromCode(2));
        assertEquals(InvocationStatusEnum.FAILED, InvocationStatusEnum.fromCode(3));
    }

    @Test
    void testFromCodeInvalidThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                InvocationStatusEnum.fromCode(99));
        assertEquals("未知状态: 99", ex.getMessage());
    }

    @Test
    void testValues() {
        InvocationStatusEnum[] values = InvocationStatusEnum.values();
        assertEquals(4, values.length);
        assertEquals(InvocationStatusEnum.PENDING, values[0]);
        assertEquals(InvocationStatusEnum.EXECUTING, values[1]);
        assertEquals(InvocationStatusEnum.SUCCESS, values[2]);
        assertEquals(InvocationStatusEnum.FAILED, values[3]);
    }
}
