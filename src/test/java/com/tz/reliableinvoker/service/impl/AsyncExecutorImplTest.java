package com.tz.reliableinvoker.service.impl;

import com.tz.reliableinvoker.service.IAsyncExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AsyncExecutorImpl 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 14:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class AsyncExecutorImplTest {

    @Test
    void testExecute() {
        AtomicBoolean executed = new AtomicBoolean(false);
        TaskExecutor taskExecutor = new TaskExecutor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }
        };
        IAsyncExecutor executor = new AsyncExecutorImpl(taskExecutor);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                executed.set(true);
            }
        });
        assertTrue(executed.get());
    }
}
