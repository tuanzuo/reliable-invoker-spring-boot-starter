package com.tz.reliableinvoker.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BackupQueryRequest 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class BackupQueryRequestTest {

    @Test
    void testSettersAndGetters() {
        BackupQueryRequest request = new BackupQueryRequest();

        request.setScene("ORDER");
        request.setStatusList(Arrays.asList(2, 3));
        request.setDays(30);
        request.setShardTotal(4);
        request.setShardIndex(1);
        request.setLimit(50);

        assertEquals("ORDER", request.getScene());
        assertEquals(Arrays.asList(2, 3), request.getStatusList());
        assertEquals(30, request.getDays());
        assertEquals(4, request.getShardTotal());
        assertEquals(1, request.getShardIndex());
        assertEquals(50, request.getLimit());
    }

    @Test
    void testDefaultValues() {
        BackupQueryRequest request = new BackupQueryRequest();

        assertNull(request.getScene());
        assertNull(request.getStatusList());
        assertEquals(0, request.getDays());
        assertEquals(1, request.getShardTotal());
        assertEquals(0, request.getShardIndex());
        assertEquals(100, request.getLimit());
    }
}
