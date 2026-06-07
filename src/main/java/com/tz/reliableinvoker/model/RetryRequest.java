package com.tz.reliableinvoker.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 重试请求
 *
 * @param <S> 业务场景枚举类型
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public class RetryRequest<S extends Enum<S>> {

    /** 业务场景（非空） */
    private final S scene;

    /** 状态列表，默认[0] */
    private final List<Integer> statusList;

    /** 最大重试次数 */
    private final int maxRetry;

    /** 重试延迟（毫秒） */
    private final int retryDelay;

    /** 分片总数，默认1 */
    private final int shardTotal;

    /** 当前分片索引 */
    private final int shardIndex;

    /** 每次处理数量上限，默认100 */
    private final int limit;

    private RetryRequest(Builder<S> builder) {
        this.scene = builder.scene;
        this.statusList = builder.statusList;
        this.maxRetry = builder.maxRetry;
        this.retryDelay = builder.retryDelay;
        this.shardTotal = builder.shardTotal;
        this.shardIndex = builder.shardIndex;
        this.limit = builder.limit;
    }

    /**
     * 创建Builder实例
     *
     * @param <S> 业务场景枚举类型
     * @return Builder
     */
    public static <S extends Enum<S>> Builder<S> builder() {
        return new Builder<>();
    }

    public S getScene() {
        return scene;
    }

    public List<Integer> getStatusList() {
        return statusList;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public int getRetryDelay() {
        return retryDelay;
    }

    public int getShardTotal() {
        return shardTotal;
    }

    public int getShardIndex() {
        return shardIndex;
    }

    public int getLimit() {
        return limit;
    }

    /**
     * RetryRequest的Builder
     *
     * @param <S> 业务场景枚举类型
     * @version 1.0.0-SNAPSHOT
     */
    public static class Builder<S extends Enum<S>> {

        private S scene;
        private List<Integer> statusList = Arrays.asList(InvocationStatusEnum.PENDING.getCode());
        private int maxRetry;
        private int retryDelay;
        private int shardTotal = 1;
        private int shardIndex;
        private int limit = 100;

        /**
         * 设置业务场景
         *
         * @param scene 业务场景
         * @return Builder
         */
        public Builder<S> scene(S scene) {
            this.scene = scene;
            return this;
        }

        /**
         * 设置状态列表
         *
         * @param statusList 状态列表
         * @return Builder
         */
        public Builder<S> statusList(List<Integer> statusList) {
            this.statusList = statusList;
            return this;
        }

        /**
         * 设置最大重试次数
         *
         * @param maxRetry 最大重试次数
         * @return Builder
         */
        public Builder<S> maxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
            return this;
        }

        /**
         * 设置重试延迟
         *
         * @param retryDelay 重试延迟（毫秒）
         * @return Builder
         */
        public Builder<S> retryDelay(int retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        /**
         * 设置分片总数
         *
         * @param shardTotal 分片总数
         * @return Builder
         */
        public Builder<S> shardTotal(int shardTotal) {
            this.shardTotal = shardTotal;
            return this;
        }

        /**
         * 设置当前分片索引
         *
         * @param shardIndex 分片索引
         * @return Builder
         */
        public Builder<S> shardIndex(int shardIndex) {
            this.shardIndex = shardIndex;
            return this;
        }

        /**
         * 设置每次处理数量上限
         *
         * @param limit 数量上限
         * @return Builder
         */
        public Builder<S> limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * 构建RetryRequest实例，校验scene非空
         *
         * @return RetryRequest实例
         * @throws NullPointerException 如果scene为null
         */
        public RetryRequest<S> build() {
            Objects.requireNonNull(scene, "scene must not be null");
            return new RetryRequest<>(this);
        }
    }
}
