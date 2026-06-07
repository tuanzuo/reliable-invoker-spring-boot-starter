package com.tz.reliableinvoker.service.impl;

import com.tz.reliableinvoker.service.IAsyncExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * 默认异步执行器实现，基于 Spring TaskExecutor
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 02:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class AsyncExecutorImpl implements IAsyncExecutor {

    /** 异步任务执行器 */
    private final TaskExecutor taskExecutor;

    public AsyncExecutorImpl(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void execute(Runnable task) {
        this.taskExecutor.execute(task);
    }
}
