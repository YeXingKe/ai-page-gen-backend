package com.miu.codemain.core.parser;

import com.miu.codemain.core.parser.HtmlCodeParser;
import com.miu.codemain.core.parser.MultiFileCodeParser;
import com.miu.codemain.exception.BusinessException;
import com.miu.codemain.exception.ErrorCode;
import com.miu.codemain.model.enums.CodeGenTypeEnum;

/**
 * 代码解析执行器（策略模式）
 *
 * 核心职责：
 * 1. 根据代码生成类型选择对应的解析器
 * 2. 统一代码解析的入口
 * 3. 将 AI 返回的原始代码解析成结构化对象
 *
 * 为什么需要解析？
 * - AI 返回的是原始文本（可能包含 Markdown 代码块）
 * - 需要提取出纯净的代码内容
 * - 不同类型的代码有不同的结构
 *
 * 策略模式应用：
 * - 抽象策略：CodeParser 接口（parseCode 方法）
 * - 具体策略：HtmlCodeParser、MultiFileCodeParser
 * - 上下文：CodeParserExecutor（根据类型选择策略）
 *
 * 解析流程：
 * AI 返回原始文本 → 解析器提取代码 → 返回结构化对象
 *
 * 设计说明：
 * - 使用静态实例，避免重复创建解析器（无状态对象）
 * - 使用 switch 表达式，代码简洁易读
 * - 返回 Object 类型（多态：HtmlCodeResult 或 MultiFileCodeResult）
 *
 * @author yupi
 */
public class CodeParserExecutor {

    // ========== 静态解析器实例 ==========
    // 设计说明：
    // - 解析器是无状态的工具类，可以使用静态实例
    // - 避免每次调用都创建新对象，减少 GC 压力
    // - 线程安全：解析器不持有可变状态，可以并发调用
    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();

    private static final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    /**
     * 执行代码解析（核心方法）
     *
     * 方法流程：
     * 1. 根据代码生成类型选择对应的解析器
     * 2. 调用解析器的 parseCode 方法
     * 3. 解析器提取代码并返回结构化对象
     *
     * 解析器说明：
     * - HtmlCodeParser：从 AI 响应中提取 HTML 代码
     *   输入：可能包含 Markdown 代码块的文本
     *   输出：HtmlCodeResult（包含纯净的 HTML 代码）
     *
     * - MultiFileCodeParser：从 AI 响应中提取多文件代码
     *   输入：JSON 格式的多文件结构或 Markdown 代码块
     *   输出：MultiFileCodeResult（包含 html、css、js 三个文件）
     *
     * @param codeContent     AI 返回的原始代码内容
     *                       可能是纯代码，也可能包裹在 Markdown 代码块中
     * @param codeGenTypeEnum 代码生成类型，决定使用哪个解析器
     * @return 解析后的结构化对象
     *         - HTML 类型：HtmlCodeResult
     *         - MULTI_FILE 类型：MultiFileCodeResult
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenTypeEnum) {
        return switch (codeGenTypeEnum) {
            // ========== HTML 代码解析 ==========
            // 使用场景：用户想要一个简单的单页应用
            // 解析器会提取 HTML 代码，去除可能存在的 Markdown 标记
            case HTML -> htmlCodeParser.parseCode(codeContent);

            // ========== 多文件代码解析 ==========
            // 使用场景：用户想要代码和样式分离的页面
            // 解析器会提取 index.html、style.css、script.js 三个文件
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);

            // ========== 不支持的类型 ==========
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        };
    }
}
