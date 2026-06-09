package com.tz.reliableinvoker.service.impl;

import com.tz.reliableinvoker.api.IInvocationHandler;
import com.tz.reliableinvoker.config.HandlerRegistry;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.exception.ExecutionException;
import com.tz.reliableinvoker.exception.ReliableInvokerException;
import com.tz.reliableinvoker.exception.ReliableInvokerException;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RetryServiceImpl 单元测试（Handler 模式）
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class RetryServiceImplTest {

    @Test
    void testRetrySuccess() {
        IInvocationHandler<?, ?> handler = mock(IInvocationHandler.class);
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<String, IInvocationHandler<?, ?>>();
        map.put("ORDER", handler);
        HandlerRegistry registry = new HandlerRegistry(map);

        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        RetryServiceImpl retryService = new RetryServiceImpl(registry, recordDao);

        InvocationRecord record = new InvocationRecord();
        record.setId(1L);
        record.setScene("ORDER");
        record.setParams("{\"orderId\":123}");
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);

        retryService.retry(record);

        verify(handler).execute("{\"orderId\":123}");
        verify(recordDao).updateStatus(1L, InvocationStatusEnum.SUCCESS.getCode(), null, "ORDER");
    }

    @Test
    void testRetryHandlerNotFoundThrowsException() {
        HandlerRegistry registry = new HandlerRegistry(Collections.<String, IInvocationHandler<?, ?>>emptyMap());
        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        RetryServiceImpl retryService = new RetryServiceImpl(registry, recordDao);

        InvocationRecord record = new InvocationRecord();
        record.setId(1L);
        record.setScene("NOT_EXIST");
        record.setParams("{}");
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);

        assertThrows(ReliableInvokerException.class, () -> retryService.retry(record));
        verify(recordDao).updateStatus(eq(1L), eq(InvocationStatusEnum.FAILED.getCode()), any(), eq("NOT_EXIST"));
    }

    @Test
    void testRetryHandlerThrowsExceptionRetryPending() {
        IInvocationHandler<?, ?> handler = mock(IInvocationHandler.class);
        doThrow(new RuntimeException("business error")).when(handler).execute(anyString());
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<String, IInvocationHandler<?, ?>>();
        map.put("ORDER", handler);
        HandlerRegistry registry = new HandlerRegistry(map);

        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        RetryServiceImpl retryService = new RetryServiceImpl(registry, recordDao);

        InvocationRecord record = new InvocationRecord();
        record.setId(1L);
        record.setScene("ORDER");
        record.setParams("{\"orderId\":123}");
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);

        assertThrows(ExecutionException.class, () -> retryService.retry(record));
        verify(recordDao).updateStatus(eq(1L), eq(InvocationStatusEnum.PENDING.getCode()), any(), eq("ORDER"));
    }

    @Test
    void testRetryMaxRetryExhaustedFails() {
        IInvocationHandler<?, ?> handler = mock(IInvocationHandler.class);
        doThrow(new RuntimeException("business error")).when(handler).execute(anyString());
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<String, IInvocationHandler<?, ?>>();
        map.put("ORDER", handler);
        HandlerRegistry registry = new HandlerRegistry(map);

        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        RetryServiceImpl retryService = new RetryServiceImpl(registry, recordDao);

        InvocationRecord record = new InvocationRecord();
        record.setId(1L);
        record.setScene("ORDER");
        record.setParams("{\"orderId\":123}");
        record.setRetryCount(2);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);

        assertThrows(ExecutionException.class, () -> retryService.retry(record));
        verify(recordDao).updateStatus(eq(1L), eq(InvocationStatusEnum.FAILED.getCode()), any(), eq("ORDER"));
    }

    @Test
    void testRetryWithNullParams() {
        IInvocationHandler<?, ?> handler = mock(IInvocationHandler.class);
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<String, IInvocationHandler<?, ?>>();
        map.put("ORDER", handler);
        HandlerRegistry registry = new HandlerRegistry(map);

        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        RetryServiceImpl retryService = new RetryServiceImpl(registry, recordDao);

        InvocationRecord record = new InvocationRecord();
        record.setId(1L);
        record.setScene("ORDER");
        record.setParams(null);
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);

        retryService.retry(record);

        verify(handler).execute(null);
        verify(recordDao).updateStatus(1L, InvocationStatusEnum.SUCCESS.getCode(), null, "ORDER");
    }
}
