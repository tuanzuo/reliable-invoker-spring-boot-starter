package com.tz.reliableinvoker.config;

import com.tz.reliableinvoker.api.IInvocationHandler;
import com.tz.reliableinvoker.exception.ReliableInvokerException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HandlerRegistry 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class HandlerRegistryTest {

    @Test
    void testGetRegisteredHandler() {
        IInvocationHandler<?, ?> handler = mock(IInvocationHandler.class);
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<String, IInvocationHandler<?, ?>>();
        map.put("ORDER", handler);
        HandlerRegistry registry = new HandlerRegistry(map);

        IInvocationHandler<?, ?> found = registry.get("ORDER");
        assertSame(handler, found);
    }

    @Test
    void testGetUnregisteredHandlerThrowsException() {
        HandlerRegistry registry = new HandlerRegistry(Collections.<String, IInvocationHandler<?, ?>>emptyMap());

        ReliableInvokerException ex = assertThrows(ReliableInvokerException.class,
                () -> registry.get("NOT_EXIST"));
        assertTrue(ex.getMessage().contains("NOT_EXIST"));
    }

    @Test
    void testRegistryIsImmutable() {
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<String, IInvocationHandler<?, ?>>();
        map.put("ORDER", mock(IInvocationHandler.class));
        HandlerRegistry registry = new HandlerRegistry(map);

        assertThrows(UnsupportedOperationException.class,
                () -> registry.entrySet().clear());
    }
}
