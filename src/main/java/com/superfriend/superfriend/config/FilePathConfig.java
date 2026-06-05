package com.superfriend.superfriend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一文件路径配置
 * 集中管理所有文件路径，包括临时目录、沙箱目录、技能目录等
 *
 * 跨平台兼容性：
 * - 所有路径使用 File.separator 或 Paths.get() 处理
 * - 相对路径转换为绝对路径（基于应用工作目录）
 * - Linux 部署时自动适配路径格式
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.file")
public class FilePathConfig {

    /**
     * 应用根目录（用于计算相对路径的基准）
     * 默认使用当前工作目录
     */
    private String appHomeDir = System.getProperty("user.dir");

    /**
     * 临时文件目录（用户上传等）
     * 默认: uploads/temp
     */
    private String tempDir = "uploads/temp";

    /**
     * 沙箱根目录
     * 默认: 系统临时目录/superfriend_sandbox
     */
    private String sandboxDir = System.getProperty("java.io.tmpdir") + File.separator + "superfriend_sandbox";

    /**
     * 技能目录
     * 默认: skills/system
     */
    private String skillsDir = "skills/system";

    /**
     * 执行追踪目录
     * 默认: traces
     */
    private String traceDir = "traces";

    /**
     * 导出目录（用于导出沙箱文件）
     * 默认: 沙箱目录/exports
     */
    private String exportDir;

    /**
     * 共享目录（用于与其他 MCP 工具共享文件）
     * 默认: 沙箱目录/shared
     */
    private String sharedDir;

    /**
     * 技能输出目录（所有技能生成的文件统一存放）
     * 默认: uploads/skill_outputs
     */
    private String skillOutputDir = "uploads/skill_outputs";

    /**
     * 临时文件 TTL（小时）
     * 默认: 24小时
     */
    private long tempFileTtlHours = 24;

    /**
     * 获取应用根目录的绝对路径
     */
    public String getAppHomeDir() {
        return Paths.get(appHomeDir).toAbsolutePath().toString();
    }

    /**
     * 将相对路径转换为绝对路径
     * 如果已经是绝对路径，直接返回
     */
    private String toAbsolutePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return path;
        }
        return Paths.get(getAppHomeDir(), path).toAbsolutePath().toString();
    }

    /**
     * 获取临时目录（绝对路径）
     */
    public String getTempDir() {
        return toAbsolutePath(tempDir);
    }

    /**
     * 获取技能目录（绝对路径）
     */
    public String getSkillsDir() {
        return toAbsolutePath(skillsDir);
    }

    /**
     * 获取执行追踪目录（绝对路径）
     */
    public String getTraceDir() {
        return toAbsolutePath(traceDir);
    }

    /**
     * 获取导出目录（如果未设置则返回沙箱目录/exports）
     */
    public String getExportDir() {
        if (exportDir == null || exportDir.isEmpty()) {
            return sandboxDir + File.separator + "exports";
        }
        return toAbsolutePath(exportDir);
    }

    /**
     * 获取共享目录（如果未设置则返回沙箱目录/shared）
     */
    public String getSharedDir() {
        if (sharedDir == null || sharedDir.isEmpty()) {
            return sandboxDir + File.separator + "shared";
        }
        return toAbsolutePath(sharedDir);
    }

    /**
     * 获取技能输出目录（绝对路径，自动创建）
     */
    public String getSkillOutputDir() {
        String absolutePath = toAbsolutePath(skillOutputDir != null ? skillOutputDir : "uploads/skill_outputs");
        // 确保目录存在
        File dir = new File(absolutePath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("Created skill output directory: " + absolutePath);
            }
        }
        return absolutePath;
    }

    /**
     * 获取临时文件 TTL（毫秒）
     */
    public long getTempFileTtlMs() {
        return tempFileTtlHours * 60 * 60 * 1000;
    }

    /**
     * 获取平台相关的沙箱根目录
     * Windows 使用系统临时目录，Linux 也使用系统临时目录
     */
    public String getPlatformSandboxDir() {
        // 统一使用系统临时目录，确保跨平台兼容
        return System.getProperty("java.io.tmpdir") + File.separator + "superfriend_sandbox";
    }
}
