package com.miu.codemain.core;

import cn.hutool.json.JSONUtil;
import com.miu.codemain.ai.AiCodeGeneratorService;
import com.miu.codemain.ai.AiCodeGeneratorServiceFactory;
import com.miu.codemain.ai.model.HtmlCodeResult;
import com.miu.codemain.ai.model.MultiFileCodeResult;
import com.miu.codemain.ai.model.message.AiResponseMessage;
import com.miu.codemain.ai.model.message.ToolExecutedMessage;
import com.miu.codemain.ai.model.message.ToolRequestMessage;
import com.miu.codemain.constant.AppConstant;
import com.miu.codemain.core.builder.VueProjectBuilder;
import com.miu.codemain.core.parser.CodeParserExecutor;
import com.miu.codemain.core.saver.CodeFileSaverExecutor;
import com.miu.codemain.exception.BusinessException;
import com.miu.codemain.exception.ErrorCode;
import com.miu.codemain.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
//import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成门面类（Facade Pattern）
 *
 * 设计模式说明：
 * - 门面模式：为复杂的子系统提供简化接口
 * - 类比：就像酒店前台，客人不需要知道办理入住的复杂流程，只需提供身份证即可
 *
 * 核心职责：
 * 1. 统一代码生成的入口，隐藏底层复杂性
 * 2. 协调 AI 服务、代码解析器、代码保存器之间的交互
 * 3. 处理同步和流式两种生成模式
 *
 * 为什么需要门面？
 * - 调用方不需要知道如何获取AI服务、如何解析代码、如何保存文件
 * - 只需调用一个方法，就能完成从用户输入到文件保存的全流程
 * - 如果底层流程变化，只需修改门面类，不影响调用方
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    // Vue 项目构建器（用于 Vue 项目生成，当前注释掉）
    @Resource
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 统一入口：根据类型生成并保存代码（同步模式）
     *
     * 方法流程说明：
     * 1. 参数校验：确保生成类型不为空
     * 2. 获取服务：根据 appId 和生成类型获取对应的 AI 服务实例
     * 3. 调用AI：让 AI 生成代码
     * 4. 解析代码：将 AI 返回的原始代码解析成结构化对象
     * 5. 保存代码：将解析后的代码保存到文件系统
     * 6. 返回结果：返回保存的目录路径
     *
     * 适用于：
     * - 不需要实时反馈的场景
     * - 生成代码较快的简单页面
     *
     * @param userMessage     用户提示词，描述想要生成的页面
     * @param codeGenTypeEnum 生成类型（HTML/多文件/Vue项目）
     * @param appId           应用 ID，用于标识不同的应用
     * @return 保存代码的目录对象
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        // ========== 步骤1：参数校验 ==========
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }

        // ========== 步骤2：获取对应的 AI 服务实例 ==========
        // 设计说明：
        // - 相同 appId 的应用会共享同一个 AI 服务实例（带对话记忆）
        // - 不同生成类型使用不同的 AI 模型和配置
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);

        // ========== 步骤3-5：根据类型执行不同的生成、解析、保存流程 ==========
        return switch (codeGenTypeEnum) {
            case HTML -> {
                // 3. 调用 AI 生成 HTML 代码
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                // 4-5. 解析并保存代码（解析器在执行器内部自动选择）
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                // 3. 调用 AI 生成多文件代码
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                // 4-5. 解析并保存代码
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式模式）
     *
     * 方法流程说明：
     * 1. 参数校验：确保生成类型不为空
     * 2. 获取服务：获取对应的 AI 服务实例
     * 3. 开始流式生成：返回 Flux<String> 流
     * 4. 边生成边返回：每个代码片段实时推送给前端
     * 5. 流结束时保存：收集完整代码后，异步保存到文件
     *
     * 流式模式的优势：
     * - 用户可以实时看到 AI 生成进度，体验更好
     * - 降低首字节时间（TTFB），用户不需要等待全部生成完成
     * - 适合生成代码较长的场景
     *
     * 技术实现：
     * - 使用 Project Reactor 的 Flux 实现响应式流
     * - 通过 SSE（Server-Sent Events）推送到前端
     * - 使用 doOnComplete 在流结束时触发保存逻辑
     *
     * @param userMessage     用户提示词，描述想要生成的页面
     * @param codeGenTypeEnum 生成类型（HTML/多文件）
     * @param appId           应用 ID，用于标识不同的应用
     * @return 代码片段的响应式流
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        // ========== 步骤1：参数校验 ==========
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }

        // ========== 步骤2：获取对应的 AI 服务实例 ==========
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);

        // ========== 步骤3：根据类型执行流式生成 ==========
        return switch (codeGenTypeEnum) {
            case HTML -> {
                // 获取流式代码生成器
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                // 处理流：边推送边收集，流结束时保存
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                // Vue 项目使用 TokenStream，支持工具调用
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param appId       应用 ID
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        // 执行 Vue 项目构建（同步执行，确保预览时项目已就绪）
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                        vueProjectBuilder.buildProject(projectPath);
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }

    /**
     * 通用流式代码处理方法
     *
     * 核心逻辑说明：
     * 这个方法实现了"边推送边收集"的模式：
     * 1. 对原始流不做任何修改，直接返回给调用方（前端）
     * 2. 同时在后台默默收集所有代码片段
     * 3. 流结束时，将收集的完整代码解析并保存到文件
     *
     * 为什么要边推送边收集？
     * - 推送：用户需要实时看到生成进度，不能等待
     * - 收集：流结束时需要保存完整代码，不能只保存片段
     * - 两者不冲突：doOnNext 不影响流的正常传递
     *
     * Reactor 操作符说明：
     * - doOnNext：每个元素通过时触发，不修改流，用于副作用（收集代码）
     * - doOnComplete：流正常结束时触发，用于善后处理（保存代码）
     *
     * 潜在问题：如果流中途中断，代码不会保存
     * 解决方案：可以在 onError 中添加保存逻辑（当前未实现）
     *
     * @param codeStream  AI 生成的代码流，每个元素是代码片段
     * @param codeGenType 代码生成类型，决定使用哪种解析器和保存器
     * @param appId       应用 ID，用于确定保存路径
     * @return 处理后的流式响应，与输入流内容相同
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        // ========== 创建代码收集器 ==========
        // 使用 StringBuilder 而非 String +=，避免频繁创建对象
        // StringBuilder 是线程不安全的，但 doOnNext 是串行执行，不会有多线程问题
        StringBuilder codeBuilder = new StringBuilder();

        return codeStream
                // ========== 处理每个代码片段 ==========
                // doOnNext 不会修改流，只是在元素通过时执行副作用
                // 类似于"监听器"，不影响数据的正常传递
                .doOnNext(chunk -> {
                    // 实时收集代码片段
                    // 每次有新数据到达时，拼接到 StringBuilder
                    // 这样流结束时，codeBuilder 就包含完整的代码
                    codeBuilder.append(chunk);
                })
                // ========== 流结束时的处理 ==========
                .doOnComplete(() -> {
                    // 流正常结束，所有代码片段都已收集完毕
                    try {
                        // 步骤1：获取完整代码
                        String completeCode = codeBuilder.toString();

                        // 步骤2：解析代码（根据类型选择对应的解析器）
                        // 解析器会将 AI 返回的 JSON 或 Markdown 转换成结构化对象
                        Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);

                        // 步骤3：保存代码到文件系统
                        // 保存器会根据类型创建不同的文件结构
                        File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);

                        log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());
                    } catch (Exception e) {
                        // 保存失败只记录日志，不影响流的正常结束
                        // 因为代码已经推送给用户了，只是本地保存失败
                        log.error("保存失败: {}", e.getMessage());
                    }
                });
    }
}
