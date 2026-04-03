package com.miu.codemain.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 项目下载服务接口
 *
 * 核心职责：
 * 1. 将生成的项目代码打包成 ZIP 文件
 * 2. 通过 HTTP 响应流直接返回给用户
 * 3. 过滤不需要的文件（如 node_modules、.git 等）
 *
 * 使用场景：
 * - 用户生成代码后，想要下载完整的源码
 * - 用户想要在本地继续编辑或部署
 * - 作为代码生成的配套功能
 *
 * 设计要点：
 * - 流式下载：边压缩边返回，不占用服务器磁盘空间
 * - 智能过滤：自动过滤开发过程中产生的临时文件和依赖
 * - 内存友好：使用流式处理，不会一次性加载所有文件到内存
 *
 * @author <a href="https://github.com/YeXingKe">野行客</a>
 */
public interface ProjectDownloadService {

    /**
     * 下载项目为压缩包
     *
     * 方法流程：
     * 1. 校验项目路径是否存在
     * 2. 设置 HTTP 响应头（Content-Type: application/zip）
     * 3. 设置下载文件名（Content-Disposition: attachment）
     * 4. 过滤不需要的文件（node_modules、.git 等）
     * 5. 将过滤后的文件压缩并写入响应流
     *
     * 过滤规则：
     * - 目录过滤：node_modules、.git、dist、build 等
     * - 文件过滤：.log、.tmp、.cache 等
     * - 确保下载包干净、轻量
     *
     * @param projectPath       项目根目录的绝对路径
     *                          例如：/tmp/code_output/html_123
     * @param downloadFileName  下载后的文件名（不含扩展名）
     *                          例如：my-webapp
     *                          最终下载文件：my-webapp.zip
     * @param response          HTTP 响应对象，用于写入 ZIP 数据流
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
