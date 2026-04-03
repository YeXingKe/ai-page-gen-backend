package com.miu.codemain.core.handler;

import com.miu.codemain.model.entity.User;
import com.miu.codemain.model.enums.ChatHistoryMessageTypeEnum;
import com.miu.codemain.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 *
 * 核心职责：
 * 1. 处理 HTML 和多文件类型的流式响应
 * 2. 收集完整的 AI 响应内容
 * 3. 在流结束时保存对话历史
 *
 * 使用场景：
 * - HTML 代码生成（单文件）
 * - 多文件代码生成（index.html + style.css + script.js）
 * - 特点：AI 直接返回纯文本代码，不涉及工具调用
 *
 * 与 JsonMessageStreamHandler 的区别：
 * - SimpleTextStreamHandler：处理纯文本流，消息格式简单
 * - JsonMessageStreamHandler：处理 TokenStream，支持工具调用
 *
 * 响应格式示例：
 * - 纯文本："<html><head>...</head><body>...</body></html>"
 * - 或者代码块："```html\n<html>...</html>\n```"
 *
 * 为什么需要收集完整响应？
 * - 前端需要实时看到生成进度（流式推送）
 * - 后端需要保存完整对话到数据库（流结束后保存）
 * - 两者不冲突：doOnComplete 在流结束时触发，不影响流式推送
 */
@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理简单文本流（核心方法）
     *
     * 方法流程：
     * 1. 创建 StringBuilder 用于收集完整响应
     * 2. 使用 .map() 透传每个代码片段（不影响流式推送）
     * 3. 在 .map() 中同时收集代码片段
     * 4. 流结束时保存完整响应到对话历史
     * 5. 流失败时保存错误消息到对话历史
     *
     * Reactor 操作符说明：
     * - map：转换流中的元素，这里不做转换，只收集
     * - doOnComplete：流正常完成时的回调
     * - doOnError：流发生错误时的回调
     *
     * 关键设计：
     * - map 中的收集操作不影响流的正常传递
     * - 前端仍然可以实时收到每个代码片段
     * - doOnComplete 在所有元素推送完毕后执行
     *
     * @param originFlux         AI 生成的原始文本流
     * @param chatHistoryService 对话历史服务
     * @param appId              应用 ID
     * @param loginUser          当前登录用户
     * @return 处理后的流（与原流内容相同）
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {

        // ========== 创建响应收集器 ==========
        // 设计说明：
        // - StringBuilder 用于拼接所有代码片段
        // - 线程安全：doOnMap 是串行执行，不会有并发问题
        // - 内存考虑：完整的代码通常不会太大（几百 KB），可以接受
        StringBuilder aiResponseBuilder = new StringBuilder();

        return originFlux
                // ========== 处理每个代码片段 ==========
                // map 操作：
                // 1. 接收上游的代码片段
                // 2. 收集到 StringBuilder
                // 3. 原样返回给下游（前端）
                // 4. 不修改流的任何内容
                .map(chunk -> {
                    // 收集 AI 响应内容
                    // 设计说明：
                    // - chunk 是 AI 生成的代码片段（可能只是一个字符、一行代码等）
                    // - append 到 StringBuilder，最终得到完整代码
                    aiResponseBuilder.append(chunk);

                    // 原样返回，不影响流式推送
                    return chunk;
                })
                // ========== 流正常完成时的处理 ==========
                // doOnComplete 操作：
                // 1. 流中所有元素都已发送完毕
                // 2. 此时 StringBuilder 包含完整的 AI 响应
                // 3. 保存到对话历史，供后续查看
                .doOnComplete(() -> {
                    // 获取完整的 AI 响应
                    String aiResponse = aiResponseBuilder.toString();

                    // 保存到对话历史
                    // 设计说明：
                    // - 类型：AI 响应
                    // - 用户 ID：记录是哪个用户触发的
                    // - 应用 ID：记录是哪个应用的对话
                    chatHistoryService.addChatMessage(
                            appId,
                            aiResponse,
                            ChatHistoryMessageTypeEnum.AI.getValue(),
                            loginUser.getId()
                    );

                    log.info("AI 响应已保存到对话历史，appId: {}, 响应长度: {}", appId, aiResponse.length());
                })
                // ========== 流发生错误时的处理 ==========
                // doOnError 操作：
                // 1. AI 生成过程中发生错误
                // 2. 网络中断、API 错误、超时等
                // 3. 也要记录到对话历史，标记为失败
                .doOnError(error -> {
                    // 构造错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();

                    // 保存错误消息到对话历史
                    // 设计说明：
                    // - 即使失败也要记录，便于排查问题
                    // - 用户可以在对话历史中看到哪里出错了
                    chatHistoryService.addChatMessage(
                            appId,
                            errorMessage,
                            ChatHistoryMessageTypeEnum.AI.getValue(),
                            loginUser.getId()
                    );

                    log.error("AI 响应失败，appId: {}, 错误: {}", appId, error.getMessage());
                });
    }
}
