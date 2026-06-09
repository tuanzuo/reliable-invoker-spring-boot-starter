# Handler 模式改造实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将调用方式从 `beanName + methodName` 字符串反射改为 `scene → IInvocationHandler` 直接调用，彻底解决代码重构时方法引用失效问题。

**Architecture:** 新增 `IInvocationHandler<S,R>` 接口 + `HandlerRegistry` 注册表；移除 `InvocationRequest`/`InvocationRecord` 的 `beanName`/`methodName`；`RetryServiceImpl` 不再依赖 `ApplicationContext` 反射，改为 `handlerRegistry.get(scene).execute(paramsJson)`；序列化库从 Jackson 切换到 fastjson。

**Tech Stack:** Java 8, Spring Boot 2.3.12, fastjson 1.2.83, JdbcTemplate, JUnit 5 + Mockito + H2

**Spec:** `docs/superpowers/specs/2026-06-10-handler-mode-design.md`

---

### Task 1: 新增 IInvocationHandler 接口

**Files:**
- Create: `src/main/java/com/tz/reliableinvoker/api/IInvocationHandler.java`

- [ ] **Step 1: 创建 IInvocationHandler 接口**

```java
package com.tz.reliableinvoker.api;

/**
 * 可靠调用处理器接口
 * <p>业务方实现此接口并注册为 Spring Bean，
 * 框架自动扫描并按场景（scene）建立映射。
 * 重试时直接调用 {@link #execute(String)} 而非反射，彻底避免方法名硬编码问题。</p>
 *
 * @param <S> 业务场景枚举类型
 * @param <R> 执行返回值类型
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public interface IInvocationHandler<S extends Enum<S>, R> {

    /**
     * 返回当前 Handler 对应的业务场景
     *
     * @return 场景枚举值
     */
    S getScene();

    /**
     * 执行业务逻辑
     * <p>参数为 JSON 字符串，由 Handler 自行反序列化为目标类型。
     * paramsJson 在无参数时为 null。</p>
     *
     * @param paramsJson 序列化后的参数 JSON，可能为 null
     * @return 执行结果
     */
    R execute(String paramsJson);
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn -s D:\my\settings-tuanzuo.xml compile
```
Expected: SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tz/reliableinvoker/api/IInvocationHandler.java
git commit -m "feat: add IInvocationHandler interface for handler-mode invocation"
```

---

### Task 2: 新增 HandlerRegistry 注册表

**Files:**
- Create: `src/main/java/com/tz/reliableinvoker/config/HandlerRegistry.java`
- Create: `src/test/java/com/tz/reliableinvoker/config/HandlerRegistryTest.java`

- [ ] **Step 1: 编写 HandlerRegistryTest 测试（TDD: 先写测试）**

```java
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
        when(handler.getScene()).thenReturn(null); // getScene 在注册阶段调用，这里不关心

        Map<String, IInvocationHandler<?, ?>> map = new HashMap<>();
        map.put("ORDER", handler);
        HandlerRegistry registry = new HandlerRegistry(map);

        IInvocationHandler<?, ?> found = registry.get("ORDER");
        assertSame(handler, found);
    }

    @Test
    void testGetUnregisteredHandlerThrowsException() {
        HandlerRegistry registry = new HandlerRegistry(Collections.emptyMap());

        ReliableInvokerException ex = assertThrows(ReliableInvokerException.class,
                () -> registry.get("NOT_EXIST"));
        assertTrue(ex.getMessage().contains("NOT_EXIST"));
    }

    @Test
    void testRegistryIsImmutable() {
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<>();
        map.put("ORDER", mock(IInvocationHandler.class));
        HandlerRegistry registry = new HandlerRegistry(map);

        assertThrows(UnsupportedOperationException.class,
                () -> registry.entrySet().clear());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn -s D:\my\settings-tuanzuo.xml test -Dtest=HandlerRegistryTest
```
Expected: FAIL (HandlerRegistry 类不存在)

- [ ] **Step 3: 创建 HandlerRegistry 类**

```java
package com.tz.reliableinvoker.config;

import com.tz.reliableinvoker.api.IInvocationHandler;
import com.tz.reliableinvoker.exception.ReliableInvokerException;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Handler 注册表
 * <p>持有 scene → Handler 的映射，不可变。
 * 由 {@link ReliableInvokerAutoConfiguration} 在启动时构建。</p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class HandlerRegistry {

    private final Map<String, IInvocationHandler<?, ?>> registry;

    /**
     * 构造函数
     *
     * @param registry scene → Handler 映射表
     */
    public HandlerRegistry(Map<String, IInvocationHandler<?, ?>> registry) {
        this.registry = Collections.unmodifiableMap(registry);
    }

    /**
     * 按场景名查找 Handler
     *
     * @param scene 场景名称（枚举的 name()）
     * @return 对应的 Handler
     * @throws ReliableInvokerException 未找到对应 Handler
     */
    public IInvocationHandler<?, ?> get(String scene) {
        IInvocationHandler<?, ?> handler = this.registry.get(scene);
        if (handler == null) {
            throw new ReliableInvokerException(
                    "No IInvocationHandler found for scene [" + scene + "]. "
                    + "Please register a bean implementing IInvocationHandler with getScene() = " + scene);
        }
        return handler;
    }

    /**
     * 返回注册表条目集合（只读视图，主要用于测试）
     *
     * @return 注册表条目集合
     */
    public Set<Map.Entry<String, IInvocationHandler<?, ?>>> entrySet() {
        return this.registry.entrySet();
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn -s D:\my\settings-tuanzuo.xml test -Dtest=HandlerRegistryTest
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tz/reliableinvoker/config/HandlerRegistry.java src/test/java/com/tz/reliableinvoker/config/HandlerRegistryTest.java
git commit -m "feat: add HandlerRegistry for scene-to-handler mapping"
```

---

### Task 3: 精简 InvocationRequest（移除 beanName/methodName）

**Files:**
- Modify: `src/main/java/com/tz/reliableinvoker/model/InvocationRequest.java`

> **注意：此步骤会破坏现有编译，需与后续任务组合完成。**

- [ ] **Step 1: 重写 InvocationRequest 类**

完整替换文件内容：

```java
package com.tz.reliableinvoker.model;

import java.util.Objects;

/**
 * 调用请求（Handler 模式）
 * <p>通过 scene 定位 IInvocationHandler 执行，
 * 移除了 beanName/methodName 字符串反射方式。</p>
 *
 * @param <S> 业务场景枚举类型
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class InvocationRequest<S extends Enum<S>> {

    /** 业务场景（非空） */
    private final S scene;

    /** 调用参数 */
    private final Object params;

    /** 是否异步调用 */
    private final boolean async;

    /** 是否保存调用记录，默认true */
    private final boolean saveRecord;

    /** 最大重试次数，需大于0 */
    private final int maxRetry;

    /** 重试延迟（毫秒），需大于0 */
    private final int retryDelay;

    /** 备注 */
    private final String remark;

    private InvocationRequest(Builder<S> builder) {
        this.scene = builder.scene;
        this.params = builder.params;
        this.async = builder.async;
        this.saveRecord = builder.saveRecord;
        this.maxRetry = builder.maxRetry;
        this.retryDelay = builder.retryDelay;
        this.remark = builder.remark;
    }

    /**
     * 创建Builder实例
     *
     * @param <S> 业务场景枚举类型
     * @return Builder
     */
    public static <S extends Enum<S>> Builder<S> builder() {
        return new Builder<>();
    }

    public S getScene() {
        return scene;
    }

    public Object getParams() {
        return params;
    }

    public boolean isAsync() {
        return async;
    }

    public boolean isSaveRecord() {
        return saveRecord;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public int getRetryDelay() {
        return retryDelay;
    }

    public String getRemark() {
        return remark;
    }

    /**
     * InvocationRequest的Builder
     *
     * @param <S> 业务场景枚举类型
     * @version 1.0.0-SNAPSHOT
     */
    public static class Builder<S extends Enum<S>> {

        private S scene;
        private Object params;
        private boolean async;
        private boolean saveRecord = true;
        private int maxRetry;
        private int retryDelay;
        private String remark;

        /**
         * 设置业务场景
         *
         * @param scene 业务场景
         * @return Builder
         */
        public Builder<S> scene(S scene) {
            this.scene = scene;
            return this;
        }

        /**
         * 设置调用参数
         *
         * @param params 调用参数
         * @return Builder
         */
        public Builder<S> params(Object params) {
            this.params = params;
            return this;
        }

        /**
         * 设置是否异步调用
         *
         * @param async 是否异步
         * @return Builder
         */
        public Builder<S> async(boolean async) {
            this.async = async;
            return this;
        }

        /**
         * 设置是否保存调用记录
         *
         * @param saveRecord 是否保存
         * @return Builder
         */
        public Builder<S> saveRecord(boolean saveRecord) {
            this.saveRecord = saveRecord;
            return this;
        }

        /**
         * 设置最大重试次数
         *
         * @param maxRetry 最大重试次数
         * @return Builder
         */
        public Builder<S> maxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
            return this;
        }

        /**
         * 设置重试延迟
         *
         * @param retryDelay 重试延迟（毫秒）
         * @return Builder
         */
        public Builder<S> retryDelay(int retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        /**
         * 设置备注
         *
         * @param remark 备注
         * @return Builder
         */
        public Builder<S> remark(String remark) {
            this.remark = remark;
            return this;
        }

        /**
         * 构建InvocationRequest实例，校验scene非空
         *
         * @return InvocationRequest实例
         * @throws NullPointerException 如果scene为null
         */
        public InvocationRequest<S> build() {
            Objects.requireNonNull(scene, "scene must not be null");
            return new InvocationRequest<>(this);
        }
    }
}
```

- [ ] **Step 2: 不单独编译（会因引用 beanName/methodName 的其他代码编译失败），进入下一个任务**

---

### Task 3.5: 新增 InvocationRequestTest

**Files:**
- Create: `src/test/java/com/tz/reliableinvoker/model/InvocationRequestTest.java`

- [ ] **Step 1: 编写 InvocationRequestTest（TDD: 先写测试）**

```java
package com.tz.reliableinvoker.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InvocationRequest 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class InvocationRequestTest {

    enum TestScene { ORDER }

    @Test
    void testBuilderCreatesRequest() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .params("payload")
                .async(true)
                .saveRecord(false)
                .maxRetry(5)
                .retryDelay(3000)
                .remark("test")
                .build();

        assertEquals(TestScene.ORDER, request.getScene());
        assertEquals("payload", request.getParams());
        assertTrue(request.isAsync());
        assertFalse(request.isSaveRecord());
        assertEquals(5, request.getMaxRetry());
        assertEquals(3000, request.getRetryDelay());
        assertEquals("test", request.getRemark());
    }

    @Test
    void testSceneRequired() {
        assertThrows(NullPointerException.class, () ->
                InvocationRequest.<TestScene>builder().build());
    }

    @Test
    void testDefaultValues() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .build();

        assertFalse(request.isAsync());
        assertTrue(request.isSaveRecord());
        assertEquals(0, request.getMaxRetry());
        assertEquals(0, request.getRetryDelay());
        assertNull(request.getParams());
        assertNull(request.getRemark());
    }

    @Test
    void testParamsCanBeNull() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.ORDER)
                .params(null)
                .build();

        assertNull(request.getParams());
    }
}
```

- [ ] **Step 2: 运行测试验证通过**

```bash
mvn -s D:\my\settings-tuanzuo.xml test -Dtest=InvocationRequestTest
```
Expected: PASS（InvocationRequest 在 Task 3 中已修改，此时应匹配新接口）

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/tz/reliableinvoker/model/InvocationRequestTest.java
git commit -m "test: add InvocationRequestTest for handler-mode API contract"
```

---

### Task 4: 精简 InvocationRecord + Schema + DAO

**Files:**
- Modify: `src/main/java/com/tz/reliableinvoker/model/InvocationRecord.java`
- Modify: `src/test/resources/schema.sql`
- Modify: `src/main/java/com/tz/reliableinvoker/dao/impl/JdbcTemplateInvocationRecordDaoImpl.java`

> **此任务与 Task 3、5、6 组合完成后才能编译通过。**

- [ ] **Step 1: 修改 InvocationRecord — 移除 beanName/methodName 字段及其 getter/setter**

在 `InvocationRecord.java` 中删除 `beanName` 和 `methodName` 两个字段声明以及对应的 `getBeanName()`、`setBeanName()`、`getMethodName()`、`setMethodName()` 四个方法。

- [ ] **Step 2: 修改 schema.sql — 移除 bean_name/method_name 列**

```sql
CREATE TABLE IF NOT EXISTS reliable_invocation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    serial_no VARCHAR(64) NOT NULL UNIQUE,
    scene VARCHAR(64) NOT NULL,
    params TEXT,
    status TINYINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    retry_delay INT NOT NULL DEFAULT 5000,
    execute_time DATETIME,
    remark VARCHAR(512),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 3: 修改 JdbcTemplateInvocationRecordDaoImpl — 更新 INSERT SQL 和 RowMapper**

**INSERT SQL（save 方法）：**

```java
@Override
public InvocationRecord save(InvocationRecord record) {
    String tableName = this.resolveTableName(record.getScene());
    String sql = "INSERT INTO " + tableName
            + " (serial_no, scene, params, status,"
            + " retry_count, max_retry_count, retry_delay, execute_time, remark)"
            + " VALUES (:serialNo, :scene, :params, :status,"
            + " :retryCount, :maxRetryCount, :retryDelay, :executeTime, :remark)";

    MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("serialNo", record.getSerialNo())
            .addValue("scene", record.getScene())
            .addValue("params", record.getParams())
            .addValue("status", record.getStatus())
            .addValue("retryCount", record.getRetryCount())
            .addValue("maxRetryCount", record.getMaxRetryCount())
            .addValue("retryDelay", record.getRetryDelay())
            .addValue("executeTime", record.getExecuteTime())
            .addValue("remark", record.getRemark());

    this.namedJdbcTemplate.update(sql, params);
    return this.findBySerialNo(record.getSerialNo(), record.getScene());
}
```

**RowMapper — 移除 `beanName` 和 `methodName` 的映射行：**

```java
/**
 * 记录行映射器
 */
private static class RecordRowMapper implements RowMapper<InvocationRecord> {

    @Override
    public InvocationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        InvocationRecord record = new InvocationRecord();
        record.setId(rs.getLong("id"));
        record.setSerialNo(rs.getString("serial_no"));
        record.setScene(rs.getString("scene"));
        record.setParams(rs.getString("params"));
        record.setStatus(rs.getInt("status"));
        record.setRetryCount(rs.getInt("retry_count"));
        record.setMaxRetryCount(rs.getInt("max_retry_count"));
        record.setRetryDelay(rs.getInt("retry_delay"));
        Timestamp executeTime = rs.getTimestamp("execute_time");
        if (executeTime != null) {
            record.setExecuteTime(executeTime.toLocalDateTime());
        }
        record.setRemark(rs.getString("remark"));
        Timestamp createTime = rs.getTimestamp("create_time");
        if (createTime != null) {
            record.setCreateTime(createTime.toLocalDateTime());
        }
        Timestamp updateTime = rs.getTimestamp("update_time");
        if (updateTime != null) {
            record.setUpdateTime(updateTime.toLocalDateTime());
        }
        return record;
    }
}
```

- [ ] **Step 4: 不单独编译，进入下一个任务**

---

### Task 5: 改造 RetryServiceImpl（Handler 模式 + 移除反射）

**Files:**
- Modify: `src/main/java/com/tz/reliableinvoker/service/impl/RetryServiceImpl.java`

- [ ] **Step 1: 重写 RetryServiceImpl**

完整替换文件内容：

```java
package com.tz.reliableinvoker.service.impl;

import com.tz.reliableinvoker.api.IInvocationHandler;
import com.tz.reliableinvoker.config.HandlerRegistry;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.exception.ExecutionException;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import com.tz.reliableinvoker.service.IRetryService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 默认重试执行服务实现（Handler 模式）
 * <p>通过 HandlerRegistry 查找 Handler 并直接调用，
 * 零反射，彻底避免方法名硬编码问题。</p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class RetryServiceImpl implements IRetryService {

    /** Handler 注册表 */
    private final HandlerRegistry handlerRegistry;

    /** 调用记录数据访问对象 */
    private final IInvocationRecordDao recordDao;

    /**
     * 构造函数
     *
     * @param handlerRegistry Handler 注册表
     * @param recordDao       调用记录 DAO
     */
    public RetryServiceImpl(HandlerRegistry handlerRegistry, IInvocationRecordDao recordDao) {
        this.handlerRegistry = handlerRegistry;
        this.recordDao = recordDao;
    }

    @Override
    public void retry(InvocationRecord record) {
        Integer retryCount = record.getRetryCount();
        try {
            IInvocationHandler<?, ?> handler = this.handlerRegistry.get(record.getScene());
            handler.execute(record.getParams());

            this.recordDao.updateStatus(record.getId(),
                    InvocationStatusEnum.SUCCESS.getCode(), null, record.getScene());
        } catch (Exception e) {
            int newRetryCount = (retryCount != null ? retryCount : 0) + 1;
            Integer maxRetryCount = record.getMaxRetryCount();
            int maxCount = maxRetryCount != null ? maxRetryCount : 0;
            Integer retryDelay = record.getRetryDelay();
            long delayMs = retryDelay != null ? retryDelay.longValue() : 0;
            LocalDateTime nextExecuteTime = LocalDateTime.now().plus(delayMs, ChronoUnit.MILLIS);

            this.recordDao.updateStatus(record.getId(),
                    newRetryCount >= maxCount
                            ? InvocationStatusEnum.FAILED.getCode()
                            : InvocationStatusEnum.PENDING.getCode(),
                    nextExecuteTime, record.getScene());
            throw new ExecutionException(
                    "Handler execution failed for scene [" + record.getScene()
                    + "], retryCount=" + newRetryCount, e);
        }
    }
}
```

- [ ] **Step 2: 不单独编译，进入下一个任务**

---

### Task 6: 改造 ReliableInvokerImpl（fastjson 序列化 + HandlerRegistry）

**Files:**
- Modify: `src/main/java/com/tz/reliableinvoker/api/impl/ReliableInvokerImpl.java`
- Modify: `pom.xml`

- [ ] **Step 1: 修改 pom.xml — Jackson → fastjson**

```diff
-        <dependency>
-            <groupId>com.fasterxml.jackson.core</groupId>
-            <artifactId>jackson-databind</artifactId>
-        </dependency>
+        <dependency>
+            <groupId>com.alibaba</groupId>
+            <artifactId>fastjson</artifactId>
+            <version>1.2.83</version>
+        </dependency>
```

- [ ] **Step 2: 重写 ReliableInvokerImpl**

完整替换文件内容。关键变更：
- 引入 `HandlerRegistry` 作为构造参数
- `serializeParams`: `objectMapper.writeValueAsString` → `JSON.toJSONString`（注意 null 处理）
- `buildRecord`: 移除 `setBeanName` / `setMethodName`
- 构造函数移除无用的 `IAsyncExecutor asyncExecutor` 参数

```java
package com.tz.reliableinvoker.api.impl;

import com.alibaba.fastjson.JSON;
import com.tz.reliableinvoker.api.IReliableInvoker;
import com.tz.reliableinvoker.config.HandlerRegistry;
import com.tz.reliableinvoker.config.ReliableInvokerProperties;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.exception.ParamsTooLargeException;
import com.tz.reliableinvoker.exception.ReliableInvokerException;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationRequest;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import com.tz.reliableinvoker.service.IRetryService;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 可靠调用执行器默认实现（Handler 模式）
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerImpl implements IReliableInvoker {

    private static final int MAX_PARAMS_BYTES = 64 * 1024;

    private final IInvocationRecordDao recordDao;
    private final IRetryService retryService;
    private final ReliableInvokerProperties properties;
    private final TaskExecutor defaultTaskExecutor;
    private final Map<String, TaskExecutor> sceneTaskExecutors;
    private final HandlerRegistry handlerRegistry;

    public ReliableInvokerImpl(IInvocationRecordDao recordDao,
                                IRetryService retryService,
                                ReliableInvokerProperties properties,
                                TaskExecutor defaultTaskExecutor,
                                Map<String, TaskExecutor> sceneTaskExecutors,
                                HandlerRegistry handlerRegistry) {
        this.recordDao = recordDao;
        this.retryService = retryService;
        this.properties = properties;
        this.defaultTaskExecutor = defaultTaskExecutor;
        this.sceneTaskExecutors = sceneTaskExecutors != null ? sceneTaskExecutors : new HashMap<String, TaskExecutor>();
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public InvocationRecord execute(InvocationRequest<?> request) {
        String scene = request.getScene().name();
        String paramsJson = this.serializeParams(request.getParams());

        int maxRetry = this.resolveMaxRetry(request);
        int retryDelay = this.resolveRetryDelay(request);

        InvocationRecord record = this.buildRecord(request, scene, paramsJson, maxRetry, retryDelay);

        if (!request.isSaveRecord()) {
            this.invokeTarget(request, record);
            return record;
        }

        this.recordDao.save(record);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ReliableInvokerImpl.this.invokeTarget(request, record);
                }
            });
        } else {
            this.invokeTarget(request, record);
        }

        return record;
    }

    private void invokeTarget(InvocationRequest<?> request, InvocationRecord record) {
        String sceneName = request.getScene().name();
        TaskExecutor executor = this.sceneTaskExecutors.getOrDefault(sceneName, this.defaultTaskExecutor);
        if (request.isAsync()) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    ReliableInvokerImpl.this.retryService.retry(record);
                }
            });
        } else {
            this.retryService.retry(record);
        }
    }

    private int resolveMaxRetry(InvocationRequest<?> request) {
        if (request.getMaxRetry() > 0) {
            return request.getMaxRetry();
        }
        ReliableInvokerProperties.SceneProperties sceneProperties =
                this.properties.getSceneProperties(request.getScene().name());
        if (sceneProperties != null && sceneProperties.getMaxRetry() != null && sceneProperties.getMaxRetry() > 0) {
            return sceneProperties.getMaxRetry();
        }
        return this.properties.getMaxRetry();
    }

    private int resolveRetryDelay(InvocationRequest<?> request) {
        if (request.getRetryDelay() > 0) {
            return request.getRetryDelay();
        }
        ReliableInvokerProperties.SceneProperties sceneProperties =
                this.properties.getSceneProperties(request.getScene().name());
        if (sceneProperties != null && sceneProperties.getDefaultDelay() != null
                && sceneProperties.getDefaultDelay() > 0) {
            return sceneProperties.getDefaultDelay();
        }
        return this.properties.getDefaultDelay();
    }

    /**
     * 序列化参数为 JSON 字符串
     * <p>注意：JSON.toJSONString(null) 返回 "null" 而非 null，
     * 需显式处理 null 情况。</p>
     */
    private String serializeParams(Object params) {
        if (params == null) {
            return null;
        }
        try {
            String json = JSON.toJSONString(params);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_PARAMS_BYTES) {
                throw new ParamsTooLargeException(bytes.length, MAX_PARAMS_BYTES);
            }
            return json;
        } catch (ParamsTooLargeException e) {
            throw e;
        } catch (Exception e) {
            throw new ReliableInvokerException(
                    "Failed to serialize params to JSON", e);
        }
    }

    private InvocationRecord buildRecord(InvocationRequest<?> request, String scene,
                                         String paramsJson, int maxRetry, int retryDelay) {
        InvocationRecord record = new InvocationRecord();
        record.setSerialNo(UUID.randomUUID().toString());
        record.setScene(scene);
        record.setParams(paramsJson);
        record.setStatus(InvocationStatusEnum.PENDING.getCode());
        record.setRetryCount(0);
        record.setMaxRetryCount(maxRetry);
        record.setRetryDelay(retryDelay);
        record.setExecuteTime(LocalDateTime.now());
        record.setRemark(request.getRemark());
        record.setCreateTime(LocalDateTime.now());
        return record;
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn -s D:\my\settings-tuanzuo.xml compile
```
Expected: FAIL（RetryServiceImpl 构造函数签名变更，ReliableInvokerAutoConfiguration 中还有 `ApplicationContext` 注入）。继续下一个任务。

---

### Task 7: 改造 ReliableInvokerAutoConfiguration

**Files:**
- Modify: `src/main/java/com/tz/reliableinvoker/config/ReliableInvokerAutoConfiguration.java`

- [ ] **Step 1: 修改 AutoConfiguration — 注册 HandlerRegistry + 更新 Bean 定义**

关键变更：
1. 新增 `handlerRegistry` Bean（扫描 `IInvocationHandler` 实现）
2. `reliableInvoker` Bean 注入 `HandlerRegistry`，移除 `IAsyncExecutor` 参数
3. `retryService` Bean 改为注入 `HandlerRegistry`，移除 `ApplicationContext`

```java
package com.tz.reliableinvoker.config;

import com.tz.reliableinvoker.api.IReliableInvoker;
import com.tz.reliableinvoker.api.IReliableInvokerTask;
import com.tz.reliableinvoker.api.IInvocationHandler;
import com.tz.reliableinvoker.api.impl.ReliableInvokerImpl;
import com.tz.reliableinvoker.api.impl.ReliableInvokerTaskImpl;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.dao.impl.JdbcTemplateInvocationRecordDaoImpl;
import com.tz.reliableinvoker.exception.ReliableInvokerException;
import com.tz.reliableinvoker.service.IAsyncExecutor;
import com.tz.reliableinvoker.service.IBackupService;
import com.tz.reliableinvoker.service.IRetryService;
import com.tz.reliableinvoker.service.impl.AsyncExecutorImpl;
import com.tz.reliableinvoker.service.impl.BackupServiceImpl;
import com.tz.reliableinvoker.service.impl.RetryServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.Map;

/**
 * 可靠调用自动配置类
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
@Configuration
@EnableConfigurationProperties(ReliableInvokerProperties.class)
@ConditionalOnProperty(prefix = "reliable.invoker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReliableInvokerAutoConfiguration {

    @Bean("reliableInvokerTaskExecutor")
    @ConditionalOnMissingBean(name = "reliableInvokerTaskExecutor")
    public ThreadPoolTaskExecutor reliableInvokerTaskExecutor(ReliableInvokerProperties properties) {
        ReliableInvokerProperties.AsyncProperties async = properties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCorePoolSize());
        executor.setMaxPoolSize(async.getMaxPoolSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix("reliable-invoker-");
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(IAsyncExecutor.class)
    public IAsyncExecutor asyncExecutor(ThreadPoolTaskExecutor reliableInvokerTaskExecutor) {
        return new AsyncExecutorImpl(reliableInvokerTaskExecutor);
    }

    @Bean
    public Map<String, TaskExecutor> sceneTaskExecutors(ReliableInvokerProperties properties) {
        Map<String, TaskExecutor> executors = new HashMap<String, TaskExecutor>();
        if (properties.getScenes() != null) {
            for (Map.Entry<String, ReliableInvokerProperties.SceneProperties> e : properties.getScenes().entrySet()) {
                if (e.getValue().getAsync() != null) {
                    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
                    ex.setCorePoolSize(e.getValue().getAsync().getCorePoolSize());
                    ex.setMaxPoolSize(e.getValue().getAsync().getMaxPoolSize());
                    ex.setQueueCapacity(e.getValue().getAsync().getQueueCapacity());
                    ex.setThreadNamePrefix("reliable-" + e.getKey() + "-");
                    ex.initialize();
                    executors.put(e.getKey(), ex);
                }
            }
        }
        return executors;
    }

    /**
     * Handler 注册表 Bean
     * <p>自动扫描所有 IInvocationHandler 实现，
     * 按 getScene().name() 建立映射。</p>
     */
    @Bean
    @ConditionalOnMissingBean(HandlerRegistry.class)
    public HandlerRegistry handlerRegistry(ApplicationContext applicationContext) {
        Map<String, IInvocationHandler> beans = applicationContext.getBeansOfType(IInvocationHandler.class);
        Map<String, IInvocationHandler<?, ?>> registry = new HashMap<String, IInvocationHandler<?, ?>>();
        for (IInvocationHandler handler : beans.values()) {
            String scene = handler.getScene().name();
            if (registry.containsKey(scene)) {
                throw new ReliableInvokerException(
                        "Duplicate handler for scene [" + scene + "]: "
                        + registry.get(scene).getClass().getName() + " and "
                        + handler.getClass().getName());
            }
            registry.put(scene, handler);
        }
        return new HandlerRegistry(registry);
    }

    @Bean
    @ConditionalOnMissingBean(IReliableInvoker.class)
    public IReliableInvoker reliableInvoker(IInvocationRecordDao recordDao,
            IRetryService retryService,
            ReliableInvokerProperties properties,
            @Qualifier("reliableInvokerTaskExecutor") TaskExecutor defaultTaskExecutor,
            Map<String, TaskExecutor> sceneTaskExecutors,
            HandlerRegistry handlerRegistry) {
        return new ReliableInvokerImpl(recordDao, retryService, properties,
                defaultTaskExecutor, sceneTaskExecutors, handlerRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(IReliableInvokerTask.class)
    public IReliableInvokerTask reliableInvokerTask(IInvocationRecordDao recordDao, IRetryService retryService,
                                                    IBackupService backupService) {
        return new ReliableInvokerTaskImpl(recordDao, retryService, backupService);
    }

    @Bean
    @ConditionalOnMissingBean(IInvocationRecordDao.class)
    public IInvocationRecordDao invocationRecordDao(JdbcTemplate jdbcTemplate,
                                                    ReliableInvokerProperties properties) {
        return new JdbcTemplateInvocationRecordDaoImpl(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(IRetryService.class)
    public IRetryService retryService(IInvocationRecordDao recordDao, HandlerRegistry handlerRegistry) {
        return new RetryServiceImpl(handlerRegistry, recordDao);
    }

    @Bean
    @ConditionalOnMissingBean(IBackupService.class)
    public IBackupService backupService(IInvocationRecordDao recordDao) {
        return new BackupServiceImpl(recordDao);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn -s D:\my\settings-tuanzuo.xml clean compile
```
Expected: FAIL（测试代码仍引用旧 API）。运行 compile-only 确认主代码通过：

```bash
mvn -s D:\my\settings-tuanzuo.xml compile -DskipTests
```
Expected: SUCCESS

---

### Task 8: 新增 RetryServiceImplTest

**Files:**
- Create: `src/test/java/com/tz/reliableinvoker/service/impl/RetryServiceImplTest.java`

- [ ] **Step 1: 编写 RetryServiceImpl 测试**

```java
package com.tz.reliableinvoker.service.impl;

import com.tz.reliableinvoker.api.IInvocationHandler;
import com.tz.reliableinvoker.config.HandlerRegistry;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.exception.ExecutionException;
import com.tz.reliableinvoker.exception.ReliableInvokerException;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RetryServiceImpl 单元测试（Handler 模式）
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class RetryServiceImplTest {

    @Test
    void testRetrySuccess() {
        IInvocationHandler<?, ?> handler = mock(IInvocationHandler.class);
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<String, IInvocationHandler<?, ?>>();
        map.put("ORDER", handler);
        HandlerRegistry registry = new HandlerRegistry(map);

        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        RetryServiceImpl retryService = new RetryServiceImpl(registry, recordDao);

        InvocationRecord record = new InvocationRecord();
        record.setId(1L);
        record.setScene("ORDER");
        record.setParams("{\"orderId\":123}");
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);

        retryService.retry(record);

        verify(handler).execute("{\"orderId\":123}");
        verify(recordDao).updateStatus(1L, InvocationStatusEnum.SUCCESS.getCode(), null, "ORDER");
    }

    @Test
    void testRetryHandlerNotFoundThrowsException() {
        HandlerRegistry registry = new HandlerRegistry(Collections.<String, IInvocationHandler<?, ?>>emptyMap());
        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        RetryServiceImpl retryService = new RetryServiceImpl(registry, recordDao);

        InvocationRecord record = new InvocationRecord();
        record.setId(1L);
        record.setScene("NOT_EXIST");
        record.setParams("{}");
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);

        assertThrows(ReliableInvokerException.class, () -> retryService.retry(record));
        verify(recordDao, never()).updateStatus(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    void testRetryHandlerThrowsExceptionRetryPending() {
        IInvocationHandler<?, ?> handler = mock(IInvocationHandler.class);
        doThrow(new RuntimeException("business error")).when(handler).execute(anyString());
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<String, IInvocationHandler<?, ?>>();
        map.put("ORDER", handler);
        HandlerRegistry registry = new HandlerRegistry(map);

        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        RetryServiceImpl retryService = new RetryServiceImpl(registry, recordDao);

        InvocationRecord record = new InvocationRecord();
        record.setId(1L);
        record.setScene("ORDER");
        record.setParams("{\"orderId\":123}");
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);

        assertThrows(ExecutionException.class, () -> retryService.retry(record));
        verify(recordDao).updateStatus(eq(1L), eq(InvocationStatusEnum.PENDING.getCode()), any(), eq("ORDER"));
    }

    @Test
    void testRetryMaxRetryExhaustedFails() {
        IInvocationHandler<?, ?> handler = mock(IInvocationHandler.class);
        doThrow(new RuntimeException("business error")).when(handler).execute(anyString());
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<String, IInvocationHandler<?, ?>>();
        map.put("ORDER", handler);
        HandlerRegistry registry = new HandlerRegistry(map);

        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        RetryServiceImpl retryService = new RetryServiceImpl(registry, recordDao);

        InvocationRecord record = new InvocationRecord();
        record.setId(1L);
        record.setScene("ORDER");
        record.setParams("{\"orderId\":123}");
        record.setRetryCount(2);  // next retry = 3 >= max=3
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);

        assertThrows(ExecutionException.class, () -> retryService.retry(record));
        verify(recordDao).updateStatus(eq(1L), eq(InvocationStatusEnum.FAILED.getCode()), any(), eq("ORDER"));
    }

    @Test
    void testRetryWithNullParams() {
        IInvocationHandler<?, ?> handler = mock(IInvocationHandler.class);
        Map<String, IInvocationHandler<?, ?>> map = new HashMap<String, IInvocationHandler<?, ?>>();
        map.put("ORDER", handler);
        HandlerRegistry registry = new HandlerRegistry(map);

        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        RetryServiceImpl retryService = new RetryServiceImpl(registry, recordDao);

        InvocationRecord record = new InvocationRecord();
        record.setId(1L);
        record.setScene("ORDER");
        record.setParams(null);
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setRetryDelay(5000);

        retryService.retry(record);

        verify(handler).execute(null);
        verify(recordDao).updateStatus(1L, InvocationStatusEnum.SUCCESS.getCode(), null, "ORDER");
    }
}
```

- [ ] **Step 2: 运行测试验证通过**

```bash
mvn -s D:\my\settings-tuanzuo.xml test -Dtest=RetryServiceImplTest
```
Expected: PASS

---

### Task 9: 更新所有现有测试 + 全量构建

**Files:**
- Modify: `src/test/java/com/tz/reliableinvoker/api/impl/ReliableInvokerImplTest.java`
- Modify: `src/test/java/com/tz/reliableinvoker/api/impl/ReliableInvokerTaskImplTest.java`
- Modify: `src/test/java/com/tz/reliableinvoker/dao/impl/JdbcTemplateInvocationRecordDaoImplTest.java`
- Modify: `src/test/java/com/tz/reliableinvoker/config/ReliableInvokerAutoConfigurationTest.java`

- [ ] **Step 1: 修改 ReliableInvokerImplTest — 适配新 API**

关键变更：
- 移除 `.beanName()` / `.methodName()` 调用
- 新增 mock `HandlerRegistry`
- `ReliableInvokerImpl` 构造函数新增 `HandlerRegistry` 参数

```java
package com.tz.reliableinvoker.api.impl;

import com.tz.reliableinvoker.config.HandlerRegistry;
import com.tz.reliableinvoker.config.ReliableInvokerProperties;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.dao.impl.JdbcTemplateInvocationRecordDaoImpl;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationRequest;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
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
 * ReliableInvokerImpl 单元测试（Handler 模式）
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerImplTest {

    private EmbeddedDatabase db;
    private IInvocationRecordDao recordDao;
    private IRetryService retryService;
    private TaskExecutor taskExecutor;
    private ReliableInvokerProperties properties;
    private HandlerRegistry handlerRegistry;
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
        taskExecutor = mock(TaskExecutor.class);
        handlerRegistry = mock(HandlerRegistry.class);
        invoker = new ReliableInvokerImpl(recordDao, retryService, properties,
                taskExecutor, new HashMap<String, TaskExecutor>(), handlerRegistry);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testSyncExecute() {
        InvocationRequest<TestScene> request = InvocationRequest.<TestScene>builder()
                .scene(TestScene.TEST_SCENE)
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
                .async(false)
                .saveRecord(false)
                .build();

        InvocationRecord record = invoker.execute(request);

        assertNotNull(record);
        InvocationRecord found = recordDao.findBySerialNo(record.getSerialNo(), "TEST_SCENE");
        assertNull(found);
    }
}
```

- [ ] **Step 2: 修改 JdbcTemplateInvocationRecordDaoImplTest — 适配新 Schema**

关键变更：
- `createRecord` 方法移除 `setBeanName` / `setMethodName`
- 所有 SQL 移除 `bean_name`, `method_name` 引用
- 断言移除对 `beanName` / `methodName` 的校验

```java
// createRecord 方法变更为：
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

// testSaveAndFindBySerialNo 断言变更（移除 beanName/methodName 校验）：
assertEquals("SN-001", saved.getSerialNo());
assertEquals(SCENE, saved.getScene());
assertEquals("{\"key\":\"value\"}", saved.getParams());
assertEquals(Integer.valueOf(InvocationStatusEnum.PENDING.getCode()), saved.getStatus());

// testFindForBackup INSERT SQL 变更：
jdbcTemplate.update(
        "INSERT INTO reliable_invocation_record"
                + " (serial_no, scene, params, status,"
                + " retry_count, max_retry_count, retry_delay, create_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        "SN-BACKUP-" + i, SCENE, "{}",
        InvocationStatusEnum.SUCCESS.getCode(), 0, 3, 5000,
        Timestamp.valueOf(LocalDateTime.now().minusDays(10))
);
```

- [ ] **Step 3: 修改 ReliableInvokerAutoConfigurationTest**

需要变更两处：

**(a) testAutoConfigurationCreatesBeans — 新增 HandlerRegistry 和自定义 Handler 扫描验证：**

```java
import com.tz.reliableinvoker.api.IInvocationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 新增：带有自定义 Handler 的场景配置
enum AutoConfigTestScene { ORDER_SCENE, PAY_SCENE }

@Configuration
static class TestHandlerConfig {
    @Bean
    public IInvocationHandler<AutoConfigTestScene, String> orderHandler() {
        return new IInvocationHandler<AutoConfigTestScene, String>() {
            @Override
            public AutoConfigTestScene getScene() { return AutoConfigTestScene.ORDER_SCENE; }
            @Override
            public String execute(String paramsJson) { return "ok"; }
        };
    }
}

// testAutoConfigurationCreatesBeans 中追加断言：
assertThat(ctx.getBean(HandlerRegistry.class)).isNotNull();
IInvocationHandler<?, ?> handler = ctx.getBean(HandlerRegistry.class).get("ORDER_SCENE");
assertThat(handler).isNotNull();
```

**(b) 新增测试：重复 Handler 注册检测**

```java
// 在 AutoConfigurationTest 类内部新增配置：
@Configuration
static class DuplicateHandlerConfig {
    @Bean
    public IInvocationHandler<AutoConfigTestScene, String> handler1() {
        return new IInvocationHandler<AutoConfigTestScene, String>() {
            @Override public AutoConfigTestScene getScene() { return AutoConfigTestScene.ORDER_SCENE; }
            @Override public String execute(String paramsJson) { return "ok"; }
        };
    }
    @Bean
    public IInvocationHandler<AutoConfigTestScene, String> handler2() {
        return new IInvocationHandler<AutoConfigTestScene, String>() {
            @Override public AutoConfigTestScene getScene() { return AutoConfigTestScene.ORDER_SCENE; }
            @Override public String execute(String paramsJson) { return "ok"; }
        };
    }
}
```

注意：由于 AutoConfiguration 内扫描是通过 `applicationContext.getBeansOfType()` 收集所有 Bean，当容器中有重复 scene 的 Handler 时会在 `handlerRegistry()` 方法内抛出 `ReliableInvokerException`。验证方式：使用 `assertThrows` 或 `context.run(context -> assertThat(context).hasFailed())`。

- [ ] **Step 4: 修改 ReliableInvokerTaskImplTest**

该测试文件有两处引用 `beanName`/`methodName` 需要修改：

**(a) `createRecord` 方法 — 移除 setBeanName/setMethodName（行 129-130）：**

```java
private InvocationRecord createRecord(int status, String serialNo) {
    InvocationRecord record = new InvocationRecord();
    record.setSerialNo(serialNo);
    record.setScene(SCENE);
    // 移除: record.setBeanName("testBean");
    // 移除: record.setMethodName("testMethod");
    record.setParams("{}");
    record.setStatus(status);
    record.setRetryCount(0);
    record.setMaxRetryCount(3);
    record.setRetryDelay(5000);
    record.setExecuteTime(LocalDateTime.now().minusHours(1));
    return record;
}
```

**(b) `testBackup` 方法 — 更新 INSERT SQL（行 91-99），移除 bean_name/method_name：**

```java
jdbcTemplate.update(
        "INSERT INTO reliable_invocation_record"
                + " (serial_no, scene, params, status,"
                + " retry_count, max_retry_count, retry_delay, create_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        "SN-BACKUP-" + i, SCENE, "{}",
        InvocationStatusEnum.SUCCESS.getCode(), 0, 3, 5000,
        Timestamp.valueOf(LocalDateTime.now().minusDays(10))
);
```

- [ ] **Step 5: 全量测试**

```bash
mvn -s D:\my\settings-tuanzuo.xml clean test
```
Expected: ALL TESTS PASS

- [ ] **Step 6: 提交**

```bash
git add .
git commit -m "refactor: migrate to handler-mode invocation, replace Jackson with fastjson"
```

---

### Task 10: 更新文档接口文档

**Files:**
- Modify: `src/main/java/com/tz/reliableinvoker/api/IReliableInvoker.java` (更新 Javadoc)

- [ ] **Step 1: 更新 IReliableInvoker 的 Javadoc**

将 `"根据请求中的方法签名和参数，通过反射（或代理）调用目标方法"` 更新为 `"根据请求中的 scene 定位 IInvocationHandler 并直接调用"`。

- [ ] **Step 2: 更新 IRetryService 的 Javadoc**

**类级别 Javadoc（行 7-10），将：**
```
 * 实现类需根据记录中的方法签名和参数重新发起调用，
```
**改为：**
```
 * 实现类通过 HandlerRegistry 查找对应场景的 Handler 并直接调用，
```

**方法级别 Javadoc（行 22），将：**
```
 * @param record 待重试的调用记录，必须包含有效的类名、方法名及参数
```
**改为：**
```
 * @param record 待重试的调用记录，包含场景名和参数 JSON
```

- [ ] **Step 3: 编译 + 测试确认**

```bash
mvn -s D:\my\settings-tuanzuo.xml clean test
```
Expected: ALL TESTS PASS

- [ ] **Step 4: 提交**

```bash
git add . && git commit -m "docs: update javadoc for handler-mode invocation"
```

---

### Task 11: 最终验证

- [ ] **Step 1: 清理 + 全量构建**

```bash
mvn -s D:\my\settings-tuanzuo.xml clean package
```
Expected: BUILD SUCCESS

- [ ] **Step 2: 确认输出 JAR**

```bash
ls target/*.jar
```
Expected: `target/reliable-invoker-spring-boot-starter-1.0.0-SNAPSHOT.jar`

- [ ] **Step 3: 查看变更摘要**

```bash
git diff --stat HEAD~1
```
确认所有变更文件符合预期。

---

### 变更文件清单

| 文件 | 操作 |
|------|------|
| `api/IInvocationHandler.java` | 新增 |
| `config/HandlerRegistry.java` | 新增 |
| `config/HandlerRegistryTest.java` | 新增 |
| `model/InvocationRequestTest.java` | 新增 |
| `service/impl/RetryServiceImplTest.java` | 新增 |
| `model/InvocationRequest.java` | 修改（移除 beanName/methodName） |
| `model/InvocationRecord.java` | 修改（移除 beanName/methodName） |
| `api/IReliableInvoker.java` | 修改（更新 Javadoc） |
| `service/IRetryService.java` | 修改（更新 Javadoc） |
| `api/impl/ReliableInvokerImpl.java` | 修改（fastjson + HandlerRegistry） |
| `service/impl/RetryServiceImpl.java` | 修改（Handler 模式） |
| `config/ReliableInvokerAutoConfiguration.java` | 修改（HandlerRegistry Bean） |
| `dao/impl/JdbcTemplateInvocationRecordDaoImpl.java` | 修改（更新 SQL） |
| `pom.xml` | 修改（Jackson → fastjson） |
| `src/test/resources/schema.sql` | 修改（移除 bean_name/method_name） |
| `api/impl/ReliableInvokerImplTest.java` | 修改（适配新 API） |
| `dao/impl/JdbcTemplateInvocationRecordDaoImplTest.java` | 修改（适配新 Schema） |
