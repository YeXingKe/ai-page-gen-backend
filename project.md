# AI页面生成后端项目全面分析文档

> 项目名称：ai-page-gen-backend
> 项目描述：基于Spring Boot 3.5.6和LangChain4j的AI驱动Web页面生成系统
> 技术栈：Java 21、Spring Boot 3.5.6、LangChain4j 1.1.0、MyBatis Flex 1.11.1
> 文档生成时间：2026-03-27
> 最后更新：2026-04-03

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
11. [文件完整清单与用途说明](#文件完整清单与用途说明)

---

## 项目概述

### 项目简介

这是一个**AI驱动的Web页面自动生成系统**，用户可以通过自然语言描述想要创建的网页，系统会自动调用大语言模型（默认使用DeepSeek）生成相应的HTML/CSS/JavaScript代码，并保存到文件系统中。

### 核心功能

1. **AI代码生成**：支持三种生成模式
   - 原生HTML模式：单文件HTML，内联CSS和JS
   - 多文件模式：分离的index.html、style.css、script.js
   - Vue工程模式：完整的Vue项目结构

2. **用户管理**：完整的用户注册、登录、权限管理

3. **应用管理**：创建、编辑、删除、部署AI生成的应用

4. **对话历史**：记录用户与AI的交互历史

5. **限流保护**：基于Redisson的分布式限流

6. **监控统计**：AI模型调用的监控和指标收集

7. **安全防护**：AI输入输出护轨，防止恶意输入和敏感信息泄露

### 技术亮点

- **流式响应**：使用SSE（Server-Sent Events）实现实时代码生成
- **智能路由**：AI自动判断最适合的代码生成类型
- **工具调用**：为Vue项目生成准备的工具系统
- **多级缓存**：Caffeine本地缓存 + Redis分布式缓存
- **监控体系**：Prometheus指标收集 + 自定义监控
- **AI护轨**：输入安全审查和输出内容验证

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
│  ┌──────────────────────────────────────────────────┐      │
│  │    AI Guardrails (输入/输出护轨)                   │      │
│  └──────────────────────────────────────────────────┘      │
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
  - 输入输出护轨（Guardrails）
- **阿里云 DashScope SDK 2.21.1**：通义千问等阿里云大模型接入

#### 数据层
- **MyBatis Flex 1.11.1**：增强版MyBatis，支持代码生成
- **MySQL**：主数据库
- **Redis + Jedis**：缓存和会话存储
- **Redisson 3.50.0**：分布式限流

#### 工具库
- **Hutool 5.8.38**：Java工具类库
- **Knife4j 4.4.0**：API文档增强
- **Lombok 1.18.36**：代码简化
- **Caffeine**：本地缓存
- **Selenium**：网页截图
- **腾讯云 COS 5.6.227**：对象存储（可选）

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
│   │   │   │       ├── StreamMessage.java
│   │   │   │       ├── StreamMessageTypeEnum.java
│   │   │   │       ├── AiResponseMessage.java
│   │   │   │       ├── ToolRequestMessage.java
│   │   │   │       └── ToolExecutedMessage.java
│   │   │   ├── guardrail/               # AI安全护轨
│   │   │   │   ├── PromptSafetyInputGuardrail.java
│   │   │   │   └── RetryOutputGuardrail.java
│   │   │   ├── tools/                   # AI工具系统
│   │   │   │   ├── BaseTool.java
│   │   │   │   ├── ToolManager.java
│   │   │   │   ├── FileReadTool.java
│   │   │   │   ├── FileWriteTool.java
│   │   │   │   ├── FileModifyTool.java
│   │   │   │   ├── FileDeleteTool.java
│   │   │   │   ├── FileDirReadTool.java
│   │   │   │   └── ExitTool.java
│   │   │   ├── AiCodeGeneratorService.java
│   │   │   ├── AiCodeGeneratorServiceFactory.java
│   │   │   ├── AiCodeGenTypeRoutingService.java
│   │   │   └── AiCodeGenTypeRoutingServiceFactory.java
│   │   │
│   │   ├── core/                        # 核心业务逻辑
│   │   │   ├── AiCodeGeneratorFacade.java
│   │   │   ├── parser/
│   │   │   │   ├── CodeParser.java
│   │   │   │   ├── CodeParserExecutor.java
│   │   │   │   ├── HtmlCodeParser.java
│   │   │   │   └── MultiFileCodeParser.java
│   │   │   ├── saver/
│   │   │   │   ├── CodeFileSaver.java
│   │   │   │   ├── CodeFileSaverExecutor.java
│   │   │   │   ├── CodeFileSaverTemplate.java
│   │   │   │   ├── HtmlCodeFileSaverTemplate.java
│   │   │   │   └── MultiFileCodeFileSaverTemplate.java
│   │   │   ├── handler/
│   │   │   │   ├── StreamHandlerExecutor.java
│   │   │   │   ├── SimpleTextStreamHandler.java
│   │   │   │   └── JsonMessageStreamHandler.java
│   │   │   └── builder/
│   │   │       └── VueProjectBuilder.java
│   │   │
│   │   ├── controller/                  # 控制器层
│   │   │   ├── AppController.java
│   │   │   ├── UserController.java
│   │   │   ├── ChatHistoryController.java
│   │   │   ├── StaticResourceController.java
│   │   │   └── HealthController.java
│   │   │
│   │   ├── service/                     # 服务层
│   │   │   ├── UserService.java
│   │   │   ├── impl/UserServiceImpl.java
│   │   │   ├── AppService.java
│   │   │   ├── impl/AppServiceImpl.java
│   │   │   ├── ChatHistoryService.java
│   │   │   └── impl/ChatHistoryServiceImpl.java
│   │   │
│   │   ├── model/                       # 数据模型
│   │   │   ├── entity/
│   │   │   │   ├── User.java
│   │   │   │   ├── App.java
│   │   │   │   └── ChatHistory.java
│   │   │   ├── dto/
│   │   │   │   ├── user/
│   │   │   │   ├── app/
│   │   │   │   └── chathistory/
│   │   │   ├── vo/
│   │   │   │   ├── UserVO.java
│   │   │   │   ├── AppVO.java
│   │   │   │   └── LoginUserVO.java
│   │   │   └── enums/
│   │   │       ├── UserRoleEnum.java
│   │   │       ├── CodeGenTypeEnum.java
│   │   │       └── ChatHistoryMessageTypeEnum.java
│   │   │
│   │   ├── mapper/
│   │   │   ├── UserMapper.java
│   │   │   ├── AppMapper.java
│   │   │   └── ChatHistoryMapper.java
│   │   │
│   │   ├── common/
│   │   │   ├── BaseResponse.java
│   │   │   ├── ResultUtils.java
│   │   │   ├── PageRequest.java
│   │   │   └── DeleteRequest.java
│   │   │
│   │   ├── exception/
│   │   │   ├── ErrorCode.java
│   │   │   ├── BusinessException.java
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── ThrowUtils.java
│   │   │
│   │   ├── annotation/
│   │   │   └── AuthCheck.java
│   │   │
│   │   ├── aop/
│   │   │   └── AuthInterceptor.java
│   │   │
│   │   ├── config/
│   │   │   ├── CorsConfig.java
│   │   │   ├── JsonConfig.java
│   │   │   ├── RedissonConfig.java
│   │   │   ├── RedisChatMemoryStoreConfig.java
│   │   │   ├── StreamingChatModelConfig.java
│   │   │   ├── RoutingAiModelConfig.java
│   │   │   ├── ReasoningStreamingChatModelConfig.java
│   │   │   └── CosClientConfig.java
│   │   │
│   │   ├── ratelimter/
│   │   │   ├── annotation/RateLimit.java
│   │   │   ├── aspect/RateLimitAspect.java
│   │   │   └── enums/RateLimitType.java
│   │   │
│   │   ├── monitor/
│   │   │   ├── MonitorContext.java
│   │   │   ├── MonitorContextHolder.java
│   │   │   ├── AiModelMonitorListener.java
│   │   │   └── AiModelMetricsCollector.java
│   │   │
│   │   ├── utils/
│   │   │   ├── CacheKeyUtils.java
│   │   │   ├── SpringContextUtil.java
│   │   │   └── WebScreenshotUtils.java
│   │   │
│   │   ├── constant/
│   │   │   ├── UserConstant.java
│   │   │   └── AppConstant.java
│   │   │
│   │   ├── generator/
│   │   │   └── MyBatisCodeGenerator.java
│   │   │
│   │   └── AiPageGenBackendApplication.java
│   │
│   ├── resources/
│   │   ├── application.yml
│   │   ├── application-local.yml
│   │   ├── prompt/
│   │   │   ├── codegen-html-system-prompt.txt
│   │   │   ├── codegen-multi-file-system-prompt.txt
│   │   │   ├── codegen-vue-project-system-prompt.txt
│   │   │   ├── code-quality-check-system-prompt.txt
│   │   │   └── codegen-routing-system-prompt.txt
│   │   └── mapper/
│   │       ├── UserMapper.xml
│   │       ├── AppMapper.xml
│   │       └── ChatHistoryMapper.xml
│   │
│   └── test/
│       └── java/com/miu/codemain/
│
├── sql/
│   └── create_table.sql
│
├── tmp/
│   ├── code_output/
│   └── code_deploy/
│
├── pom.xml
├── CLAUDE.md
└── README.md
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

#### 1.2 AiCodeGeneratorServiceFactory.java
**作用**：AI服务实例工厂，负责创建和管理AI服务实例

**核心功能**：
1. 为每个appId创建独立的AI服务实例
2. 集成Redis聊天记忆存储
3. 注册工具系统
4. 添加输入输出护轨
5. 使用Caffeine缓存服务实例

**差异化创建**：
- Vue项目：使用推理模型 + 工具调用
- HTML/多文件：使用普通流式模型

#### 1.3 AI安全护轨 (guardrail包)

##### PromptSafetyInputGuardrail.java - 输入安全护轨
**作用**：在AI处理用户输入前进行安全检查

**安全检查项**：
1. 输入长度限制（不超过1000字）
2. 空内容检测
3. 敏感词过滤（忽略指令、破解、越狱等）
4. 注入攻击模式检测（正则匹配）

**使用方式**：
```java
.inputGuardrails(new PromptSafetyInputGuardrail())
```

##### RetryOutputGuardrail.java - 输出重试护轨
**作用**：验证AI输出内容，不合格则重新生成

**验证检查项**：
1. 响应是否为空
2. 响应长度是否过短（<10字符）
3. 是否包含敏感信息（密码、密钥、token等）

**使用方式**：
```java
.outputGuardrails(new RetryOutputGuardrail())
```

#### 1.4 AI智能路由 (routing包)

##### AiCodeGenTypeRoutingService.java
**作用**：根据用户需求智能选择代码生成类型

**核心方法**：
```java
@SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
CodeGenTypeEnum routeCodeGenType(String userPrompt);
```

**返回类型**：
- HTML：简单单页应用
- MULTI_FILE：需要分离样式的多文件项目
- VUE_PROJECT：复杂的交互式应用

##### AiCodeGenTypeRoutingServiceFactory.java
**作用**：创建路由服务实例的工厂

**特点**：
- 使用轻量级模型（如qwen-turbo）进行快速判断
- prototype作用域，每次调用创建新实例

#### 1.5 AI消息模型 (model/message包)

##### StreamMessage.java
**作用**：流式消息响应基类

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamMessage {
    private String type;  // 消息类型
}
```

##### StreamMessageTypeEnum.java
**作用**：定义流式消息类型枚举

**枚举值**：
- AI_RESPONSE：AI响应文本
- TOOL_REQUEST：工具调用请求
- TOOL_EXECUTED：工具执行结果

##### AiResponseMessage.java
**作用**：AI文本响应消息

```java
public class AiResponseMessage extends StreamMessage {
    private String data;  // AI响应内容
}
```

##### ToolRequestMessage.java
**作用**：工具调用请求消息

```java
public class ToolRequestMessage extends StreamMessage {
    private String id;        // 工具调用ID
    private String name;      // 工具名称
    private String arguments; // 工具参数
}
```

##### ToolExecutedMessage.java
**作用**：工具执行结果消息

```java
public class ToolExecutedMessage extends StreamMessage {
    private String id;        // 工具调用ID
    private String name;      // 工具名称
    private String arguments; // 工具参数
    private String result;    // 执行结果
}
```

#### 1.6 工具系统 (tools包)

##### BaseTool.java - 工具基类
**作用**：定义AI工具的统一接口

```java
public abstract class BaseTool {
    public abstract String getToolName();           // 工具英文名
    public abstract String getDisplayName();        // 工具显示名
    public String generateToolRequestResponse();    // 请求响应
    public abstract String generateToolExecutedResult(JSONObject arguments);
}
```

**具体工具**：
- FileReadTool：读取文件内容
- FileWriteTool：写入文件
- FileModifyTool：修改文件
- FileDeleteTool：删除文件
- FileDirReadTool：读取目录结构
- ExitTool：退出工具调用

##### ToolManager.java - 工具管理器
**作用**：自动扫描和管理所有工具实例

- Spring依赖注入收集工具
- 提供根据名称获取工具的功能
- 返回工具列表供AI服务使用

### 2. 核心业务模块 (core包)

#### 2.1 AiCodeGeneratorFacade.java
**作用**：门面模式，统一代码生成入口

**核心方法**：
```java
public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId)
public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId)
```

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

#### 2.3 代码保存器 (saver包)

**CodeFileSaver.java** - 保存器接口

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

#### 2.4 流处理器 (handler包)

**StreamHandlerExecutor.java** - 流处理器执行器

**SimpleTextStreamHandler.java** - 简单文本处理器
- 处理传统的Flux<String>流
- 适用于HTML和多文件生成

**JsonMessageStreamHandler.java** - JSON消息处理器
- 处理TokenStream格式的复杂流
- 支持工具调用消息
- 适用于Vue项目生成

### 3. 控制器模块 (controller包)

#### AppController.java - 应用控制器
**作用**：处理应用相关的HTTP请求

**核心接口**：
1. POST /api/app/chat/gen/code - AI对话生成代码（SSE流式）
2. POST /api/app/add - 创建应用
3. POST /api/app/update - 更新应用
4. POST /api/app/delete - 删除应用
5. GET /api/app/get/vo - 获取应用详情
6. POST /api/app/my/list/page/vo - 我的应用列表
7. POST /api/app/good/list/page/vo - 精选应用列表
8. 管理员接口：delete/update/list/page

#### UserController.java - 用户控制器
**作用**：处理用户相关的HTTP请求

**核心接口**：
1. POST /api/user/register - 用户注册
2. POST /api/user/login - 用户登录
3. GET /api/user/get/login - 获取当前登录用户
4. GET /api/user/get/vo - 根据ID获取用户
5. POST /api/user/update - 更新用户信息
6. POST /api/user/list/page/vo - 分页获取用户列表（管理员）

#### ChatHistoryController.java - 对话历史控制器
**作用**：处理对话历史查询请求

**核心接口**：
1. GET /api/chatHistory/app/{appId} - 分页查询应用的对话历史（游标查询）
2. POST /api/chatHistory/admin/list/page/vo - 管理员分页查询所有对话历史

#### StaticResourceController.java - 静态资源控制器
**作用**：提供生成的网页预览访问

**核心功能**：
- 映射路径：/api/static/{deployKey}/**
- 自动重定向目录访问
- 默认返回index.html
- 根据文件扩展名设置正确的Content-Type

#### HealthController.java - 健康检查控制器
**作用**：提供服务健康检查接口

**接口**：
- GET /api/health/ - 返回健康状态

### 4. 服务模块 (service包)

#### UserServiceImpl.java - 用户服务实现
**作用**：实现用户相关业务逻辑

**核心方法**：
1. userRegister - 用户注册
2. userLogin - 用户登录
3. getLoginUser - 获取当前登录用户
4. getUserVO - 获取用户视图对象
5. listUserByPage - 分页查询用户列表
6. updateUser - 更新用户信息
7. getQueryWrapper - 构建查询条件

#### AppServiceImpl.java - 应用服务实现
**作用**：实现应用相关业务逻辑

**核心方法**：
1. chatToGenCode - AI对话生成代码
2. createApp - 创建应用（含AI智能路由）
3. deployApp - 部署应用
4. getAppVO - 获取应用视图对象
5. deleteApp - 删除应用
6. updateApp - 更新应用
7. listAppByPage - 分页查询应用列表

#### ChatHistoryServiceImpl.java - 对话历史服务实现
**作用**：实现对话历史相关业务逻辑

**核心方法**：
1. saveChatHistory - 保存对话消息
2. listAppChatHistoryByPage - 游标分页查询对话历史
3. clearChatHistory - 清空对话历史
4. loadChatHistoryToMemory - 从数据库加载历史到AI记忆
5. getQueryWrapper - 构建查询条件

### 5. 数据模型模块 (model包)

#### 实体类 (entity包)

##### App.java - 应用实体
```java
@Table("app")
public class App implements Serializable {
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

##### User.java - 用户实体
- id：用户ID
- userAccount：账号
- userPassword：密码
- userName：昵称
- userAvatar：头像
- userProfile：简介
- userRole：角色（user/admin）
- 时间戳字段

##### ChatHistory.java - 对话历史实体
- id：消息ID
- message：消息内容
- messageType：消息类型（user/ai）
- appId：应用ID
- userId：用户ID
- 时间戳字段

#### 枚举类 (enums包)

##### CodeGenTypeEnum - 代码生成类型
- HTML：原生 HTML 模式
- MULTI_FILE：原生多文件模式
- VUE_PROJECT：Vue 工程模式

##### UserRoleEnum - 用户角色
- USER：普通用户
- ADMIN：管理员

##### ChatHistoryMessageTypeEnum - 消息类型
- USER：用户消息
- AI：AI响应

### 6. 配置模块 (config包)

#### 启动类配置 - AiPageGenBackendApplication.java
**作用**：Spring Boot应用入口

**关键配置**：
- 排除RedisEmbeddingStore自动配置（避免RediSearch依赖）
- 扫描MyBatis Mapper接口

#### CosClientConfig.java - 腾讯云COS配置
**作用**：配置腾讯云对象存储客户端（可选）

**条件配置**：
- 只有配置了cos.client.secretId时才创建Bean
- 未配置时自动跳过，不影响系统运行

#### RedisChatMemoryStoreConfig.java - Redis对话记忆配置
**作用**：为LangChain4j提供Redis存储的AI对话历史功能

#### StreamingChatModelConfig.java - 流式模型配置
**作用**：配置用于HTML和多文件生成的流式模型

**特点**：
- prototype作用域，每次调用创建新实例
- 集成AI监控监听器
- 支持请求/响应日志记录

#### RoutingAiModelConfig.java - 路由模型配置
**作用**：配置用于智能路由判断的轻量级模型

**特点**：
- 使用快速模型（如qwen-turbo）
- 低token限制（100）
- prototype作用域

#### ReasoningStreamingChatModelConfig.java - 推理模型配置
**作用**：配置用于Vue项目生成的推理流式模型

**特点**：
- 支持工具调用
- 高token限制（32768）
- 低温度（0.1）确保稳定性

#### CorsConfig.java - 跨域配置
**作用**：配置CORS跨域资源共享策略

#### JsonConfig.java - JSON配置
**作用**：配置Jackson序列化，解决Long转JSON精度丢失问题

#### RedissonConfig.java - Redisson配置
**作用**：配置Redisson客户端，用于分布式限流

### 7. 限流模块 (ratelimter包)

#### RateLimit.java - 限流注解
**作用**：标记需要进行限流的方法

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

#### RateLimitType.java - 限流类型枚举
- API：接口级别限流
- USER：用户级别限流
- IP：IP级别限流

#### RateLimitAspect.java - 限流切面
**作用**：使用Redisson实现分布式限流

### 8. 监控模块 (monitor包)

#### MonitorContext.java - 监控上下文
**作用**：存储监控数据的上下文对象

#### MonitorContextHolder.java - 监控上下文持有者
**作用**：ThreadLocal存储监控上下文

#### AiModelMonitorListener.java - AI模型监听器
**作用**：监听AI模型调用事件

**监控指标**：
1. 请求计数
2. 响应时间
3. Token使用量
4. 错误率

#### AiModelMetricsCollector.java - AI模型指标收集器
**作用**：收集和导出Prometheus指标

### 9. 工具类模块 (utils包)

#### CacheKeyUtils.java - 缓存键工具
**作用**：生成缓存键

**方法**：
```java
public static String generateKey(Object obj) {
    // 对象转JSON，再转MD5
    String jsonStr = JSONUtil.toJsonStr(obj);
    return DigestUtil.md5Hex(jsonStr);
}
```

#### SpringContextUtil.java - Spring上下文工具
**作用**：在静态方法中获取Spring Bean

**方法**：
```java
public static <T> T getBean(Class<T> clazz)
public static Object getBean(String name)
public static <T> T getBean(String name, Class<T> clazz)
```

#### WebScreenshotUtils.java - 网页截图工具
**作用**：使用Selenium生成网页截图

**核心功能**：
1. 初始化Chrome无头浏览器
2. 访问指定URL
3. 等待页面加载完成
4. 截取页面截图
5. 压缩图片（30%质量）
6. 返回压缩后的图片路径

### 10. 常量模块 (constant包)

#### UserConstant.java - 用户常量
**作用**：定义用户相关的常量

**常量**：
- USER_ROLE：默认用户角色
- ADMIN_ROLE：管理员角色
- LOGIN_USER_SESSION_KEY：登录用户Session键

#### AppConstant.java - 应用常量
**作用**：定义应用相关的常量

**常量**：
- CODE_OUTPUT_ROOT_DIR：代码输出根目录
- CODE_DEPLOY_ROOT_DIR：代码部署根目录
- GOOD_APP_PRIORITY：精选应用优先级

### 11. 代码生成器模块 (generator包)

#### MyBatisCodeGenerator.java - MyBatis代码生成器
**作用**：根据数据库表自动生成Entity、Mapper、Service、Controller代码

**功能**：
1. 从application.yml读取数据库配置
2. 配置生成策略（表前缀、逻辑删除字段等）
3. 生成带Lombok注解的实体类
4. 生成Mapper接口和XML
5. 生成Service接口和实现类
6. 生成Controller类

### 12. 公共模块 (common包)

#### BaseResponse.java - 统一响应格式
**作用**：定义所有API接口的统一响应格式

```java
@Data
public class BaseResponse<T> implements Serializable {
    private int code;       // 状态码
    private T data;         // 响应数据
    private String message; // 提示信息
}
```

#### ResultUtils.java - 响应工具类
**作用**：快速创建统一响应对象

**方法**：
```java
public static <T> BaseResponse<T> success(T data)
public static BaseResponse<?> error(ErrorCode errorCode)
```

#### PageRequest.java - 分页请求
**作用**：定义分页请求的通用参数

#### DeleteRequest.java - 删除请求
**作用**：定义删除请求的参数（id）

### 13. 异常模块 (exception包)

#### ErrorCode.java - 错误码枚举
**作用**：定义系统中所有的错误码

**错误码**：
- SUCCESS(0, "ok")
- PARAMS_ERROR(40000, "请求参数错误")
- NOT_LOGIN_ERROR(40100, "未登录")
- NO_AUTH_ERROR(40101, "无权限")
- TOO_MANY_REQUEST(42900, "请求过于频繁")
- NOT_FOUND_ERROR(40400, "请求数据不存在")
- SYSTEM_ERROR(50000, "系统内部异常")
- OPERATION_ERROR(50001, "操作失败")

#### BusinessException.java - 业务异常
**作用**：自定义业务异常类

#### GlobalExceptionHandler.java - 全局异常处理器
**作用**：统一捕获和处理异常，返回友好的错误信息

#### ThrowUtils.java - 异常工具类
**作用**：提供便捷的异常抛出方法

```java
public static void throwIf(boolean condition, ErrorCode errorCode)
public static void throwIf(boolean condition, ErrorCode errorCode, String message)
```

### 14. 注解模块 (annotation包)

#### AuthCheck.java - 权限校验注解
**作用**：标记需要进行权限校验的方法

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {
    String mustRole();  // 必须具备的角色
}
```

### 15. 切面模块 (aop包)

#### AuthInterceptor.java - 权限拦截器
**作用**：基于AOP实现权限校验

**功能**：
1. 拦截带@AuthCheck注解的方法
2. 获取当前登录用户
3. 校验用户角色
4. 权限不足时抛出异常

---

## 配置文件解析

### application.yml - 主配置文件

**核心配置项**：

1. **Spring配置**
```yaml
spring:
  application:
    name: ai-page-gen-backend
  session:
    store-type: redis
    timeout: 2592000  # 30天
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/ai_page_gen
  data:
    redis:
      host: localhost
      port: 6379
      ttl: 3600
```

2. **服务器配置**
```yaml
server:
  port: 8123
  servlet:
    context-path: /api
```

3. **LangChain4j AI配置**
```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: https://api.deepseek.com
      api-key: <Your API Key>
      model-name: deepseek-chat
      max-tokens: 8192
```

### application-local.yml - 本地环境配置

**作用**：本地开发环境的特定配置

**包含**：
- 数据库连接信息
- Redis连接信息
- AI模型API密钥
- 多种AI模型配置（routing、reasoning、streaming）

### prompt目录 - AI提示词模板

1. **codegen-html-system-prompt.txt** - HTML生成系统提示词
2. **codegen-multi-file-system-prompt.txt** - 多文件生成系统提示词
3. **codegen-vue-project-system-prompt.txt** - Vue项目生成系统提示词
4. **codegen-routing-system-prompt.txt** - 智能路由判断提示词
5. **code-quality-check-system-prompt.txt** - 代码质量检查提示词

---

## 数据模型设计

### 数据库表结构

#### user - 用户表
```sql
CREATE TABLE user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    userAccount  VARCHAR(256) NOT NULL UNIQUE,
    userPassword VARCHAR(512) NOT NULL,
    userName     VARCHAR(256),
    userAvatar   VARCHAR(1024),
    userProfile  VARCHAR(512),
    userRole     VARCHAR(256) DEFAULT 'user',
    editTime     DATETIME DEFAULT CURRENT_TIMESTAMP,
    createTime   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updateTime   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete     TINYINT DEFAULT 0
)
```

#### app - 应用表
```sql
CREATE TABLE app (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    appName      VARCHAR(256),
    cover        VARCHAR(512),
    initPrompt   TEXT,
    codeGenType  VARCHAR(64),
    deployKey    VARCHAR(64) UNIQUE,
    deployedTime DATETIME,
    priority     INT DEFAULT 0,
    userId       BIGINT NOT NULL,
    editTime     DATETIME DEFAULT CURRENT_TIMESTAMP,
    createTime   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updateTime   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete     TINYINT DEFAULT 0
)
```

#### chat_history - 对话历史表
```sql
CREATE TABLE chat_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    message     TEXT NOT NULL,
    messageType VARCHAR(32) NOT NULL,
    appId       BIGINT NOT NULL,
    userId      BIGINT NOT NULL,
    createTime  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updateTime  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete    TINYINT DEFAULT 0,
    INDEX idx_appId_createTime (appId, createTime)
)
```

---

## API接口设计

### 用户相关接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/user/register | 用户注册 | 公开 |
| POST | /api/user/login | 用户登录 | 公开 |
| GET | /api/user/get/login | 获取当前登录用户 | 登录 |
| GET | /api/user/get/vo | 根据ID获取用户 | 登录 |
| POST | /api/user/update | 更新用户信息 | 登录 |
| POST | /api/user/list/page/vo | 分页获取用户列表 | 管理员 |

### 应用相关接口

| 方法 | 路径 | 说明 | 权限 | 限流 |
|------|------|------|------|------|
| GET | /api/app/chat/gen/code | AI对话生成代码 | 登录 | 5次/分钟 |
| POST | /api/app/add | 创建应用 | 登录 | - |
| POST | /api/app/update | 更新应用 | 所有者 | - |
| POST | /api/app/delete | 删除应用 | 所有者/管理员 | - |
| GET | /api/app/get/vo | 获取应用详情 | 公开 | - |
| GET | /api/static/{deployKey}/** | 预览应用 | 公开 | - |

### 对话历史接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/chatHistory/app/{appId} | 分页获取对话历史 | 应用所有者 |
| POST | /api/chatHistory/admin/list/page/vo | 管理员查询所有历史 | 管理员 |

---

## 设计模式应用

### 1. 门面模式 (Facade Pattern)
**应用场景**：`AiCodeGeneratorFacade`
**作用**：为复杂的子系统提供简化接口

### 2. 工厂模式 (Factory Pattern)
**应用场景**：`AiCodeGeneratorServiceFactory`
**作用**：根据不同条件创建不同类型的AI服务

### 3. 策略模式 (Strategy Pattern)
**应用场景**：代码解析器和保存器
**作用**：定义一系列算法，封装起来，使它们可以互相替换

### 4. 模板方法模式 (Template Method Pattern)
**应用场景**：`CodeFileSaverTemplate`
**作用**：定义算法骨架，将部分步骤延迟到子类实现

### 5. 单例模式 (Singleton Pattern)
**应用场景**：`ToolManager`
**作用**：确保一个类只有一个实例

### 6. 观察者模式 (Observer Pattern)
**应用场景**：`AiModelMonitorListener`
**作用**：当对象状态发生变化时，通知所有依赖它的对象

### 7. AOP代理模式
**应用场景**：`AuthInterceptor`、`RateLimitAspect`
**作用**：在不修改原有代码的情况下，为程序添加额外功能

---

## 业务流程分析

### 1. AI代码生成流程

```
用户发起请求
    ↓
AppController.chatToGenCode()
    - 参数校验
    - 获取登录用户
    - 应用限流检查
    ↓
AppService.chatToGenCode()
    - 查询应用信息
    - 权限校验
    - 获取代码生成类型
    - 保存用户消息
    - 设置监控上下文
    ↓
AiCodeGeneratorFacade.generateAndSaveCodeStream()
    - 获取AI服务实例
    - 调用流式生成方法
    ↓
[输入安全护轨] PromptSafetyInputGuardrail
    - 检查输入长度
    - 检查敏感词
    - 检查注入攻击
    ↓
LangChain4j AI服务
    - 加载系统提示词
    - 调用LLM API
    - 流式返回响应
    ↓
StreamHandlerExecutor.doExecute()
    - 处理流式响应
    - 收集完整代码
    - 保存对话历史
    ↓
[输出安全护轨] RetryOutputGuardrail
    - 检查响应是否为空
    - 检查响应长度
    - 检查敏感信息
    ↓
CodeParserExecutor.executeParser()
    - 解析JSON格式代码
    - 提取HTML/CSS/JS
    ↓
CodeFileSaverExecutor.executeSaver()
    - 创建输出目录
    - 保存代码文件
    - 返回目录路径
    ↓
SSE流式返回给前端
    - 实时推送代码片段
    - 发送完成事件
```

### 2. 应用创建流程

```
用户创建应用
    ↓
AppController.addApp()
    - 接收创建请求
    - 获取登录用户
    ↓
AppService.createApp()
    - 参数校验
    - 构造应用对象
    ↓
AiCodeGenTypeRoutingService.routeCodeGenType()
    - AI分析用户需求
    - 智能选择生成类型
    - 返回HTML/MULTI_FILE/VUE
    ↓
保存到数据库
    - 生成应用ID
    - 设置初始状态
    ↓
返回应用ID
```

### 3. 应用部署流程

```
用户部署应用
    ↓
参数和权限校验
    - 应用是否存在
    - 是否为应用所有者
    ↓
生成/获取deployKey
    - 6位随机字符串
    - 确保唯一性
    ↓
获取源代码路径
    - codeGenType + appId
    - 检查目录是否存在
    ↓
Vue项目特殊处理
    - 执行npm run build
    - 使用dist目录
    ↓
复制到部署目录
    - CODE_DEPLOY_ROOT_DIR/deployKey
    - 递归复制所有文件
    ↓
更新数据库
    - 保存deployKey
    - 记录部署时间
    ↓
异步生成截图
    - Selenium访问应用URL
    - 截取页面图片
    - 上传到对象存储
    - 更新应用封面
    ↓
返回访问URL
```

---

## 部署与运维

### 环境要求

**运行环境**：
- Java 21+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+（标准版即可，不需要 Redis Stack）

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
mysql -u root -p < sql/create_table.sql
```

3. **修改配置**
```yaml
# application-local.yml
spring.datasource.url: jdbc:mysql://localhost:3306/ai_page_gen
spring.datasource.username: your_username
spring.datasource.password: your_password
langchain4j.open-ai.chat-model.api-key: your_deepseek_api_key
```

4. **启动Redis**
```bash
# Windows
redis-server.exe

# macOS/Linux
redis-server
# 或使用 Docker
docker run -d -p 6379:6379 --name redis redis:7-alpine
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

---

## 文件完整清单与用途说明

### 一、启动类

| 文件路径 | 作用 |
|---------|------|
| [AiPageGenBackendApplication.java](src/main/java/com/miu/codemain/AiPageGenBackendApplication.java) | Spring Boot启动类，排除RedisEmbeddingStore自动配置，扫描Mapper接口 |

### 二、AI模块 (ai包)

#### AI服务接口与工厂

| 文件路径 | 作用 |
|---------|------|
| [AiCodeGeneratorService.java](src/main/java/com/miu/codemain/ai/AiCodeGeneratorService.java) | AI代码生成服务接口，定义各种生成方法（HTML、多文件、Vue项目） |
| [AiCodeGeneratorServiceFactory.java](src/main/java/com/miu/codemain/ai/AiCodeGeneratorServiceFactory.java) | AI服务实例工厂，负责创建和管理AI服务实例，集成对话记忆、工具和护轨 |
| [AiCodeGenTypeRoutingService.java](src/main/java/com/miu/codemain/ai/AiCodeGenTypeRoutingService.java) | AI智能路由服务接口，根据用户需求自动选择代码生成类型 |
| [AiCodeGenTypeRoutingServiceFactory.java](src/main/java/com/miu/codemain/ai/AiCodeGenTypeRoutingServiceFactory.java) | 路由服务工厂，创建用于类型判断的AI服务实例 |

#### AI安全护轨 (guardrail包)

| 文件路径 | 作用 |
|---------|------|
| [PromptSafetyInputGuardrail.java](src/main/java/com/miu/codemain/ai/guardrail/PromptSafetyInputGuardrail.java) | 输入安全护轨，在AI处理前检查输入长度、敏感词、注入攻击 |
| [RetryOutputGuardrail.java](src/main/java/com/miu/codemain/ai/guardrail/RetryOutputGuardrail.java) | 输出重试护轨，验证AI输出内容，不合格时要求重新生成 |

#### AI工具系统 (tools包)

| 文件路径 | 作用 |
|---------|------|
| [BaseTool.java](src/main/java/com/miu/codemain/ai/tools/BaseTool.java) | AI工具抽象基类，定义工具的统一接口 |
| [ToolManager.java](src/main/java/com/miu/codemain/ai/tools/ToolManager.java) | 工具管理器，自动扫描并管理所有工具实例 |
| [FileReadTool.java](src/main/java/com/miu/codemain/ai/tools/FileReadTool.java) | 文件读取工具，供AI调用读取文件内容 |
| [FileWriteTool.java](src/main/java/com/miu/codemain/ai/tools/FileWriteTool.java) | 文件写入工具，供AI调用创建新文件 |
| [FileModifyTool.java](src/main/java/com/miu/codemain/ai/tools/FileModifyTool.java) | 文件修改工具，供AI调用修改已有文件 |
| [FileDeleteTool.java](src/main/java/com/miu/codemain/ai/tools/FileDeleteTool.java) | 文件删除工具，供AI调用删除文件 |
| [FileDirReadTool.java](src/main/java/com/miu/codemain/ai/tools/FileDirReadTool.java) | 目录读取工具，供AI调用查看目录结构 |
| [ExitTool.java](src/main/java/com/miu/codemain/ai/tools/ExitTool.java) | 退出工具，用于结束工具调用流程 |

#### AI模型 (model包)

| 文件路径 | 作用 |
|---------|------|
| [HtmlCodeResult.java](src/main/java/com/miu/codemain/ai/model/HtmlCodeResult.java) | HTML代码生成结果模型 |
| [MultiFileCodeResult.java](src/main/java/com/miu/codemain/ai/model/MultiFileCodeResult.java) | 多文件代码生成结果模型 |

#### AI消息模型 (model/message包)

| 文件路径 | 作用 |
|---------|------|
| [StreamMessage.java](src/main/java/com/miu/codemain/ai/model/message/StreamMessage.java) | 流式消息基类，包含消息类型字段 |
| [StreamMessageTypeEnum.java](src/main/java/com/miu/codemain/ai/model/message/StreamMessageTypeEnum.java) | 流式消息类型枚举（AI响应、工具请求、工具执行结果） |
| [AiResponseMessage.java](src/main/java/com/miu/codemain/ai/model/message/AiResponseMessage.java) | AI文本响应消息，包含响应内容 |
| [ToolRequestMessage.java](src/main/java/com/miu/codemain/ai/model/message/ToolRequestMessage.java) | 工具调用请求消息，包含工具ID、名称、参数 |
| [ToolExecutedMessage.java](src/main/java/com/miu/codemain/ai/model/message/ToolExecutedMessage.java) | 工具执行结果消息，包含执行结果 |

### 三、核心业务模块 (core包)

#### 门面与执行器

| 文件路径 | 作用 |
|---------|------|
| [AiCodeGeneratorFacade.java](src/main/java/com/miu/codemain/core/AiCodeGeneratorFacade.java) | 代码生成门面，统一入口，协调AI服务、解析器和保存器 |

#### 代码解析器 (parser包)

| 文件路径 | 作用 |
|---------|------|
| [CodeParser.java](src/main/java/com/miu/codemain/core/parser/CodeParser.java) | 代码解析器接口，定义解析方法 |
| [CodeParserExecutor.java](src/main/java/com/miu/codemain/core/parser/CodeParserExecutor.java) | 解析器执行器，根据类型选择对应解析器 |
| [HtmlCodeParser.java](src/main/java/com/miu/codemain/core/parser/HtmlCodeParser.java) | HTML代码解析器，从AI响应提取HTML代码 |
| [MultiFileCodeParser.java](src/main/java/com/miu/codemain/core/parser/MultiFileCodeParser.java) | 多文件代码解析器，解析多文件项目结构 |

#### 代码保存器 (saver包)

| 文件路径 | 作用 |
|---------|------|
| [CodeFileSaver.java](src/main/java/com/miu/codemain/core/saver/CodeFileSaver.java) | 代码保存器接口，定义保存方法 |
| [CodeFileSaverExecutor.java](src/main/java/com/miu/codemain/core/saver/CodeFileSaverExecutor.java) | 保存器执行器，根据类型选择对应保存器 |
| [CodeFileSaverTemplate.java](src/main/java/com/miu/codemain/core/saver/CodeFileSaverTemplate.java) | 保存器模板类，定义保存流程骨架 |
| [HtmlCodeFileSaverTemplate.java](src/main/java/com/miu/codemain/core/saver/HtmlCodeFileSaverTemplate.java) | HTML保存器，保存单个HTML文件 |
| [MultiFileCodeFileSaverTemplate.java](src/main/java/com/miu/codemain/core/saver/MultiFileCodeFileSaverTemplate.java) | 多文件保存器，保存多个文件并创建目录结构 |

#### 流处理器 (handler包)

| 文件路径 | 作用 |
|---------|------|
| [StreamHandlerExecutor.java](src/main/java/com/miu/codemain/core/handler/StreamHandlerExecutor.java) | 流处理器执行器，统一处理流式响应 |
| [SimpleTextStreamHandler.java](src/main/java/com/miu/codemain/core/handler/SimpleTextStreamHandler.java) | 简单文本处理器，处理Flux<String>流 |
| [JsonMessageStreamHandler.java](src/main/java/com/miu/codemain/core/handler/JsonMessageStreamHandler.java) | JSON消息处理器，处理TokenStream复杂流 |

#### 项目构建器 (builder包)

| 文件路径 | 作用 |
|---------|------|
| [VueProjectBuilder.java](src/main/java/com/miu/codemain/core/builder/VueProjectBuilder.java) | Vue项目构建器，处理Vue项目构建流程 |

### 四、控制器模块 (controller包)

| 文件路径 | 作用 |
|---------|------|
| [AppController.java](src/main/java/com/miu/codemain/controller/AppController.java) | 应用控制器，处理应用CRUD、AI代码生成、部署等请求 |
| [UserController.java](src/main/java/com/miu/codemain/controller/UserController.java) | 用户控制器，处理用户注册、登录、查询等请求 |
| [ChatHistoryController.java](src/main/java/com/miu/codemain/controller/ChatHistoryController.java) | 对话历史控制器，处理对话历史查询请求 |
| [StaticResourceController.java](src/main/java/com/miu/codemain/controller/StaticResourceController.java) | 静态资源控制器，提供生成网页的预览访问 |
| [HealthController.java](src/main/java/com/miu/codemain/controller/HealthController.java) | 健康检查控制器，提供服务健康状态接口 |

### 五、服务模块 (service包)

#### 服务接口

| 文件路径 | 作用 |
|---------|------|
| [UserService.java](src/main/java/com/miu/codemaine/service/UserService.java) | 用户服务接口，定义用户相关业务方法 |
| [AppService.java](src/main/java/com/miu/codemain/service/AppService.java) | 应用服务接口，定义应用相关业务方法 |
| [ChatHistoryService.java](src/main/java/com/miu/codemain/service/ChatHistoryService.java) | 对话历史服务接口，定义对话历史相关方法 |

#### 服务实现 (impl包)

| 文件路径 | 作用 |
|---------|------|
| [UserServiceImpl.java](src/main/java/com/miu/codemain/service/impl/UserServiceImpl.java) | 用户服务实现，含注册、登录、查询等业务逻辑 |
| [AppServiceImpl.java](src/main/java/com/miu/codemain/service/impl/AppServiceImpl.java) | 应用服务实现，含AI生成、创建、部署等业务逻辑 |
| [ChatHistoryServiceImpl.java](src/main/java/com/miu/codemain/service/impl/ChatHistoryServiceImpl.java) | 对话历史服务实现，含保存、查询、加载等业务逻辑 |

### 六、数据模型模块 (model包)

#### 实体类 (entity包)

| 文件路径 | 作用 |
|---------|------|
| [User.java](src/main/java/com/miu/codemain/model/entity/User.java) | 用户实体，对应user表 |
| [App.java](src/main/java/com/miu/codemain/model/entity/App.java) | 应用实体，对应app表 |
| [ChatHistory.java](src/main/java/com/miu/codemain/model/entity/ChatHistory.java) | 对话历史实体，对应chat_history表 |

#### 数据传输对象 (dto包)

**用户DTO (dto/user包)**
| 文件路径 | 作用 |
|---------|------|
| [UserAddRequest.java](src/main/java/com/miu/codemain/model/dto/user/UserAddRequest.java) | 用户添加请求DTO |
| [UserLoginRequest.java](src/main/java/com/miu/codemain/model/dto/user/UserLoginRequest.java) | 用户登录请求DTO |
| [UserQueryRequest.java](src/main/java/com/miu/codemain/model/dto/user/UserQueryRequest.java) | 用户查询请求DTO |
| [UserRegisterRequest.java](src/main/java/com/miu/codemain/model/dto/user/UserRegisterRequest.java) | 用户注册请求DTO |
| [UserUpdateRequest.java](src/main/java/com/miu/codemain/model/dto/user/UserUpdateRequest.java) | 用户更新请求DTO |

**应用DTO (dto/app包)**
| 文件路径 | 作用 |
|---------|------|
| [AppAddRequest.java](src/main/java/com/miu/codemain/model/dto/app/AppAddRequest.java) | 应用添加请求DTO |
| [AppUpdateRequest.java](src/main/java/com/miu/codemain/model/dto/app/AppUpdateRequest.java) | 应用更新请求DTO |
| [AppAdminUpdateRequest.java](src/main/java/com/miu/codemain/model/dto/app/AppAdminUpdateRequest.java) | 管理员更新应用请求DTO |
| [AppQueryRequest.java](src/main/java/com/miu/codemain/model/dto/app/AppQueryRequest.java) | 应用查询请求DTO |
| [AppDeployRequest.java](src/main/java/com/miu/codemain/model/dto/app/AppDeployRequest.java) | 应用部署请求DTO |

**对话历史DTO (dto/chathistory包)**
| 文件路径 | 作用 |
|---------|------|
| [ChatHistoryQueryRequest.java](src/main/java/com/miu/codemain/model/dto/chathistory/ChatHistoryQueryRequest.java) | 对话历史查询请求DTO |

#### 视图对象 (vo包)

| 文件路径 | 作用 |
|---------|------|
| [UserVO.java](src/main/java/com/miu/codemain/model/vo/UserVO.java) | 用户视图对象，用于返回给前端 |
| [AppVO.java](src/main/java/com/miu/codemain/model/vo/AppVO.java) | 应用视图对象，用于返回给前端 |
| [LoginUserVO.java](src/main/java/com/miu/codemain/model/vo/LoginUserVO.java) | 登录用户视图对象 |

#### 枚举类 (enums包)

| 文件路径 | 作用 |
|---------|------|
| [UserRoleEnum.java](src/main/java/com/miu/codemain/model/enums/UserRoleEnum.java) | 用户角色枚举（用户/管理员） |
| [CodeGenTypeEnum.java](src/main/java/com/miu/codemain/model/enums/CodeGenTypeEnum.java) | 代码生成类型枚举（HTML/多文件/Vue项目） |
| [ChatHistoryMessageTypeEnum.java](src/main/java/com/miu/codemain/model/enums/ChatHistoryMessageTypeEnum.java) | 对话历史消息类型枚举（用户/AI） |

### 七、数据访问层 (mapper包)

| 文件路径 | 作用 |
|---------|------|
| [UserMapper.java](src/main/java/com/miu/codemain/mapper/UserMapper.java) | 用户数据访问接口 |
| [AppMapper.java](src/main/java/com/miu/codemain/mapper/AppMapper.java) | 应用数据访问接口 |
| [ChatHistoryMapper.java](src/main/java/com/miu/codemain/mapper/ChatHistoryMapper.java) | 对话历史数据访问接口 |

### 八、公共模块 (common包)

| 文件路径 | 作用 |
|---------|------|
| [BaseResponse.java](src/main/java/com/miu/codemain/common/BaseResponse.java) | 统一响应格式，定义API返回结构 |
| [ResultUtils.java](src/main/java/com/miu/codemain/common/ResultUtils.java) | 响应工具类，快速创建响应对象 |
| [PageRequest.java](src/main/java/com/miu/codemain/common/PageRequest.java) | 分页请求基类，定义通用分页参数 |
| [DeleteRequest.java](src/main/java/com/miu/codemain/common/DeleteRequest.java) | 删除请求DTO，包含要删除的ID |

### 九、异常处理模块 (exception包)

| 文件路径 | 作用 |
|---------|------|
| [ErrorCode.java](src/main/java/com/miu/codemain/exception/ErrorCode.java) | 错误码枚举，定义所有系统错误码 |
| [BusinessException.java](src/main/java/com/miu/codemain/exception/BusinessException.java) | 业务异常类，用于抛出业务错误 |
| [GlobalExceptionHandler.java](src/main/java/com/miu/codemain/exception/GlobalExceptionHandler.java) | 全局异常处理器，统一捕获并返回友好错误信息 |
| [ThrowUtils.java](src/main/java/com/miu/codemain/exception/ThrowUtils.java) | 异常工具类，提供便捷的异常抛出方法 |

### 十、注解与切面

#### 自定义注解 (annotation包)

| 文件路径 | 作用 |
|---------|------|
| [AuthCheck.java](src/main/java/com/miu/codemain/annotation/AuthCheck.java) | 权限校验注解，标记需要权限验证的方法 |

#### 切面 (aop包)

| 文件路径 | 作用 |
|---------|------|
| [AuthInterceptor.java](src/main/java/com/miu/codemain/aop/AuthInterceptor.java) | 权限拦截器，基于AOP实现权限校验逻辑 |

### 十一、配置模块 (config包)

| 文件路径 | 作用 |
|---------|------|
| [CorsConfig.java](src/main/java/com/miu/codemain/config/CorsConfig.java) | 跨域配置，配置CORS策略 |
| [JsonConfig.java](src/main/java/com/miu/codemain/config/JsonConfig.java) | JSON配置，配置Jackson序列化，解决Long精度丢失 |
| [RedissonConfig.java](src/main/java/com/miu/codemain/config/RedissonConfig.java) | Redisson配置，配置分布式功能客户端 |
| [RedisChatMemoryStoreConfig.java](src/main/java/com/miu/codemain/config/RedisChatMemoryStoreConfig.java) | Redis对话记忆配置，配置LangChain4j的Redis存储 |
| [StreamingChatModelConfig.java](src/main/java/com/miu/codemain/config/StreamingChatModelConfig.java) | 流式模型配置，配置用于HTML/多文件生成的流式模型 |
| [RoutingAiModelConfig.java](src/main/java/com/miu/codemain/config/RoutingAiModelConfig.java) | 路由模型配置，配置用于智能路由的轻量级模型 |
| [ReasoningStreamingChatModelConfig.java](src/main/java/com/miu/codemain/config/ReasoningStreamingChatModelConfig.java) | 推理模型配置，配置用于Vue项目生成的推理流式模型 |
| [CosClientConfig.java](src/main/java/com/miu/codemain/config/CosClientConfig.java) | 腾讯云COS配置，配置对象存储客户端（可选） |

### 十二、限流模块 (ratelimter包)

#### 限流注解 (annotation包)

| 文件路径 | 作用 |
|---------|------|
| [RateLimit.java](src/main/java/com/miu/codemain/ratelimter/annotation/RateLimit.java) | 限流注解，标记需要限流的方法 |

#### 限流实现

| 文件路径 | 作用 |
|---------|------|
| [RateLimitAspect.java](src/main/java/com/miu/codemain/ratelimter/aspect/RateLimitAspect.java) | 限流切面，使用Redisson实现分布式限流 |

#### 限流枚举 (enums包)

| 文件路径 | 作用 |
|---------|------|
| [RateLimitType.java](src/main/java/com/miu/codemain/ratelimter/enums/RateLimitType.java) | 限流类型枚举（API/用户/IP） |

### 十三、监控模块 (monitor包)

| 文件路径 | 作用 |
|---------|------|
| [MonitorContext.java](src/main/java/com/miu/codemain/monitor/MonitorContext.java) | 监控上下文，存储监控数据 |
| [MonitorContextHolder.java](src/main/java/com/miu/codemain/monitor/MonitorContextHolder.java) | 监控上下文持有者，使用ThreadLocal存储 |
| [AiModelMonitorListener.java](src/main/java/com/miu/codemain/monitor/AiModelMonitorListener.java) | AI模型监听器，监听AI调用事件并记录指标 |
| [AiModelMetricsCollector.java](src/main/java/com/miu/codemain/monitor/AiModelMetricsCollector.java) | AI模型指标收集器，收集Prometheus指标 |

### 十四、工具类模块 (utils包)

| 文件路径 | 作用 |
|---------|------|
| [CacheKeyUtils.java](src/main/java/com/miu/codemain/utils/CacheKeyUtils.java) | 缓存键工具，生成对象MD5哈希作为缓存键 |
| [SpringContextUtil.java](src/main/java/com/miu/codemain/utils/SpringContextUtil.java) | Spring上下文工具，在静态方法中获取Bean |
| [WebScreenshotUtils.java](src/main/java/com/miu/codemain/utils/WebScreenshotUtils.java) | 网页截图工具，使用Selenium生成网页截图 |

### 十五、常量模块 (constant包)

| 文件路径 | 作用 |
|---------|------|
| [UserConstant.java](src/main/java/com/miu/codemain/constant/UserConstant.java) | 用户常量，定义用户角色、Session键等常量 |
| [AppConstant.java](src/main/java/com/miu/codemain/constant/AppConstant.java) | 应用常量，定义输出目录、部署目录等常量 |

### 十六、代码生成器模块 (generator包)

| 文件路径 | 作用 |
|---------|------|
| [MyBatisCodeGenerator.java](src/main/java/com/miu/codemain/generator/MyBatisCodeGenerator.java) | MyBatis代码生成器，根据数据库表自动生成代码 |

### 十七、配置文件

#### 应用配置

| 文件路径 | 作用 |
|---------|------|
| [application.yml](src/main/resources/application.yml) | 主配置文件，包含数据库、Redis、AI模型等配置 |
| [application-local.yml](src/main/resources/application-local.yml) | 本地环境配置，包含本地开发环境的特定配置 |

#### AI提示词模板 (prompt目录)

| 文件路径 | 作用 |
|---------|------|
| [codegen-html-system-prompt.txt](src/main/resources/prompt/codegen-html-system-prompt.txt) | HTML生成系统提示词 |
| [codegen-multi-file-system-prompt.txt](src/main/resources/prompt/codegen-multi-file-system-prompt.txt) | 多文件生成系统提示词 |
| [codegen-vue-project-system-prompt.txt](src/main/resources/prompt/codegen-vue-project-system-prompt.txt) | Vue项目生成系统提示词 |
| [code-quality-check-system-prompt.txt](src/main/resources/prompt/code-quality-check-system-prompt.txt) | 代码质量检查提示词 |
| [codegen-routing-system-prompt.txt](src/main/resources/prompt/codegen-routing-system-prompt.txt) | 智能路由判断提示词 |

#### MyBatis映射文件 (mapper目录)

| 文件路径 | 作用 |
|---------|------|
| [UserMapper.xml](src/main/resources/mapper/UserMapper.xml) | 用户SQL映射文件 |
| [AppMapper.xml](src/main/resources/mapper/AppMapper.xml) | 应用SQL映射文件 |
| [ChatHistoryMapper.xml](src/main/resources/mapper/ChatHistoryMapper.xml) | 对话历史SQL映射文件 |

### 十八、数据库脚本

| 文件路径 | 作用 |
|---------|------|
| [create_table.sql](sql/create_table.sql) | 数据库建表脚本，创建user、app、chat_history表 |

### 十九、项目文档

| 文件路径 | 作用 |
|---------|------|
| [CLAUDE.md](CLAUDE.md) | Claude Code项目说明文档 |
| [README.md](README.md) | 项目说明文档 |
| [pom.xml](pom.xml) | Maven项目配置文件 |

---

> **文档版本**：v2.0
> **更新日期**：2026-04-03
> **作者**：Claude Code
> **项目地址**：[github-repo-url]
