package com.tz.reliableinvoker.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BackupRequest 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class BackupRequestTest {

    enum TestScene { ORDER }

    @Test
    void testBuilderCreatesRequest() {
        BackupRequest<TestScene> request = BackupRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .statusList(Arrays.asList(2, 3))
                .days(30)
                .shardTotal(4)
                .shardIndex(1)
                .limit(50)
                .build();

        assertEquals(TestScene.ORDER, request.getScene());
        assertEquals(Arrays.asList(2, 3), request.getStatusList());
        assertEquals(30, request.getDays());
        assertEquals(4, request.getShardTotal());
        assertEquals(1, request.getShardIndex());
        assertEquals(50, request.getLimit());
    }

    @Test
    void testDefaultValues() {
        BackupRequest<TestScene> request = BackupRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .days(7)
                .build();

        assertEquals(Arrays.asList(InvocationStatusEnum.SUCCESS.getCode()), request.getStatusList());
        assertEquals(1, request.getShardTotal());
        assertEquals(0, request.getShardIndex());
        assertEquals(100, request.getLimit());
    }

    @Test
    void testSceneRequired() {
        assertThrows(NullPointerException.class, () ->
                BackupRequest.<TestScene>builder().days(1).build());
    }

    @Test
    void testDaysMustBeGreaterThanZero() {
        assertThrows(IllegalArgumentException.class, () ->
                BackupRequest.<TestScene>builder()
                        .scene(TestScene.ORDER)
                        .days(0)
                        .build());

        assertThrows(IllegalArgumentException.class, () ->
                BackupRequest.<TestScene>builder()
                        .scene(TestScene.ORDER)
                        .days(-1)
                        .build());
    }
}
