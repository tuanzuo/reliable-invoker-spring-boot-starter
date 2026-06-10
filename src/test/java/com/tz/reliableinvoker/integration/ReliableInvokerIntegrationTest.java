package com.tz.reliableinvoker.integration;

import com.tz.reliableinvoker.api.IInvocationHandler;
import com.tz.reliableinvoker.api.IReliableInvoker;
import com.tz.reliableinvoker.api.IReliableInvokerTask;
import com.tz.reliableinvoker.config.HandlerRegistry;
import com.tz.reliableinvoker.config.ReliableInvokerProperties;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.dao.impl.JdbcTemplateInvocationRecordDaoImpl;
import com.tz.reliableinvoker.model.BackupRequest;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationRequest;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import com.tz.reliableinvoker.model.RetryRequest;
import com.tz.reliableinvoker.service.IBackupService;
import com.tz.reliableinvoker.service.IRetryService;
import com.tz.reliableinvoker.service.impl.AsyncExecutorImpl;
import com.tz.reliableinvoker.service.impl.BackupServiceImpl;
import com.tz.reliableinvoker.service.impl.RetryServiceImpl;
import com.tz.reliableinvoker.api.impl.ReliableInvokerImpl;
import com.tz.reliableinvoker.api.impl.ReliableInvokerTaskImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 可靠调用组件集成测试
 * <p>测试完整调用链路：IReliableInvoker → DAO → IReliableInvokerTask</p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerIntegrationTest {

    enum TestScene {
        ORDER
    }

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;
    private IInvocationRecordDao recordDao;
    private IReliableInvoker invoker;
    private IReliableInvokerTask task;
    private ThreadPoolTaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(db);

        ReliableInvokerProperties properties = new ReliableInvokerProperties();
        recordDao = new JdbcTemplateInvocationRecordDaoImpl(jdbcTemplate, properties);

        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(2);
        taskExecutor.setMaxPoolSize(5);
        taskExecutor.setQueueCapacity(10);
        taskExecutor.setThreadNamePrefix("test-");
        taskExecutor.initialize();

        IInvocationHandler<TestScene, String> handler = new IInvocationHandler<TestScene, String>() {
            @Override
            public TestScene getScene() {
                return TestScene.ORDER;
            }

            @Override
            public String execute(String paramsJson) {
                return "success";
            }
        };

        Map<String, IInvocationHandler<?, ?>> handlerMap = new HashMap<String, IInvocationHandler<?, ?>>();
        handlerMap.put("ORDER", handler);
        HandlerRegistry handlerRegistry = new HandlerRegistry(handlerMap);

        IRetryService retryService = new RetryServiceImpl(handlerRegistry, recordDao);
        IBackupService backupService = new BackupServiceImpl(recordDao);

        Map<String, TaskExecutor> sceneExecutors = new HashMap<String, TaskExecutor>();
        invoker = new ReliableInvokerImpl(recordDao, retryService, properties,
                taskExecutor, sceneExecutors, handlerRegistry);
        task = new ReliableInvokerTaskImpl(recordDao, retryService, backupService);
    }

    @AfterEach
    void tearDown() {
        taskExecutor.shutdown();
        db.shutdown();
    }

    @Test
    void testSyncInvokeAndRetry() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .params("{\"orderId\":123}")
                .async(false)
                .saveRecord(true)
                .maxRetry(3)
                .retryDelay(1000)
                .build();

        InvocationRecord record = invoker.execute(request);

        assertNotNull(record);
        assertNotNull(record.getSerialNo());
        assertEquals("ORDER", record.getScene());
        assertEquals(Integer.valueOf(InvocationStatusEnum.PENDING.getCode()), record.getStatus());

        InvocationRecord saved = recordDao.findBySerialNo(record.getSerialNo(), "ORDER");
        assertNotNull(saved);
        assertEquals("\"{\\\"orderId\\\":123}\"", saved.getParams());

        RetryRequest<TestScene> retryRequest = RetryRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .statusList(Arrays.asList(InvocationStatusEnum.PENDING.getCode()))
                .shardTotal(1)
                .shardIndex(0)
                .limit(100)
                .build();

        int count = task.retry(retryRequest);
        assertEquals(1, count);

        InvocationRecord afterRetry = recordDao.findBySerialNo(record.getSerialNo(), "ORDER");
        assertEquals(Integer.valueOf(InvocationStatusEnum.SUCCESS.getCode()), afterRetry.getStatus());
    }

    @Test
    void testAsyncInvokeAndRetry() throws InterruptedException {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .params("{\"orderId\":456}")
                .async(true)
                .saveRecord(true)
                .maxRetry(3)
                .retryDelay(1000)
                .build();

        InvocationRecord record = invoker.execute(request);
        assertNotNull(record);

        CountDownLatch latch = new CountDownLatch(1);
        Thread.sleep(200);

        RetryRequest<TestScene> retryRequest = RetryRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .statusList(Arrays.asList(InvocationStatusEnum.PENDING.getCode()))
                .shardTotal(1)
                .shardIndex(0)
                .limit(100)
                .build();

        int count = task.retry(retryRequest);
        assertEquals(1, count);

        Thread.sleep(500);

        InvocationRecord afterRetry = recordDao.findBySerialNo(record.getSerialNo(), "ORDER");
        assertEquals(Integer.valueOf(InvocationStatusEnum.SUCCESS.getCode()), afterRetry.getStatus());
    }

    @Test
    void testInvokeWithoutSaveRecord() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .params("{\"orderId\":789}")
                .async(false)
                .saveRecord(false)
                .build();

        InvocationRecord record = invoker.execute(request);

        assertNotNull(record);
        assertNull(recordDao.findBySerialNo(record.getSerialNo(), "ORDER"));
    }

    @Test
    void testRetryMaxRetryExhausted() {
        InvocationRecord record = new InvocationRecord();
        record.setSerialNo("SN-MAX-RETRY");
        record.setScene("ORDER");
        record.setParams("{}");
        record.setStatus(InvocationStatusEnum.PENDING.getCode());
        record.setRetryCount(3);
        record.setMaxRetryCount(3);
        record.setRetryDelay(1000);
        record.setExecuteTime(LocalDateTime.now().minusHours(1));
        recordDao.save(record);

        RetryRequest<TestScene> retryRequest = RetryRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .statusList(Arrays.asList(InvocationStatusEnum.PENDING.getCode()))
                .shardTotal(1)
                .shardIndex(0)
                .limit(100)
                .build();

        int count = task.retry(retryRequest);
        assertEquals(1, count);

        InvocationRecord afterRetry = recordDao.findBySerialNo("SN-MAX-RETRY", "ORDER");
        assertEquals(Integer.valueOf(InvocationStatusEnum.FAILED.getCode()), afterRetry.getStatus());
    }

    @Test
    void testBackupAndDelete() {
        for (int i = 0; i < 3; i++) {
            jdbcTemplate.update(
                    "INSERT INTO reliable_invocation_record"
                            + " (serial_no, scene, params, status,"
                            + " retry_count, max_retry_count, retry_delay, create_time)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    "SN-BACKUP-" + i, "ORDER", "{}",
                    InvocationStatusEnum.SUCCESS.getCode(), 0, 3, 5000,
                    Timestamp.valueOf(LocalDateTime.now().minusDays(10))
            );
        }

        BackupRequest<TestScene> request = BackupRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .statusList(Arrays.asList(InvocationStatusEnum.SUCCESS.getCode()))
                .days(7)
                .shardTotal(1)
                .shardIndex(0)
                .limit(100)
                .build();

        int count = task.backup(request);
        assertEquals(3, count);

        for (int i = 0; i < 3; i++) {
            assertNull(recordDao.findBySerialNo("SN-BACKUP-" + i, "ORDER"));
        }
    }

    @Test
    void testScenePropertiesOverride() {
        ReliableInvokerProperties properties = new ReliableInvokerProperties();
        properties.setMaxRetry(3);
        properties.setDefaultDelay(5000);

        ReliableInvokerProperties.SceneProperties sceneProps = new ReliableInvokerProperties.SceneProperties();
        sceneProps.setMaxRetry(10);
        sceneProps.setDefaultDelay(2000);
        properties.getScenes().put("ORDER", sceneProps);

        recordDao = new JdbcTemplateInvocationRecordDaoImpl(jdbcTemplate, properties);

        IInvocationHandler<TestScene, String> handler = new IInvocationHandler<TestScene, String>() {
            @Override
            public TestScene getScene() {
                return TestScene.ORDER;
            }

            @Override
            public String execute(String paramsJson) {
                return "success";
            }
        };

        Map<String, IInvocationHandler<?, ?>> handlerMap = new HashMap<String, IInvocationHandler<?, ?>>();
        handlerMap.put("ORDER", handler);
        HandlerRegistry handlerRegistry = new HandlerRegistry(handlerMap);

        IRetryService retryService = new RetryServiceImpl(handlerRegistry, recordDao);
        IBackupService backupService = new BackupServiceImpl(recordDao);

        IReliableInvoker customInvoker = new ReliableInvokerImpl(recordDao, retryService, properties,
                taskExecutor, new HashMap<String, TaskExecutor>(), handlerRegistry);

        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .async(false)
                .saveRecord(true)
                .build();

        InvocationRecord record = customInvoker.execute(request);

        assertEquals(Integer.valueOf(10), record.getMaxRetryCount());
        assertEquals(Integer.valueOf(2000), record.getRetryDelay());
    }
}
