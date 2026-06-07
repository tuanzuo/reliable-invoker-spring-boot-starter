package com.tz.reliableinvoker.model;

import java.util.Objects;

/**
 * 调用请求
 *
 * @param <S> 业务场景枚举类型
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public class InvocationRequest<S extends Enum<S>> {

    /** 业务场景（非空） */
    private final S scene;

    /** Bean名称（非空） */
    private final String beanName;

    /** 方法名（非空） */
    private final String methodName;

    /** 调用参数 */
    private final Object params;

    /** 是否异步调用 */
    private final boolean async;

    /** 是否保存调用记录，默认true */
    private final boolean saveRecord;

    /** 最大重试次数，需大于0 */
    private final int maxRetry;

    /** 重试延迟（毫秒），需大于0 */
    private final int retryDelay;

    /** 备注 */
    private final String remark;

    private InvocationRequest(Builder<S> builder) {
        this.scene = builder.scene;
        this.beanName = builder.beanName;
        this.methodName = builder.methodName;
        this.params = builder.params;
        this.async = builder.async;
        this.saveRecord = builder.saveRecord;
        this.maxRetry = builder.maxRetry;
        this.retryDelay = builder.retryDelay;
        this.remark = builder.remark;
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

    public String getBeanName() {
        return beanName;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object getParams() {
        return params;
    }

    public boolean isAsync() {
        return async;
    }

    public boolean isSaveRecord() {
        return saveRecord;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public int getRetryDelay() {
        return retryDelay;
    }

    public String getRemark() {
        return remark;
    }

    /**
     * InvocationRequest的Builder
     *
     * @param <S> 业务场景枚举类型
     * @version 1.0.0-SNAPSHOT
     */
    public static class Builder<S extends Enum<S>> {

        private S scene;
        private String beanName;
        private String methodName;
        private Object params;
        private boolean async;
        private boolean saveRecord = true;
        private int maxRetry;
        private int retryDelay;
        private String remark;

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
         * 设置Bean名称
         *
         * @param beanName Bean名称
         * @return Builder
         */
        public Builder<S> beanName(String beanName) {
            this.beanName = beanName;
            return this;
        }

        /**
         * 设置方法名
         *
         * @param methodName 方法名
         * @return Builder
         */
        public Builder<S> methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        /**
         * 设置调用参数
         *
         * @param params 调用参数
         * @return Builder
         */
        public Builder<S> params(Object params) {
            this.params = params;
            return this;
        }

        /**
         * 设置是否异步调用
         *
         * @param async 是否异步
         * @return Builder
         */
        public Builder<S> async(boolean async) {
            this.async = async;
            return this;
        }

        /**
         * 设置是否保存调用记录
         *
         * @param saveRecord 是否保存
         * @return Builder
         */
        public Builder<S> saveRecord(boolean saveRecord) {
            this.saveRecord = saveRecord;
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
         * 设置备注
         *
         * @param remark 备注
         * @return Builder
         */
        public Builder<S> remark(String remark) {
            this.remark = remark;
            return this;
        }

        /**
         * 构建InvocationRequest实例，校验scene、beanName、methodName非空
         *
         * @return InvocationRequest实例
         * @throws NullPointerException 如果scene、beanName或methodName为null
         */
        public InvocationRequest<S> build() {
            Objects.requireNonNull(scene, "scene must not be null");
            Objects.requireNonNull(beanName, "beanName must not be null");
            Objects.requireNonNull(methodName, "methodName must not be null");
            return new InvocationRequest<>(this);
        }
    }
}
