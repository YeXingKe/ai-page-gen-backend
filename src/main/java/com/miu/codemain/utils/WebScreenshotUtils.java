package com.miu.codemain.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.miu.codemain.exception.BusinessException;
import com.miu.codemain.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

/**
 * 网页截图工具类
 *
 * 核心功能：
 * 1. 使用 Selenium WebDriver 自动化浏览器
 * 2. 访问指定 URL 并等待页面加载完成
 * 3. 截取页面截图
 * 4. 压缩图片以减少存储空间
 *
 * 使用场景：
 * - 应用部署后自动生成预览截图
 * - 为应用列表页面提供缩略图
 * - 无需手动截图，提升用户体验
 *
 * 技术实现：
 * - 使用 Chrome 无头浏览器（Headless Mode）
 * - 全局单例 WebDriver，避免重复初始化
 * - 自动等待页面加载完成
 * - PNG 转 JPG 压缩（压缩率 70%）
 *
 * 注意事项：
 * - 需要安装 Chrome 浏览器
 * - 使用 WebDriverManager 自动管理驱动
 * - 截图质量为原生的 30%，适合预览场景
 */
@Slf4j
public class WebScreenshotUtils {

    // ========== 全局 WebDriver 实例 ==========
    // 设计说明：
    // - 使用静态变量，全局共享一个 WebDriver 实例
    // - 避免重复初始化的开销（启动浏览器很耗时）
    // - 适用于单线程或低并发场景
    // - 如果并发量大，考虑使用 WebDriver 连接池
    private static final WebDriver webDriver;

    // ========== 静态初始化块 ==========
    // 设计说明：
    // - 类加载时自动执行，只执行一次
    // - 初始化 Chrome 浏览器驱动
    // - 如果初始化失败，会抛出异常，应用无法启动
    static {
        final int DEFAULT_WIDTH = 1600;   // 默认宽度：适合大多数网页
        final int DEFAULT_HEIGHT = 900;   // 默认高度：适合大多数网页
        webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * 应用关闭时销毁 WebDriver
     *
     * 设计说明：
     * - @PreDestroy：Spring 容器销毁 Bean 时调用
     * - 释放浏览器资源，关闭 Chrome 进程
     * - 如果不关闭，Chrome 进程会一直占用内存
     */
    @PreDestroy
    public void destroy() {
        webDriver.quit();
    }

    /**
     * 生成网页截图（核心方法）
     *
     * 执行流程：
     * 1. 参数校验（URL 不能为空）
     * 2. 创建临时目录（用于存储截图）
     * 3. 访问网页（使用 WebDriver）
     * 4. 等待页面加载完成
     * 5. 截取全屏截图
     * 6. 保存原始 PNG 图片
     * 7. 压缩为 JPG（减小文件大小）
     * 8. 删除原始 PNG，返回 JPG 路径
     *
     * 目录结构：
     * tmp/screenshots/{随机8位}/{随机5位}.png
     * tmp/screenshots/{随机8位}/{随机5位}_compressed.jpg
     *
     * @param webUrl 要截图的网址
     * @return 压缩后的截图文件路径（JPG），失败返回 null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        // ========== 步骤1：非空校验 ==========
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页截图失败，url为空");
            return null;
        }

        // ========== 步骤2：创建临时目录 ==========
        // 设计说明：
        // - 使用 UUID 避免目录名冲突
        // - 每次截图使用不同目录，便于清理
        try {
            String rootPath = System.getProperty("user.dir") + "/tmp/screenshots/" + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);

            // ========== 步骤3：准备文件路径 ==========
            final String IMAGE_SUFFIX = ".png";
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;

            // ========== 步骤4：访问网页 ==========
            webDriver.get(webUrl);

            // ========== 步骤5：等待页面加载 ==========
            waitForPageLoad(webDriver);

            // ========== 步骤6：截取全屏 ==========
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);

            // ========== 步骤7：保存原始图片 ==========
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功：{}", imageSavePath);

            // ========== 步骤8：压缩图片 ==========
            final String COMPRESS_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESS_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功：{}", compressedImagePath);

            // ========== 步骤9：删除原始图片 ==========
            // 设计说明：PNG 文件较大，压缩后删除，节省空间
            FileUtil.del(imageSavePath);

            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败：{}", webUrl, e);
            return null;
        }
    }

    /**
     * 初始化 Chrome 浏览器驱动
     *
     * Chrome 选项说明：
     * - --headless：无头模式，不显示浏览器窗口
     * - --disable-gpu：禁用 GPU，避免某些环境下的兼容性问题
     * - --no-sandbox：禁用沙盒，Docker 环境必需
     * - --disable-dev-shm-usage：禁用 /dev/shm，Docker 环境推荐
     * - --window-size：设置窗口大小，影响截图尺寸
     * - --disable-extensions：禁用扩展，加快启动速度
     * - --user-agent：设置用户代理，模拟真实浏览器
     *
     * @param width  浏览器窗口宽度（像素）
     * @param height 浏览器窗口高度（像素）
     * @return 初始化好的 WebDriver 实例
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // ========== 步骤1：自动管理 ChromeDriver ==========
            // 设计说明：WebDriverManager 会自动下载匹配的 ChromeDriver
            // 无需手动下载和配置
            WebDriverManager.chromedriver().setup();

            // ========== 步骤2：配置 Chrome 选项 ==========
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");                              // 无头模式
            options.addArguments("--disable-gpu");                           // 禁用 GPU
            options.addArguments("--no-sandbox");                            // 禁用沙盒（Docker 必需）
            options.addArguments("--disable-dev-shm-usage");                 // 禁用 /dev/shm
            options.addArguments(String.format("--window-size=%d,%d", width, height));  // 窗口大小
            options.addArguments("--disable-extensions");                    // 禁用扩展
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            // ========== 步骤3：创建 WebDriver ==========
            WebDriver driver = new ChromeDriver(options);

            // ========== 步骤4：设置超时时间 ==========
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));   // 页面加载超时：30秒
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));    // 隐式等待：10秒

            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 保存图片到文件
     *
     * @param imageBytes 图片字节数组
     * @param imagePath 保存路径
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败：{}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     *
     * 压缩说明：
     * - PNG → JPG：格式转换可以大幅减小文件大小
     * - 质量 30%：压缩率约 70%，适合预览场景
     * - 原始 PNG 可能有几 MB，压缩后 JPG 只有几百 KB
     *
     * @param originImagePath      原始图片路径（PNG）
     * @param compressedImagePath 压缩后图片路径（JPG）
     */
    private static void compressImage(String originImagePath, String compressedImagePath) {
        // 压缩质量：0.3 = 30%，意味着保留 30% 的质量
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败：{} -> {}", originImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     *
     * 等待策略：
     * 1. 等待 document.readyState 变为 "complete"
     * 2. 额外等待 2 秒，确保动态内容加载完成
     *
     * 为什么需要额外等待？
     * - document.readyState 为 complete 只表示 HTML 解析完成
     * - 现代网页常有异步加载的内容（AJAX、懒加载图片等）
     * - 额外等待可以提高截图的完整性
     *
     * @param webDriver WebDriver 实例
     */
    private static void waitForPageLoad(WebDriver webDriver) {
        try {
            // ========== 步骤1：等待 document.readyState = "complete" ==========
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
            wait.until(driver -> ((JavascriptExecutor) driver)
                    .executeScript("return document.readyState")
                    .equals("complete")
            );

            // ========== 步骤2：额外等待动态内容 ==========
            Thread.sleep(2000);  // 2 秒额外等待
            log.info("页面加载完成");
        } catch (Exception e) {
            // 等待失败不影响截图，记录日志后继续
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }
}
