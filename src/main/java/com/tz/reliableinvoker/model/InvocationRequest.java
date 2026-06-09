package com.tz.reliableinvoker.model;

import java.util.Objects;

/**
 * 调用请求（Handler 模式）
 * <p>通过 scene 定位 IInvocationHandler 执行，
 * 移除了 beanName/methodName 字符串反射方式。</p>
 *
 * @param <S> 业务场景枚举类型
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class InvocationRequest<S extends Enum<S>> {

    /** 业务场景（非空） */
    private final S scene;

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
        this.params = builder.params;
        this.async = builder.async;
        this.saveRecord = builder.saveRecord;
        this.maxRetry = builder.maxRetry;
        this.retryDelay = builder.retryDelay;
        this.remark = builder.remark;
    }

    public static <S extends Enum<S>> Builder<S> builder() {
        return new Builder<>();
    }

    public S getScene() { return scene; }
    public Object getParams() { return params; }
    public boolean isAsync() { return async; }
    public boolean isSaveRecord() { return saveRecord; }
    public int getMaxRetry() { return maxRetry; }
    public int getRetryDelay() { return retryDelay; }
    public String getRemark() { return remark; }

    public static class Builder<S extends Enum<S>> {
        private S scene;
        private Object params;
        private boolean async;
        private boolean saveRecord = true;
        private int maxRetry;
        private int retryDelay;
        private String remark;

        public Builder<S> scene(S scene) { this.scene = scene; return this; }
        public Builder<S> params(Object params) { this.params = params; return this; }
        public Builder<S> async(boolean async) { this.async = async; return this; }
        public Builder<S> saveRecord(boolean saveRecord) { this.saveRecord = saveRecord; return this; }
        public Builder<S> maxRetry(int maxRetry) { this.maxRetry = maxRetry; return this; }
        public Builder<S> retryDelay(int retryDelay) { this.retryDelay = retryDelay; return this; }
        public Builder<S> remark(String remark) { this.remark = remark; return this; }

        public InvocationRequest<S> build() {
            Objects.requireNonNull(scene, "scene must not be null");
            return new InvocationRequest<>(this);
        }
    }
}
