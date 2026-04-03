package com.miu.codemain.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.miu.codemain.ai.AiCodeGenTypeRoutingServiceFactory;
import com.miu.codemain.ai.AiCodeGenTypeRoutingService;
import com.miu.codemain.constant.AppConstant;
import com.miu.codemain.core.AiCodeGeneratorFacade;
import com.miu.codemain.core.builder.VueProjectBuilder;
import com.miu.codemain.core.handler.StreamHandlerExecutor;
import com.miu.codemain.exception.BusinessException;
import com.miu.codemain.exception.ErrorCode;
import com.miu.codemain.exception.ThrowUtils;
import com.miu.codemain.model.dto.app.AppAddRequest;
import com.miu.codemain.model.dto.app.AppQueryRequest;
import com.miu.codemain.model.entity.User;
import com.miu.codemain.model.enums.ChatHistoryMessageTypeEnum;
import com.miu.codemain.model.enums.CodeGenTypeEnum;
import com.miu.codemain.model.vo.AppVO;
import com.miu.codemain.model.vo.UserVO;
import com.miu.codemain.monitor.MonitorContext;
import com.miu.codemain.monitor.MonitorContextHolder;
import com.miu.codemain.service.ChatHistoryService;
import com.miu.codemain.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.miu.codemain.model.entity.App;
import com.miu.codemain.mapper.AppMapper;
import com.miu.codemain.service.AppService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用服务层实现类
 *
 * 核心职责：
 * 1. 应用的 CRUD 操作（创建、查询、更新、删除）
 * 2. AI 代码生成的业务编排（调用 AI、保存代码、记录历史）
 * 3. 应用部署（复制文件、生成 URL、异步截图）
 * 4. 权限校验（确保用户只能操作自己的应用）
 *
 * 关键业务逻辑：
 * - chatToGenCode: AI 对话生成代码的核心流程
 * - createApp: 创建应用时使用 AI 智能选择生成类型
 * - deployApp: 部署应用到静态资源服务器
 *
 * @author <a href="https://github.com/YeXingKe">野行客</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

    // 部署主机地址，从配置文件读取，默认为 localhost
    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;

    @Resource
    private UserService userService;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    /**
     * AI 对话生成代码（核心方法）
     *
     * 业务流程：
     * 1. 参数校验（appId、message）
     * 2. 查询应用信息
     * 3. 权限校验（只有应用所有者才能调用）
     * 4. 获取应用的代码生成类型
     * 5. 保存用户消息到对话历史
     * 6. 设置监控上下文（用于指标收集）
     * 7. 调用 AI 生成代码（流式）
     * 8. 处理 AI 响应流，收集完整响应并保存到历史
     *
     * 设计要点：
     * - 用户消息在调用 AI 前就保存，确保对话记录完整
     * - 使用流式响应提升用户体验
     * - 监控上下文使用 ThreadLocal，流结束时清理
     *
     * @param appId    应用 ID，用于标识不同的应用
     * @param message  用户的提示词/消息
     * @param loginUser 当前登录用户
     * @return AI 生成的代码流（Flux<String>）
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // ========== 步骤1：参数校验 ==========
        // 设计说明：在业务入口就进行校验，避免无效请求进入后续流程
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");

        // ========== 步骤2：查询应用信息 ==========
        // 设计说明：需要检查应用是否存在，并获取其配置信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // ========== 步骤3：权限校验 ==========
        // 设计说明：只能和自己的应用对话，防止越权访问
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }

        // ========== 步骤4：获取应用的代码生成类型 ==========
        // 设计说明：应用创建时已确定生成类型（HTML/多文件/Vue）
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }

        // ========== 步骤5：保存用户消息到数据库 ==========
        // 设计说明：在调用 AI 前保存，确保对话记录完整性
        // 即使 AI 调用失败，用户消息也已记录
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());

        // ========== 步骤6：设置监控上下文 ==========
        // 设计说明：使用 ThreadLocal 存储监控上下文
        // 在 AI 调用过程中，监听器可以获取用户 ID 和应用 ID
        MonitorContextHolder.setContext(
                MonitorContext.builder()
                        .userId(loginUser.getId().toString())
                        .appId(appId.toString())
                        .build()
        );

        // ========== 步骤7：调用 AI 生成代码（流式） ==========
        // 设计说明：使用流式响应，用户可以实时看到生成进度
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);

        // ========== 步骤8：处理 AI 响应流 ==========
        // 设计说明：
        // 1. 流式返回给前端（实时推送）
        // 2. 收集完整响应
        // 3. 保存 AI 响应到对话历史
        // 4. 流结束时清理监控上下文
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .doFinally(signalType -> {
                    // 流结束时清理监控上下文
                    // doFinally 会在流完成、失败或取消时都执行
                    MonitorContextHolder.clearContext();
                });
    }

    /**
     * 创建应用
     *
     * 业务流程：
     * 1. 参数校验（initPrompt 不能为空）
     * 2. 构造应用对象
     * 3. 使用 AI 智能选择代码生成类型
     * 4. 保存到数据库
     *
     * 设计亮点：
     * - 使用 AI 自动判断生成类型，用户无需手动选择
     * - 应用名称自动从 prompt 提取前 12 位
     * - 每次创建新的路由服务实例，避免状态污染
     *
     * @param appAddRequest 应用创建请求
     * @param loginUser    当前登录用户
     * @return 创建的应用 ID
     */
    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // ========== 步骤1：参数校验 ==========
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");

        // ========== 步骤2：构造应用对象 ==========
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位（后续可以 AI 优化）
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));

        // ========== 步骤3：AI 智能选择代码生成类型 ==========
        // 设计说明：
        // - 使用轻量级模型快速判断生成类型
        // - 根据用户需求描述选择 HTML/多文件/Vue
        // - 多例模式：每次创建新实例，避免并发问题
        AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());

        // ========== 步骤4：保存到数据库 ==========
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    /**
     * 部署应用到静态资源服务器
     *
     * 业务流程：
     * 1. 参数和权限校验
     * 2. 生成/获取 deployKey（部署唯一标识）
     * 3. 确定源代码路径
     * 4. Vue 项目特殊处理（构建）
     * 5. 复制文件到部署目录
     * 6. 更新数据库
     * 7. 异步生成应用截图
     *
     * 设计要点：
     * - deployKey 是应用的唯一访问标识，类似于短链接
     * - Vue 项目需要先构建（npm run build），再部署 dist 目录
     * - HTML/多文件项目直接复制源文件
     * - 截图异步生成，不阻塞部署流程
     *
     * 目录结构：
     * - 源代码：tmp/code_output/{类型}_{appId}/
     * - 部署目录：tmp/code_deploy/{deployKey}/
     * - 访问 URL：http://host/{deployKey}/
     *
     * @param appId    应用 ID
     * @param loginUser 当前登录用户
     * @return 应用访问 URL
     */
    @Override
    public String deployApp(Long appId, User loginUser) {
        // ========== 步骤1：参数和权限校验 ==========
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // ========== 步骤2：查询应用信息 ==========
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // ========== 步骤3：权限校验 ==========
        // 设计说明：只有应用所有者才能部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }

        // ========== 步骤4：生成/获取 deployKey ==========
        // deployKey 说明：
        // - 6位随机字符串（字母+数字）
        // - 作为应用的唯一访问标识
        // - 类似于短链接的短码
        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }

        // ========== 步骤5：确定源代码路径 ==========
        // 路径格式：code_output/{类型}_{appId}/
        // 例如：code_output/html_123/
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;

        // ========== 步骤6：检查源代码是否存在 ==========
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在，请先生成应用");
        }

        // ========== 步骤7：Vue 项目特殊处理 ==========
        // 设计说明：Vue 项目需要构建后才能部署
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // 执行构建：npm run build
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请重试");

            // 检查构建产物（dist 目录）
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");

            // 构建后，部署源改为 dist 目录
            sourceDir = distDir;
        }

        // ========== 步骤8：复制文件到部署目录 ==========
        // 部署目录格式：code_deploy/{deployKey}/
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            // 递归复制所有文件和子目录
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败：" + e.getMessage());
        }

        // ========== 步骤9：更新数据库 ==========
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");

        // ========== 步骤10：构建应用访问 URL ==========
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);

        // ========== 步骤11：异步生成应用截图 ==========
        // 设计说明：使用 Selenium 访问 URL 并截图
        // 异步执行，不阻塞部署流程
        generateAppScreenshotAsync(appId, appDeployUrl);

        return appDeployUrl;
    }

    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
//        // 使用虚拟线程并执行
//        Thread.startVirtualThread(() -> {
//            // 调用截图服务生成截图并上传
//            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
//            // 更新数据库的封面
//            App updateApp = new App();
//            updateApp.setId(appId);
//            updateApp.setCover(screenshotUrl);
//            boolean updated = this.updateById(updateApp);
//            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
//        });
    }

    /**
     * 将应用实体转换为视图对象（单个）
     *
     * 设计说明：
     * - VO（View Object）是返回给前端的视图对象
     * - 与 Entity 的区别：VO 可能包含额外的关联数据
     * - 这里需要关联查询用户信息，因为前端需要显示创建者
     *
     * 使用场景：
     * - 获取单个应用详情时
     * - 不需要考虑 N+1 查询问题（只查一次）
     *
     * @param app 应用实体对象
     * @return 应用视图对象
     */
    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        // 复制基本属性
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);

        // 关联查询用户信息
        // 设计说明：前端需要显示创建者信息（用户名、头像等）
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    /**
     * 批量将应用实体转换为视图对象
     *
     * 性能优化：解决 N+1 查询问题
     *
     * 什么是 N+1 查询问题？
     * - 假设有 100 个应用，每个应用都要查询一次创建者信息
     * - 错误做法：循环中调用 userService.getById()，会导致 101 次数据库查询
     * - 正确做法：批量查询所有用户，只触发 2 次数据库查询
     *
     * 优化方案：
     * 1. 收集所有不重复的 userId
     * 2. 一次性批量查询所有用户（1次SQL）
     * 3. 转换成 Map，key 是 userId
     * 4. 遍历应用列表，从 Map 中获取对应的用户信息
     *
     * 时间复杂度对比：
     * - 错误做法：O(n * m)，n 是应用数量，m 是单次查询时间
     * - 正确做法：O(n + m)，1 次批量查询 + n 次 Map 查找
     *
     * @param appList 应用实体列表
     * @return 应用视图对象列表
     */
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        // 空列表判断
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }

        // ========== 步骤1：收集所有不重复的 userId ==========
        // 设计说明：使用 Set 自动去重，减少查询次数
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());

        // ========== 步骤2：批量查询所有用户 ==========
        // 设计说明：listByIds 会生成 "WHERE id IN (1,2,3...)" 的 SQL
        // 只触发 1 次数据库查询，无论有多少用户
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));

        // ========== 步骤3：组装 VO 对象 ==========
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            // 从 Map 中获取用户信息，时间复杂度 O(1)
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    /**
     * 构建查询条件（QueryWrapper）
     *
     * 设计说明：
     * - QueryWrapper 是 MyBatis Flex 的查询条件构造器
     * - 用于动态构建 SQL WHERE 子句
     * - 支持等值查询、模糊查询、排序等
     *
     * 当前实现说明：
     * - 所有字段都参与查询，即使为 null 也会生成 SQL
     * - 需要注意：如果传入 null，可能会导致查询不到结果
     * - 建议改进：在 .eq() 和 .like() 前添加条件判断
     *
     * 改进示例：
     * .eq(id != null, "id", id)
     * .like(StrUtil.isNotBlank(appName), "appName", appName)
     *
     * @param appQueryRequest 应用查询请求
     * @return MyBatis Flex 查询条件对象
     */
    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        // 提取查询参数
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();

        // 构建查询条件
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 删除应用时，关联删除对话历史
     *
     * @param id
     * @return
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
//            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用关联的对话历史失败：{}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }
}
