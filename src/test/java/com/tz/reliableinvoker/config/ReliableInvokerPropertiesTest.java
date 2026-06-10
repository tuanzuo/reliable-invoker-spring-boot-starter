package com.tz.reliableinvoker.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReliableInvokerProperties 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerPropertiesTest {

    @Test
    void testDefaultValues() {
        ReliableInvokerProperties props = new ReliableInvokerProperties();

        assertTrue(props.isEnabled());
        assertEquals("reliable_invocation_record", props.getTableName());
        assertEquals(3, props.getMaxRetry());
        assertEquals(5000, props.getDefaultDelay());

        ReliableInvokerProperties.AsyncProperties async = props.getAsync();
        assertNotNull(async);
        assertEquals(5, async.getCorePoolSize());
        assertEquals(20, async.getMaxPoolSize());
        assertEquals(100, async.getQueueCapacity());

        assertNotNull(props.getScenes());
        assertTrue(props.getScenes().isEmpty());
    }

    @Test
    void testSettersAndGetters() {
        ReliableInvokerProperties props = new ReliableInvokerProperties();
        props.setEnabled(false);
        props.setTableName("custom_table");
        props.setMaxRetry(5);
        props.setDefaultDelay(10000);

        assertFalse(props.isEnabled());
        assertEquals("custom_table", props.getTableName());
        assertEquals(5, props.getMaxRetry());
        assertEquals(10000, props.getDefaultDelay());
    }

    @Test
    void testAsyncPropertiesSetters() {
        ReliableInvokerProperties.AsyncProperties async = new ReliableInvokerProperties.AsyncProperties();
        async.setCorePoolSize(10);
        async.setMaxPoolSize(50);
        async.setQueueCapacity(200);

        assertEquals(10, async.getCorePoolSize());
        assertEquals(50, async.getMaxPoolSize());
        assertEquals(200, async.getQueueCapacity());
    }

    @Test
    void testScenePropertiesSetters() {
        ReliableInvokerProperties.SceneProperties scene = new ReliableInvokerProperties.SceneProperties();
        scene.setTableName("scene_table");
        scene.setMaxRetry(7);
        scene.setDefaultDelay(15000);

        ReliableInvokerProperties.AsyncProperties sceneAsync = new ReliableInvokerProperties.AsyncProperties();
        sceneAsync.setCorePoolSize(3);
        scene.setAsync(sceneAsync);

        assertEquals("scene_table", scene.getTableName());
        assertEquals(Integer.valueOf(7), scene.getMaxRetry());
        assertEquals(Integer.valueOf(15000), scene.getDefaultDelay());
        assertEquals(3, scene.getAsync().getCorePoolSize());
    }

    @Test
    void testGetSceneProperties() {
        ReliableInvokerProperties props = new ReliableInvokerProperties();

        ReliableInvokerProperties.SceneProperties orderScene = new ReliableInvokerProperties.SceneProperties();
        orderScene.setMaxRetry(10);
        props.getScenes().put("ORDER", orderScene);

        assertNull(props.getSceneProperties("NOT_EXIST"));
        assertEquals(Integer.valueOf(10), props.getSceneProperties("ORDER").getMaxRetry());
    }

    @Test
    void testGetScenePropertiesWithNullScenes() {
        ReliableInvokerProperties props = new ReliableInvokerProperties();
        props.setScenes(null);

        assertNull(props.getSceneProperties("ORDER"));
    }
}
