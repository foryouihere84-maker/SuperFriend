package com.superfriend.superfriend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bash 沙箱配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "bash-sandbox")
public class SandboxConfig {

    /**
     * 是否启用 Bash 沙箱
     */
    private boolean enabled = true;

    /**
     * Linux 环境下的沙箱根目录
     */
    private String rootDir = "/tmp/superfriend_sandbox";

    /**
     * Windows 环境下的沙箱根目录
     * 默认使用系统临时目录
     */
    private String windowsRootDir;

    /**
     * 默认命令执行超时时间（毫秒）
     */
    private long defaultTimeoutMs = 60000;

    /**
     * 最大并发会话数
     */
    private int maxConcurrentSessions = 10;

    /**
     * 资源预留比例（0.0 - 1.0）
     * 系统会保留这部分资源不分配给沙箱
     */
    private double resourceReserveRatio = 0.2;

    /**
     * 会话超时时间（毫秒）
     * 超过此时间未活动的会话将被自动清理
     */
    private long sessionTimeoutMs = 30 * 60 * 1000;

    /**
     * 最大命令长度
     */
    private int maxCommandLength = 8192;

    /**
     * 最大输出大小（字节）
     */
    private long maxOutputBytes = 10 * 1024 * 1024;

    /**
     * 是否在 Windows 上禁用沙箱
     * Windows 上的功能有限，可以选择禁用
     */
    private boolean disableOnWindows = false;

    /**
     * MCP Server 名称
     */
    private String mcpServerName = "bash-sandbox";

    /**
     * 获取当前平台的沙箱根目录
     */
    public String getPlatformRootDir() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            if (windowsRootDir != null && !windowsRootDir.isEmpty()) {
                return windowsRootDir;
            }
            String tempDir = System.getProperty("java.io.tmpdir");
            return tempDir + "/superfriend_sandbox";
        }
        return rootDir;
    }

    /**
     * 判断当前平台是否支持完整沙箱功能
     */
    public boolean isFullFeatureSupported() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return !osName.contains("win");
    }
}
