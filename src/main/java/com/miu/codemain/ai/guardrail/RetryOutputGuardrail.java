package com.miu.codemain.ai.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * 重试输出护轨（输出验证）
 *
 * 设计模式：责任链模式的一部分，在 AI 返回结果后进行质量检查
 *
 * 核心职责：
 * 1. 检查响应是否为空
 * 2. 检查响应长度是否过短
 * 3. 检查是否包含敏感信息
 * 4. 验证失败时要求 AI 重新生成
 *
 * 工作流程：
 * AI生成响应 → 输出护轨检查 → 通过则返回用户 → 不通过则要求重新生成
 *
 * 为什么需要输出护轨？
 * - AI 有时会产生幻觉，返回无意义的内容
 * - AI 可能返回过短或为空的响应
 * - AI 可能意外地泄露敏感信息
 * - 输出护轨可以作为最后一道防线，确保质量
 *
 * reprompt 机制：
 * - 当验证失败时，LangChain4j 会自动向 AI 发送重试请求
 * - 重试请求包含失败原因和建议
 * - AI 可以根据建议重新生成更好的响应
 * - 最多重试次数由 LangChain4j 内部控制（通常 1-2 次）
 *
 * 注意事项：
 * - 流式输出模式下，输出护轨可能不生效
 * - 因为流式输出是边生成边推送，无法在生成前验证
 * - 本项目中，流式接口注释了 outputGuardrails 的使用
 */
public class RetryOutputGuardrail implements OutputGuardrail {

    /**
     * 验证 AI 输出
     *
     * 验证流程：
     * 1. 检查响应是否为空
     * 2. 检查响应长度（至少 10 个字符）
     * 3. 检查是否包含敏感信息
     *
     * 返回值说明：
     * - success()：验证通过，返回给用户
     * - reprompt(原因, 建议)：验证失败，要求 AI 重新生成
     *
     * reprompt vs fatal：
     * - reprompt：给 AI 改进机会，可以重试
     * - fatal：直接拒绝，不允许重试
     * - 这里使用 reprompt，因为响应质量问题是可以通过重试解决的
     *
     * @param responseFromLLM AI 返回的消息
     * @return 验证结果
     */
    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String response = responseFromLLM.text();

        // ========== 检查1：响应是否为空 ==========
        // 设计说明：AI 有时会返回空响应，可能是由于 API 错误或网络问题
        if (response == null || response.trim().isEmpty()) {
            return reprompt("响应内容为空", "请重新生成完整的内容");
        }

        // ========== 检查2：响应长度 ==========
        // 设计说明：
        // - 少于 10 个字符通常意味着响应不完整或无意义
        // - 例如："好的"、"可以"等简短回复不符合代码生成场景
        // - 对于代码生成任务，至少应该有一些实际的代码内容
        if (response.trim().length() < 10) {
            return reprompt("响应内容过短", "请提供更详细的内容");
        }

        // ========== 检查3：敏感信息 ==========
        // 设计说明：
        // - 防止 AI 意外泄露敏感信息
        // - 例如：API 密钥、密码、证书等
        // - 这类信息可能来自训练数据或用户之前的对话
        if (containsSensitiveContent(response)) {
            return reprompt("包含敏感信息", "请重新生成内容，避免包含敏感信息");
        }

        // ========== 所有检查通过 ==========
        return success();
    }

    /**
     * 检查响应是否包含敏感内容
     *
     * 设计说明：
     * - 使用简单的关键词匹配
     * - 对于代码生成场景，这些关键词通常不应该出现
     * - 如果误报率过高，可以考虑改进检测逻辑
     *
     * 潜在问题：
     * - 如果用户明确要求生成包含这些词的代码（如示例），会被误判
     * - 改进方案：使用更复杂的上下文分析，而非简单关键词
     *
     * @param response AI 响应内容
     * @return 是否包含敏感内容
     */
    private boolean containsSensitiveContent(String response) {
        // 转小写，实现大小写不敏感
        String lowerResponse = response.toLowerCase();

        // 敏感词列表
        // 设计说明：这些词在实际应用中应该被保护，不应该出现在 AI 生成的代码中
        String[] sensitiveWords = {
            "密码",     // 中文：密码
            "password", // 英文：密码
            "secret",   // 密钥
            "token",    // 令牌
            "api key",  // API 密钥
            "私钥",     // 私钥
            "证书",     // 证书
            "credential" // 凭证
        };

        // 检查是否包含任何敏感词
        for (String word : sensitiveWords) {
            if (lowerResponse.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
