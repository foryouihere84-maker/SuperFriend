package com.superfriend.superfriend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 统一文件路径配置
 * 集中管理所有文件路径，包括临时目录、沙箱目录、技能目录等
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.file")
public class FilePathConfig {

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
     * 临时文件 TTL（小时）
     * 默认: 24小时
     */
    private long tempFileTtlHours = 24;

    /**
     * 获取导出目录（如果未设置则返回沙箱目录/exports）
     */
    public String getExportDir() {
        if (exportDir == null || exportDir.isEmpty()) {
            return sandboxDir + File.separator + "exports";
        }
        return exportDir;
    }

    /**
     * 获取共享目录（如果未设置则返回沙箱目录/shared）
     */
    public String getSharedDir() {
        if (sharedDir == null || sharedDir.isEmpty()) {
            return sandboxDir + File.separator + "shared";
        }
        return sharedDir;
    }

    /**
     * 获取临时文件 TTL（毫秒）
     */
    public long getTempFileTtlMs() {
        return tempFileTtlHours * 60 * 60 * 1000;
    }

    /**
     * 获取平台相关的沙箱根目录
     * Windows 使用 windows-root-dir，其他系统使用 root-dir
     */
    public String getPlatformSandboxDir() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            // Windows 使用系统临时目录
            return System.getProperty("java.io.tmpdir") + File.separator + "superfriend_sandbox";
        }
        return sandboxDir;
    }
}
