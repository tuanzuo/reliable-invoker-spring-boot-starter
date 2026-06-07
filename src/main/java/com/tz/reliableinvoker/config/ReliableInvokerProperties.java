package com.tz.reliableinvoker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 可靠调用配置属性
 *
 * <p>配置前缀：reliable.invoker
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
@ConfigurationProperties(prefix = "reliable.invoker")
public class ReliableInvokerProperties {

    /** 是否启用，默认true */
    private boolean enabled = true;

    /** 默认表名 */
    private String tableName = "reliable_invocation_record";

    /** 全局默认最大重试次数 */
    private int maxRetry = 3;

    /** 全局默认重试间隔(ms) */
    private int defaultDelay = 5000;

    /** 全局异步执行器配置 */
    private AsyncProperties async = new AsyncProperties();

    /** 场景级配置，key为场景名称 */
    private Map<String, SceneProperties> scenes = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public int getDefaultDelay() {
        return defaultDelay;
    }

    public void setDefaultDelay(int defaultDelay) {
        this.defaultDelay = defaultDelay;
    }

    public AsyncProperties getAsync() {
        return async;
    }

    public void setAsync(AsyncProperties async) {
        this.async = async;
    }

    public Map<String, SceneProperties> getScenes() {
        return scenes;
    }

    public void setScenes(Map<String, SceneProperties> scenes) {
        this.scenes = scenes;
    }

    /**
     * 根据场景名称获取场景级配置，未配置时返回null
     *
     * @param sceneName 场景名称
     * @return 场景配置，不存在时返回null
     */
    public SceneProperties getSceneProperties(String sceneName) {
        return scenes != null ? scenes.get(sceneName) : null;
    }

    /**
     * 场景级配置属性
     *
     * @version 1.0.0-SNAPSHOT
     */
    public static class SceneProperties {

        /** 场景级表名（覆盖全局） */
        private String tableName;

        /** 场景级最大重试次数（覆盖全局） */
        private Integer maxRetry;

        /** 场景级重试间隔ms（覆盖全局） */
        private Integer defaultDelay;

        /** 场景级异步执行器配置（覆盖全局） */
        private AsyncProperties async;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public Integer getMaxRetry() {
            return maxRetry;
        }

        public void setMaxRetry(Integer maxRetry) {
            this.maxRetry = maxRetry;
        }

        public Integer getDefaultDelay() {
            return defaultDelay;
        }

        public void setDefaultDelay(Integer defaultDelay) {
            this.defaultDelay = defaultDelay;
        }

        public AsyncProperties getAsync() {
            return async;
        }

        public void setAsync(AsyncProperties async) {
            this.async = async;
        }
    }

    /**
     * 异步线程池配置
     *
     * @version 1.0.0-SNAPSHOT
     */
    public static class AsyncProperties {

        /** 核心线程数，默认5 */
        private int corePoolSize = 5;

        /** 最大线程数，默认20 */
        private int maxPoolSize = 20;

        /** 队列容量，默认100 */
        private int queueCapacity = 100;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}
