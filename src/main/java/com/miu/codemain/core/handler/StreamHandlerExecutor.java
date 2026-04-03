package com.miu.codemain.core.handler;

import com.miu.codemain.model.entity.User;
import com.miu.codemain.model.enums.CodeGenTypeEnum;
import com.miu.codemain.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 流处理器执行器（策略模式）
 *
 * 核心职责：
 * 1. 根据代码生成类型选择合适的流处理器
 * 2. 统一流处理的入口
 * 3. 协调流处理器与对话历史服务的交互
 *
 * 为什么需要不同的流处理器？
 * - HTML/多文件：使用简单的 Flux<String> 流，AI 返回纯文本代码
 * - Vue 项目：使用 TokenStream 流，支持工具调用（AI 可能调用文件操作工具）
 * - 两种流的格式和处理方式不同，需要不同的处理器
 *
 * 策略模式应用：
 * - 定义抽象：StreamHandler（handle 方法）
 * - 具体策略：SimpleTextStreamHandler、JsonMessageStreamHandler
 * - 上下文：StreamHandlerExecutor（根据类型选择策略）
 *
 * 流处理流程：
 * AI 生成 → 原始流 → 流处理器 → 收集完整响应 → 保存到对话历史 → 返回给前端
 */
@Slf4j
@Component
public class StreamHandlerExecutor {

    // JsonMessageStreamHandler 需要依赖注入，因为它可能依赖其他 Bean
    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    /**
     * 执行流处理（核心方法）
     *
     * 方法流程：
     * 1. 根据代码生成类型选择对应的处理器
     * 2. 调用处理器的 handle 方法
     * 3. 处理器会：
     *    - 处理流式响应
     *    - 收集完整的 AI 响应内容
     *    - 保存到对话历史
     *    - 返回流给前端
     *
     * 处理器选择逻辑：
     * - VUE_PROJECT：使用 JsonMessageStreamHandler
     *   原因：Vue 项目使用工具调用，需要处理 TokenStream 格式的复杂消息
     *   消息类型：AI 响应、工具请求、工具执行结果
     *
     * - HTML/MULTI_FILE：使用 SimpleTextStreamHandler
     *   原因：简单场景，AI 直接返回纯文本代码
     *   消息类型：只有纯文本
     *
     * 设计亮点：
     * - SimpleTextStreamHandler 使用 new 创建（无状态，无依赖）
     * - JsonMessageStreamHandler 使用注入（可能有状态或有依赖）
     *
     * @param originFlux         AI 生成的原始流
     * @param chatHistoryService 对话历史服务（用于保存消息）
     * @param appId              应用 ID（用于关联对话历史）
     * @param loginUser          当前登录用户（用于记录谁发送的消息）
     * @param codeGenType        代码生成类型（决定使用哪个处理器）
     * @return 处理后的流，最终返回给前端
     */
    public Flux<String> doExecute(Flux<String> originFlux,
                                  ChatHistoryService chatHistoryService,
                                  long appId, User loginUser, CodeGenTypeEnum codeGenType) {
        return switch (codeGenType) {
            // ========== Vue 项目：使用 JSON 消息处理器 ==========
            // 设计说明：
            // - 使用注入的实例，因为它可能有复杂的依赖
            // - 支持 TokenStream 格式，包含工具调用信息
            // - 消息格式：{"type":"ai_response","data":"..."}
            //              {"type":"tool_request","name":"writeFile","arguments":"..."}
            case VUE_PROJECT ->
                    jsonMessageStreamHandler.handle(originFlux, chatHistoryService, appId, loginUser);

            // ========== HTML/多文件：使用简单文本处理器 ==========
            // 设计说明：
            // - 直接 new 创建，因为是无状态工具类
            // - 处理纯文本流，不涉及工具调用
            // - 消息格式：直接的代码字符串
            case HTML, MULTI_FILE ->
                    new SimpleTextStreamHandler().handle(originFlux, chatHistoryService, appId, loginUser);
        };
    }
}
