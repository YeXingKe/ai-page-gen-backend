package com.miu.codemain.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.miu.codemain.ai.guardrail.PromptSafetyInputGuardrail;
import com.miu.codemain.ai.guardrail.RetryOutputGuardrail;
import com.miu.codemain.ai.tools.ToolManager;
import com.miu.codemain.exception.BusinessException;
import com.miu.codemain.exception.ErrorCode;
import com.miu.codemain.model.enums.CodeGenTypeEnum;
import com.miu.codemain.service.ChatHistoryService;
import com.miu.codemain.utils.SpringContextUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 服务创建工厂
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（为了兼容老逻辑）
     *
     * 设计说明：这是一个兼容性方法，当不指定生成类型时，默认使用HTML模式
     * 原因：早期版本只支持HTML生成，为了保持向后兼容性而保留
     *
     * @param appId 应用ID，用于标识不同的AI应用实例
     * @return AI代码生成服务实例
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 获取服务
     *
     * 设计说明：
     * 1. 使用appId作为缓存键的一部分，确保每个应用有独立的AI服务实例
     * 2. 不同生成类型（HTML/Vue/多文件）使用不同的AI服务配置
     * 3. 使用Caffeine缓存避免重复创建，提高性能
     *
     * 缓存策略：
     * - 最多缓存1000个实例
     * - 写入后30分钟过期（避免占用过多内存）
     * - 访问后10分钟过期（活跃实例保持更久）
     *
     * @param appId       应用 id，用于标识不同的AI应用
     * @param codeGenType 生成类型（HTML/多文件/Vue项目），决定使用哪种AI模型和配置
     * @return AI代码生成服务实例
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        // 构建缓存键：appId_生成类型，如 "123_html"
        String cacheKey = buildCacheKey(appId, codeGenType);
        // 从缓存获取，不存在时调用createAiCodeGeneratorService创建
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 创建新的 AI 服务实例
     *
     * 核心逻辑说明：
     * 1. 为每个应用创建独立的对话记忆（ChatMemory），避免不同应用间的对话混淆
     * 2. 从数据库加载历史对话，确保AI能记住之前的上下文
     * 3. 根据生成类型选择不同的AI模型和配置：
     *    - Vue项目：使用推理模型（deepseek-reasoner）+ 工具调用能力
     *    - HTML/多文件：使用普通流式模型（deepseek-chat）
     * 4. 使用prototype作用域的模型Bean，解决并发问题
     * 5. 添加输入输出护轨，确保AI交互的安全性
     *
     * 为什么使用prototype作用域？
     * - StreamingChatModel不是线程安全的
     * - 每次请求创建新实例可以避免并发状态污染
     * - 虽然有性能开销，但保证了正确性
     *
     * @param appId       应用 id，用于标识不同的AI应用
     * @param codeGenType 生成类型，决定使用哪种AI模型和配置
     * @return 配置好的AI代码生成服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);

        // ========== 第一步：创建独立的对话记忆 ==========
        // 设计说明：
        // 1. 使用appId作为记忆ID，确保每个应用有独立的对话上下文
        // 2. 使用Redis存储，支持分布式部署
        // 3. 最多保留20条历史消息，避免token消耗过大
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)                        // 使用应用ID作为记忆标识
                .chatMemoryStore(redisChatMemoryStore)  // 使用Redis存储，支持分布式
                .maxMessages(20)                  // 窗口大小：保留最近20条消息
                .build();

        // ========== 第二步：从数据库加载历史对话 ==========
        // 设计说明：
        // 1. 用户重新访问时，需要恢复之前的对话上下文
        // 2. 从数据库读取历史记录并加载到ChatMemory
        // 3. 加载最新的20条记录，与窗口大小一致
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);

        // ========== 第三步：根据生成类型创建不同的AI服务 ==========
        return switch (codeGenType) {
            // ========== Vue项目生成模式 ==========
            // 特点：需要工具调用能力（创建文件、修改文件等）
            // 模型：使用推理模型（deepseek-reasoner），支持复杂推理
            case VUE_PROJECT -> {
                // 获取推理模型的流式实例（prototype作用域，每次创建新实例）
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);

                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)                          // 用于非流式调用的模型
                        .streamingChatModel(reasoningStreamingChatModel)  // 用于流式调用的推理模型
                        .chatMemoryProvider(memoryId -> chatMemory)    // 提供对话记忆
                        .tools(toolManager.getAllTools())              // 注册所有工具（文件操作等）
                        // 处理工具调用幻觉问题：当AI调用不存在的工具时，返回错误信息
                        // 这防止AI陷入错误循环
                        .hallucinatedToolNameStrategy(toolExecutionRequest ->
                                ToolExecutionResultMessage.from(toolExecutionRequest,
                                        "Error: there is no tool called " + toolExecutionRequest.name())
                        )
                        .maxSequentialToolsInvocations(20)             // 最多连续调用20次工具，防止无限循环
                        .inputGuardrails(new PromptSafetyInputGuardrail())  // 输入安全护轨：检查恶意输入
                        .outputGuardrails(new RetryOutputGuardrail())  // 输出验证护轨：检查输出质量（流式输出时可能不生效）
                        .build();
            }
            // ========== HTML/多文件生成模式 ==========
            // 特点：不需要工具调用，直接生成代码文本
            // 模型：使用普通流式模型（deepseek-chat），响应速度快
            case HTML, MULTI_FILE -> {
                // 获取普通模型的流式实例（prototype作用域）
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);

                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)                          // 用于非流式调用
                        .streamingChatModel(openAiStreamingChatModel)   // 用于流式调用
                        .chatMemory(chatMemory)                        // 直接设置对话记忆
                        .inputGuardrails(new PromptSafetyInputGuardrail())  // 输入安全护轨
                        .outputGuardrails(new RetryOutputGuardrail())  // 输出验证护轨
                        .build();
            }
            default ->
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

    /**
     * 创建 AI 代码生成器服务
     *
     * @return
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0);
    }

    /**
     * 构造缓存键
     *
     * @param appId
     * @param codeGenType
     * @return
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }


}
