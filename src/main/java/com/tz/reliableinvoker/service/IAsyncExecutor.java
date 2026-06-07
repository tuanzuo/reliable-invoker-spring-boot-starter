package com.tz.reliableinvoker.service;

/**
 * 异步执行器接口
 * <p>
 * 将长时间运行的任务提交到独立线程池执行，
 * 避免阻塞主业务流程。
 * </p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public interface IAsyncExecutor {

    /**
     * 异步执行指定任务
     *
     * @param task 待执行的任务
     */
    void execute(Runnable task);
}
