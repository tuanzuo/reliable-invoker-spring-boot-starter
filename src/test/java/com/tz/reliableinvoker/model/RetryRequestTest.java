package com.tz.reliableinvoker.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RetryRequest 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class RetryRequestTest {

    enum TestScene { ORDER }

    @Test
    void testBuilderCreatesRequest() {
        RetryRequest<TestScene> request = RetryRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .statusList(Arrays.asList(0, 1))
                .maxRetry(5)
                .retryDelay(3000)
                .shardTotal(2)
                .shardIndex(0)
                .limit(200)
                .build();

        assertEquals(TestScene.ORDER, request.getScene());
        assertEquals(Arrays.asList(0, 1), request.getStatusList());
        assertEquals(5, request.getMaxRetry());
        assertEquals(3000, request.getRetryDelay());
        assertEquals(2, request.getShardTotal());
        assertEquals(0, request.getShardIndex());
        assertEquals(200, request.getLimit());
    }

    @Test
    void testDefaultValues() {
        RetryRequest<TestScene> request = RetryRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .build();

        assertEquals(Arrays.asList(InvocationStatusEnum.PENDING.getCode()), request.getStatusList());
        assertEquals(1, request.getShardTotal());
        assertEquals(0, request.getShardIndex());
        assertEquals(100, request.getLimit());
        assertEquals(0, request.getMaxRetry());
        assertEquals(0, request.getRetryDelay());
    }

    @Test
    void testSceneRequired() {
        assertThrows(NullPointerException.class, () ->
                RetryRequest.<TestScene>builder().build());
    }
}
