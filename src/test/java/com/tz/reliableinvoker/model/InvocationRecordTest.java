package com.tz.reliableinvoker.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InvocationRecord 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class InvocationRecordTest {

    @Test
    void testSettersAndGetters() {
        InvocationRecord record = new InvocationRecord();
        LocalDateTime now = LocalDateTime.now();

        record.setId(1L);
        record.setSerialNo("SN001");
        record.setScene("ORDER");
        record.setParams("{\"key\":\"value\"}");
        record.setStatus(0);
        record.setRetryCount(1);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);
        record.setExecuteTime(now);
        record.setRemark("remark");
        record.setCreateTime(now);
        record.setUpdateTime(now);

        assertEquals(Long.valueOf(1L), record.getId());
        assertEquals("SN001", record.getSerialNo());
        assertEquals("ORDER", record.getScene());
        assertEquals("{\"key\":\"value\"}", record.getParams());
        assertEquals(Integer.valueOf(0), record.getStatus());
        assertEquals(Integer.valueOf(1), record.getRetryCount());
        assertEquals(Integer.valueOf(3), record.getMaxRetryCount());
        assertEquals(Integer.valueOf(5000), record.getRetryDelay());
        assertEquals(now, record.getExecuteTime());
        assertEquals("remark", record.getRemark());
        assertEquals(now, record.getCreateTime());
        assertEquals(now, record.getUpdateTime());
    }

    @Test
    void testDefaultValues() {
        InvocationRecord record = new InvocationRecord();

        assertNull(record.getId());
        assertNull(record.getSerialNo());
        assertNull(record.getScene());
        assertNull(record.getParams());
        assertNull(record.getStatus());
        assertNull(record.getRetryCount());
        assertNull(record.getMaxRetryCount());
        assertNull(record.getRetryDelay());
        assertNull(record.getExecuteTime());
        assertNull(record.getRemark());
        assertNull(record.getCreateTime());
        assertNull(record.getUpdateTime());
    }
}
