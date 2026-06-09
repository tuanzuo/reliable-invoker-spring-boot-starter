package com.tz.reliableinvoker.dao.impl;

import com.tz.reliableinvoker.config.ReliableInvokerProperties;
import com.tz.reliableinvoker.model.BackupQueryRequest;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import com.tz.reliableinvoker.model.RetryQueryRequest;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JdbcTemplateInvocationRecordDaoImpl 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 10:30:00
 * @version 1.0.0-SNAPSHOT
 */
class JdbcTemplateInvocationRecordDaoImplTest {

    private static final String SCENE = "default";

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;
    private JdbcTemplateInvocationRecordDaoImpl dao;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(db);
        ReliableInvokerProperties properties = new ReliableInvokerProperties();
        dao = new JdbcTemplateInvocationRecordDaoImpl(jdbcTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testSaveAndFindBySerialNo() {
        InvocationRecord record = createRecord(SCENE, "SN-001");
        InvocationRecord saved = dao.save(record);

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("SN-001", saved.getSerialNo());
        assertEquals(SCENE, saved.getScene());
        assertEquals("{\"key\":\"value\"}", saved.getParams());
        assertEquals(Integer.valueOf(InvocationStatusEnum.PENDING.getCode()), saved.getStatus());
        assertEquals(Integer.valueOf(0), saved.getRetryCount());
        assertEquals(Integer.valueOf(3), saved.getMaxRetryCount());
        assertEquals(Integer.valueOf(5000), saved.getRetryDelay());

        InvocationRecord found = dao.findBySerialNo("SN-001", SCENE);
        assertNotNull(found);
        assertEquals(saved.getId(), found.getId());
    }

    @Test
    void testUpdateStatus() {
        InvocationRecord record = createRecord(SCENE, "SN-002");
        InvocationRecord saved = dao.save(record);
        assertNotNull(saved);

        dao.updateStatus(saved.getId(), InvocationStatusEnum.SUCCESS.getCode(), null, SCENE);

        InvocationRecord updated = dao.findBySerialNo("SN-002", SCENE);
        assertNotNull(updated);
        assertEquals(Integer.valueOf(InvocationStatusEnum.SUCCESS.getCode()), updated.getStatus());
    }

    @Test
    void testFindForRetryWithShard() {
        for (int i = 0; i < 10; i++) {
            InvocationRecord record = createRecord(SCENE, "SN-SHARD-" + i);
            dao.save(record);
        }

        RetryQueryRequest request = new RetryQueryRequest();
        request.setScene(SCENE);
        request.setStatusList(Arrays.asList(InvocationStatusEnum.PENDING.getCode()));
        request.setShardTotal(2);
        request.setShardIndex(0);
        request.setLimit(100);

        List<InvocationRecord> results = dao.findForRetry(request);

        assertFalse(results.isEmpty());
        for (InvocationRecord r : results) {
            assertEquals(0, r.getId() % 2, "Expected id%2==0, got id=" + r.getId());
        }
    }

    @Test
    void testFindForRetryWithLimit() {
        for (int i = 0; i < 10; i++) {
            InvocationRecord record = createRecord(SCENE, "SN-LIMIT-" + i);
            dao.save(record);
        }

        RetryQueryRequest request = new RetryQueryRequest();
        request.setScene(SCENE);
        request.setStatusList(Arrays.asList(InvocationStatusEnum.PENDING.getCode()));
        request.setShardTotal(1);
        request.setShardIndex(0);
        request.setLimit(3);

        List<InvocationRecord> results = dao.findForRetry(request);

        assertEquals(3, results.size());
    }

    @Test
    void testFindForBackup() {
        for (int i = 0; i < 5; i++) {
            jdbcTemplate.update(
                    "INSERT INTO reliable_invocation_record"
                            + " (serial_no, scene, params, status,"
                            + " retry_count, max_retry_count, retry_delay, create_time)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    "SN-BACKUP-" + i, SCENE, "{}",
                    InvocationStatusEnum.SUCCESS.getCode(), 0, 3, 5000,
                    Timestamp.valueOf(LocalDateTime.now().minusDays(10))
            );
        }

        BackupQueryRequest request = new BackupQueryRequest();
        request.setScene(SCENE);
        request.setStatusList(Arrays.asList(InvocationStatusEnum.SUCCESS.getCode()));
        request.setDays(7);
        request.setShardTotal(1);
        request.setShardIndex(0);
        request.setLimit(100);

        List<InvocationRecord> results = dao.findForBackup(request);

        assertEquals(5, results.size());
    }

    @Test
    void testDeleteById() {
        InvocationRecord saved = dao.save(createRecord(SCENE, "SN-DEL"));

        dao.deleteById(saved.getId(), SCENE);

        InvocationRecord found = dao.findBySerialNo("SN-DEL", SCENE);
        assertNull(found);
    }

    private InvocationRecord createRecord(String scene, String serialNo) {
        InvocationRecord record = new InvocationRecord();
        record.setSerialNo(serialNo);
        record.setScene(scene);
        record.setParams("{\"key\":\"value\"}");
        record.setStatus(InvocationStatusEnum.PENDING.getCode());
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);
        record.setExecuteTime(LocalDateTime.now().minusHours(1));
        record.setRemark("test remark");
        return record;
    }
}
