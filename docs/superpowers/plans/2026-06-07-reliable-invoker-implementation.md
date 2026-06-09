# 可靠调用 Spring Boot Starter 实施计划

> **For agentic workers:** 使用 subagent-driven-development 或 executing-plans 逐任务执行。

**Goal:** 实现 Spring Boot Starter 组件，提供编程式 API 用于带持久化保证的方法调用，支持同步/异步执行、重试、备份/清理。

**Architecture:** 接口驱动，I-前缀 + Impl 后缀，@ConditionalOnMissingBean 可覆盖，NamedParameterJdbcTemplate 持久化，@Configuration + spring.factories 自动装配，scene 枚举由使用方自定义。

**Tech Stack:** Java 11, Spring Boot 2.3.12, Maven, JdbcTemplate, Jackson

**注释规范:** 所有 public 类、方法、字段均有 Javadoc 注释。

---

### Task 1: 项目初始化

- Create: `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.3.12.RELEASE</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>reliable-invoker-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <properties><java.version>11</java.version></properties>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
        <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency>
    </dependencies>
</project>
```

`mvn compile` → BUILD SUCCESS → commit

---

### Task 2: 异常类

Create: `exception/ReliableInvokerException.java`, `ExecutionException.java`, `RecordNotFoundException.java`, `ParamsTooLargeException.java`

所有异常 extends `ReliableInvokerException extends RuntimeException`，含 `(String message)` 和 `(String message, Throwable cause)` 构造器。`mvn compile` → commit

---

### Task 3: InvocationRecord 实体

Create: `model/InvocationRecord.java`

```java
package com.example.reliableinvoker.model;

import java.time.LocalDateTime;

/**
 * 调用记录实体
 */
public class InvocationRecord {

    /** 主键ID */
    private Long id;
    /** 流水号 */
    private String serialNo;
    /** 业务场景 */
    private String scene;
    /** Bean名称 */
    private String beanName;
    /** 方法名 */
    private String methodName;
    /** 参数JSON */
    private String params;
    /** 状态：0-待执行 1-执行中 2-成功 3-失败 */
    private Integer status;
    /** 已重试次数 */
    private Integer retryCount;
    /** 最大重试次数 */
    private Integer maxRetryCount;
    /** 重试延迟(ms) */
    private Integer retryDelay;
    /** 计划执行时间 */
    private LocalDateTime executeTime;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    // getters and setters ...
}
```

`mvn compile` → commit

---

### Task 4: 请求对象

Create: `model/InvocationRequest.java`, `RetryRequest.java`, `BackupRequest.java`, `RetryQueryRequest.java`, `BackupQueryRequest.java`

**InvocationRequest\<S extends Enum\<S\>\>** — builder 模式：
- `S scene` (not null)
- `String beanName` (not null), `String methodName` (not null)
- `Object params`, `boolean async`, `boolean saveRecord = true`
- `int maxRetry` (>0 覆盖), `int retryDelay` (>0 覆盖), `String remark`

**RetryRequest\<S extends Enum\<S\>\>** — builder 模式：
- `S scene` (not null), `List<Integer> statusList` (默认 `[0]`)
- `int maxRetry`, `int retryDelay`, `int shardTotal = 1`, `int shardIndex`, `int limit = 100`

**BackupRequest\<S extends Enum\<S\>\>** — builder 模式：
- `S scene` (not null), `List<Integer> statusList` (默认 `[2]`)
- `int days` (>0), `int shardTotal = 1`, `int shardIndex`, `int limit = 100`

**RetryQueryRequest** — 普通 POJO（用于 Dao 查询）：
- `String scene`, `List<Integer> statusList`, `int shardTotal = 1`, `int shardIndex`, `int limit = 100`

**BackupQueryRequest** — 普通 POJO（用于 Dao 查询）：
- `String scene`, `List<Integer> statusList`, `int days`, `int shardTotal = 1`, `int shardIndex`, `int limit = 100`

`mvn compile` → commit

---

### Task 5: API 接口

Create:
- `api/IReliableInvoker.java` — `InvocationRecord execute(InvocationRequest<?> request)`
- `api/IReliableInvokerTask.java` — `int retry(RetryRequest<?>)`, `int backup(BackupRequest<?>)`
- `dao/IInvocationRecordDao.java` — `save(InvocationRecord)`, `updateStatus(Long, Integer, String scene)`, `deleteById(Long, String scene)`, `findBySerialNo(String, String scene)`, `findForRetry(RetryQueryRequest)`, `findForBackup(BackupQueryRequest)`
- `service/IRetryService.java` — `void retry(InvocationRecord)`
- `service/IBackupService.java` — `void backup(List<InvocationRecord>)`
- `service/IAsyncExecutor.java` — `void execute(Runnable)`

`mvn compile` → commit

---

### Task 6: 配置属性类

Create: `config/ReliableInvokerProperties.java`

```java
@ConfigurationProperties(prefix = "reliable.invoker")
public class ReliableInvokerProperties {
    private boolean enabled = true;
    private String tableName = "reliable_invocation_record";
    private int maxRetry = 3;
    private int defaultDelay = 5000;
    private AsyncProperties async = new AsyncProperties();
    private Map<String, SceneProperties> scenes = new HashMap<>();

    public static class SceneProperties {
        private String tableName;
        private Integer maxRetry;
        private Integer defaultDelay;
        private AsyncProperties async;
    }

    public static class AsyncProperties {
        private int corePoolSize = 5;
        private int maxPoolSize = 20;
        private int queueCapacity = 100;
    }
}
```

`mvn compile` → commit

---

### Task 7: JdbcTemplateInvocationRecordDaoImpl

Create: `dao/impl/JdbcTemplateInvocationRecordDaoImpl.java`

```java
package com.example.reliableinvoker.dao.impl;

import com.example.reliableinvoker.config.ReliableInvokerProperties;
import com.example.reliableinvoker.dao.IInvocationRecordDao;
import com.example.reliableinvoker.model.BackupQueryRequest;
import com.example.reliableinvoker.model.InvocationRecord;
import com.example.reliableinvoker.model.RetryQueryRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 基于 JdbcTemplate 的调用记录数据访问实现
 *
 * <p>表名通过 resolveTableName(scene) 动态解析，支持场景级独立表名
 */
public class JdbcTemplateInvocationRecordDaoImpl implements IInvocationRecordDao {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ReliableInvokerProperties properties;

    /** 行映射器 */
    private static final RowMapper<InvocationRecord> ROW_MAPPER = (rs, rowNum) -> {
        InvocationRecord r = new InvocationRecord();
        r.setId(rs.getLong("id"));
        r.setSerialNo(rs.getString("serial_no"));
        r.setScene(rs.getString("scene"));
        r.setBeanName(rs.getString("bean_name"));
        r.setMethodName(rs.getString("method_name"));
        r.setParams(rs.getString("params"));
        r.setStatus(rs.getInt("status"));
        r.setRetryCount(rs.getInt("retry_count"));
        r.setMaxRetryCount(rs.getInt("max_retry_count"));
        r.setRetryDelay(rs.getInt("retry_delay"));
        Timestamp et = rs.getTimestamp("execute_time");
        if (et != null) r.setExecuteTime(et.toLocalDateTime());
        r.setRemark(rs.getString("remark"));
        r.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
        r.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
        return r;
    };

    public JdbcTemplateInvocationRecordDaoImpl(JdbcTemplate jdbcTemplate,
                                                ReliableInvokerProperties properties) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.properties = properties;
    }

    /** 解析场景级表名：场景配置 > 全局默认 */
    private String resolveTableName(String scene) {
        ReliableInvokerProperties.SceneProperties sceneProps = this.properties.getSceneProperties(scene);
        if (sceneProps != null && sceneProps.getTableName() != null) {
            return sceneProps.getTableName();
        }
        return this.properties.getTableName();
    }

    @Override
    public InvocationRecord save(InvocationRecord record) {
        String table = this.resolveTableName(record.getScene());
        String sql = String.format(
            "INSERT INTO %s (serial_no, scene, bean_name, method_name, params, status, " +
            "retry_count, max_retry_count, retry_delay, execute_time, remark, create_time, update_time) " +
            "VALUES (:serialNo, :scene, :beanName, :methodName, :params, :status, " +
            ":retryCount, :maxRetryCount, :retryDelay, :executeTime, :remark, :createTime, :updateTime)",
            table);

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("serialNo", record.getSerialNo())
            .addValue("scene", record.getScene())
            .addValue("beanName", record.getBeanName())
            .addValue("methodName", record.getMethodName())
            .addValue("params", record.getParams())
            .addValue("status", record.getStatus())
            .addValue("retryCount", record.getRetryCount())
            .addValue("maxRetryCount", record.getMaxRetryCount())
            .addValue("retryDelay", record.getRetryDelay())
            .addValue("executeTime", record.getExecuteTime() != null
                ? Timestamp.valueOf(record.getExecuteTime()) : null)
            .addValue("remark", record.getRemark())
            .addValue("createTime", Timestamp.valueOf(record.getCreateTime()))
            .addValue("updateTime", Timestamp.valueOf(record.getUpdateTime()));

        this.namedJdbcTemplate.update(sql, params);

        InvocationRecord saved = this.findBySerialNo(record.getSerialNo(), record.getScene());
        if (saved != null) {
            record.setId(saved.getId());
        }
        return record;
    }

    @Override
    public void updateStatus(Long id, Integer status, String scene) {
        String table = this.resolveTableName(scene);
        String sql = String.format(
            "UPDATE %s SET status = :status, retry_count = retry_count + 1, " +
            "update_time = :updateTime WHERE id = :id", table);
        this.namedJdbcTemplate.update(sql,
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status)
                .addValue("updateTime", LocalDateTime.now()));
    }

    @Override
    public void deleteById(Long id, String scene) {
        String table = this.resolveTableName(scene);
        String sql = String.format("DELETE FROM %s WHERE id = :id", table);
        this.namedJdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    @Override
    public InvocationRecord findBySerialNo(String serialNo, String scene) {
        String table = this.resolveTableName(scene);
        String sql = String.format("SELECT * FROM %s WHERE serial_no = :serialNo", table);
        List<InvocationRecord> list = this.namedJdbcTemplate.query(sql,
            new MapSqlParameterSource("serialNo", serialNo), ROW_MAPPER);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<InvocationRecord> findForRetry(RetryQueryRequest request) {
        String table = this.resolveTableName(request.getScene());
        String sql = String.format(
            "SELECT * FROM %s WHERE scene = :scene AND status IN (:statusList) " +
            "AND MOD(id, :shardTotal) = :shardIndex ORDER BY id LIMIT :limit", table);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("scene", request.getScene())
            .addValue("statusList", request.getStatusList())
            .addValue("shardTotal", request.getShardTotal())
            .addValue("shardIndex", request.getShardIndex())
            .addValue("limit", request.getLimit());
        return this.namedJdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    @Override
    public List<InvocationRecord> findForBackup(BackupQueryRequest request) {
        String table = this.resolveTableName(request.getScene());
        String sql = String.format(
            "SELECT * FROM %s WHERE scene = :scene AND status IN (:statusList) " +
            "AND create_time < :before AND MOD(id, :shardTotal) = :shardIndex " +
            "ORDER BY id LIMIT :limit", table);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("scene", request.getScene())
            .addValue("statusList", request.getStatusList())
            .addValue("before", LocalDateTime.now().minusDays(request.getDays()))
            .addValue("shardTotal", request.getShardTotal())
            .addValue("shardIndex", request.getShardIndex())
            .addValue("limit", request.getLimit());
        return this.namedJdbcTemplate.query(sql, params, ROW_MAPPER);
    }
}
```

测试：`dao/impl/JdbcTemplateInvocationRecordDaoImplTest.java` — 6 PASS → commit

---

### Task 8: AsyncExecutorImpl

Create: `service/impl/AsyncExecutorImpl.java`

```java
public class AsyncExecutorImpl implements IAsyncExecutor {
    private final TaskExecutor taskExecutor;
    // 构造器注入
    @Override public void execute(Runnable task) { taskExecutor.execute(task); }
}
```

测试：`service/impl/AsyncExecutorImplTest.java` — 1 PASS → commit

---

### Task 9: RetryServiceImpl

Create: `service/impl/RetryServiceImpl.java`

```java
public class RetryServiceImpl implements IRetryService {
    private final ApplicationContext applicationContext;
    private final IInvocationRecordDao recordDao;

    @Override
    public void retry(InvocationRecord record) {
        // 重试延迟
        if (record.getRetryDelay() > 0 && record.getRetryCount() > 0) {
            Thread.sleep(record.getRetryDelay());
        }
        try {
            Object bean = applicationContext.getBean(record.getBeanName());
            Method method = bean.getClass().getMethod(record.getMethodName(), String.class);
            method.invoke(bean, record.getParams());
            recordDao.updateStatus(record.getId(), 2, record.getScene()); // SUCCESS
        } catch (Exception e) {
            int newCount = record.getRetryCount() + 1;
            recordDao.updateStatus(record.getId(),
                newCount >= record.getMaxRetryCount() ? 3 : 0, record.getScene());
            throw new ExecutionException("重试失败: " + e.getMessage(), e);
        }
    }
}
```

`mvn compile` → commit

---

### Task 10: BackupServiceImpl

Create: `service/impl/BackupServiceImpl.java`

```java
public class BackupServiceImpl implements IBackupService {
    private final IInvocationRecordDao recordDao;
    @Override public void backup(List<InvocationRecord> records) {
        records.forEach(r -> recordDao.deleteById(r.getId(), r.getScene()));
    }
}
```

`mvn compile` → commit

---

### Task 11: ReliableInvokerImpl

Create: `api/impl/ReliableInvokerImpl.java`

```java
public class ReliableInvokerImpl implements IReliableInvoker {
    private final IInvocationRecordDao recordDao;
    private final IRetryService retryService;
    private final IAsyncExecutor asyncExecutor;
    private final ReliableInvokerProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public InvocationRecord execute(InvocationRequest<?> request) {
        String sceneName = request.getScene().name();
        String paramsJson = serializeParams(request.getParams());
        // 配置优先级：调用级 > 场景级 > 全局默认
        int maxRetry = resolveMaxRetry(...);
        int retryDelay = resolveRetryDelay(...);
        // 构造记录
        InvocationRecord record = buildRecord(...);
        // saveRecord=false 跳过持久化
        if (!request.isSaveRecord()) { executeTarget(request, record); return record; }
        recordDao.save(record);
        // 事务感知
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 注册 afterCommit → invokeTarget
        } else {
            invokeTarget(request, record);
        }
        return record;
    }

    private void invokeTarget(..., InvocationRecord record) {
        if (request.isAsync()) asyncExecutor.execute(() -> retryService.retry(record));
        else retryService.retry(record);
    }
    // serializeParams, resolveMaxRetry, resolveRetryDelay
}
```

测试：`api/impl/ReliableInvokerImplTest.java` — 3 PASS → commit

---

### Task 12: ReliableInvokerTaskImpl

Create: `api/impl/ReliableInvokerTaskImpl.java`

```java
public class ReliableInvokerTaskImpl implements IReliableInvokerTask {
    private final IInvocationRecordDao recordDao;
    private final IRetryService retryService;
    private final IBackupService backupService;

    @Override
    public int retry(RetryRequest<?> request) {
        String scene = request.getScene().name();
        List<Integer> statusList = request.getStatusList() != null
            ? request.getStatusList() : List.of(0);
        // 构建查询对象
        RetryQueryRequest query = new RetryQueryRequest();
        query.setScene(scene);
        query.setStatusList(statusList);
        query.setShardTotal(request.getShardTotal());
        query.setShardIndex(request.getShardIndex());
        query.setLimit(request.getLimit());
        List<InvocationRecord> records = recordDao.findForRetry(query);
        int count = 0;
        for (InvocationRecord record : records) {
            if (record.getRetryCount() >= record.getMaxRetryCount()) {
                recordDao.updateStatus(record.getId(), 3, record.getScene());
                continue;
            }
            if (retryService != null) retryService.retry(record);
            count++;
        }
        return count;
    }

    @Override
    public int backup(BackupRequest<?> request) {
        String scene = request.getScene().name();
        List<Integer> statusList = request.getStatusList() != null
            ? request.getStatusList() : List.of(2);
        BackupQueryRequest query = new BackupQueryRequest();
        query.setScene(scene);
        query.setStatusList(statusList);
        query.setDays(request.getDays());
        query.setShardTotal(request.getShardTotal());
        query.setShardIndex(request.getShardIndex());
        query.setLimit(request.getLimit());
        List<InvocationRecord> records = recordDao.findForBackup(query);
        if (!records.isEmpty()) backupService.backup(records);
        return records.size();
    }
}
```

测试：`api/impl/ReliableInvokerTaskImplTest.java` — 2 PASS → commit

---

### Task 13: 自动配置

Create:
- `config/ReliableInvokerAutoConfiguration.java` — `@Configuration` + `@EnableConfigurationProperties` + `@ConditionalOnProperty`
- `src/main/resources/META-INF/spring.factories`

自动配置类注册 6 个 Bean（均 `@ConditionalOnMissingBean`）：
1. `IReliableInvoker` → `ReliableInvokerImpl`
2. `IReliableInvokerTask` → `ReliableInvokerTaskImpl`
3. `IInvocationRecordDao` → `JdbcTemplateInvocationRecordDaoImpl`
4. `IRetryService` → `RetryServiceImpl`
5. `IBackupService` → `BackupServiceImpl`
6. `IAsyncExecutor` → `AsyncExecutorImpl` + 线程池 `reliableInvokerTaskExecutor`

spring.factories:
```
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.example.reliableinvoker.config.ReliableInvokerAutoConfiguration
```

测试：`config/ReliableInvokerAutoConfigurationTest.java` — 2 PASS → commit

---

### Task 14: 最终验证

`mvn test` → ALL PASS → `mvn package` → commit
