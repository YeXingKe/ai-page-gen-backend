package com.miu.codemain.core.saver;

import com.miu.codemain.ai.model.HtmlCodeResult;
import com.miu.codemain.ai.model.MultiFileCodeResult;
import com.miu.codemain.exception.BusinessException;
import com.miu.codemain.exception.ErrorCode;
import com.miu.codemain.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 代码文件保存执行器（策略模式）
 *
 * 核心职责：
 * 1. 根据代码生成类型选择对应的保存器
 * 2. 统一代码保存的入口
 * 3. 将解析后的代码对象保存到文件系统
 *
 * 为什么需要保存器？
 * - AI 生成的代码需要持久化存储
 * - 不同类型的代码有不同的文件结构
 * - 需要创建目录、写入文件、处理异常等
 *
 * 策略模式应用：
 * - 抽象策略：CodeFileSaver 接口（saveCode 方法）
 * - 具体策略：HtmlCodeFileSaverTemplate、MultiFileCodeFileSaverTemplate
 * - 上下文：CodeFileSaverExecutor（根据类型选择策略）
 *
 * 保存流程：
 * 解析后的代码对象 → 保存器创建目录 → 写入文件 → 返回目录路径
 *
 * 目录结构：
 * - HTML：tmp/code_output/html_{appId}/index.html
 * - MULTI_FILE：tmp/code_output/multi_file_{appId}/index.html + style.css + script.js
 * - VUE_PROJECT：tmp/code_output/vue_project_{appId}/...（完整的 Vue 项目）
 *
 * 设计说明：
 * - 使用静态实例，避免重复创建保存器（无状态对象）
 * - 使用 switch 表达式，代码简洁易读
 * - 返回 File 对象，方便后续操作（如部署、预览）
 *
 * @author yupi
 */
public class CodeFileSaverExecutor {

    // ========== 静态保存器实例 ==========
    // 设计说明：
    // - 保存器是无状态的工具类，可以使用静态实例
    // - 避免每次调用都创建新对象，减少 GC 压力
    // - 线程安全：保存器不持有可变状态，可以并发调用
    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaver = new HtmlCodeFileSaverTemplate();

    private static final MultiFileCodeFileSaverTemplate multiFileCodeFileSaver = new MultiFileCodeFileSaverTemplate();

    /**
     * 执行代码保存（核心方法）
     *
     * 方法流程：
     * 1. 根据代码生成类型选择对应的保存器
     * 2. 调用保存器的 saveCode 方法
     * 3. 保存器创建目录并写入文件
     * 4. 返回保存的目录路径
     *
     * 保存器说明：
     * - HtmlCodeFileSaverTemplate：保存单个 HTML 文件
     *   操作：
     *   1. 创建目录：code_output/html_{appId}/
     *   2. 写入文件：index.html
     *   3. 返回目录对象
     *
     * - MultiFileCodeFileSaverTemplate：保存多个文件
     *   操作：
     *   1. 创建目录：code_output/multi_file_{appId}/
     *   2. 写入文件：index.html、style.css、script.js
     *   3. 返回目录对象
     *
     * @param codeResult  解析后的代码结果对象
     *                    - HTML 类型：HtmlCodeResult
     *                    - MULTI_FILE 类型：MultiFileCodeResult
     * @param codeGenType 代码生成类型，决定使用哪个保存器
     * @param appId       应用 ID，用于确定保存路径
     * @return 保存代码的目录对象
     *         可以用于后续的部署、预览等操作
     */
    public static File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType, Long appId) {
        return switch (codeGenType) {
            // ========== HTML 代码保存 ==========
            // 使用场景：用户生成了一个单页应用
            // 保存内容：单个 index.html 文件
            // 目录结构：code_output/html_{appId}/index.html
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) codeResult, appId);

            // ========== 多文件代码保存 ==========
            // 使用场景：用户生成了代码和样式分离的页面
            // 保存内容：index.html、style.css、script.js 三个文件
            // 目录结构：code_output/multi_file_{appId}/
            //             ├── index.html
            //             ├── style.css
            //             └── script.js
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) codeResult, appId);

            // ========== 不支持的类型 ==========
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }
}
