# 可靠调用 Handler 模式改造设计文档

## 日期：2026-06-10

---

## 1. 问题背景

当前 `RetryServiceImpl` 通过反射执行目标方法，数据库存储的是 `beanName` + `methodName` 字符串。当代码重构时（类名、方法名、参数变化），历史记录中的方法引用会失效，导致重试失败。

**核心问题**：数据库里存的是代码的静态快照，重构后快照就过期了。

## 2. 方案选择

对比三种 Handler 变体：

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| A：Handler 自解析 JSON | execute(String paramsJson) | 框架极简，不感知参数类型 | Handler 需手写解析 |
| B：框架托管反序列化 | execute(T params) | Handler 拿强类型对象 | 需存 params_type，类名变更仍有风险 |
| C：Handler 无参数 | execute() | 最简单 | 无法传动态参数 |

**选择方案 A**：Handler 自解析 JSON，框架复杂度最低，参数类重构时 Handler 自行适配解析逻辑即可。

## 3. 设计决策

| 决策点 | 选择 |
|--------|------|
| 首要目标 | 从设计上解决字符串反射的脆弱性 |
| Handler 识别 | 按 scene 枚举 name() 映射 |
| Handler 注册 | Spring Bean 自动扫描 |
| Scene 绑定 | 接口 getScene() 方法 |
| 参数传递 | JSON 字符串，Handler 自解析 |
| 返回值 | 泛型 R |
| 旧 API | 完全替换，不保留兼容 |
| 序列化库 | fastjson 替代 Jackson |

## 4. 核心设计

### 4.1 Handler 接口

```java
package com.tz.reliableinvoker.api;

/**
 * 可靠调用处理器接口
 * <p>业务方实现此接口并注册为 Spring Bean，
 * 框架自动扫描并按场景建立映射。
 * 重试时直接调用 {@link #execute(String)} 而非反射。</p>
 *
 * @param <S> 业务场景枚举类型
 * @param <R> 执行返回值类型
 */
public interface IInvocationHandler<S extends Enum<S>, R> {

    /**
     * 返回当前 Handler 对应的业务场景
     */
    S getScene();

    /**
     * 执行业务逻辑
     * <p>参数为 JSON 字符串，由 Handler 自行反序列化为目标类型。
     * paramsJson 无参数时为 null。</p>
     */
    R execute(String paramsJson);
}
```

### 4.2 HandlerRegistry

```java
package com.tz.reliableinvoker.config;

public class HandlerRegistry {

    private final Map<String, IInvocationHandler<?, ?>> registry;

    public HandlerRegistry(Map<String, IInvocationHandler<?, ?>> registry) {
        this.registry = Collections.unmodifiableMap(registry);
    }

    public IInvocationHandler<?, ?> get(String scene) {
        IInvocationHandler<?, ?> handler = this.registry.get(scene);
        if (handler == null) {
            throw new ReliableInvokerException(
                "No IInvocationHandler found for scene [" + scene + "].");
        }
        return handler;
    }
}
```

**自动注册流程：**

1. Spring 容器启动 → `ReliableInvokerAutoConfiguration` 中通过 `ApplicationContext.getBeansOfType(IInvocationHandler.class)` 获取所有 Handler Bean
2. 遍历每个 Handler，调用 `getScene().name()` 获取场景名
3. 构建 `Map<String, IInvocationHandler>` 注册表
4. 同一 scene 有多个 Handler → 抛出异常拒绝启动
5. 注册表注入到 `RetryServiceImpl` / `ReliableInvokerImpl`

### 4.3 精简后的 InvocationRequest

```java
public class InvocationRequest<S extends Enum<S>> {

    private final S scene;
    private final Object params;
    private final boolean async;
    private final boolean saveRecord;
    private final int maxRetry;
    private final int retryDelay;
    private final String remark;

    // Builder：移除 .beanName() .methodName()

    public static <S extends Enum<S>> Builder<S> builder() {
        return new Builder<>();
    }
}
```

**使用对比：**

```java
// 旧方式
InvocationRequest.<Scene>builder()
    .scene(Scene.ORDER)
    .beanName("orderService")       // 删除
    .methodName("createOrder")      // 删除
    .params(req)
    .build();

// 新方式
InvocationRequest.<Scene>builder()
    .scene(Scene.ORDER)
    .params(req)
    .build();
```

### 4.4 ReliableInvokerImpl 改造

- `serializeParams`: Jackson → fastjson（`JSON.toJSONString`）
- `buildRecord`: 不再设置 `beanName` / `methodName`
- `invokeTarget`: 直接调 `retryService.retry(record)`，无反射

### 4.5 RetryServiceImpl 改造

```java
public class RetryServiceImpl implements IRetryService {

    private final HandlerRegistry handlerRegistry;
    private final IInvocationRecordDao recordDao;

    @Override
    public void retry(InvocationRecord record) {
        try {
            IInvocationHandler<?, ?> handler = this.handlerRegistry.get(record.getScene());
            handler.execute(record.getParams());  // 直接调用，零反射
            this.recordDao.updateStatus(record.getId(), SUCCESS, null, record.getScene());
        } catch (Exception e) {
            int newRetryCount = record.getRetryCount() + 1;
            LocalDateTime nextTime = LocalDateTime.now()
                .plus(record.getRetryDelay(), ChronoUnit.MILLIS);
            if (newRetryCount >= record.getMaxRetryCount()) {
                this.recordDao.updateStatus(record.getId(), FAILED, nextTime, record.getScene());
            } else {
                this.recordDao.updateStatus(record.getId(), PENDING, nextTime, record.getScene());
            }
            throw new ExecutionException("Handler failed scene=[" + record.getScene() + "]", e);
        }
    }
}
```

**移除代码：** `findMethod()`、`parseParams()`、`retryByReflection()`、`ApplicationContext` 注入。

### 4.6 调用链路

```
首次调用：invoker.execute(request)
  ├── scene = request.scene.name()
  ├── paramsJson = JSON.toJSONString(params)
  ├── 构建 InvocationRecord（scene + paramsJson）
  ├── handlerRegistry.get(scene) → Handler
  ├── recordDao.save(record)
  └── afterCommit / 立即 → handler.execute(paramsJson)

重试：retryService.retry(record)
  ├── handlerRegistry.get(record.scene) → Handler
  └── handler.execute(record.params)
```

### 4.7 业务方使用示例

```java
@Service
public class OrderCreateHandler implements IInvocationHandler<BusinessSceneEnum, String> {

    private final OrderService orderService;

    public OrderCreateHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public BusinessSceneEnum getScene() {
        return BusinessSceneEnum.ORDER_CREATE;
    }

    @Override
    public String execute(String paramsJson) {
        CreateOrderRequest req = JSON.parseObject(paramsJson, CreateOrderRequest.class);
        return this.orderService.createOrder(req);
    }
}
```

## 5. 数据库变更

```sql
-- 移除不再需要的列
ALTER TABLE reliable_invocation_record DROP COLUMN bean_name;
ALTER TABLE reliable_invocation_record DROP COLUMN method_name;
-- params 列保持不变
```

## 6. 异常处理

```
启动阶段：
  ├── 同一 scene 注册多个 Handler → ReliableInvokerException（拒绝启动）

调用阶段：
  ├── scene 对应 Handler 未注册 → ReliableInvokerException
  ├── params 序列化 > 64KB → ParamsTooLargeException
  ├── JSON.toJSONString 失败 → ReliableInvokerException
  └── Handler.execute() 抛异常 → 按重试策略处理

重试阶段：
  ├── Handler.execute() 成功 → 标记 SUCCESS
  └── Handler.execute() 失败 → retryCount++，超限→FAILED，未超限→PENDING
```

## 7. 测试策略

| 测试类 | 测试点 |
|--------|--------|
| HandlerRegistryTest | 重复 scene 抛异常、未注册抛异常、正常获取 |
| InvocationRequestTest | builder 不含 beanName/methodName、scene 必填 |
| ReliableInvokerImplTest | 快速执行、事务提交、异步、params=null、>64KB |
| RetryServiceImplTest | Handler 成功→SUCCESS、Handler 异常→重试/失败、未注册 scene→异常 |
| JdbcTemplateInvocationRecordDaoImplTest | 适配无 beanName/methodName 的新表结构 |
| ReliableInvokerAutoConfigurationTest | Handler 扫描注册、重复检测 |

## 8. 变更清单汇总

| 文件 | 变更类型 | 变更描述 |
|------|---------|---------|
| `api/IInvocationHandler.java` | 新增 | Handler 接口 |
| `config/HandlerRegistry.java` | 新增 | 注册表 |
| `model/InvocationRequest.java` | 修改 | 移除 beanName/methodName |
| `model/InvocationRecord.java` | 修改 | 移除 beanName/methodName 字段 |
| `api/impl/ReliableInvokerImpl.java` | 修改 | fastjson 序列化，移除反射 |
| `service/impl/RetryServiceImpl.java` | 修改 | 直接调 Handler，移除反射代码 |
| `config/ReliableInvokerAutoConfiguration.java` | 修改 | 注册 HandlerRegistry Bean |
| `pom.xml` | 修改 | Jackson → fastjson |
| DB Schema | 修改 | 移除 bean_name, method_name 列 |

## 9. 方案对比总结

| 维度 | 旧方案 | 新方案 |
|------|--------|--------|
| 调用方式 | beanName + methodName 字符串反射 | scene → Handler 直接调用 |
| 重构安全 | 方法重命名→运行时异常 | Handler 接口契约保护 |
| 序列化 | Jackson ObjectMapper | fastjson |
| RetryServiceImpl 行数 | ~120 行 | ~40 行 |
| DB 字段 | 含 bean_name, method_name | 移除这两列 |
| 框架复杂度 | 中（反射匹配参数类型） | 低（纯 handler 调用） |

---

*文档结束*
