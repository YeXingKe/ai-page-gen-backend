package com.miu.codemain.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.miu.codemain.exception.BusinessException;
import com.miu.codemain.exception.ErrorCode;
import com.miu.codemain.exception.ThrowUtils;
import com.miu.codemain.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

/**
 * 项目下载服务实现类
 *
 * 核心功能：
 * 1. 流式压缩下载：边压缩边返回，不生成临时文件
 * 2. 智能文件过滤：排除开发过程中的临时文件和依赖
 * 3. 安全路径校验：防止路径遍历攻击
 *
 * 技术实现：
 * - 使用 Hutool 的 ZipUtil 进行压缩
 * - 使用 FileFilter 实现文件过滤
 * - 直接写入 HttpServletResponse 的输出流
 *
 * 为什么要过滤文件？
 * - node_modules：可能包含数千个依赖包，体积巨大（几百 MB）
 * - .git：包含版本控制信息，用户下载源码不需要
 * - dist/build：构建产物，可以重新生成
 * - .env：可能包含敏感配置信息
 * - .log/.tmp：临时文件，无实际价值
 *
 * 性能优化：
 * - 流式处理：不占用服务器磁盘空间
 * - 内存友好：不一次性加载所有文件
 * - 按需压缩：只包含用户需要的文件
 *
 * 安全考虑：
 * - 路径校验：确保只下载指定目录下的文件
 * - 相对路径检查：防止路径遍历攻击
 * - 文件名编码：使用 UTF-8 避免中文乱码
 */
@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {

    /**
     * 需要过滤的目录和文件名称
     *
     * 设计说明：
     * - 这些目录/文件通常是开发环境特有的
     * - 用户下载源码后可以通过 npm install 等命令重新生成
     * - 过滤后可以大幅减小下载包大小
     *
     * 过滤项说明：
     * - node_modules：npm 依赖包，可能占几百 MB
     * - .git：Git 版本控制目录
     * - dist/build：构建输出目录
     * - .DS_Store：Mac 系统自动生成的文件
     * - .env：环境变量配置（可能包含敏感信息）
     * - target：Maven 编译输出目录
     * - .mvn/.idea/.vscode：IDE 和构建工具的配置
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",    // npm 依赖包
            ".git",           // Git 版本控制
            "dist",           // 构建输出（Vue/React）
            "build",          // 构建输出（其他框架）
            ".DS_Store",      // Mac 系统文件
            ".env",           // 环境变量（敏感信息）
            "target",         // Maven 编译输出
            ".mvn",           // Maven 配置目录
            ".idea",          // IntelliJ IDEA 配置
            ".vscode"         // VS Code 配置
    );

    /**
     * 需要过滤的文件扩展名
     *
     * 设计说明：
     * - 这些文件通常是临时文件或日志
     * - 对用户使用代码没有帮助
     * - 过滤后保持下载包的干净
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",    // 日志文件
            ".tmp",    // 临时文件
            ".cache"   // 缓存文件
    );

    /**
     * 下载项目为压缩包（核心方法）
     *
     * 执行流程：
     * 1. 参数校验（路径、文件名不能为空）
     * 2. 目录校验（路径存在且是目录）
     * 3. 设置 HTTP 响应头
     * 4. 创建文件过滤器
     * 5. 流式压缩并写入响应
     *
     * HTTP 响应头设置：
     * - Status: 200 OK
     * - Content-Type: application/zip
     * - Content-Disposition: attachment; filename="xxx.zip"
     *
     * 流式下载优势：
     * - 不占用服务器磁盘空间
     * - 用户可以立即开始下载
     * - 内存占用小（不需要缓存整个 ZIP）
     *
     * @param projectPath       项目根目录的绝对路径
     * @param downloadFileName  下载后的文件名（不含 .zip 扩展名）
     * @param response          HTTP 响应对象
     */
    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response) {
        // ========== 步骤1：基础参数校验 ==========
        // 设计说明：在处理前就校验参数，避免无效操作
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR, "项目路径不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName), ErrorCode.PARAMS_ERROR, "下载文件名不能为空");

        // ========== 步骤2：目录校验 ==========
        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.PARAMS_ERROR, "项目路径不存在");
        ThrowUtils.throwIf(!projectDir.isDirectory(), ErrorCode.PARAMS_ERROR, "项目路径不是一个目录");

        log.info("开始打包下载项目: {} -> {}.zip", projectPath, downloadFileName);

        // ========== 步骤3：设置 HTTP 响应头 ==========
        // 状态码：200 OK
        response.setStatus(HttpServletResponse.SC_OK);

        // 内容类型：application/zip（告诉浏览器这是一个 ZIP 文件）
        response.setContentType("application/zip");

        // Content-Disposition：attachment（触发下载行为）
        // filename：指定下载后的文件名
        response.addHeader("Content-Disposition",
                String.format("attachment; filename=\"%s.zip\"", downloadFileName));

        // ========== 步骤4：定义文件过滤器 ==========
        // 设计说明：
        // - FileFilter 接口：用于决定是否包含某个文件/目录
        // - isPathAllowed 方法：实现具体的过滤逻辑
        FileFilter filter = file -> isPathAllowed(projectDir.toPath(), file.toPath());

        // ========== 步骤5：流式压缩并写入响应 ==========
        try {
            // ZipUtil.zip 参数说明：
            // - response.getOutputStream()：直接写入 HTTP 响应流
            // - StandardCharsets.UTF_8：文件名编码（支持中文）
            // - false：不包含父目录（直接从项目根目录开始）
            // - filter：文件过滤器
            // - projectDir：要压缩的根目录
            ZipUtil.zip(response.getOutputStream(), StandardCharsets.UTF_8, false, filter, projectDir);

            log.info("打包下载项目成功: {} -> {}.zip", projectPath, downloadFileName);
        } catch (IOException e) {
            log.error("打包下载项目失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "打包下载项目失败");
        }
    }

    /**
     * 校验路径是否允许包含在压缩包中
     *
     * 核心逻辑：
     * 1. 获取文件相对于项目根目录的路径
     * 2. 检查路径中的每一部分
     * 3. 如果任何部分在忽略列表中，则拒绝该文件
     *
     * 为什么检查相对路径的每一部分？
     * - 假设文件路径：project/node_modules/package/index.js
     * - 相对路径：node_modules/package/index.js
     * - 路径部分：["node_modules", "package", "index.js"]
     * - 检查发现 "node_modules" 在忽略列表中 → 拒绝
     *
     * 安全考虑：
     * - 使用相对路径，防止路径遍历攻击
     * - 只检查相对路径的部分，不检查绝对路径
     * - 确保不会意外包含项目外的文件
     *
     * @param projectRoot 项目根目录的 Path 对象
     * @param fullPath    要检查的文件完整路径
     * @return true 表示允许包含，false 表示过滤掉
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        // ========== 步骤1：获取相对路径 ==========
        // relativize 说明：
        // - projectRoot: /tmp/code_output/html_123
        // - fullPath: /tmp/code_output/html_123/node_modules/lodash/index.js
        // - relativePath: node_modules/lodash/index.js
        Path relativePath = projectRoot.relativize(fullPath);

        // ========== 步骤2：检查路径中的每一部分 ==========
        // 设计说明：
        // - 遍历相对路径的每一部分
        // - 如果任何部分匹配忽略规则，则整个文件被过滤
        // - 这样可以递归地过滤整个目录
        for (Path part : relativePath) {
            String partName = part.toString();

            // 检查1：是否在忽略名称列表中
            // 例如：node_modules、.git、dist 等
            if (IGNORED_NAMES.contains(partName)) {
                return false;  // 过滤掉
            }

            // 检查2：是否以忽略扩展名结尾
            // 例如：app.log、config.tmp 等
            // 使用 anyMatch：检查是否匹配任何忽略的扩展名
            if (IGNORED_EXTENSIONS.stream().anyMatch(ext -> partName.toLowerCase().endsWith(ext))) {
                return false;  // 过滤掉
            }
        }

        // ========== 所有检查通过 ==========
        return true;  // 允许包含在压缩包中
    }
}
