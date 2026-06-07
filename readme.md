# reliable-invoker-spring-boot-starter

Spring Boot Starter 组件，提供带持久化保证的方法调用能力，支持同步/异步执行、重试和备份/清理。

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.tz</groupId>
    <artifactId>reliable-invoker-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 创建调用记录表

```sql
CREATE TABLE IF NOT EXISTS reliable_invocation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    serial_no VARCHAR(64) NOT NULL UNIQUE COMMENT '流水号',
    scene VARCHAR(64) NOT NULL COMMENT '业务场景',
    bean_name VARCHAR(128) NOT NULL COMMENT 'Bean名称',
    method_name VARCHAR(128) NOT NULL COMMENT '方法名',
    params TEXT COMMENT '参数JSON',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待执行 1-执行中 2-成功 3-失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    retry_delay INT NOT NULL DEFAULT 5000 COMMENT '重试延迟(ms)',
    execute_time DATETIME COMMENT '计划执行时间',
    remark VARCHAR(512) COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='可靠调用记录表';
```

### 3. 定义场景枚举

```java
public enum BusinessSceneEnum {
    ORDER_SCENE("订单场景"),
    PAYMENT_SCENE("支付场景");

    private final String description;
    BusinessSceneEnum(String description) { this.description = description; }
    public String getDescription() { return description; }
}
```

### 4. 配置

```yaml
reliable:
  invoker:
    # ===== 全局配置 =====
    enabled: true                           # 是否启用组件（默认 true）
    table-name: reliable_invocation_record  # 全局默认表名
    max-retry: 3                            # 全局默认最大重试次数
    default-delay: 5000                     # 全局默认重试间隔，单位毫秒

    # 全局异步线程池配置
    async:
      core-pool-size: 5                     # 核心线程数
      max-pool-size: 20                     # 最大线程数
      queue-capacity: 100                   # 阻塞队列容量

    # ===== 场景级配置（可选，key 对应枚举 name()） =====
    # 未配置的项自动使用全局默认值
    scenes:
      ORDER_SCENE:                          # 场景标识
        table-name: order_invocation        # 场景独立表名（不配则用全局）
        max-retry: 5                        # 场景级最大重试次数（不配则用全局）
        default-delay: 3000                 # 场景级重试间隔（不配则用全局）
        # 场景独立线程池（可选，不配则复用全局 async 配置）
        async:
          core-pool-size: 10
          max-pool-size: 50
          queue-capacity: 200
      PAYMENT_SCENE:                        # 另一个场景示例
        table-name: payment_invocation
        max-retry: 3
        default-delay: 5000
```

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | Boolean | `true` | 是否启用组件 |
| `table-name` | String | `reliable_invocation_record` | 默认表名，场景可覆盖 |
| `max-retry` | Integer | `3` | 全局默认最大重试次数，场景可覆盖，调用级 `maxRetry>0` 可再次覆盖 |
| `default-delay` | Integer | `5000` | 全局默认重试间隔（毫秒），场景可覆盖，调用级 `retryDelay>0` 可再次覆盖 |
| `async.core-pool-size` | Integer | `5` | 线程池核心线程数 |
| `async.max-pool-size` | Integer | `20` | 线程池最大线程数 |
| `async.queue-capacity` | Integer | `100` | 线程池阻塞队列容量 |
| `scenes.{name}.table-name` | String | `null` | 场景独立表名，不配则使用全局 `table-name` |
| `scenes.{name}.max-retry` | Integer | `null` | 场景级最大重试次数，不配则使用全局 `max-retry` |
| `scenes.{name}.default-delay` | Integer | `null` | 场景级重试间隔，不配则使用全局 `default-delay` |
| `scenes.{name}.async` | AsyncProperties | `null` | 场景独立线程池，不配则复用全局 `async` |

### 5. 使用

```java
@Service
public class OrderService {

    @Autowired
    private IReliableInvoker reliableInvoker;
    @Autowired
    private IReliableInvokerTask invokerTask;

    public void createOrder(Order order) {
        // 可靠调用
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

    // 手动重试失败任务（也可通过 @Scheduled 定时触发）
    @Scheduled(cron = "0 */5 * * * *")
    public void retryFailedTasks() {
        RetryRequest<BusinessSceneEnum> request = RetryRequest.<BusinessSceneEnum>builder()
            .scene(BusinessSceneEnum.ORDER_SCENE)
            .statusList(Arrays.asList(0))  // PENDING
            .shardTotal(1)
            .shardIndex(0)
            .limit(100)
            .build();
        int count = invokerTask.retry(request);
    }

    // 备份/清理旧数据
    @Scheduled(cron = "0 0 2 * * *")
    public void backupOldRecords() {
        BackupRequest<BusinessSceneEnum> request = BackupRequest.<BusinessSceneEnum>builder()
            .scene(BusinessSceneEnum.ORDER_SCENE)
            .days(7)
            .build();
        int count = invokerTask.backup(request);
    }
}
```

## 核心 API

### IReliableInvoker — 执行接口

| 方法 | 说明 |
|------|------|
| `execute(InvocationRequest<?>)` | 执行带可靠性保证的方法调用 |

### IReliableInvokerTask — 任务管理接口

| 方法 | 说明 |
|------|------|
| `retry(RetryRequest<?>)` | 查询待重试记录并循环重试，返回处理条数 |
| `backup(BackupRequest<?>)` | 查询旧记录并执行备份/清理，返回处理条数 |

### 请求对象

| 类 | 说明 |
|------|------|
| `InvocationRequest<S>` | 执行请求，`S` 为使用方枚举。支持 `saveRecord` 控制是否持久化 |
| `RetryRequest<S>` | 重试请求，支持 `statusList`、分片参数 |
| `BackupRequest<S>` | 备份请求，支持 `statusList`、天数、分片参数 |

### 事件状态

| 状态值 | 含义 |
|--------|------|
| 0 | PENDING（待执行） |
| 1 | EXECUTING（执行中） |
| 2 | SUCCESS（成功） |
| 3 | FAILED（失败） |

## 扩展点

所有核心接口均提供默认实现，使用方可通过 `@ConditionalOnMissingBean` 覆盖：

| 接口 | 默认实现 | 说明 |
|------|---------|------|
| `IReliableInvoker` | `ReliableInvokerImpl` | 执行入口 |
| `IReliableInvokerTask` | `ReliableInvokerTaskImpl` | 重试/备份逻辑 |
| `IInvocationRecordDao` | `JdbcTemplateInvocationRecordDaoImpl` | 数据访问 |
| `IRetryService` | `RetryServiceImpl` | 单条重试 |
| `IBackupService` | `BackupServiceImpl` | 备份（默认删除） |
| `IAsyncExecutor` | `AsyncExecutorImpl` | 异步执行 |

## 配置优先级

```
InvocationRequest 参数 > 场景级配置 > 全局默认配置
```

## 分片支持

`findForRetry` 和 `findForBackup` 支持分片查询，SQL 条件为 `MOD(id, shardTotal) = shardIndex`，配合多实例部署实现分布式处理。

## 事务集成

- 调用记录插入参与当前 Spring 事务
- 事务提交后 (`afterCommit`) 执行目标方法
- 事务回滚时记录不持久化

## 技术栈

| 项目 | 版本 |
|------|------|
| Java | 1.8 |
| Spring Boot | 2.3.12.RELEASE |
| 持久层 | JdbcTemplate |
| 序列化 | Jackson |
