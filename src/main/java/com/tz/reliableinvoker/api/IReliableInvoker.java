package com.tz.reliableinvoker.api;

import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationRequest;

/**
 * 可靠调用执行接口
 * <p>
 * 定义具备可靠性保证的方法调用执行能力。
 * 调用者通过本接口提交{@link InvocationRequest}，
 * 系统自动完成参数校验、持久化、调用链传递等可靠性保障。
 * </p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public interface IReliableInvoker {

    /**
     * 执行带有可靠性保证的方法调用
     * <p>
     * 根据请求中的方法签名和参数，通过反射（或代理）调用目标方法。
     * 调用成功后返回包含执行结果的{@link InvocationRecord}；
     * 若调用失败，记录会标记为待重试状态。
     * </p>
     *
     * @param request 调用请求，包含类名、方法名、参数等信息
     * @return 调用记录，包含执行状态、序列号等元数据
     */
    InvocationRecord execute(InvocationRequest<?> request);
}
