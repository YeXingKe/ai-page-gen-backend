# AI-Powered Web Page Generation Platform

---

## Project Overview

**AI-Powered Web Page Generation Platform** | Backend Developer | 2024.06 - Present

An AI-driven web page generation system built with Spring Boot 3.5.6 and LangChain4j. Users describe desired web pages in natural language, and the system automatically invokes LLMs (DeepSeek/Qwen) to generate complete HTML/CSS/JavaScript code with online preview, one-click deployment, and conversational iteration.

**Core Value**: Transforms traditional web development workflow from "requirements → design → coding → testing" to "natural language → instant generation", reducing frontend development barrier and improving prototype validation efficiency by 10x.

---

## Tech Stack & Responsibilities

| Category | Technology | Purpose & Responsibility |
|----------|------------|-------------------------|
| **Backend Framework** | Spring Boot 3.5.6 | RESTful API development, dependency injection, auto-configuration |
| | Spring AOP | Cross-cutting concerns: rate limiting, permission checks, logging |
| | Spring Session | Distributed session management via Redis |
| **AI Integration** | LangChain4j 1.1.0 | AI orchestration: structured output, tool calling, chat memory |
| | LangGraph4j 1.6.0 | State machine-based AI workflow for complex reasoning |
| | DashScope SDK 2.21.1 | Alibaba Cloud Qwen LLM integration |
| **Data Storage** | MyBatis Flex 1.11.1 | ORM with code generation, flexible querying |
| | MySQL 8.0 | Primary database for users, apps, chat history |
| | Redis 6.0 | Caching, session storage, AI conversation memory |
| | HikariCP | Database connection pooling (performance optimization) |
| **Distributed** | Redisson 3.50.0 | Distributed rate limiting with token bucket algorithm |
| | Spring Session Data Redis | Session sharing across multiple instances |
| **Reactive** | Project Reactor | Asynchronous, non-blocking stream processing |
| | Server-Sent Events (SSE) | Real-time code streaming to frontend |
| **Utilities** | Hutool 5.8.38 | Java utility library for file operations, encryption |
| | Lombok 1.18.36 | Boilerplate code reduction |
| | Knife4j 4.4.0 | API documentation (Swagger enhancement) |
| | Caffeine | High-performance local caching |
| **Monitoring** | Spring Actuator | Application health checks and metrics |
| | Prometheus | Metrics collection and monitoring |

---

## Core Technical Contributions

### 1. AI Code Generation Engine

**Implementation**: Integrated LLMs via LangChain4j with three generation modes (single-file HTML, multi-file separation, Vue engineering). Used structured output to ensure AI returns code in expected format.

**Challenge Solved**: Unstable AI output format → Designed CodeParser with regex extraction to handle multiple JSON formats.

**Code Example**:
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

### 2. SSE Streaming Response Architecture

**Implementation**: Real-time AI code streaming using Server-Sent Events, built reactive pipeline with Project Reactor.

**Interview Highlights**:
- **SSE vs WebSocket**: SSE is HTTP-based, auto-reconnect, better for server push
- **Backpressure**: Prevents memory overflow when AI generates faster than frontend can process
- **Reconnection**: Automatic recovery from network interruptions

**Code Example**:
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

### 3. Distributed Rate Limiting System

**Implementation**: Token bucket algorithm via Redisson with three-tier strategy (API/User/IP levels), declarative rate limiting using Spring AOP + custom annotation.

**Interview Highlights**:
- **Why Redisson**: Lua scripts ensure atomicity, supports cluster environments
- **Token Bucket vs Leaky Bucket**: Token bucket allows burst traffic, better for AI generation
- **Rate Limiting Granularity**: User-level limits prevent resource monopolization

**Code Example**:
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

// Usage
@RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60)
public Flux<String> chatToGenCode() { ... }
```

---

### 4. Design Pattern Applications

| Pattern | Scenario | Problem Solved |
|---------|----------|----------------|
| **Strategy** | CodeParser, CodeFileSaver | Isolate parsing/saving strategies for different generation types |
| **Template Method** | CodeFileSaverTemplate | Define save flow skeleton, subclasses implement specific logic |
| **Factory** | AiCodeGeneratorServiceFactory | Create different AI services based on conditions |
| **Facade** | AiCodeGeneratorFacade | Hide complex AI call, parse, save workflow |

**Code Example**:
```java
// Strategy Pattern
public class CodeParserExecutor {
    public static Object executeParser(String codeContent, CodeGenTypeEnum type) {
        return switch (type) {
            case HTML -> htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
        };
    }
}

// Template Method Pattern
public abstract class CodeFileSaverTemplate {
    public final File saveCode(Object codeResult, Long appId) {
        File outputDir = createOutputDir(appId);  // Skeleton
        doSaveCode(codeResult, outputDir);         // Subclass implementation
        return outputDir;
    }
}
```

---

### 5. Performance Optimization

| Optimization | Technique | Impact |
|--------------|-----------|--------|
| **N+1 Query** | Batch query + Map mapping | Query count reduced from O(n) to O(1) |
| **Local Cache** | Caffeine multi-level cache | 80% reduction in database access |
| **Connection Pool** | HikariCP configuration | 50% increase in QPS |
| **Async Processing** | Virtual threads for screenshots | 70% reduction in response time |

**Code Example (N+1 Query Optimization)**:
```java
// Before: N+1 Problem
for (App app : appList) {
    User user = userService.getById(app.getUserId());  // N queries
    app.setUser(user);
}

// After: Batch Query
Set<Long> userIds = appList.stream()
    .map(App::getUserId)
    .collect(Collectors.toSet());
Map<Long, User> userMap = userService.listByIds(userIds).stream()
    .collect(Collectors.toMap(User::getId, Function.identity()));
appList.forEach(app -> app.setUser(userMap.get(app.getUserId())));
```

---

## Project Highlights

### Highlight 1: AI Tool Calling System

Designed tool calling system for Vue project generation, supporting AI file operations (read, write, modify, delete). Spring auto-scanning for tool registration, new tools require no config changes.

**Technical Challenge**: AI may call non-existent tools (hallucination) → Implemented `hallucinatedToolNameStrategy` to return friendly error messages, guiding AI to re-select.

---

### Highlight 2: Conditional Configuration & Graceful Degradation

Used `@ConditionalOnProperty` for graceful degradation of optional features. System runs without Tencent Cloud COS, dev environment doesn't need RediSearch module.

**Code Example**:
```java
// Only enable when secretId is configured
@Configuration
@ConditionalOnProperty(prefix = "cos.client", name = "secretId")
public class CosClientConfig { }

// Exclude vector embedding storage
@SpringBootApplication(exclude = RedisEmbeddingStoreAutoConfiguration.class)
public class Application { }
```

---

### Highlight 3: One-Click Deployment & Screenshot

Designed deployKey mechanism for short access URLs, integrated Selenium for automatic screenshot generation, supported Vue project build and deployment.

---

### Highlight 4: AI Conversation Memory Management

Stored AI conversation history in Redis with sliding window strategy (last 20 messages), implemented persistent history for context recovery after restart.

---

## Interview Q&A Preparation

### Q1: LangChain4j vs Direct OpenAI API?

**Answer**:
- **Structured Output**: LangChain4j supports Java objects as output format, auto-parses JSON
- **Tool Calling**: Built-in Function Calling support, no manual handling needed
- **Memory Management**: Multiple ChatMemory implementations for multi-turn conversations
- **Streaming Response**: Unified streaming interface, abstracts underlying differences

---

### Q2: SSE vs WebSocket?

**Answer**:
- **SSE Advantages**: One-way push, auto-reconnect, HTTP-based (firewall friendly)
- **WebSocket Advantages**: Two-way communication, binary support
- **Project Choice**: SSE chosen for server push with auto-reconnect requirement

---

### Q3: How to ensure AI-generated code security?

**Answer**:
- **Input Guardrails**: PromptSafetyInputGuardrail detects malicious input
- **Sandbox Execution**: Generated code previewed in isolated iframe
- **File Isolation**: Each app's code stored in separate directory
- **Code Scanning**: CodeQualityCheckPrompt detects malicious code

---

### Q4: Why Redisson for rate limiting instead of custom implementation?

**Answer**:
- **Distributed Consistency**: Redisson based on Redis, supports clusters natively
- **Algorithm Support**: Built-in token bucket, leaky bucket algorithms
- **Atomicity Guarantee**: Lua scripts ensure atomic rate limit operations
- **Ready to Use**: Reduces development effort

---

### Q5: How to handle AI generation failures?

**Answer**:
- **Retry Mechanism**: Configured max-retries: 3
- **Fallback Strategy**: Return preset template code
- **Error Logging**: Record to monitoring system for analysis
- **User Notification**: SSE pushes error events with friendly messages

---

## Project Metrics

- **Lines of Code**: ~10,000+
- **API Endpoints**: 30+
- **Code Generation Types**: 3 (HTML/Multi-file/Vue)
- **Concurrent Connections**: 100+ SSE connections
- **Token Consumption**: ~2000 tokens per generation
- **Response Time**: P50 < 3s, P95 < 10s

---

## Key Learnings

1. **AI Engineering**: Mastered LangChain4j framework, understood AI application development patterns
2. **Reactive Programming**: Deep understanding of Project Reactor and SSE streaming architecture
3. **Distributed Systems**: Hands-on experience with Redis rate limiting, session sharing, distributed locks
4. **Design Patterns**: Practical application of Facade, Factory, Strategy, Template Method patterns
5. **Performance Optimization**: Experience with N+1 query optimization, caching strategies, async processing
