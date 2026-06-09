# AGENTS.md

## 工作流程规范
- 使用Superpowers来进行开发过程的管理；请严格按照Brainstorming（头脑风暴），Writing Plans（任务拆解），Subagent Development（子代理开发），TDD（测试驱动开发），Code Review（代码审查），Finish Branch（完成）的过程执行，不允许跳过任何一个步骤；
- 代码分析时使用codegraph
- 每次改动需要有文档来记录，不允许任何没有文档记录的改动

## Build / Test / Lint Commands

本项目使用 Maven，自定义 settings.xml 路径，**所有 Maven 命令必须加上 `-s` 参数**。

```bash
# 编译（使用自定义 Maven settings）
mvn -s D:\my\settings-tuanzuo.xml clean compile

# 运行全部测试
mvn -s D:\my\settings-tuanzuo.xml test

# 运行单个测试类
mvn -s D:\my\settings-tuanzuo.xml test -Dtest=JdbcTemplateInvocationRecordDaoImplTest

# 运行单个测试方法
mvn -s D:\my\settings-tuanzuo.xml test -Dtest=JdbcTemplateInvocationRecordDaoImplTest#testSaveAndFindBySerialNo

# 打包（跳过测试）
mvn -s D:\my\settings-tuanzuo.xml clean package -DskipTests

# 打包（含测试）
mvn -s D:\my\settings-tuanzuo.xml clean package
```

## 技术栈

| 项目 | 版本 |
|------|------|
| Java | **1.8**（源码兼容 JDK 8，禁止使用 JDK 9+ API） |
| Spring Boot | 2.3.12.RELEASE |
| 持久层 | JdbcTemplate + NamedParameterJdbcTemplate |
| 序列化 | Jackson ObjectMapper |
| 测试 | JUnit 5 + Mockito + H2 内存数据库 |
| 构建 | Maven |

## 代码风格

### 包结构

```
com.tz.reliableinvoker
├── api/            # 对外 API 接口（I 前缀）及其实现（api/impl/）
├── service/        # 内部服务接口（I 前缀）及其实现（service/impl/）
├── dao/            # 数据访问接口（I 前缀）及其实现（dao/impl/）
├── model/          # 实体类、请求/查询对象
├── config/         # 自动配置、配置属性类
└── exception/      # 异常类
```

### 命名规范

- **接口**：`I` 前缀，如 `IReliableInvoker`、`IIinvocationRecordDao`
- **接口实现**：`Impl` 后缀，如 `ReliableInvokerImpl`、`RetryServiceImpl`
- **枚举**：`Enum` 后缀，如 `BusinessSceneEnum`
- **方法名**：驼峰命名，动词开头
- **字段名**：驼峰命名，不使用 `m_`、`_` 等前缀

### Javadoc 注释（强制）

所有 public 类、接口、方法、字段必须包含 Javadoc 注释，格式：

```java
/**
 * 类/方法描述
 * <p>补充说明</p>
 *
 * @author tuanzuo use AI
 * @time yyyy-MM-dd HH:mm:ss        # 文件创建时间（年月日时分秒）
 * @version 1.0.0-SNAPSHOT          # 项目版本号
 */
```

### 代码风格

- **this 前缀**：类内调用自身方法/字段统一使用 `this.` 前缀，如 `this.namedJdbcTemplate.query(...)`
- **全量 getter/setter**：POJO 和配置类不省略任何字段的 getter/setter
- **禁止使用 `var`**（JDK 10+ 特性）
- **禁止使用 `List.of()`**（JDK 9+ 特性），使用 `Arrays.asList()` 或 `Collections.singletonList()` 替代
- **禁止使用文本块 `"""`**（JDK 13+ 特性）
- **禁止使用 `switch` 表达式**（JDK 14+ 特性）
- **禁止使用 `record`**（JDK 14+ 特性）

### 导入规范

- 不使用通配符 `import ...*`（测试代码例外）
- import 按包路径字母序排列：`com.tz` → `org.springframework` → `java`
- 静态 import 放在最后

### 泛型枚举

对外 API 使用泛型枚举 `S extends Enum<S>`，使用方自定义枚举：

```java
// 使用方定义
public enum BusinessSceneEnum {
    ORDER_SCENE("订单场景");
    private final String description;
    // ...
}

// 调用
InvocationRequest<BusinessSceneEnum> req = InvocationRequest.<BusinessSceneEnum>builder()
    .scene(BusinessSceneEnum.ORDER_SCENE).build();
```

配置 YAML 的 key 对应枚举的 `name()` 值（如 `ORDER_SCENE`）。

### SQL / 表名

- 表名由 `resolveTableName(scene)` 动态解析：场景级 `table-name` → 全局 `table-name`
- SQL 字符串方法内 `String.format` 动态拼接，不作为类级别常量
- 包含分片查询条件：`MOD(id, :shardTotal) = :shardIndex`

### 配置优先级

```
调用级参数 > 场景级配置（Scenes.SCENE_NAME.xxx） > 全局默认配置
```

### 事务

- 记录插入参与当前 Spring 事务
- 有事务时：`afterCommit()` 中执行目标方法
- 无事务时：立即执行

### 错误处理

- 自定义异常统一继承 `ReliableInvokerException`
- 持久化错误：回滚事务，抛出异常
- 执行错误：更新记录状态（FAILED），抛出 `ExecutionException`
- 参数超限：抛出 `ParamsTooLargeException`（64KB 限制）

## Git

```bash
git remote -v   # origin  git@github.com:tuanzuo/reliable-invoker-spring-boot-starter.git
git branch      # main
```
