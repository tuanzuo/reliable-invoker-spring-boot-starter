package com.tz.reliableinvoker.api.impl;

import com.tz.reliableinvoker.config.ReliableInvokerProperties;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.dao.impl.JdbcTemplateInvocationRecordDaoImpl;
import com.tz.reliableinvoker.model.BackupQueryRequest;
import com.tz.reliableinvoker.model.BackupRequest;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import com.tz.reliableinvoker.model.RetryRequest;
import com.tz.reliableinvoker.service.IBackupService;
import com.tz.reliableinvoker.service.IRetryService;
import com.tz.reliableinvoker.service.impl.BackupServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

enum TaskTestScene { TEST_SCENE }

/**
 * ReliableInvokerTaskImpl 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 14:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerTaskImplTest {

    private static final String SCENE = "TEST_SCENE";

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;
    private IInvocationRecordDao recordDao;
    private IRetryService retryService;
    private IBackupService backupService;
    private ReliableInvokerTaskImpl task;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(db);
        ReliableInvokerProperties properties = new ReliableInvokerProperties();
        recordDao = new JdbcTemplateInvocationRecordDaoImpl(jdbcTemplate, properties);
        retryService = mock(IRetryService.class);
        backupService = new BackupServiceImpl(recordDao);
        task = new ReliableInvokerTaskImpl(recordDao, retryService, backupService);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testRetry() {
        for (int i = 0; i < 3; i++) {
            InvocationRecord record = createRecord(InvocationStatusEnum.PENDING.getCode(), "SN-RETRY-" + i);
            recordDao.save(record);
        }

        RetryRequest<TaskTestScene> request = RetryRequest.<TaskTestScene>builder()
                .scene(TaskTestScene.TEST_SCENE)
                .statusList(Arrays.asList(InvocationStatusEnum.PENDING.getCode()))
                .shardTotal(1)
                .shardIndex(0)
                .limit(100)
                .build();

        int count = task.retry(request);

        assertEquals(3, count);
    }

    @Test
    void testBackup() {
        for (int i = 0; i < 5; i++) {
            jdbcTemplate.update(
                    "INSERT INTO reliable_invocation_record"
                            + " (serial_no, scene, bean_name, method_name, params, status,"
                            + " retry_count, max_retry_count, retry_delay, create_time)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "SN-BACKUP-" + i, SCENE, "testBean", "testMethod", "{}",
                    InvocationStatusEnum.SUCCESS.getCode(), 0, 3, 5000,
                    Timestamp.valueOf(LocalDateTime.now().minusDays(10))
            );
        }

        BackupRequest<TaskTestScene> request = BackupRequest.<TaskTestScene>builder()
                .scene(TaskTestScene.TEST_SCENE)
                .statusList(Arrays.asList(InvocationStatusEnum.SUCCESS.getCode()))
                .days(7)
                .shardTotal(1)
                .shardIndex(0)
                .limit(100)
                .build();

        int count = task.backup(request);

        assertEquals(5, count);

        BackupQueryRequest query = new BackupQueryRequest();
        query.setScene(SCENE);
        query.setStatusList(Arrays.asList(InvocationStatusEnum.SUCCESS.getCode()));
        query.setDays(7);
        query.setShardTotal(1);
        query.setShardIndex(0);
        query.setLimit(100);
        assertTrue(recordDao.findForBackup(query).isEmpty());
    }

    private InvocationRecord createRecord(int status, String serialNo) {
        InvocationRecord record = new InvocationRecord();
        record.setSerialNo(serialNo);
        record.setScene(SCENE);
        record.setBeanName("testBean");
        record.setMethodName("testMethod");
        record.setParams("{}");
        record.setStatus(status);
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);
        record.setExecuteTime(LocalDateTime.now().plusHours(1));
        return record;
    }
}
