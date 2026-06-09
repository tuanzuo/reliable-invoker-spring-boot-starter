package com.tz.reliableinvoker.config;

import com.tz.reliableinvoker.api.IInvocationHandler;
import com.tz.reliableinvoker.exception.ReliableInvokerException;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Handler 注册表
 * <p>持有 scene → Handler 的映射，不可变。
 * 由 {@link ReliableInvokerAutoConfiguration} 在启动时构建。</p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class HandlerRegistry {

    private final Map<String, IInvocationHandler<?, ?>> registry;

    /**
     * 构造函数
     *
     * @param registry scene → Handler 映射表
     */
    public HandlerRegistry(Map<String, IInvocationHandler<?, ?>> registry) {
        this.registry = Collections.unmodifiableMap(registry);
    }

    /**
     * 按场景名查找 Handler
     *
     * @param scene 场景名称（枚举的 name()）
     * @return 对应的 Handler
     * @throws ReliableInvokerException 未找到对应 Handler
     */
    public IInvocationHandler<?, ?> get(String scene) {
        IInvocationHandler<?, ?> handler = this.registry.get(scene);
        if (handler == null) {
            throw new ReliableInvokerException(
                    "No IInvocationHandler found for scene [" + scene + "]. "
                    + "Please register a bean implementing IInvocationHandler with getScene() = " + scene);
        }
        return handler;
    }

    /**
     * 返回注册表条目集合（只读视图）
     *
     * @return 注册表条目集合
     */
    public Set<Map.Entry<String, IInvocationHandler<?, ?>>> entrySet() {
        return this.registry.entrySet();
    }
}
