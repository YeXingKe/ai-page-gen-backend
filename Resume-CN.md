# AI 驱动的智能 Web 页面生成平台

---

## 项目概述

**AI 驱动的智能 Web 页面生成平台** | 后端开发工程师 | 2024.06 - 至今

基于 Spring Boot 3.5.6 和 LangChain4j 框架开发的 AI 驱动 Web 页面自动生成系统。用户通过自然语言描述想要创建的网页，系统自动调用大语言模型（DeepSeek/通义千问）生成完整的 HTML/CSS/JavaScript 代码，支持在线预览、一键部署、对话式迭代优化。

**核心价值**：将传统的网页开发流程从"需求分析→设计→编码→测试"简化为"自然语言描述→即时生成"，大幅降低前端开发门槛，提升原型验证效率 10 倍以上。

---

## 技术栈与职责

| 类别 | 技术 | 作用说明 |
|------|------|----------|
| **后端框架** | Spring Boot 3.5.6 | 提供 RESTful API 开发能力、依赖注入、自动配置等核心功能 |
| | Spring AOP | 实现横切关注点：权限校验、限流拦截、日志记录等 |
| | Spring Session | 实现分布式会话管理，多实例间 Session 共享 |
| **AI 集成** | LangChain4j 1.1.0 | AI 编排框架：提供结构化输出、工具调用、对话记忆管理 |
| | LangGraph4j 1.6.0 | 状态机式 AI 工作流，支持复杂推理场景 |
| | DashScope SDK 2.21.1 | 接入阿里云通义千问大模型，扩展 AI 能力 |
| **数据存储** | MyBatis Flex 1.11.1 | ORM 框架：支持代码生成、灵活查询、多表关联 |
| | MySQL 8.0 | 主数据库：存储用户、应用、对话历史等业务数据 |
| | Redis 6.0 | 缓存与会话存储：AI 对话记忆、Session 分布式存储 |
| | HikariCP | 数据库连接池：优化连接管理，提升 QPS 50% |
| **分布式** | Redisson 3.50.0 | 分布式限流：基于令牌桶算法，支持集群环境 |
| | Spring Session Data Redis | 分布式会话：多实例间用户登录状态同步 |
| **响应式** | Project Reactor | 响应式编程：异步非阻塞流式处理，提升并发能力 |
| | Server-Sent Events (SSE) | 服务端推送：实时流式返回 AI 生成的代码片段 |
| **工具库** | Hutool 5.8.38 | Java 工具集：文件操作、加密解密、集合处理等 |
| | Lombok 1.18.36 | 代码简化：自动生成 getter/setter/toString 等样板代码 |
| | Knife4j 4.4.0 | API 文档：基于 Swagger 的增强接口文档工具 |
| | Caffeine | 本地缓存：高性能多级缓存，减少数据库访问 80% |
| **监控** | Spring Actuator | 应用监控：健康检查、指标暴露、线程状态监控 |
| | Prometheus | 指标收集：AI Token 消耗、响应时间、错误率等监控 |

---

## 核心技术贡献

### 1. AI 代码生成引擎

**技术实现**：基于 LangChain4j 框架集成大语言模型，设计三种代码生成模式（单文件 HTML、多文件分离、Vue 工程化），使用结构化输出确保 AI 返回符合预期格式的代码。

**难点解决**：AI 输出格式不稳定 → 设计 CodeParser 解析器，使用正则提取 JSON 代码块，兼容多种输出格式。

**代码示例**：
```java
public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum type) {
    MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
        .id(appId)
        .chatMemoryStore(redisChatMemoryStore)
        .maxMessages(20)
        .build();
    return AiServices.builder(AiCodeGeneratorService.class)
        .chatModel(chatModel)
        .chatMemory(chatMemory)
        .build();
}
```

---

### 2. SSE 流式响应架构

**技术实现**：使用 Server-Sent Events 实现 AI 生成代码的实时推送，基于 Project Reactor 构建响应式流处理管道。

**面试亮点**：
- **SSE vs WebSocket**：SSE 基于 HTTP，自动重连，更适合单向推送场景
- **背压处理**：防止 AI 生成速度超过前端处理能力导致内存溢出
- **断线重连**：支持网络中断后自动恢复，用户体验更好

**代码示例**：
```java
@GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                   @RequestParam String message) {
    return aiService.generateCode(message)
        .map(chunk -> ServerSentEvent.<String>builder()
            .data(JSONUtil.toJsonStr(Map.of("d", chunk)))
            .build())
        .concatWith(Mono.just(ServerSentEvent.<String>builder()
            .event("done").data("").build()));
}
```

---

### 3. 分布式限流系统

**技术实现**：基于 Redisson 实现令牌桶算法的分布式限流，设计三级限流策略（API 级别、用户级别、IP 级别），使用 Spring AOP + 自定义注解实现声明式限流。

**面试亮点**：
- **为什么用 Redisson**：Lua 脚本保证原子性，支持集群环境
- **令牌桶 vs 漏桶**：令牌桶允许突发流量，更适合 AI 生成场景
- **限流粒度**：用户级别限流防止单个用户占用过多资源

**代码示例**：
```java
@Aspect
@Component
public class RateLimitAspect {
    @Before("@annotation(rateLimit)")
    public void doBefore(JoinPoint point, RateLimit rateLimit) {
        String key = "rate_limit:user:" + loginUser.getId();
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.trySetRate(RateType.OVERALL, 5, 1, RateIntervalUnit.MINUTES);
        if (!limiter.tryAcquire(1)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST);
        }
    }
}

// 使用示例
@RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60)
public Flux<String> chatToGenCode() { ... }
```

---

### 4. 设计模式应用

| 设计模式 | 应用场景 | 解决问题 |
|----------|----------|----------|
| **策略模式** | CodeParser、CodeFileSaver | 不同生成类型的解析/保存策略隔离 |
| **模板方法** | CodeFileSaverTemplate | 定义保存流程骨架，子类实现具体逻辑 |
| **工厂模式** | AiCodeGeneratorServiceFactory | 根据条件创建不同类型的 AI 服务 |
| **门面模式** | AiCodeGeneratorFacade | 隐藏复杂的 AI 调用、解析、保存流程 |

**代码示例**：
```java
// 策略模式
public class CodeParserExecutor {
    public static Object executeParser(String codeContent, CodeGenTypeEnum type) {
        return switch (type) {
            case HTML -> htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
        };
    }
}

// 模板方法模式
public abstract class CodeFileSaverTemplate {
    public final File saveCode(Object codeResult, Long appId) {
        File outputDir = createOutputDir(appId);  // 模板方法定义骨架
        doSaveCode(codeResult, outputDir);         // 子类实现具体保存逻辑
        return outputDir;
    }
}
```

---

### 5. 性能优化

| 优化点 | 方案 | 效果 |
|--------|------|------|
| **N+1 查询** | 批量查询 + Map 映射 | 查询次数从 O(n) 降到 O(1) |
| **本地缓存** | Caffeine 多级缓存 | 减少数据库访问 80% |
| **连接池** | HikariCP 配置优化 | 提升 QPS 50% |
| **异步处理** | 虚拟线程处理截图任务 | 响应时间减少 70% |

**代码示例（N+1 查询优化）**：
```java
// 优化前：循环查询，N+1 问题
for (App app : appList) {
    User user = userService.getById(app.getUserId());  // N 次查询
    app.setUser(user);
}

// 优化后：批量查询
Set<Long> userIds = appList.stream()
    .map(App::getUserId)
    .collect(Collectors.toSet());
Map<Long, User> userMap = userService.listByIds(userIds).stream()
    .collect(Collectors.toMap(User::getId, Function.identity()));
appList.forEach(app -> app.setUser(userMap.get(app.getUserId())));
```

---

## 项目亮点

### 亮点 1：AI 工具调用系统

为 Vue 项目生成设计工具调用系统，支持 AI 执行文件操作（读、写、改、删），使用 Spring 自动扫描注册工具，新增工具无需修改配置。

**技术难点**：AI 可能调用不存在的工具（幻觉问题）→ 设计 `hallucinatedToolNameStrategy` 返回友好错误提示，引导 AI 重新选择。

---

### 亮点 2：条件配置与优雅降级

使用 `@ConditionalOnProperty` 实现可选功能的优雅降级，不配置腾讯云 COS 也能正常运行，开发环境不需要 RediSearch 模块。

**代码示例**：
```java
// 配置了 secretId 才启用
@Configuration
@ConditionalOnProperty(prefix = "cos.client", name = "secretId")
public class CosClientConfig { }

// 排除向量嵌入存储
@SpringBootApplication(exclude = RedisEmbeddingStoreAutoConfiguration.class)
public class Application { }
```

---

### 亮点 3：一键部署与截图

设计部署键（deployKey）机制生成简短访问 URL，集成 Selenium 实现应用截图自动生成封面，支持 Vue 项目构建后部署。

---

### 亮点 4：AI 对话记忆管理

使用 Redis 存储 AI 对话历史，设计滑动窗口策略保留最近 20 条消息，实现对话历史持久化，应用重启后可恢复上下文。

---

## 面试问答准备

### Q1：LangChain4j 与直接调用 OpenAI API 的区别？

**答案**：
- **结构化输出**：LangChain4j 支持定义 Java 对象作为输出格式，自动解析 JSON
- **工具调用**：内置 Function Calling 支持，无需手动处理
- **记忆管理**：提供多种 ChatMemory 实现，简化多轮对话
- **流式响应**：统一的流式接口，屏蔽底层差异

---

### Q2：SSE 与 WebSocket 的选择？

**答案**：
- **SSE 优势**：单向推送、自动重连、基于 HTTP（穿透防火墙）
- **WebSocket 优势**：双向通信、二进制支持
- **本项目选择 SSE**：只需服务端推送，且需要自动重连机制

---

### Q3：如何保证 AI 生成代码的安全性？

**答案**：
- **输入护轨**：实现 PromptSafetyInputGuardrail 检测恶意输入
- **沙箱执行**：生成的代码在独立的 iframe 中预览
- **文件隔离**：每个应用的代码保存在独立目录
- **代码扫描**：设计 CodeQualityCheckPrompt 检测恶意代码

---

### Q4：限流为什么选择 Redisson 而不是自己实现？

**答案**：
- **分布式一致性**：Redisson 基于 Redis，天然支持集群
- **算法支持**：内置令牌桶、漏桶等算法
- **原子性保证**：Lua 脚本保证限流操作的原子性
- **开箱即用**：减少开发工作量

---

### Q5：如何处理 AI 生成失败的情况？

**答案**：
- **重试机制**：配置 max-retries: 3
- **降级策略**：返回预设的模板代码
- **错误日志**：记录到监控系统，便于分析
- **用户提示**：SSE 推送错误事件，前端友好提示

---

## 项目数据

- **代码行数**：约 10,000+ 行
- **API 接口数**：30+ 个
- **支持代码类型**：3 种（HTML/多文件/Vue）
- **并发处理**：支持 SSE 长连接 100+
- **AI Token 消耗**：平均每次生成消耗 2000 tokens
- **响应时间**：P50 < 3s，P95 < 10s

---

## 项目收获

1. **AI 工程化实践**：掌握 LangChain4j 框架，理解 AI 应用开发模式
2. **响应式编程**：深入理解 Project Reactor 和 SSE 流式架构
3. **分布式系统**：Redis 限流、Session 共享、分布式锁等实战经验
4. **设计模式**：门面、工厂、策略、模板方法等模式的实际应用
5. **性能优化**：N+1 查询、缓存策略、异步处理等优化经验
