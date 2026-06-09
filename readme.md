# reliable-invoker-spring-boot-starter

Spring Boot Starter 高可靠性调用组件。通过将方法调用持久化到数据库，结合事务提交后执行、失败自动重试等机制，对任何 Spring 管理的 Bean 方法实现最终一致性，达到"调用即可靠"的目标。业务方实现 `IInvocationHandler` 接口并按场景注册，框架通过 scene 自动路由调用，彻底避免方法名硬编码问题。

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

### 4. 实现 Handler

```java
@Service
public class OrderPaymentHandler implements IInvocationHandler<BusinessSceneEnum, String> {

    @Autowired
    private PaymentService paymentService;

    @Override
    public BusinessSceneEnum getScene() {
        return BusinessSceneEnum.ORDER_SCENE;
    }

    @Override
    public String execute(String paramsJson) {
        Order order = JSON.parseObject(paramsJson, Order.class);
        return this.paymentService.processPayment(order);
    }
}
```

### 5. 配置

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

### 6. 使用

```java
@Service
public class OrderService {

    @Autowired
    private IReliableInvoker reliableInvoker;
    @Autowired
    private IReliableInvokerTask invokerTask;

    public void createOrder(Order order) {
        // 可靠调用（通过 scene 定位 Handler）
        InvocationRequest<BusinessSceneEnum> request = InvocationRequest.<BusinessSceneEnum>builder()
            .scene(BusinessSceneEnum.ORDER_SCENE)
            .params(order)
            .async(true)
            .maxRetry(3)
            .retryDelay(5000)
            .remark("订单支付")
            .build();

        reliableInvoker.execute(request);
    }

    // 定时重试失败任务
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

## 完整示例：订单支付 + 事务 + 可靠 Kafka 消息

场景：用户下单后调用三方支付，支付成功后更新订单状态为已支付（事务），最后需要可靠地发送一条支付成功的 Kafka 消息。利用 `reliable-invoker` 的 `afterCommit` 事务提交后执行机制 + 对 Kafka 发送的持久化保障，确保通知不丢失。

### 场景枚举

```java
public enum BusinessSceneEnum {
    ORDER_PAY_NOTIFY("订单支付成功通知场景");

    private final String description;
    BusinessSceneEnum(String description) { this.description = description; }
    public String getDescription() { return description; }
}
```

### Handler 实现

```java
@Service
public class OrderPayNotifyHandler implements IInvocationHandler<BusinessSceneEnum, Void> {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public BusinessSceneEnum getScene() {
        return BusinessSceneEnum.ORDER_PAY_NOTIFY;
    }

    @Override
    public Void execute(String paramsJson) {
        PayNotifyMessage msg = JSON.parseObject(paramsJson, PayNotifyMessage.class);
        // 发送 Kafka 消息
        kafkaTemplate.send("order-paid-topic", msg.getOrderId(), paramsJson);
        return null;
    }
}
```

### 业务 Service

```java
@Service
public class OrderService {

    @Autowired
    private PaymentGateway paymentGateway;       // 三方支付
    @Autowired
    private OrderDao orderDao;                   // 订单 DAO
    @Autowired
    private IReliableInvoker reliableInvoker;
    @Autowired
    private IReliableInvokerTask invokerTask;

    /**
     * 支付订单
     * <p>
     * 执行流程：
     * 1. 调用三方支付 → 扣款
     * 2. 更新订单状态为已支付（与步骤 1 在同一事务中）
     * 3. 事务提交成功后（afterCommit），由 reliable-invoker 可靠发送 Kafka 通知
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long orderId) {
        // 1. 调用三方支付
        PaymentResult result = paymentGateway.pay(orderId);
        if (!result.isSuccess()) {
            throw new PaymentException("支付失败: " + result.getErrorMsg());
        }

        // 2. 更新订单状态（当前事务）
        orderDao.updateStatus(orderId, "PAID");

        // 3. 构建 Kafka 消息体
        PayNotifyMessage message = new PayNotifyMessage();
        message.setOrderId(orderId.toString());
        message.setPayTime(new Date());
        message.setAmount(result.getAmount());

        // 4. 可靠调用：事务提交后发送 Kafka 消息
        //    —— 若 Kafka 宕机，消息记录将持久化到 DB，后续定时任务重试
        InvocationRequest<BusinessSceneEnum> request = InvocationRequest.<BusinessSceneEnum>builder()
                .scene(BusinessSceneEnum.ORDER_PAY_NOTIFY)
                .params(message)
                .async(true)          // 异步发送，不阻塞支付接口响应
                .maxRetry(5)          // 最多重试 5 次
                .retryDelay(60000)    // 重试间隔 60 秒（给 Kafka 恢复时间窗口）
                .remark("订单" + orderId + "支付通知")
                .build();

        reliableInvoker.execute(request);
    }

    // 定时重试失败的 Kafka 消息
    @Scheduled(cron = "0 */1 * * * *")
    public void retryFailedNotify() {
        RetryRequest<BusinessSceneEnum> request = RetryRequest.<BusinessSceneEnum>builder()
                .scene(BusinessSceneEnum.ORDER_PAY_NOTIFY)
                .statusList(Arrays.asList(0))  // PENDING
                .shardTotal(1)
                .shardIndex(0)
                .limit(100)
                .build();
        int count = invokerTask.retry(request);
        if (count > 0) {
            log.info("重试了 {} 条支付通知消息", count);
        }
    }
}
```

### 消息体

```java
public class PayNotifyMessage {
    private String orderId;
    private Date payTime;
    private BigDecimal amount;
    // getter / setter 省略
}
```

### 设计要点

- **事务边界**：三方支付 + 订单状态更新在同一个 `@Transactional` 内；Kafka 发送在事务提交后（`afterCommit`）执行，确保"先扣款成功，再发通知"
- **可靠性**：Kafka 消息发送委托给 `reliable-invoker`，失败时记录自动持久化到 DB，`@Scheduled` 定时重试
- **异步解耦**：`async(true)` 在线程池中发送 Kafka，不阻塞支付接口响应
- **重试策略**：`retryDelay=60000` 给 Kafka 恢复留足时间窗口，避免无效重试

---

## 完整示例：支付成功后调用积分系统发积分

场景：订单支付成功后，需要调用积分系统的接口为用户发放积分。积分系统可能因网络波动或服务重启暂时不可用，需要可靠地保证发积分操作最终成功。

### 场景枚举（扩展）

```java
public enum BusinessSceneEnum {
    ORDER_PAY_NOTIFY("订单支付成功通知"),
    AWARD_POINTS("支付成功发放积分");      // 新增

    private final String description;
    BusinessSceneEnum(String description) { this.description = description; }
    public String getDescription() { return description; }
}
```

### Handler 实现

```java
@Service
public class AwardPointsHandler implements IInvocationHandler<BusinessSceneEnum, String> {

    @Autowired
    private PointsSystemClient pointsSystemClient;  // 积分系统 Feign/RestTemplate

    @Override
    public BusinessSceneEnum getScene() {
        return BusinessSceneEnum.AWARD_POINTS;
    }

    @Override
    public String execute(String paramsJson) {
        AwardPointsRequest req = JSON.parseObject(paramsJson, AwardPointsRequest.class);
        // 调用积分系统接口
        return this.pointsSystemClient.award(req);
    }
}
```

### 积分请求体

```java
public class AwardPointsRequest {
    private Long userId;
    private Long orderId;
    private BigDecimal amount;    // 根据支付金额计算积分
    // getter / setter 省略
}
```

### 业务 Service

```java
@Service
public class OrderService {

    @Autowired
    private PaymentGateway paymentGateway;
    @Autowired
    private OrderDao orderDao;
    @Autowired
    private IReliableInvoker reliableInvoker;
    @Autowired
    private IReliableInvokerTask invokerTask;

    /**
     * 支付订单并发放积分
     * <p>
     * 流程：
     * 1. 调用三方支付
     * 2. 更新订单状态（步骤 1、2 在同一事务）
     * 3. 事务提交后调用积分系统发积分（若积分系统暂不可用，记录持久化，定时重试）
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long orderId, Long userId) {
        // 1. 三方支付
        PaymentResult result = paymentGateway.pay(orderId);
        if (!result.isSuccess()) {
            throw new PaymentException("支付失败: " + result.getErrorMsg());
        }

        // 2. 更新订单状态
        orderDao.updateStatus(orderId, "PAID");

        // 3. 构建发积分请求
        AwardPointsRequest pointsReq = new AwardPointsRequest();
        pointsReq.setUserId(userId);
        pointsReq.setOrderId(orderId);
        pointsReq.setAmount(result.getAmount());

        // 4. 可靠调用：事务提交后发积分
        //    —— 若积分系统不可用，记录持久化到 DB，后续重试
        InvocationRequest<BusinessSceneEnum> request = InvocationRequest.<BusinessSceneEnum>builder()
                .scene(BusinessSceneEnum.AWARD_POINTS)
                .params(pointsReq)
                .async(true)       // 异步调用，不阻塞支付接口
                .maxRetry(10)      // 最多重试 10 次
                .retryDelay(10000) // 重试间隔 10 秒
                .remark("订单" + orderId + "发积分")
                .build();

        reliableInvoker.execute(request);
    }

    // 定时重试失败的积分发放
    @Scheduled(cron = "0 */1 * * * *")
    public void retryFailedPoints() {
        RetryRequest<BusinessSceneEnum> request = RetryRequest.<BusinessSceneEnum>builder()
                .scene(BusinessSceneEnum.AWARD_POINTS)
                .statusList(Arrays.asList(0))  // PENDING
                .shardTotal(1)
                .shardIndex(0)
                .limit(100)
                .build();
        int count = invokerTask.retry(request);
        if (count > 0) {
            log.info("重试了 {} 条积分发放记录", count);
        }
    }
}
```

### 设计要点

- **最终一致性**：积分发放不在支付事务内，而是通过 `afterCommit` 异步执行。即使积分系统暂时不可用，记录也会持久化，定时任务持续重试直到成功
- **幂等性**：积分系统接口自身需保证幂等（按 `orderId` 去重），避免重试时重复发放
- **限流保护**：`retryDelay=10000` 避免频繁重试对积分系统造成压力
- **多场景共享**：支付通知和积分发放共享同一个 `IReliableInvoker` 入口，通过不同 scene 路由到不同 Handler

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
| `IInvocationHandler` | —（业务方实现） | 场景处理器，业务方实现 |
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
| 序列化 | fastjson |
