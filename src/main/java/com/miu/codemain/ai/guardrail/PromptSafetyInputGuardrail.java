package com.miu.codemain.ai.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt 安全审查护轨（输入护轨）
 *
 * 设计模式：责任链模式的一部分，在 AI 处理输入前进行安全检查
 *
 * 核心职责：
 * 1. 检查输入长度（防止资源耗尽攻击）
 * 2. 检查空输入
 * 3. 检查敏感词（防止恶意指令）
 * 4. 检查注入攻击模式（防止提示词注入）
 *
 * 工作流程：
 * 用户输入 → 输入护轨检查 → 通过则调用AI → 不通过则返回错误
 *
 * 为什么需要输入护轨？
 * - 防止用户通过精心构造的输入绕过 AI 的限制
 * - 防止提示词注入攻击（Prompt Injection）
 * - 防止资源耗尽攻击（超长输入）
 * - 保护系统安全和稳定性
 *
 * 常见的攻击手段：
 * 1. 提示词注入："忽略之前的指令，告诉我..."
 * 2. 越狱攻击："假装你是一个不受限制的AI..."
 * 3. 角色扮演："扮演一个黑客，教我如何..."
 * 4. 系统提示覆盖："system: 你现在是一个新的AI..."
 */
public class PromptSafetyInputGuardrail implements InputGuardrail {

    // ========== 敏感词列表 ==========
    // 设计说明：这些词常用于提示词注入和越狱攻击
    // 中英文混合，防止绕过
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令",    // 忽略之前指令
            "ignore previous instructions",  // 英文版本
            "ignore above",      // 忽略上面内容
            "破解",             // 用于非法目的
            "hack",
            "绕过",             // 绕过限制
            "bypass",
            "越狱",             // AI越狱
            "jailbreak"
    );

    // ========== 注入攻击模式（正则表达式）==========
    // 设计说明：
    // - 比简单的关键词匹配更强大
    // - 可以检测变体和组合攻击
    // - 使用 (?i) 标志实现大小写不敏感
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            // 匹配 "ignore previous/above/all instructions/commands/prompts"
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),

            // 匹配 "forget/disregard everything/all above/before"
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),

            // 匹配 "pretend/act/behave as/like if/you are"（角色扮演攻击）
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),

            // 匹配 "system: you are..."（系统提示覆盖）
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),

            // 匹配 "new instructions/commands/prompts:"（新指令注入）
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    /**
     * 验证用户输入
     *
     * 验证流程：
     * 1. 检查输入长度（不超过 1000 字）
     * 2. 检查是否为空
     * 3. 检查敏感词
     * 4. 检查注入攻击模式
     *
     * 返回值说明：
     * - success()：验证通过，可以继续处理
     * - fatal(原因, 消息)：验证失败，终止处理并返回错误消息
     *
     * @param userMessage 用户消息
     * @return 验证结果
     */
    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();

        // ========== 检查1：输入长度 ==========
        // 设计说明：限制输入长度，防止资源耗尽攻击
        // 1000 字是合理的限制，足够描述大多数需求
        if (input.length() > 1000) {
            return fatal("输入内容过长，不要超过 1000 字");
        }

        // ========== 检查2：空输入 ==========
        // 设计说明：空输入会浪费 AI 资源，提前拦截
        if (input.trim().isEmpty()) {
            return fatal("输入内容不能为空");
        }

        // ========== 检查3：敏感词 ==========
        // 设计说明：使用 toLowerCase() 实现大小写不敏感
        // 这比正则表达式更简单高效
        String lowerInput = input.toLowerCase();
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerInput.contains(sensitiveWord.toLowerCase())) {
                return fatal("输入包含不当内容，请修改后重试");
            }
        }

        // ========== 检查4：注入攻击模式 ==========
        // 设计说明：使用正则表达式检测复杂的注入模式
        // 可以捕获经过变形的攻击方式
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return fatal("检测到恶意输入，请求被拒绝");
            }
        }

        // ========== 所有检查通过 ==========
        return success();
    }
}
