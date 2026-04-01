# AI页面生成后端项目全面分析文档

> 项目名称：ai-page-gen-backend
> 项目描述：基于Spring Boot 3.5.6和LangChain4j的AI驱动Web页面生成系统
> 技术栈：Java 21、Spring Boot 3.5.6、LangChain4j 1.1.0、MyBatis Flex 1.11.0
> 文档生成时间：2026-03-27

---

## 目录

1. [项目概述](#项目概述)
2. [技术架构](#技术架构)
3. [项目结构分析](#项目结构分析)
4. [核心模块详解](#核心模块详解)
5. [配置文件解析](#配置文件解析)
6. [数据模型设计](#数据模型设计)
7. [API接口设计](#api接口设计)
8. [设计模式应用](#设计模式应用)
9. [业务流程分析](#业务流程分析)
10. [部署与运维](#部署与运维)

---

## 项目概述

### 项目简介

这是一个**AI驱动的Web页面自动生成系统**，用户可以通过自然语言描述想要创建的网页，系统会自动调用大语言模型（默认使用DeepSeek）生成相应的HTML/CSS/JavaScript代码，并保存到文件系统中。

### 核心功能

1. **AI代码生成**：支持三种生成模式
   - 原生HTML模式：单文件HTML，内联CSS和JS
   - 多文件模式：分离的index.html、style.css、script.js
   - Vue工程模式：完整的Vue项目结构（计划中）

2. **用户管理**：完整的用户注册、登录、权限管理

3. **应用管理**：创建、编辑、删除、部署AI生成的应用

4. **对话历史**：记录用户与AI的交互历史

5. **限流保护**：基于Redisson的分布式限流

6. **监控统计**：AI模型调用的监控和指标收集

### 技术亮点

- **流式响应**：使用SSE（Server-Sent Events）实现实时代码生成
- **智能路由**：AI自动判断最适合的代码生成类型
- **工具调用**：为Vue项目生成准备的工具系统
- **多级缓存**：Caffeine本地缓存 + Redis分布式缓存
- **监控体系**：Prometheus指标收集 + 自定义监控

---

## 技术架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                         前端层                              │
│                    (Vue.js / React)                         │
└─────────────────────┬───────────────────────────────────────┘
                      │ HTTP/SSE
┌─────────────────────▼───────────────────────────────────────┐
│                      控制层 (Controller)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │AppController │  │UserController │  │ChatController│      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                      服务层 (Service)                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ AppService   │  │ UserService  │  │ChatHistory   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│  ┌──────────────────────────────────────────────────┐      │
│  │        AiCodeGeneratorFacade (门面模式)           │      │
│  └──────────────────────────────────────────────────┘      │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                     AI核心层 (AI Layer)                       │
│  ┌──────────────────────────────────────────────────┐      │
│  │   AiCodeGeneratorServiceFactory (工厂模式)        │      │
│  └──────────────────────────────────────────────────┘      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Parser     │  │    Saver     │  │    Builder   │      │
│  │  Executor    │  │  Executor    │  │              │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                   LangChain4j AI层                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ ChatModel    │  │ Streaming    │  │  ChatMemory  │      │
│  │              │  │ ChatModel    │  │              │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                    数据持久层                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   MySQL      │  │    Redis     │  │ File System  │      │
│  │(MyBatisFlex) │  │  (Session)   │  │(Code Output) │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 技术栈详解

#### 后端框架
- **Spring Boot 3.5.6**：最新版本，支持虚拟线程、观测性增强
- **Spring AOP**：用于权限校验、限流等横切关注点
- **Spring Session**：分布式会话管理

#### AI集成
- **LangChain4j 1.1.0**：Java AI编排框架
  - 结构化输出支持
  - 流式响应处理
  - 工具调用能力
  - 对话记忆管理
- **LangGraph4j 1.6.0-rc2**：状态机式AI工作流

#### 数据层
- **MyBatis Flex 1.11.0**：增强版MyBatis，支持代码生成
- **MySQL**：主数据库
- **Redis + Jedis**：缓存和会话存储
- **Redisson 3.50.0**：分布式限流

#### 工具库
- **Hutool 5.8.38**：Java工具类库
- **Knife4j 4.4.0**：API文档增强
- **Lombok 1.18.36**：代码简化
- **Caffeine**：本地缓存
- **Selenium**：网页截图

---

## 项目结构分析

### 目录结构

```
ai-page-gen-backend/
├── src/main/
│   ├── java/com/miu/codemain/
│   │   ├── ai/                          # AI集成模块
│   │   │   ├── model/                   # AI响应模型
│   │   │   │   ├── HtmlCodeResult.java
│   │   │   │   ├── MultiFileCodeResult.java
│   │   │   │   └── message/             # 流式消息类型
│   │   │   ├── tools/                   # AI工具系统
│   │   │   │   ├── BaseTool.java        # 工具基类
│   │   │   │   ├── ToolManager.java     # 工具管理器
│   │   │   │   ├── FileReadTool.java    # 文件读取工具
│   │   │   │   ├── FileWriteTool.java   # 文件写入工具
│   │   │   │   ├── FileModifyTool.java  # 文件修改工具
│   │   │   │   ├── FileDeleteTool.java  # 文件删除工具
│   │   │   │   ├── FileDirReadTool.java # 目录读取工具
│   │   │   │   └── ExitTool.java        # 退出工具
│   │   │   ├── AiCodeGeneratorService.java           # AI服务接口
│   │   │   ├── AiCodeGeneratorServiceFactory.java    # AI服务工厂
│   │   │   ├── AiCodeGenTypeRoutingService.java      # 类型路由服务
│   │   │   └── AiCodeGenTypeRoutingServiceFactory.java
│   │   │
│   │   ├── core/                        # 核心业务逻辑
│   │   │   ├── AiCodeGeneratorFacade.java    # 代码生成门面
│   │   │   ├── parser/                  # 代码解析器
│   │   │   │   ├── CodeParser.java
│   │   │   │   ├── CodeParserExecutor.java
│   │   │   │   ├── HtmlCodeParser.java
│   │   │   │   └── MultiFileCodeParser.java
│   │   │   ├── saver/                   # 代码保存器
│   │   │   │   ├── CodeFileSaver.java
│   │   │   │   ├── CodeFileSaverExecutor.java
│   │   │   │   ├── CodeFileSaverTemplate.java
│   │   │   │   ├── HtmlCodeFileSaverTemplate.java
│   │   │   │   └── MultiFileCodeFileSaverTemplate.java
│   │   │   ├── handler/                 # 流处理器
│   │   │   │   ├── StreamHandlerExecutor.java
│   │   │   │   ├── SimpleTextStreamHandler.java
│   │   │   │   └── JsonMessageStreamHandler.java
│   │   │   └── builder/                 # 项目构建器
│   │   │       └── VueProjectBuilder.java
│   │   │
│   │   ├── controller/                  # 控制器层
│   │   │   ├── AppController.java       # 应用控制器
│   │   │   ├── UserController.java      # 用户控制器
│   │   │   ├── ChatHistoryController.java
│   │   │   ├── StaticResourceController.java
│   │   │   └── HealthController.java
│   │   │
│   │   ├── service/                     # 服务层
│   │   │   ├── UserService.java
│   │   │   ├── impl/
│   │   │   │   └── UserServiceImpl.java
│   │   │   ├── AppService.java
│   │   │   ├── impl/
│   │   │   │   └── AppServiceImpl.java
│   │   │   ├── ChatHistoryService.java
│   │   │   └── impl/
│   │   │       └── ChatHistoryServiceImpl.java
│   │   │
│   │   ├── model/                       # 数据模型
│   │   │   ├── entity/                  # 实体类
│   │   │   │   ├── User.java
│   │   │   │   ├── App.java
│   │   │   │   └── ChatHistory.java
│   │   │   ├── dto/                     # 数据传输对象
│   │   │   │   ├── user/
│   │   │   │   ├── app/
│   │   │   │   └── chathistory/
│   │   │   ├── vo/                      # 视图对象
│   │   │   │   ├── UserVO.java
│   │   │   │   ├── AppVO.java
│   │   │   │   └── LoginUserVO.java
│   │   │   └── enums/                   # 枚举类
│   │   │       ├── UserRoleEnum.java
│   │   │       ├── CodeGenTypeEnum.java
│   │   │       └── ChatHistoryMessageTypeEnum.java
│   │   │
│   │   ├── mapper/                      # 数据访问层
│   │   │   ├── UserMapper.java
│   │   │   ├── AppMapper.java
│   │   │   └── ChatHistoryMapper.java
│   │   │
│   │   ├── common/                      # 公共组件
│   │   │   ├── BaseResponse.java        # 统一响应格式
│   │   │   ├── ResultUtils.java         # 响应工具类
│   │   │   ├── PageRequest.java         # 分页请求
│   │   │   └── DeleteRequest.java       # 删除请求
│   │   │
│   │   ├── exception/                   # 异常处理
│   │   │   ├── ErrorCode.java           # 错误码枚举
│   │   │   ├── BusinessException.java   # 业务异常
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── ThrowUtils.java          # 异常工具
│   │   │
│   │   ├── annotation/                  # 自定义注解
│   │   │   └── AuthCheck.java           # 权限校验注解
│   │   │
│   │   ├── aop/                         # 切面编程
│   │   │   └── AuthInterceptor.java     # 权限拦截器
│   │   │
│   │   ├── config/                      # 配置类
│   │   │   ├── CorsConfig.java          # 跨域配置
│   │   │   ├── JsonConfig.java          # JSON配置
│   │   │   ├── RedissonConfig.java      # Redisson配置
│   │   │   ├── RedisChatMemoryStoreConfig.java
│   │   │   ├── StreamingChatModelConfig.java
│   │   │   ├── RoutingAiModelConfig.java
│   │   │   ├── ReasoningStreamingChatModelConfig.java
│   │   │   └── CosClientConfig.java     # 腾讯云COS配置
│   │   │
│   │   ├── ratelimter/                  # 限流模块
│   │   │   ├── annotation/
│   │   │   │   └── RateLimit.java       # 限流注解
│   │   │   ├── aspect/
│   │   │   │   └── RateLimitAspect.java # 限流切面
│   │   │   └── enums/
│   │   │       └── RateLimitType.java   # 限流类型
│   │   │
│   │   ├── monitor/                     # 监控模块
│   │   │   ├── MonitorContext.java      # 监控上下文
│   │   │   ├── MonitorContextHolder.java
│   │   │   ├── AiModelMonitorListener.java
│   │   │   └── AiModelMetricsCollector.java
│   │   │
│   │   ├── utils/                       # 工具类
│   │   │   ├── CacheKeyUtils.java       # 缓存键工具
│   │   │   ├── SpringContextUtil.java   # Spring上下文工具
│   │   │   └── WebScreenshotUtils.java  # 网页截图工具
│   │   │
│   │   ├── constant/                    # 常量定义
│   │   │   ├── UserConstant.java
│   │   │   └── AppConstant.java
│   │   │
│   │   ├── generator/                   # 代码生成器
│   │   │   └── MyBatisCodeGenerator.java
│   │   │
│   │   └── AiPageGenBackendApplication.java  # 启动类
│   │
│   ├── resources/
│   │   ├── application.yml              # 主配置文件
│   │   ├── application-local.yml        # 本地环境配置
│   │   ├── prompt/                      # AI提示词模板
│   │   │   ├── codegen-html-system-prompt.txt
│   │   │   ├── codegen-multi-file-system-prompt.txt
│   │   │   ├── codegen-vue-project-system-prompt.txt
│   │   │   ├── code-quality-check-system-prompt.txt
│   │   │   └── codegen-routing-system-prompt.txt
│   │   └── mapper/                      # MyBatis映射文件
│   │       ├── UserMapper.xml
│   │       ├── AppMapper.xml
│   │       └── ChatHistoryMapper.xml
│   │
│   └── test/                            # 测试代码
│       └── java/com/miu/codemain/
│
├── sql/                                 # 数据库脚本
│   └── create_table.sql
│
├── tmp/                                 # 临时输出目录
│   ├── code_output/                     # AI生成代码输出
│   └── code_deploy/                     # 部署目录
│
├── pom.xml                              # Maven配置
├── CLAUDE.md                            # Claude Code项目说明
└── README.md                            # 项目说明
```

### 模块依赖关系

```
┌─────────────────────────────────────────────────────────┐
│                    Controller层                         │
│  依赖: Service, Common, Exception, Annotation           │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│                    Service层                            │
│  依赖: Mapper, Entity, AI, Core, Utils                  │
└─────────────────────┬───────────────────────────────────┘
                      │
         ┌────────────┴────────────┐
         │                         │
┌────────▼────────┐       ┌────────▼────────┐
│   AI模块        │       │   Core模块       │
│  - LangChain4j  │       │  - Parser        │
│  - 工具系统      │       │  - Saver         │
│  - 服务工厂      │       │  - Handler       │
└─────────────────┘       └─────────────────┘
         │                         │
         └────────────┬────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│              数据持久层 (Mapper + Entity)                │
│  依赖: MyBatis Flex, MySQL                               │
└─────────────────────────────────────────────────────────┘
```

---

## 核心模块详解

### 1. AI集成模块 (ai包)

#### 1.1 AiCodeGeneratorService.java
**作用**：定义AI代码生成服务的接口

**核心方法**：
```java
// 生成HTML代码
@SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
HtmlCodeResult generateHtmlCode(String userMessage);

// 生成多文件代码
@SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
MultiFileCodeResult generateMultiFileCode(String userMessage);

// 流式生成HTML代码
Flux<String> generateHtmlCodeStream(String userMessage);

// 流式生成Vue项目（带记忆）
TokenStream generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);
```

**设计要点**：
- 使用LangChain4j的注解驱动方式定义AI服务
- `@SystemMessage`注解从资源文件加载系统提示词
- 支持同步和流式两种调用方式
- Vue项目生成使用`@MemoryId`实现对话记忆

#### 1.2 AiCodeGeneratorServiceFactory.java
**作用**：AI服务实例工厂，负责创建和管理AI服务实例

**核心功能**：
1. **服务实例管理**：
   - 为每个appId创建独立的AI服务实例
   - 集成Redis聊天记忆存储
   - 注册工具系统

2. **缓存策略**（已注释）：
   ```java
   private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
       .maximumSize(1000)              // 最多缓存1000个实例
       .expireAfterWrite(Duration.ofMinutes(30))   // 写入30分钟后过期
       .expireAfterAccess(Duration.ofMinutes(10))  // 访问10分钟后过期
       .build();
   ```

3. **差异化创建**：
   - Vue项目：使用推理模型 + 工具调用
   - HTML/多文件：使用普通流式模型

**设计模式**：工厂模式 + 单例模式（每个appId对应一个服务实例）

#### 1.3 工具系统 (tools包)

**BaseTool.java** - 工具基类
```java
public abstract class BaseTool {
    // 获取工具英文名称（对应方法名）
    public abstract String getToolName();

    // 获取工具中文显示名称
    public abstract String getDisplayName();

    // 生成工具请求响应
    public String generateToolRequestResponse();

    // 生成工具执行结果
    public abstract String generateToolExecutedResult(JSONObject arguments);
}
```

**具体工具实现**：
1. **FileReadTool**：读取文件内容
2. **FileWriteTool**：写入文件
3. **FileModifyTool**：修改文件
4. **FileDeleteTool**：删除文件
5. **FileDirReadTool**：读取目录结构
6. **ExitTool**：退出工具调用

**ToolManager.java** - 工具管理器
- 自动扫描所有`BaseTool`子类
- 通过Spring依赖注入收集工具
- 提供根据名称获取工具的功能

**工具调用流程**：
```
AI决策 → 工具请求 → 工具执行 → 结果返回 → AI继续处理
```

### 2. 核心业务模块 (core包)

#### 2.1 AiCodeGeneratorFacade.java
**作用**：门面模式，统一代码生成入口

**核心方法**：
```java
// 同步生成并保存代码
public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId)

// 流式生成并保存代码
public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId)
```

**执行流程**：
1. 获取对应的AI服务实例
2. 根据生成类型调用不同的生成方法
3. 解析AI输出
4. 保存代码到文件系统

**设计模式**：门面模式

#### 2.2 代码解析器 (parser包)

**CodeParser.java** - 解析器接口
```java
public interface CodeParser {
    Object parseCode(String codeContent);
}
```

**HtmlCodeParser.java** - HTML代码解析器
- 从JSON格式的AI响应中提取HTML代码
- 处理markdown代码块包裹

**MultiFileCodeParser.java** - 多文件代码解析器
- 解析多文件项目的JSON结构
- 提取index.html、style.css、script.js

**CodeParserExecutor.java** - 解析器执行器
- 根据代码类型选择对应的解析器
- 使用策略模式实现

**设计模式**：策略模式

#### 2.3 代码保存器 (saver包)

**CodeFileSaver.java** - 保存器接口
```java
public interface CodeFileSaver {
    File saveCode(Object codeResult, Long appId);
}
```

**CodeFileSaverTemplate.java** - 保存器模板类
- 定义保存流程骨架
- 实现通用逻辑（目录创建、时间戳等）

**HtmlCodeFileSaverTemplate.java** - HTML保存器
- 保存单个HTML文件

**MultiFileCodeFileSaverTemplate.java** - 多文件保存器
- 保存多个文件（html/css/js）
- 创建项目目录结构

**CodeFileSaverExecutor.java** - 保存器执行器
- 根据类型选择保存器
- 统一保存入口

**设计模式**：模板方法模式 + 策略模式

#### 2.4 流处理器 (handler包)

**StreamHandlerExecutor.java** - 流处理器执行器
```java
public Flux<String> doExecute(Flux<String> originFlux,
                              ChatHistoryService chatHistoryService,
                              long appId, User loginUser,
                              CodeGenTypeEnum codeGenType)
```

**SimpleTextStreamHandler.java** - 简单文本处理器
- 处理传统的Flux<String>流
- 适用于HTML和多文件生成

**JsonMessageStreamHandler.java** - JSON消息处理器
- 处理TokenStream格式的复杂流
- 支持工具调用消息
- 适用于Vue项目生成

**设计模式**：策略模式

### 3. 控制器模块 (controller包)

#### AppController.java - 应用控制器

**核心接口**：

1. **AI代码生成（流式）**：
```java
@GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60)
public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                   @RequestParam String message,
                                                   HttpServletRequest request)
```
- 使用SSE流式返回生成结果
- 限流：每用户每分钟最多5次请求

2. **创建应用**：
```java
@PostMapping("/add")
public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest,
                                  HttpServletRequest request)
```
- 创建新的AI应用
- AI自动选择代码生成类型

3. **更新应用**：
```java
@PostMapping("/update")
public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest,
                                        HttpServletRequest request)
```
- 用户只能更新自己的应用名称

4. **删除应用**：
```java
@PostMapping("/delete")
public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest,
                                        HttpServletRequest request)
```
- 用户只能删除自己的应用

5. **查询应用**：
```java
@GetMapping("/get/vo")
public BaseResponse<AppVO> getAppVOById(long id)

@PostMapping("/my/list/page/vo")
public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest,
                                                     HttpServletRequest request)
```
- 支持单个查询和分页查询
- 支持按用户筛选

6. **管理员接口**：
```java
@PostMapping("/admin/delete")
@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest)

@PostMapping("/admin/update")
@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest)
```
- 使用`@AuthCheck`注解进行权限校验

#### UserController.java - 用户控制器

**核心接口**：
1. 用户注册
2. 用户登录
3. 用户信息更新
4. 获取当前登录用户
5. 用户列表（管理员）

### 4. 服务模块 (service包)

#### AppServiceImpl.java - 应用服务实现

**核心方法**：

1. **chatToGenCode** - AI对话生成代码
```java
@Override
public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
    // 1. 参数校验
    // 2. 查询应用信息
    // 3. 权限校验
    // 4. 获取代码生成类型
    // 5. 保存用户消息到历史
    // 6. 设置监控上下文
    // 7. 调用AI生成代码
    // 8. 处理流式响应并保存历史
}
```

2. **createApp** - 创建应用
```java
@Override
public Long createApp(AppAddRequest appAddRequest, User loginUser) {
    // 1. 参数校验
    // 2. 构造应用对象
    // 3. 使用AI智能选择代码生成类型
    // 4. 保存到数据库
}
```

3. **deployApp** - 部署应用
```java
@Override
public String deployApp(Long appId, User loginUser) {
    // 1. 参数校验
    // 2. 查询应用信息
    // 3. 权限校验
    // 4. 生成或获取deployKey
    // 5. 获取源代码路径
    // 6. Vue项目特殊处理（构建）
    // 7. 复制到部署目录
    // 8. 更新数据库
    // 9. 返回访问URL
    // 10. 异步生成截图
}
```

4. **getAppVO** - 获取应用视图对象
- 关联查询用户信息
- 避免N+1查询问题

**设计要点**：
- 使用批量查询优化性能
- 完善的权限校验
- 流式处理AI响应

### 5. 数据模型模块 (model包)

#### 实体类 (entity包)

**App.java** - 应用实体
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app")
public class App implements Serializable {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;                    // 雪花ID

    private String appName;             // 应用名称
    private String cover;               // 应用封面
    private String initPrompt;          // 初始化提示词
    private String codeGenType;         // 代码生成类型
    private String deployKey;           // 部署标识
    private LocalDateTime deployedTime; // 部署时间
    private Integer priority;           // 优先级
    private Long userId;                // 创建用户ID
    private LocalDateTime editTime;     // 编辑时间
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
    private Integer isDelete;           // 是否删除
}
```

**User.java** - 用户实体
- id：用户ID
- userAccount：账号
- userPassword：密码
- userName：昵称
- userAvatar：头像
- userProfile：简介
- userRole：角色（user/admin）
- 时间戳字段

**ChatHistory.java** - 对话历史实体
- id：消息ID
- message：消息内容
- messageType：消息类型（user/ai）
- appId：应用ID
- userId：用户ID
- 时间戳字段

#### 枚举类 (enums包)

**CodeGenTypeEnum** - 代码生成类型
```java
public enum CodeGenTypeEnum {
    HTML("原生 HTML 模式", "html"),
    MULTI_FILE("原生多文件模式", "multi_file"),
    VUE_PROJECT("Vue 工程模式", "vue_project");
}
```

**UserRoleEnum** - 用户角色
- USER：普通用户
- ADMIN：管理员

**ChatHistoryMessageTypeEnum** - 消息类型
- USER：用户消息
- AI：AI响应

### 6. 配置模块 (config包)

#### CorsConfig.java - 跨域配置
```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowCredentials(true)      // 允许携带Cookie
                .allowedOriginPatterns("*")  // 允许所有域名
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }
}
```

#### JsonConfig.java - JSON配置
```java
@Bean
public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
    ObjectMapper objectMapper = builder.createXmlMapper(false).build();
    SimpleModule module = new SimpleModule();
    // 解决Long类型转JSON精度丢失问题
    module.addSerializer(Long.class, ToStringSerializer.instance);
    module.addSerializer(Long.TYPE, ToStringSerializer.instance);
    objectMapper.registerModule(module);
    return objectMapper;
}
```

### 7. 限流模块 (ratelimter包)

#### RateLimit.java - 限流注解
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    RateLimitType limitType();  // 限流类型
    int rate();                  // 每个时间窗口的请求数
    int rateInterval();          // 时间窗口（秒）
    String message() default "请求过于频繁，请稍后再试";
    String key() default "";     // 自定义前缀
}
```

#### RateLimitType.java - 限流类型
```java
public enum RateLimitType {
    API,    // 接口级别限流
    USER,   // 用户级别限流
    IP      // IP级别限流
}
```

#### RateLimitAspect.java - 限流切面
- 使用Redisson实现分布式限流
- 支持多种限流策略
- 自动提取用户ID或IP

### 8. 监控模块 (monitor包)

#### AiModelMonitorListener.java - AI模型监听器
```java
@Component
public class AiModelMonitorListener implements ChatModelListener {
    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 记录请求开始
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        // 记录响应成功、响应时间、Token使用
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // 记录错误
    }
}
```

**监控指标**：
1. 请求计数
2. 响应时间
3. Token使用量（输入/输出/总计）
4. 错误率

---

## 配置文件解析

### application.yml - 主配置文件

```yaml
spring:
  application:
    name: ai-page-gen-backend

  # Session配置
  session:
    store-type: redis              # 使用Redis存储Session
    timeout: 2592000               # 30天过期

  # 数据源配置
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/ai_page_gen
    username: root
    password: 123456

  # Redis配置
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      password:
      ttl: 3600                    # 缓存1小时过期

  profiles:
    active: local                  # 激活本地环境配置

server:
  port: 8123
  servlet:
    context-path: /api             # API上下文路径

# API文档配置
springdoc:
  group-configs:
    - group: 'default'
      paths-to-match: "/**"
      packages-to-scan: com.miu.codemain.controller

knife4j:
  enable: true
  setting:
    language: zh_cn                # 中文界面

# LangChain4j AI配置
langchain4j:
  open-ai:
    chat-model:
      base-url: https://api.deepseek.com
      api-key: <Your API Key>
      model-name: deepseek-chat
      response-format: json_object  # JSON格式输出
      max-tokens: 8192
      log-requests: true
      log-responses: true
      max-retries: 3
    streaming-chat-model:
      base-url: https://api.deepseek.com
      api-key: <Your API Key>
      model-name: deepseek-chat
      max-tokens: 8192
```

### pom.xml - Maven依赖配置

**核心依赖**：

1. **Spring Boot**
   - spring-boot-starter-web：Web模块
   - spring-boot-starter-aop：AOP支持

2. **数据存储**
   - mybatis-flex-spring-boot3-starter：ORM框架
   - mysql-connector-j：MySQL驱动
   - spring-session-data-redis：Session存储
   - redisson：分布式功能

3. **AI集成**
   - langchain4j：AI编排框架
   - langchain4j-open-ai-spring-boot-starter：OpenAI兼容接口
   - langchain4j-reactor：响应式支持
   - langgraph4j-core：状态机工作流

4. **工具库**
   - hutool-all：Java工具集
   - lombok：代码简化
   - knife4j-openapi3-jakarta-spring-boot-starter：API文档

5. **其他**
   - selenium-java：网页截图
   - cos_api：腾讯云对象存储
   - caffeine：本地缓存

---

## 数据模型设计

### 数据库表结构

#### user - 用户表
```sql
CREATE TABLE user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    userAccount  VARCHAR(256) NOT NULL COMMENT '账号',
    userPassword VARCHAR(512) NOT NULL COMMENT '密码',
    userName     VARCHAR(256) COMMENT '用户昵称',
    userAvatar   VARCHAR(1024) COMMENT '用户头像',
    userProfile  VARCHAR(512) COMMENT '用户简介',
    userRole     VARCHAR(256) DEFAULT 'user' NOT NULL COMMENT '用户角色',
    editTime     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
    createTime   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete     TINYINT DEFAULT 0 NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) COMMENT '用户';
```

**设计要点**：
- userAccount唯一索引：确保账号不重复
- userName普通索引：优化按名称查询
- 逻辑删除：使用isDelete字段

#### app - 应用表
```sql
CREATE TABLE app (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    appName      VARCHAR(256) COMMENT '应用名称',
    cover        VARCHAR(512) COMMENT '应用封面',
    initPrompt   TEXT COMMENT '应用初始化prompt',
    codeGenType  VARCHAR(64) COMMENT '代码生成类型',
    deployKey    VARCHAR(64) COMMENT '部署标识',
    deployedTime DATETIME COMMENT '部署时间',
    priority     INT DEFAULT 0 NOT NULL COMMENT '优先级',
    userId       BIGINT NOT NULL COMMENT '创建用户id',
    editTime     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
    createTime   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete     TINYINT DEFAULT 0 NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_deployKey (deployKey),
    INDEX idx_appName (appName),
    INDEX idx_userId (userId)
) COMMENT '应用';
```

**设计要点**：
- deployKey唯一索引：确保部署标识不重复
- userId关联用户：外键关系（逻辑上）
- priority字段：用于精选应用排序

#### chat_history - 对话历史表
```sql
CREATE TABLE chat_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    message     TEXT NOT NULL COMMENT '消息',
    messageType VARCHAR(32) NOT NULL COMMENT 'user/ai',
    appId       BIGINT NOT NULL COMMENT '应用id',
    userId      BIGINT NOT NULL COMMENT '创建用户id',
    createTime  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete    TINYINT DEFAULT 0 NOT NULL COMMENT '是否删除',
    INDEX idx_appId (appId),
    INDEX idx_createTime (createTime),
    INDEX idx_appId_createTime (appId, createTime)  -- 游标查询核心索引
) COMMENT '对话历史';
```

**设计要点**：
- 复合索引(appId, createTime)：优化分页查询
- message使用TEXT类型：支持长消息
- messageType区分消息来源

### ER图

```
┌─────────────┐         ┌─────────────┐         ┌─────────────────┐
│    user     │         │     app     │         │  chat_history   │
├─────────────┤         ├─────────────┤         ├─────────────────┤
│ id (PK)     │◄────────│ userId (FK) │◄────────│ appId (FK)      │
│ userAccount │         │ id (PK)     │         │ id (PK)         │
│ userName    │         │ appName     │         │ message         │
│ userRole    │         │ codeGenType │         │ messageType     │
│ ...         │         │ ...         │         │ userId (FK)     │
└─────────────┘         └─────────────┘         │ createTime      │
                                                │ ...             │
                                                └─────────────────┘
```

---

## API接口设计

### 接口列表

#### 用户相关接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/user/register | 用户注册 | 公开 |
| POST | /api/user/login | 用户登录 | 公开 |
| GET | /api/user/get/login | 获取当前登录用户 | 登录 |
| GET | /api/user/get/vo | 根据ID获取用户 | 登录 |
| POST | /api/user/update | 更新用户信息 | 登录 |
| POST | /api/user/list/page/vo | 分页获取用户列表 | 管理员 |

#### 应用相关接口

| 方法 | 路径 | 说明 | 权限 | 限流 |
|------|------|------|------|------|
| GET | /api/app/chat/gen/code | AI对话生成代码 | 登录 | 5次/分钟 |
| POST | /api/app/add | 创建应用 | 登录 | - |
| POST | /api/app/update | 更新应用 | 所有者 | - |
| POST | /api/app/delete | 删除应用 | 所有者/管理员 | - |
| GET | /api/app/get/vo | 获取应用详情 | 公开 | - |
| POST | /api/app/my/list/page/vo | 我的应用列表 | 登录 | - |
| POST | /api/app/good/list/page/vo | 精选应用列表 | 公开 | - |
| POST | /api/app/admin/delete | 管理员删除应用 | 管理员 | - |
| POST | /api/app/admin/update | 管理员更新应用 | 管理员 | - |
| POST | /api/app/admin/list/page/vo | 管理员应用列表 | 管理员 | - |

#### 对话历史接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/chat/history/list/page | 分页获取对话历史 | 应用所有者 |
| POST | /api/chat/history/clear | 清空对话历史 | 应用所有者 |

### 响应格式

**统一响应结构**：
```json
{
    "code": 0,           // 状态码，0表示成功
    "data": {},          // 响应数据
    "message": "ok"      // 提示信息
}
```

**错误码定义**：
```java
SUCCESS(0, "ok")
PARAMS_ERROR(40000, "请求参数错误")
NOT_LOGIN_ERROR(40100, "未登录")
NO_AUTH_ERROR(40101, "无权限")
TOO_MANY_REQUEST(42900, "请求过于频繁")
NOT_FOUND_ERROR(40400, "请求数据不存在")
FORBIDDEN_ERROR(40300, "禁止访问")
SYSTEM_ERROR(50000, "系统内部异常")
OPERATION_ERROR(50001, "操作失败")
```

### SSE流式响应格式

**AI代码生成接口**返回格式：
```
event: message
data: {"d": "生成的代码片段"}

event: message
data: {"d": "更多代码片段"}

event: done
data: {}
```

**错误事件格式**：
```
event: business-error
data: {"error": true, "code": 40000, "message": "错误信息"}

event: done
data: {}
```

---

## 设计模式应用

### 1. 门面模式 (Facade Pattern)

**应用场景**：`AiCodeGeneratorFacade`

**作用**：为复杂的子系统提供简化接口

```java
@Service
public class AiCodeGeneratorFacade {
    // 隐藏了AI服务获取、代码解析、代码保存等复杂细节
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        AiCodeGeneratorService service = factory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        Object result = service.generateXXX(userMessage);
        Object parsed = parser.parse(result);
        return saver.save(parsed);
    }
}
```

### 2. 工厂模式 (Factory Pattern)

**应用场景**：`AiCodeGeneratorServiceFactory`

**作用**：根据不同条件创建不同类型的AI服务

```java
public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
    return switch (codeGenType) {
        case VUE_PROJECT -> createVueProjectService();
        case HTML, MULTI_FILE -> createBasicService();
    };
}
```

### 3. 策略模式 (Strategy Pattern)

**应用场景**：代码解析器和保存器

**作用**：定义一系列算法，封装起来，使它们可以互相替换

```java
// 解析器策略
public interface CodeParser {
    Object parseCode(String codeContent);
}

// 具体策略
public class HtmlCodeParser implements CodeParser { ... }
public class MultiFileCodeParser implements CodeParser { ... }

// 执行器
public class CodeParserExecutor {
    public static Object executeParser(String codeContent, CodeGenTypeEnum type) {
        return switch (type) {
            case HTML -> htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
        };
    }
}
```

### 4. 模板方法模式 (Template Method Pattern)

**应用场景**：`CodeFileSaverTemplate`

**作用**：定义算法骨架，将部分步骤延迟到子类实现

```java
public abstract class CodeFileSaverTemplate implements CodeFileSaver {
    // 模板方法：定义保存流程骨架
    @Override
    public File saveCode(Object codeResult, Long appId) {
        // 1. 创建目录
        File outputDir = createOutputDir(appId);
        // 2. 保存代码（由子类实现）
        doSaveCode(codeResult, outputDir);
        // 3. 返回目录
        return outputDir;
    }

    // 钩子方法：由子类实现具体保存逻辑
    protected abstract void doSaveCode(Object codeResult, File outputDir);
}
```

### 5. 单例模式 (Singleton Pattern)

**应用场景**：工具管理器

**作用**：确保一个类只有一个实例

```java
@Component
public class ToolManager {
    private final Map<String, BaseTool> toolMap = new HashMap<>();

    @PostConstruct
    public void initTools() {
        // 初始化工具注册表
    }

    public BaseTool getTool(String toolName) {
        return toolMap.get(toolName);
    }
}
```

### 6. 观察者模式 (Observer Pattern)

**应用场景**：AI模型监听器

**作用**：当对象状态发生变化时，通知所有依赖它的对象

```java
@Component
public class AiModelMonitorListener implements ChatModelListener {
    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 请求开始时触发
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        // 响应返回时触发
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // 发生错误时触发
    }
}
```

### 7. AOP代理模式

**应用场景**：权限拦截、限流

**作用**：在不修改原有代码的情况下，为程序添加额外功能

```java
@Aspect
@Component
public class AuthInterceptor {
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) {
        // 权限校验逻辑
        if (!hasPermission()) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }
}
```

---

## 业务流程分析

### 1. AI代码生成流程

```
┌──────────────┐
│ 用户发起请求  │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────┐
│ 1. AppController.chatToGenCode() │
│    - 参数校验                      │
│    - 获取登录用户                  │
│    - 应用限流检查                  │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 2. AppService.chatToGenCode()    │
│    - 查询应用信息                  │
│    - 权限校验（仅所有者）           │
│    - 获取代码生成类型              │
│    - 保存用户消息                  │
│    - 设置监控上下文                │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 3. AiCodeGeneratorFacade        │
│    generateAndSaveCodeStream()   │
│    - 获取AI服务实例                │
│    - 调用流式生成方法              │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 4. LangChain4j AI服务           │
│    - 加载系统提示词                │
│    - 调用LLM API                  │
│    - 流式返回响应                  │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 5. StreamHandlerExecutor        │
│    doExecute()                   │
│    - 处理流式响应                  │
│    - 收集完整代码                  │
│    - 保存对话历史                  │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 6. CodeParserExecutor           │
│    executeParser()               │
│    - 解析JSON格式代码              │
│    - 提取HTML/CSS/JS              │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 7. CodeFileSaverExecutor        │
│    executeSaver()                │
│    - 创建输出目录                  │
│    - 保存代码文件                  │
│    - 返回目录路径                  │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 8. SSE流式返回给前端             │
│    - 实时推送代码片段              │
│    - 发送完成事件                  │
└──────────────────────────────────┘
```

### 2. 应用创建流程

```
┌──────────────┐
│ 用户创建应用  │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────┐
│ 1. AppController.addApp()       │
│    - 接收创建请求                  │
│    - 获取登录用户                  │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 2. AppService.createApp()       │
│    - 参数校验                      │
│    - 构造应用对象                  │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 3. AiCodeGenTypeRoutingService │
│    routeCodeGenType()            │
│    - AI分析用户需求                │
│    - 智能选择生成类型              │
│    - 返回HTML/MULTI_FILE/VUE     │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 4. 保存到数据库                  │
│    - 生成应用ID                    │
│    - 设置初始状态                  │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────┐
│ 返回应用ID    │
└──────────────┘
```

### 3. 应用部署流程

```
┌──────────────┐
│ 用户部署应用  │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────┐
│ 1. 参数和权限校验                 │
│    - 应用是否存在                  │
│    - 是否为应用所有者               │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 2. 生成/获取deployKey            │
│    - 6位随机字符串                 │
│    - 确保唯一性                    │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 3. 获取源代码路径                 │
│    - codeGenType + appId          │
│    - 检查目录是否存在              │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 4. Vue项目特殊处理               │
│    - 执行npm run build            │
│    - 使用dist目录                  │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 5. 复制到部署目录                 │
│    - CODE_DEPLOY_ROOT_DIR/deployKey│
│    - 递归复制所有文件              │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 6. 更新数据库                    │
│    - 保存deployKey                │
│    - 记录部署时间                  │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 7. 异步生成截图                  │
│    - Selenium访问应用URL          │
│    - 截取页面图片                  │
│    - 上传到对象存储                │
│    - 更新应用封面                  │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────┐
│ 返回访问URL   │
└──────────────┘
```

### 4. 用户登录流程

```
┌──────────────┐
│ 用户提交登录  │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────┐
│ 1. UserController.userLogin()   │
│    - 接收账号密码                  │
│    - 参数校验                      │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 2. UserService.doLogin()        │
│    - 根据账号查询用户              │
│    - 验证密码（加密比较）           │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ 3. 生成用户会话                  │
│    - 生成登录令牌                  │
│    - 存储到Session/Redis           │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────┐
│ 返回登录用户信息│
└──────────────┘
```

---

## 部署与运维

### 环境要求

**运行环境**：
- Java 21+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

**系统配置**：
- 内存：至少2GB
- 磁盘：至少10GB可用空间
- 网络：可访问DeepSeek API

### 本地开发环境搭建

1. **克隆项目**
```bash
git clone <repository-url>
cd ai-page-gen-backend
```

2. **配置数据库**
```bash
# 创建数据库
mysql -u root -p < sql/create_table.sql
```

3. **修改配置**
```yaml
# application.yml
spring.datasource.url: jdbc:mysql://localhost:3306/ai_page_gen
spring.datasource.username: your_username
spring.datasource.password: your_password
langchain4j.open-ai.chat-model.api-key: your_deepseek_api_key
```

4. **启动Redis**
```bash
redis-server
```

5. **编译运行**
```bash
mvn clean compile
mvn spring-boot:run
```

6. **访问应用**
- API地址：http://localhost:8123/api
- API文档：http://localhost:8123/api/doc.html

### 生产环境部署

1. **打包应用**
```bash
mvn clean package -DskipTests
```

2. **配置JVM参数**
```bash
java -Xms1g -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar ai-page-gen-backend.jar
```

3. **使用Docker部署**
```dockerfile
FROM openjdk:21-slim
WORKDIR /app
COPY target/ai-page-gen-backend.jar app.jar
EXPOSE 8123
ENTRYPOINT ["java", "-jar", "app.jar"]
```

4. **Nginx反向代理**
```nginx
server {
    listen 80;
    server_name your-domain.com;

    location /api {
        proxy_pass http://localhost:8123;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        # SSE支持
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
    }
}
```

### 监控配置

**Prometheus指标**：
- JVM内存使用
- 请求响应时间
- AI Token消耗
- 错误率统计

**健康检查**：
```bash
curl http://localhost:8123/api/health
```

### 日志管理

**日志配置**（logback-spring.xml）：
- 控制台输出：开发环境
- 文件输出：生产环境
- 日志级别：INFO/WARN/ERROR

**日志位置**：
- 应用日志：logs/app.log
- AI调用日志：logs/ai.log
- 错误日志：logs/error.log

### 性能优化建议

1. **数据库优化**
   - 添加合适的索引
   - 使用连接池（HikariCP）
   - 开启查询缓存

2. **Redis优化**
   - 设置合理的过期时间
   - 使用连接池
   - 开启持久化

3. **JVM优化**
   - 调整堆内存大小
   - 选择合适的垃圾回收器
   - 开启JIT编译优化

4. **应用优化**
   - 使用本地缓存（Caffeine）
   - 异步处理耗时操作
   - 批量查询减少N+1问题

---

## 总结

本项目是一个设计精良的AI驱动Web页面生成系统，具有以下特点：

### 技术优势

1. **现代化技术栈**：Spring Boot 3.5.6 + Java 21 + LangChain4j
2. **流式响应**：SSE实时推送AI生成结果
3. **智能路由**：AI自动判断最适合的代码生成类型
4. **工具系统**：为复杂项目生成准备工具调用能力
5. **完善监控**：全链路监控AI调用指标
6. **分布式支持**：Redis Session + Redisson限流

### 架构优势

1. **分层清晰**：Controller → Service → Core → AI
2. **职责分离**：解析、保存、处理各司其职
3. **设计模式**：门面、工厂、策略、模板方法等
4. **可扩展性**：新增生成类型只需实现对应接口

### 业务价值

1. **降低开发门槛**：自然语言描述即可生成网页
2. **多种生成模式**：满足不同复杂度需求
3. **一键部署**：自动生成部署URL
4. **对话历史**：支持迭代优化

### 未来优化方向

1. 完善Vue项目生成功能
2. 支持更多前端框架（React、Angular）
3. 增加代码质量检查
4. 实现代码版本管理
5. 支持团队协作功能

---

> **文档版本**：v1.0
> **更新日期**：2026-03-27
> **作者**：Claude Code
> **项目地址**：[github-repo-url]
