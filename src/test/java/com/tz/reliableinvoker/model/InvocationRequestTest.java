package com.tz.reliableinvoker.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InvocationRequest 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class InvocationRequestTest {

    enum TestScene { ORDER }

    @Test
    void testBuilderCreatesRequest() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .params("payload")
                .async(true)
                .saveRecord(false)
                .maxRetry(5)
                .retryDelay(3000)
                .remark("test")
                .build();

        assertEquals(TestScene.ORDER, request.getScene());
        assertEquals("payload", request.getParams());
        assertTrue(request.isAsync());
        assertFalse(request.isSaveRecord());
        assertEquals(5, request.getMaxRetry());
        assertEquals(3000, request.getRetryDelay());
        assertEquals("test", request.getRemark());
    }

    @Test
    void testSceneRequired() {
        assertThrows(NullPointerException.class, () ->
                InvocationRequest.<TestScene>builder().build());
    }

    @Test
    void testDefaultValues() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .build();

        assertFalse(request.isAsync());
        assertTrue(request.isSaveRecord());
        assertEquals(0, request.getMaxRetry());
        assertEquals(0, request.getRetryDelay());
        assertNull(request.getParams());
        assertNull(request.getRemark());
    }

    @Test
    void testParamsCanBeNull() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .params(null)
                .build();

        assertNull(request.getParams());
    }
}
