package com.tz.reliableinvoker.api.impl;

import com.tz.reliableinvoker.config.ReliableInvokerProperties;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.dao.impl.JdbcTemplateInvocationRecordDaoImpl;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationRequest;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import com.tz.reliableinvoker.service.IAsyncExecutor;
import com.tz.reliableinvoker.service.IRetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

enum TestScene { TEST_SCENE }

/**
 * ReliableInvokerImpl 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 14:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerImplTest {

    private EmbeddedDatabase db;
    private IInvocationRecordDao recordDao;
    private IRetryService retryService;
    private IAsyncExecutor asyncExecutor;
    private TaskExecutor taskExecutor;
    private ReliableInvokerProperties properties;
    private ReliableInvokerImpl invoker;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
        properties = new ReliableInvokerProperties();
        recordDao = new JdbcTemplateInvocationRecordDaoImpl(jdbcTemplate, properties);
        retryService = mock(IRetryService.class);
        asyncExecutor = mock(IAsyncExecutor.class);
        taskExecutor = mock(TaskExecutor.class);
        invoker = new ReliableInvokerImpl(recordDao, retryService, asyncExecutor, properties,
                taskExecutor, new HashMap<>());
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testSyncExecute() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.TEST_SCENE)
                .beanName("testBean")
                .methodName("testMethod")
                .async(false)
                .saveRecord(true)
                .build();

        InvocationRecord record = invoker.execute(request);

        assertNotNull(record);
        assertNotNull(record.getSerialNo());
        assertEquals("TEST_SCENE", record.getScene());
        verify(taskExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void testAsyncExecute() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.TEST_SCENE)
                .beanName("testBean")
                .methodName("testMethod")
                .async(true)
                .saveRecord(true)
                .build();

        InvocationRecord record = invoker.execute(request);

        assertNotNull(record);
        assertEquals(Integer.valueOf(InvocationStatusEnum.PENDING.getCode()), record.getStatus());
        verify(taskExecutor, times(1)).execute(any(Runnable.class));
    }

    @Test
    void testSaveRecordFalse() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.TEST_SCENE)
                .beanName("testBean")
                .methodName("testMethod")
                .async(false)
                .saveRecord(false)
                .build();

        InvocationRecord record = invoker.execute(request);

        assertNotNull(record);
        InvocationRecord found = recordDao.findBySerialNo(record.getSerialNo(), "TEST_SCENE");
        assertNull(found);
    }
}
