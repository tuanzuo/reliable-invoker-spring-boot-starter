# 可靠调用 Spring Boot Starter - 设计文档

**日期:** 2026-06-07
**状态:** 已批准
**作者:** AI 助手

## 1. 概述

一个 Spring Boot Starter 组件，提供高可靠性的方法调用能力，包含持久化、重试和备份功能。该组件确保方法调用在执行前被记录到数据库表中，支持同步和异步执行，并提供可配置的重试机制。

**注意：** 本组件只提供调用接口和默认实现，不提供定时调度能力。重试和备份由使用方通过接口手动触发。

### 1.1 Spring Boot Starter 约定

本项目严格遵循 Spring Boot Starter 规范：

- **Maven 坐标**: `groupId.artifactId` 符合 `xxx-spring-boot-starter` 命名规范
- **自动配置**: 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 声明自动配置类
- **条件装配**: 使用 `@ConditionalOnMissingBean` 允许使用方覆盖所有默认实现
- **配置绑定**: 使用 `@ConfigurationProperties(prefix = "reliable.invoker")` 实现类型安全配置
- **开箱即用**: 引入依赖即自动装配，无需额外配置即可使用（需配置数据源）
- **可覆盖**: 所有核心接口均可通过自定义 Bean 替换默认实现

### 1.2 命名规范

- **接口**: 以 `I` 开头（如 `IReliableInvoker`）
- **接口实现**: 以 `Impl` 结尾（如 `ReliableInvokerImpl`）

## 2. 目标

- 提供编程式 API 用于可靠的方法调用
- 通过 JdbcTemplate 将调用记录持久化到 MySQL
- 支持事务集成（记录参与业务事务）
- 支持可配置的同步/异步执行
- 支持可配置的重试，包括固定间隔和指数退避策略
- 支持场景级配置（表名、重试策略）
- 提供可扩展的接口及默认实现
- 提供查询待重试数据和成功数据的接口
- 支持成功记录的备份/清理

## 3. 架构

### 3.1 核心组件

```
┌──────────────────────────────────────────────────────────────┐
│                    IReliableInvoker (执行API)                  │
│                    IReliableInvokerTask (重试/备份API)          │
└──────────────────────┬───────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
┌───────▼──────┐ ┌────▼─────┐ ┌──────▼───────┐
│    IInvocation│ │ IRetry  │ │  IBackup     │
│ RecordService│ │ Service │ │  Service     │
└───────┬──────┘ └────┬─────┘ └──────┬───────┘
        │             │              │
┌───────▼─────────────▼──────────────▼───────┐
│         JdbcTemplate (默认实现)              │
└─────────────────────────────────────────────┘
```

### 3.2 组件详情

- **IReliableInvoker**: 执行入口接口，提供 `execute()` 方法
- **IReliableInvokerTask**: 任务管理接口，提供 `retry()` 和 `backup()` 方法
- **IInvocationRecordDao**: 调用记录的 CRUD 操作接口（默认：JdbcTemplate）
- **IRetryService**: 单条记录的重试执行逻辑（默认实现）
- **IBackupService**: 备份/清理接口（默认：删除旧记录）
- **IAsyncExecutor**: 异步执行包装器（默认：TaskExecutor）

### 3.3 交互流程

```
用户代码        IReliableInvoker      IReliableInvokerTask    底层Service
  │                   │                      │                   │
  ├─ execute(req) ───►│                      │                   │
  │                   ├─ save() ─────────────────────────────────►│
  │                   ├─ [sync/async]         │                   │
  │◄── result ────────┤                      │                   │
  │                   │                      │                   │
  ├─ retry(req) ────────────────────────────►│                   │
  │                   │                      ├─ findByScene()───►│
  │                   │                      ├─ for each:        │
  │                   │                      │    retry() ──────►│
  │                   │                      │    updateStatus()►│
  │◄── count ───────────────────────────────┤                   │
  │                   │                      │                   │
  ├─ backup(req) ───────────────────────────►│                   │
  │                   │                      ├─ findSuccess()───►│
  │                   │                      ├─ backupService───►│
  │◄── count ───────────────────────────────┤                   │
```

## 4. 数据模型

### 4.1 表结构

```sql
CREATE TABLE reliable_invocation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    serial_no VARCHAR(64) NOT NULL UNIQUE COMMENT '流水号',
    scene VARCHAR(64) NOT NULL COMMENT '业务场景',
    bean_name VARCHAR(128) NOT NULL COMMENT 'Bean名称',
    method_name VARCHAR(128) NOT NULL COMMENT '方法名',
    params TEXT COMMENT '参数JSON (最大64KB字节)',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待执行(PENDING) 1-执行中(EXECUTING) 2-成功(SUCCESS) 3-失败(FAILED)',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    retry_delay INT NOT NULL DEFAULT 5000 COMMENT '重试延迟(ms)',
    execute_time DATETIME COMMENT '计划执行时间',
    remark VARCHAR(512) COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scene_status (scene, status),
    INDEX idx_execute_time (execute_time),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 4.2 实体类

```java
public class InvocationRecord {
    private Long id;
    private String serialNo;
    private String scene;
    private String beanName;
    private String methodName;
    private String params;
    private Integer status; // 0-待执行(PENDING), 1-执行中(EXECUTING), 2-成功(SUCCESS), 3-失败(FAILED)
    private Integer retryCount;
    private Integer maxRetryCount;
    private Integer retryDelay;
    private LocalDateTime executeTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

## 5. API 设计

### 5.1 主 API 接口

```java
@Data
@Builder
public class InvocationRequest<S extends Enum<S>> {
    private S scene;                // 业务场景（由使用方定义的枚举）
    private String beanName;        // 目标 Bean 名称
    private String methodName;      // 要调用的方法名
    private Object params;          // 方法参数（内部使用Jackson序列化为JSON）
    private boolean async;          // 是否异步执行
    private boolean saveRecord = true; // 是否保存调用记录（默认 true）
    private int maxRetry;           // 最大重试次数（">0 覆盖场景配置，0 使用场景默认值）
    private int retryDelay;         // 重试间隔，单位毫秒（">0 覆盖场景配置，0 使用场景默认值）
    private String remark;          // 附加备注
}

public interface IReliableInvoker {
    InvocationRecord execute(InvocationRequest<?> request);
}

public interface IReliableInvoker {
    /**
     * 执行带有可靠性保证的方法
     */
    InvocationRecord execute(InvocationRequest request);
}
```

### 5.2 任务管理接口

```java
@Data
@Builder
public class RetryRequest<S extends Enum<S>> {
    private S scene;                // 业务场景（由使用方定义的枚举）
    private List<Integer> statusList; // 要重试的记录状态列表（默认为 [0]）
    private int maxRetry;           // 本次重试的最大次数（">0 覆盖，0 使用记录原值）
    private int retryDelay;         // 重试间隔，单位毫秒（">0 覆盖，0 使用记录原值）
    private int shardTotal = 1;     // 分片总数
    private int shardIndex;         // 当前分片（0 ~ shardTotal-1，mod(id, shardTotal) = shardIndex）
    private int limit = 100;        // 查询条数
}

@Data
@Builder
public class BackupRequest<S extends Enum<S>> {
    private S scene;                // 业务场景（由使用方定义的枚举）
    private List<Integer> statusList; // 要备份的记录状态列表（默认为 [2]）
    private int days;               // 备份多少天前的数据（必须 >0）
    private int shardTotal = 1;     // 分片总数
    private int shardIndex;         // 当前分片（0 ~ shardTotal-1，mod(id, shardTotal) = shardIndex）
    private int limit = 100;        // 查询条数
}

public interface IReliableInvokerTask {
    int retry(RetryRequest<?> request);
    int backup(BackupRequest<?> request);
}

@Data
@Builder
public class BackupRequest {
    private String scene;           // 业务场景标识符
    private List<Integer> statusList; // 要备份的记录状态列表（默认为 [2]，可传入 [2, 3] 同时备份成功和失败记录）
    private int days;               // 备份多少天前的数据（必须 >0）
}

public interface IReliableInvokerTask {
    /**
     * 重试失败任务
     * 默认实现：查询 status=0 的记录，循环执行重试
     * 使用方可覆盖此接口实现自定义重试逻辑
     * 
     * @param request 重试请求
     * @return 重试了多少条记录
     */
    int retry(RetryRequest request);

    /**
     * 备份/清理成功任务
     * 默认实现：查询 N 天前 status=2 的记录，调用 BackupService 处理
     * 使用方可覆盖此接口实现自定义备份逻辑
     * 
     * @param request 备份请求
     * @return 处理了多少条记录
     */
    int backup(BackupRequest request);
}

/**
 * 默认实现
 */
public class ReliableInvokerTaskImpl implements IReliableInvokerTask {

    private final IInvocationRecordDao recordService;
    private final IBackupService backupService;
    private final IRetryService retryService;

    @Override
    public int retry(RetryRequest<?> request) {
        String scene = request.getScene().name();
        List<Integer> statusList = request.getStatusList() != null
            ? request.getStatusList() : List.of(0);
        List<InvocationRecord> records = recordService.findForRetry(
            scene, statusList, request.getShardTotal(), request.getShardIndex(), request.getLimit());

        // 2. 循环重试
        int retryCount = 0;
        for (InvocationRecord record : records) {
            if (record.getRetryCount() >= record.getMaxRetryCount()) {
                recordService.updateStatus(record.getId(), 3); // FAILED
                continue;
            }
            retryService.retry(record);
            retryCount++;
        }
        return retryCount;
    }

    @Override
    public int backup(BackupRequest<?> request) {
        String scene = request.getScene().name();
        List<Integer> statusList = request.getStatusList() != null
            ? request.getStatusList() : List.of(2);
        List<InvocationRecord> records = recordService.findForBackup(
            scene, statusList, request.getDays(), request.getShardTotal(), request.getShardIndex(), request.getLimit());

        if (!records.isEmpty()) {
            backupService.backup(records);
        }
        return records.size();
    }
}
```

### 5.3 服务接口

```java
public interface IInvocationRecordDao {
    InvocationRecord save(InvocationRecord record);
    void updateStatus(Long id, Integer status);
    void deleteById(Long id);
    InvocationRecord findBySerialNo(String serialNo);
    List<InvocationRecord> findForRetry(String scene, List<Integer> statusList,
                                         int shardTotal, int shardIndex, int limit);
    List<InvocationRecord> findForBackup(String scene, List<Integer> statusList,
                                          int days, int shardTotal, int shardIndex, int limit);
}

public interface IRetryService {
    void retry(InvocationRecord record);
}

public interface IBackupService {
    void backup(List<InvocationRecord> records);
}

public interface IAsyncExecutor {
    void execute(Runnable task);
}
```

## 6. 配置

### 6.1 配置属性

```yaml
reliable:
  invoker:
    enabled: true                    # 启用/禁用组件
    table-name: reliable_invocation_record  # 默认表名
    max-retry: 3                     # 全局默认最大重试次数
    default-delay: 5000              # 全局默认重试间隔（毫秒）
    async:                           # 全局异步执行器配置
      core-pool-size: 5
      max-pool-size: 20
      queue-capacity: 100
    scenes:                          # 场景特定配置（可选），key 对应枚举的 name()
      ORDER_SCENE:                   # 对应 BusinessSceneEnum.ORDER_SCENE
        table-name: order_invocation  # 覆盖全局 table-name
        max-retry: 5                 # 覆盖全局 max-retry
        default-delay: 3000          # 覆盖全局 default-delay
        async:                        # 覆盖全局异步配置
          core-pool-size: 10
          max-pool-size: 50
          queue-capacity: 200
      PAYMENT_SCENE:
        table-name: payment_invocation
        max-retry: 3
        default-delay: 5000
```

**配置优先级:**
- 场景级配置 > 全局配置
- `max-retry` / `default-delay`: 场景配置了则覆盖全局，未配置则使用全局默认值
- `table-name`: 场景配置了则使用场景表名，未配置则使用全局 table-name
- `async`: 场景配置了则使用场景线程池，未配置则使用全局 async 配置

### 6.2 配置类

```java
@ConfigurationProperties(prefix = "reliable.invoker")
public class ReliableInvokerProperties {
    private boolean enabled = true;
    private String tableName = "reliable_invocation_record";
    private int maxRetry = 3;
    private int defaultDelay = 5000;
    private AsyncProperties async = new AsyncProperties();
    private Map<String, SceneProperties> scenes = new HashMap<>();

    @Data
    public static class SceneProperties {
        private String tableName;
        private Integer maxRetry;
        private Integer defaultDelay;
        private AsyncProperties async;
    }

    @Data
    public static class AsyncProperties {
        private int corePoolSize = 5;
        private int maxPoolSize = 20;
        private int queueCapacity = 100;
    }
}
```

## 7. 事务集成

### 7.1 事务感知执行流程

1. **检测事务**: 使用 `TransactionSynchronizationManager.isActualTransactionActive()`
2. **注册同步器**: 如果在事务中，注册 `TransactionSynchronization`
3. **提交后**: 事务提交后执行方法
4. **回滚后**: 记录随业务数据一起回滚（记录不会持久化）

### 7.2 事务参与策略

`IInvocationRecordDao.save()` 方法使用当前的 Spring 事务上下文。在 `@Transactional` 方法中调用时，记录插入操作参与现有事务。

**重要**: 当业务事务回滚时，调用记录也会被回滚且不会持久化。

### 7.3 执行流程

**同步执行 (有事务):**
```
用户代码 (@Transactional)
    │
    ▼
IReliableInvoker.execute()
    │
    ├──► 插入记录 (参与业务事务), status=0 (PENDING)
    │
    ├──► 注册 TransactionSynchronization
    │       ├── afterCommit() ──► 业务事务已提交, 记录已持久化
    │       │       ├── 执行目标方法
    │       │       │       ├── 成功 ──► 更新记录 status=2 (SUCCESS)
    │       │       │       └── 失败 ──► 更新记录 status=3 (FAILED), 抛出异常
    │       └── afterRollback() ──► 业务事务回滚, 记录不持久化, 抛出异常
    │
    └──► 返回 InvocationRecord (成功时) / 抛出异常 (失败时)
```

**同步执行 (无事务):**
```
用户代码 (无@Transactional)
    │
    ▼
IReliableInvoker.execute()
    │
    ├──► 插入记录, status=0 (PENDING)
    │
    ├──► 立即执行目标方法
    │       ├── 成功 ──► 更新 status=2 (SUCCESS)
    │       └── 失败 ──► 更新 status=3 (FAILED), 抛出异常
    │
    └──► 返回 InvocationRecord (成功时) / 抛出异常 (失败时)
```

**异步执行:**
```
用户代码 (@Transactional)
    │
    ▼
IReliableInvoker.execute()
    │
    ├──► 插入记录 (参与业务事务), status=0 (PENDING)
    │
    ├──► 注册 TransactionSynchronization
    │       ├── afterCommit() ──► 提交异步任务到线程池
    │       │       └── 线程池稍后执行目标方法
    │       │               ├── 成功 ──► 更新 status=2 (SUCCESS)
    │       │               └── 失败 ──► 更新 status=3 (FAILED)
    │       └── afterRollback() ──► 记录回滚, 任务不会提交
    │
    └──► 返回 InvocationRecord (status=PENDING)
```

### 7.4 状态转换总结

| 场景 | 事务状态 | 记录持久化 | 记录最终状态 | 后续操作 |
|------|----------|-----------|-------------|---------|
| 成功（有事务） | 提交 | 是 | SUCCESS (2) | 无需操作 |
| 失败（有事务） | 已提交后失败 | 是 | FAILED (3) | 调用 IReliableInvokerTask.retry() |
| 成功（无事务） | 无 | 是 | SUCCESS (2) | 无需操作 |
| 失败（无事务） | 无 | 是 | FAILED (3) | 调用 IReliableInvokerTask.retry() |
| 异步成功 | - | 是 | SUCCESS (2) | 无需操作 |
| 异步失败 | - | 是 | FAILED (3) | 调用 IReliableInvokerTask.retry() |

## 8. 重试与备份

重试和备份功能由 `IReliableInvokerTask` 接口统一管理，默认实现中封装了查询数据和循环处理的完整逻辑。

### 8.1 重试逻辑（ReliableInvokerTaskImpl 默认实现）

```java
public int retry(RetryRequest<?> request) {
    String scene = request.getScene().name();
    List<Integer> statusList = request.getStatusList() != null
        ? request.getStatusList() : List.of(0);
    List<InvocationRecord> records = recordService.findForRetry(
        scene, statusList,
        request.getShardTotal(), request.getShardIndex(), request.getLimit());

    int count = 0;
    for (InvocationRecord record : records) {
        if (record.getRetryCount() >= record.getMaxRetryCount()) {
            recordService.updateStatus(record.getId(), 3);
            continue;
        }
        retryService.retry(record);
        count++;
    }
    return count;
}
```

### 8.2 备份逻辑（ReliableInvokerTaskImpl 默认实现）

```java
public int backup(BackupRequest<?> request) {
    String scene = request.getScene().name();
    List<Integer> statusList = request.getStatusList() != null
        ? request.getStatusList() : List.of(2);
    List<InvocationRecord> records = recordService
        .findForBackup(scene, statusList, request.getDays(),
            request.getShardTotal(), request.getShardIndex(), request.getLimit());

    if (!records.isEmpty()) {
        backupService.backup(records);
    }
    return records.size();
}
```

### 8.3 使用方可覆盖

使用方可以完全替换 `IReliableInvokerTask` 的默认实现，自定义重试和备份逻辑。

## 9. 备份/清理

### 9.1 默认实现

```java
@Service
@ConditionalOnMissingBean(IBackupService.class)
public class BackupServiceImpl implements IBackupService {
    private final IInvocationRecordDao recordService;

    @Override
    public void backup(List<InvocationRecord> records) {
        // 默认实现：删除记录
        // 使用方可覆盖此接口实现真正的备份逻辑
        for (InvocationRecord record : records) {
            recordService.deleteById(record.getId());
        }
    }
}
```

### 9.2 使用方式

```java
IReliableInvokerTask task = ...;
BackupRequest<BusinessSceneEnum> request = BackupRequest.<BusinessSceneEnum>builder()
    .scene(BusinessSceneEnum.ORDER_SCENE)
    .days(7)
    .build();
int count = task.backup(request);
```

## 10. 异步执行

### 10.1 实现

```java
@Service
@ConditionalOnMissingBean(IAsyncExecutor.class)
public class AsyncExecutorImpl implements IAsyncExecutor {
    private final TaskExecutor taskExecutor;

    @Override
    public void execute(Runnable task) {
        taskExecutor.execute(task);
    }
}
```

### 10.2 任务执行器配置

```java
@Configuration
public class AsyncConfig {
    @Bean("reliableInvokerTaskExecutor")
    public TaskExecutor taskExecutor(ReliableInvokerProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAsync().getQueueCapacity());
        executor.setThreadNamePrefix("reliable-invoker-");
        executor.initialize();
        return executor;
    }
}
```

## 11. 自动配置

### 11.1 自动配置类

```java
@AutoConfiguration
@EnableConfigurationProperties(ReliableInvokerProperties.class)
@ConditionalOnProperty(prefix = "reliable.invoker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReliableInvokerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IReliableInvoker reliableInvoker(IInvocationRecordDao recordService,
                                             IRetryService retryService,
                                             IAsyncExecutor asyncExecutor) {
        return new ReliableInvokerImpl(recordService, retryService, asyncExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public IReliableInvokerTask reliableInvokerTask(IInvocationRecordDao recordService,
                                                      IRetryService retryService,
                                                      IBackupService backupService) {
        return new ReliableInvokerTaskImpl(recordService, retryService, backupService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public IInvocationRecordDao invocationRecordService(JdbcTemplate jdbcTemplate) {
        return new JdbcTemplateInvocationRecordDaoImpl(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public IRetryService retryService() {
        return new RetryServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public IBackupService backupService(IInvocationRecordDao recordService) {
        return new BackupServiceImpl(recordService);
    }

    @Bean
    @ConditionalOnMissingBean
    public IAsyncExecutor asyncExecutor(
            @Qualifier("reliableInvokerTaskExecutor") TaskExecutor taskExecutor) {
        return new AsyncExecutorImpl(taskExecutor);
    }
}
```

### 11.2 自动配置注册

文件 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.example.reliableinvoker.autoconfigure.ReliableInvokerAutoConfiguration
```

### 11.3 默认实现清单

| 接口 | 默认实现 | 技术 |
|-----------|----------------------|------------|
| IReliableInvoker | ReliableInvokerImpl | Java |
| IReliableInvokerTask | ReliableInvokerTaskImpl | Java |
| IInvocationRecordDao | JdbcTemplateInvocationRecordDaoImpl | JdbcTemplate |
| IRetryService | RetryServiceImpl | Java |
| IBackupService | BackupServiceImpl | Java (删除) |
| IAsyncExecutor | AsyncExecutorImpl | TaskExecutor |

## 12. 使用示例

### 12.1 定义场景枚举

```java
// 使用方自定义
public enum BusinessSceneEnumEnum {
    ORDER_SCENE("订单场景"),
    PAYMENT_SCENE("支付场景"),
    NOTIFICATION_SCENE("通知场景");

    private final String description;

    BusinessSceneEnumEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
```

### 12.2 基本调用

```java
@Service
public class OrderService {
    @Autowired
    private IReliableInvoker reliableInvoker;

    public void createOrder(Order order) {
        InvocationRequest<BusinessSceneEnum> request = InvocationRequest.<BusinessSceneEnum>builder()
            .scene(BusinessSceneEnum.ORDER_SCENE)
            .beanName("paymentService")
            .methodName("processPayment")
            .params(order)
            .async(true)
            .maxRetry(3)
            .retryDelay(5000)
            .remark("订单支付")
            .build();

        reliableInvoker.execute(request);
    }
}
```

### 12.3 重试失败记录

```java
@Service
public class RetryScheduler {
    @Autowired
    private IReliableInvokerTask invokerTask;

    @Scheduled(cron = "0 */5 * * * *")
    public void retryFailedTasks() {
        RetryRequest<BusinessSceneEnum> request = RetryRequest.<BusinessSceneEnum>builder()
            .scene(BusinessSceneEnum.ORDER_SCENE)
            .build();
        int count = invokerTask.retry(request);
        log.info("本次重试完成，共处理 {} 条记录", count);
    }
}
```

### 12.4 备份/清理旧数据

```java
@Service
public class BackupScheduler {
    @Autowired
    private IReliableInvokerTask invokerTask;

    @Scheduled(cron = "0 0 2 * * *")
    public void backupOldRecords() {
        BackupRequest<BusinessSceneEnum> request = BackupRequest.<BusinessSceneEnum>builder()
            .scene(BusinessSceneEnum.ORDER_SCENE)
            .days(7)
            .build();
        int count = invokerTask.backup(request);
        log.info("备份/清理完成，共处理 {} 条记录", count);
    }
}
```

## 13. 错误处理

### 13.1 异常类型

- `ReliableInvokerException`: 基础异常
- `RecordNotFoundException`: 记录未找到
- `ExecutionException`: 方法执行失败
- `ParamsTooLargeException`: 参数超过64KB字节

### 13.2 错误处理策略

1. **持久化错误**: 回滚事务，抛出异常
2. **执行错误**: 更新记录状态，由调用方决定是否重试
3. **最大重试**: 更新状态为失败

## 14. 测试策略

### 14.1 单元测试

- 使用 Mock 测试每个服务接口
- 测试不同策略的重试逻辑

### 14.2 集成测试

- 使用嵌入式数据库 (H2) 测试
- 测试异步执行
- 测试重试机制
- 测试备份/清理

## 15. 配置

```yaml
reliable:
  invoker:
    enabled: true
    table-name: reliable_invocation_record
    max-retry: 3
    default-delay: 5000
    scenes:
      ORDER_SCENE:
        table-name: order_invocation
        max-retry: 5
        default-delay: 3000
        async:
          core-pool-size: 10
          max-pool-size: 50
          queue-capacity: 200
```

## 16. Maven 依赖

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>reliable-invoker-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 17. 参考

- Spring Boot 文档
- Spring Data JdbcTemplate 文档
- MySQL 文档

---

**批准状态:** 用户于 2025-01-09 批准
**下一步:** 实施规划
