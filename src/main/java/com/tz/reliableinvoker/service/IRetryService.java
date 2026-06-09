package com.tz.reliableinvoker.service;

import com.tz.reliableinvoker.model.InvocationRecord;

/**
 * 重试执行服务接口
 * <p>
 * 对失败的调用记录进行重试。
 * 实现类通过 HandlerRegistry 查找对应场景的 Handler 并直接调用，
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
     * @param record 待重试的调用记录，包含场景名和参数 JSON
     */
    void retry(InvocationRecord record);
}
