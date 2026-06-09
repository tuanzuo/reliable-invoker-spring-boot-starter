package com.tz.reliableinvoker.api;

/**
 * 可靠调用处理器接口
 * <p>业务方实现此接口并注册为 Spring Bean，
 * 框架自动扫描并按场景（scene）建立映射。
 * 重试时直接调用 {@link #execute(String)} 而非反射，彻底避免方法名硬编码问题。</p>
 *
 * @param <S> 业务场景枚举类型
 * @param <R> 执行返回值类型
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public interface IInvocationHandler<S extends Enum<S>, R> {

    /**
     * 返回当前 Handler 对应的业务场景
     *
     * @return 场景枚举值
     */
    S getScene();

    /**
     * 执行业务逻辑
     * <p>参数为 JSON 字符串，由 Handler 自行反序列化为目标类型。
     * paramsJson 在无参数时为 null。</p>
     *
     * @param paramsJson 序列化后的参数 JSON，可能为 null
     * @return 执行结果
     */
    R execute(String paramsJson);
}
