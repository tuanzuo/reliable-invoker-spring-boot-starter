package com.tz.reliableinvoker.service;

import com.tz.reliableinvoker.model.InvocationRecord;

/**
 * 重试执行服务接口
 * <p>
 * 对失败的调用记录进行重试。
 * 实现类需根据记录中的方法签名和参数重新发起调用，
 * 并根据结果更新记录状态（成功/仍失败/达到上限后转备份）。
 * </p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public interface IRetryService {

    /**
     * 对单条调用记录执行重试
     *
     * @param record 待重试的调用记录，必须包含有效的类名、方法名及参数
     */
    void retry(InvocationRecord record);
}
