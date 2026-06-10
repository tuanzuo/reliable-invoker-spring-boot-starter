package com.tz.reliableinvoker.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RetryQueryRequest 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class RetryQueryRequestTest {

    @Test
    void testSettersAndGetters() {
        RetryQueryRequest request = new RetryQueryRequest();

        request.setScene("ORDER");
        request.setStatusList(Arrays.asList(0, 1));
        request.setShardTotal(4);
        request.setShardIndex(2);
        request.setLimit(50);

        assertEquals("ORDER", request.getScene());
        assertEquals(Arrays.asList(0, 1), request.getStatusList());
        assertEquals(4, request.getShardTotal());
        assertEquals(2, request.getShardIndex());
        assertEquals(50, request.getLimit());
    }

    @Test
    void testDefaultValues() {
        RetryQueryRequest request = new RetryQueryRequest();

        assertNull(request.getScene());
        assertNull(request.getStatusList());
        assertEquals(1, request.getShardTotal());
        assertEquals(0, request.getShardIndex());
        assertEquals(100, request.getLimit());
    }
}
