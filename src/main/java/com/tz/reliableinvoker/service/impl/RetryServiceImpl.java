package com.tz.reliableinvoker.service.impl;

import com.tz.reliableinvoker.api.IInvocationHandler;
import com.tz.reliableinvoker.config.HandlerRegistry;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.exception.ExecutionException;
import com.tz.reliableinvoker.exception.ReliableInvokerException;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import com.tz.reliableinvoker.service.IRetryService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 默认重试执行服务实现（Handler 模式）
 * <p>通过 HandlerRegistry 查找 Handler 并直接调用，
 * 零反射，彻底避免方法名硬编码问题。</p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class RetryServiceImpl implements IRetryService {

    /** Handler 注册表 */
    private final HandlerRegistry handlerRegistry;

    /** 调用记录数据访问对象 */
    private final IInvocationRecordDao recordDao;

    public RetryServiceImpl(HandlerRegistry handlerRegistry, IInvocationRecordDao recordDao) {
        this.handlerRegistry = handlerRegistry;
        this.recordDao = recordDao;
    }

    @Override
    public void retry(InvocationRecord record) {
        Integer retryCount = record.getRetryCount();
        try {
            IInvocationHandler<?, ?> handler = this.handlerRegistry.get(record.getScene());
            handler.execute(record.getParams());

            this.recordDao.updateStatus(record.getId(),
                    InvocationStatusEnum.SUCCESS.getCode(), null, record.getScene());
        } catch (ReliableInvokerException e) {
            this.recordDao.updateStatus(record.getId(),
                    InvocationStatusEnum.FAILED.getCode(), null, record.getScene());
            throw e;
        } catch (Exception e) {
            int newRetryCount = (retryCount != null ? retryCount : 0) + 1;
            Integer maxRetryCount = record.getMaxRetryCount();
            int maxCount = maxRetryCount != null ? maxRetryCount : 0;
            Integer retryDelay = record.getRetryDelay();
            long delayMs = retryDelay != null ? retryDelay.longValue() : 0;
            LocalDateTime nextExecuteTime = LocalDateTime.now().plus(delayMs, ChronoUnit.MILLIS);

            this.recordDao.updateStatus(record.getId(),
                    newRetryCount >= maxCount
                            ? InvocationStatusEnum.FAILED.getCode()
                            : InvocationStatusEnum.PENDING.getCode(),
                    nextExecuteTime, record.getScene());
            throw new ExecutionException(
                    "Handler execution failed for scene [" + record.getScene()
                    + "], retryCount=" + newRetryCount, e);
        }
    }
}
