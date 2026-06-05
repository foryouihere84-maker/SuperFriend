package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.service.McpHostService;
import com.superfriend.superfriend.service.AgentSessionManager;
import com.superfriend.superfriend.service.AgentSessionManager.InterruptMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Host 控制器
 * 提供 MCP Servers 管理和工具调用接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v16/mcp")
@CrossOrigin(origins = "*")
@Tag(name = "MCP Host 管理", description = "MCP Servers 配置、工具调用接口")
public class MCPController {
    
    @Autowired
    private McpHostService mcpHostService;

    @Autowired
    private AgentSessionManager sessionManager;
    
    /**
     * 获取所有 MCP Servers 状态
     */
    @GetMapping("/servers")
    @Operation(summary = "获取 MCP 服务器状态", description = "返回所有运行中的 MCP 服务器列表和配置信息")
    public ApiResponse<Map<String, Object>> getServersStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("running", mcpHostService.getRunningServers());
            status.put("config", mcpHostService.loadConfig());
            return ApiResponse.success(status);
        } catch (Exception e) {
            log.error("获取服务器状态失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取服务器状态失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取可用工具列表
     */
    @GetMapping("/tools")
    @Operation(summary = "获取可用工具列表", description = "返回所有 MCP Servers 提供的可用工具列表")
    public ApiResponse<List<McpToolDefinition>> getTools() {
        try {
            List<McpToolDefinition> tools = mcpHostService.listTools();
            log.info("返回工具列表，共 {} 个工具", tools.size());
            return ApiResponse.success(tools);
        } catch (Exception e) {
            log.error("获取工具列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取工具列表失败：" + e.getMessage());
        }
    }
    
    /**
     * 启动指定 MCP Server
     */
    @PostMapping("/servers/{name}/start")
    @Operation(summary = "启动 MCP 服务器", description = "手动启动指定的 MCP Server")
    public ApiResponse<String> startServer(@PathVariable String name) {
        try {
            McpServersFile config = mcpHostService.loadConfig();
            
            if (config.getMcpServers() == null || !config.getMcpServers().containsKey(name)) {
                return ApiResponse.error("服务器配置不存在：" + name);
            }
            
            McpServerConfig serverConfig = config.getMcpServers().get(name);
            mcpHostService.startServer(name, serverConfig);
            
            return ApiResponse.success("服务器 '" + name + "' 已启动");
        } catch (Exception e) {
            log.error("启动服务器失败：{}", e.getMessage(), e);
            return ApiResponse.error("启动服务器失败：" + e.getMessage());
        }
    }

    /**
     * 启动精简配置中选中的所有 MCP Servers
     */
    @PostMapping("/servers/start-selected")
    @Operation(summary = "启动选中的服务器", description = "读取精简配置文件，启动其中未运行的选中服务器")
    public ApiResponse<String> startSelectedServers() {
        try {
            String result = mcpHostService.startSelectedServers();
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("启动选中服务器失败：{}", e.getMessage(), e);
            return ApiResponse.error("启动选中服务器失败：" + e.getMessage());
        }
    }

    /**
     * 停止指定 MCP Server
     */
    @PostMapping("/servers/{name}/stop")
    @Operation(summary = "停止 MCP 服务器", description = "手动停止指定的 MCP Server")
    public ApiResponse<String> stopServer(@PathVariable String name) {
        try {
            mcpHostService.stopServer(name);
            return ApiResponse.success("服务器 '" + name + "' 已停止");
        } catch (Exception e) {
            log.error("停止服务器失败：{}", e.getMessage(), e);
            return ApiResponse.error("停止服务器失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新并重新加载配置
     */
    @PostMapping("/reload-config")
    @Operation(summary = "重新加载配置", description = "更新 MCP Servers 配置并重启所有服务器")
    public ApiResponse<String> reloadConfig(@RequestBody(required = false) McpServersFile newConfig) {
        try {
            if (newConfig != null) {
                mcpHostService.saveConfig(newConfig);
                log.info("已保存新的 MCP 配置文件");
            }
            
            // 重启所有服务器
            mcpHostService.stopAllServers();
            Thread.sleep(1000); // 等待进程完全停止
            mcpHostService.initializeServers();
            
            return ApiResponse.success("配置已重新加载");
        } catch (Exception e) {
            log.error("重新加载配置失败：{}", e.getMessage(), e);
            return ApiResponse.error("重新加载配置失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取当前配置文件内容
     */
    @GetMapping("/config")
    @Operation(summary = "获取配置信息", description = "返回当前使用的 MCP Servers 配置")
    public ApiResponse<McpServersFile> getConfig() {
        try {
            McpServersFile config = mcpHostService.loadConfig();
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("获取配置文件失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取配置文件失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取配置文件路径信息
     */
    @GetMapping("/config/path")
    @Operation(summary = "获取配置路径", description = "返回配置文件的加载路径信息（精简配置 > classpath 或外部文件）")
    public ApiResponse<Map<String, String>> getConfigPath() {
        Map<String, String> paths = new HashMap<>();
        String userHome = System.getProperty("user.home");
        
        // ⭐ 精简配置路径（项目目录）
        paths.put("smallConfig", "src/main/resources/mcpserverconfig/mcp-servers-small-config.json");
        
        // classpath 默认配置路径
        paths.put("classpath", "mcpserverconfig/mcp-servers-config.json");
        
        // 外部可写配置路径
        paths.put("external", userHome + "/.harmonynotes/mcp-servers-config.json");
        
        // ⭐ 检查哪个存在（优先级：精简配置 > 外部配置 > classpath）
        File smallConfigFile = new File("src/main/resources/mcpserverconfig/mcp-servers-small-config.json");
        File externalFile = new File(userHome, ".harmonynotes/mcp-servers-config.json");
        
        if (smallConfigFile.exists()) {
            paths.put("active", "smallConfig");
            paths.put("activePath", smallConfigFile.getAbsolutePath());
        } else if (externalFile.exists()) {
            paths.put("active", "external");
            paths.put("activePath", externalFile.getAbsolutePath());
        } else {
            paths.put("active", "classpath");
            paths.put("activePath", "classpath:mcpserverconfig/mcp-servers-config.json");
        }
        
        return ApiResponse.success(paths);
    }
    
    /**
     * 刷新工具列表缓存
     */
    @PostMapping("/tools/refresh-cache")
    @Operation(summary = "刷新工具缓存", description = "强制重新获取所有 MCP Server 的工具列表并更新缓存")
    public ApiResponse<Map<String, Object>> refreshToolsCache() {
        try {
            log.info("收到刷新工具缓存请求");
            
            // 清空现有缓存
            // 注意：这里需要通过反射或新增方法来访问私有字段，暂时使用简单方案
            // 实际应该 McpHostService 中添加 refreshToolsCache() 方法
            mcpHostService.stopAllServers();
            Thread.sleep(500);
            mcpHostService.initializeServers();
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "工具缓存已刷新，服务器正在重启...");
            result.put("note", "请等待 3-5 秒后再次调用 /tools 接口获取新工具列表");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("刷新工具缓存失败：{}", e.getMessage(), e);
            return ApiResponse.error("刷新工具缓存失败：" + e.getMessage());
        }
    }
    
    /**
     * 直接调用工具（测试用）
     */
    @PostMapping("/tools/call")
    @Operation(summary = "调用工具", description = "直接调用指定的 MCP 工具（用于测试）")
    public ApiResponse<McpToolCallResponse> callTool(@RequestBody McpToolCallRequest request) {
        try {
            log.info("直接工具调用请求：server={}, tool={}, args={}", 
                request.getServerName(), 
                request.getToolName(),
                request.getArguments());
            
            McpToolCallResponse result = mcpHostService.callTool(
                request.getServerName(),
                request.getToolName(),
                request.getArguments()
            );
            
            if (result.isSuccess()) {
                log.info("工具调用成功：{}.{}", request.getServerName(), request.getToolName());
            } else {
                log.warn("工具调用失败：{}.{}, 错误：{}", 
                    request.getServerName(), request.getToolName(), result.getError());
            }
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("工具调用异常：{}", e.getMessage(), e);
            return ApiResponse.error("工具调用异常：" + e.getMessage());
        }
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查 MCP Host 运行状态")
    public ApiResponse<String> health() {
        return ApiResponse.success("MCP Host 运行正常，活跃服务器数：" + mcpHostService.getRunningServers().size());
    }
    
    /**
     * ⭐ 获取所有可用的 MCP 服务器列表（用于前端展示）
     */
    @GetMapping("/servers/available")
    @Operation(summary = "获取可用服务器列表", description = "返回配置文件中定义的所有启用的 MCP 服务器")
    public ApiResponse<Map<String, Object>> getAvailableServers() {
        try {
            log.info("开始获取可用服务器列表...");
            
            Map<String, Object> result = new HashMap<>();
            Map<String, McpServerConfig> availableServers = mcpHostService.getAllAvailableServers();
            log.info("获取到 {} 个可用服务器", availableServers.size());
            
            List<String> selectedServers = mcpHostService.getSelectedServers();
            log.info("当前选中 {} 个服务器：{}", selectedServers.size(), selectedServers);
            
            // 构建响应数据
            List<Map<String, Object>> serversList = new ArrayList<>();
            for (Map.Entry<String, McpServerConfig> entry : availableServers.entrySet()) {
                Map<String, Object> serverInfo = new HashMap<>();
                serverInfo.put("name", entry.getKey());
                serverInfo.put("description", entry.getValue().getDescription());
                serverInfo.put("selected", selectedServers.contains(entry.getKey()));
                
                // 获取该服务器的工具数量
                int toolCount = 0;
                if (mcpHostService.getRunningServers().contains(entry.getKey())) {
                    // 如果服务器正在运行，从缓存获取工具数
                    long count = mcpHostService.listTools().stream()
                        .filter(tool -> tool.getName().startsWith(entry.getKey() + "__"))
                        .count();
                    toolCount = (int) count;
                    log.debug("服务器 {} 的工具数：{}", entry.getKey(), toolCount);
                }
                serverInfo.put("toolCount", toolCount);
                serverInfo.put("running", mcpHostService.getRunningServers().contains(entry.getKey()));
                
                serversList.add(serverInfo);
            }
            
            result.put("servers", serversList);
            result.put("totalServers", availableServers.size());
            result.put("selectedCount", selectedServers.size());
            
            log.info("返回服务器列表，共 {} 个服务器", serversList.size());
            return ApiResponse.success(result);
            
        } catch (Exception e) {
            log.error("获取可用服务器列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取可用服务器列表失败：" + e.getMessage());
        }
    }
    
    /**
     * ⭐ 更新用户选中的 MCP 服务器列表
     */
    @PostMapping("/servers/selection")
    @Operation(summary = "更新服务器选择", description = "更新用户选中的 MCP 服务器列表")
    public ApiResponse<Map<String, Object>> updateServerSelection(@RequestBody List<String> serverNames) {
        try {
            log.info("收到服务器选择更新请求：{}", serverNames);
            mcpHostService.updateSelectedServers(serverNames);
            
            Map<String, Object> result = new HashMap<>();
            result.put("selectedServers", mcpHostService.getSelectedServers());
            result.put("message", "已更新选中的服务器，共 " + serverNames.size() + " 个");
            result.put("configFilePath", "src/main/resources/mcpserverconfig/mcp-servers-small-config.json");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("更新服务器选择失败：{}", e.getMessage(), e);
            return ApiResponse.error("更新服务器选择失败：" + e.getMessage());
        }
    }
    
    /**
     * ⭐ 选择单个 MCP 服务器
     */
    @PostMapping("/servers/{name}/select")
    @Operation(summary = "选择服务器", description = "将指定的 MCP 服务器添加到选中列表")
    public ApiResponse<String> selectServer(@PathVariable String name) {
        try {
            mcpHostService.selectServer(name);
            return ApiResponse.success("已选择服务器：" + name);
        } catch (Exception e) {
            log.error("选择服务器失败：{}", e.getMessage(), e);
            return ApiResponse.error("选择服务器失败：" + e.getMessage());
        }
    }
    
    /**
     * ⭐ 取消选择单个 MCP 服务器
     */
    @PostMapping("/servers/{name}/deselect")
    @Operation(summary = "取消选择服务器", description = "将指定的 MCP 服务器从选中列表中移除")
    public ApiResponse<String> deselectServer(@PathVariable String name) {
        try {
            mcpHostService.deselectServer(name);
            return ApiResponse.success("已取消选择服务器：" + name);
        } catch (Exception e) {
            log.error("取消选择服务器失败：{}", e.getMessage(), e);
            return ApiResponse.error("取消选择服务器失败：" + e.getMessage());
        }
    }
    
    /**
     * ⭐ 获取当前选中的 MCP 服务器配置（精简版）
     */
    @GetMapping("/servers/selected/config")
    @Operation(summary = "获取选中服务器配置", description = "返回当前选中的 MCP 服务器配置（精简版）")
    public ApiResponse<McpServersFile> getSelectedConfig() {
        try {
            McpServersFile selectedConfig = mcpHostService.getSelectedServersConfig();
            int serverCount = selectedConfig.getMcpServers() != null ? selectedConfig.getMcpServers().size() : 0;
            log.info("返回精简版 MCP 配置，包含 {} 个服务器", serverCount);
            return ApiResponse.success(selectedConfig);
        } catch (Exception e) {
            log.error("获取选中服务器配置失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取选中服务器配置失败：" + e.getMessage());
        }
    }
    
    /**
     * ⭐ 获取工具调用统计（集成差距2：智能工具调用机制）
     */
    @GetMapping("/stats/tool-usage")
    @Operation(summary = "获取工具调用统计", description = "返回工具调用成功率、平均执行时间等统计信息")
    public ApiResponse<Map<String, Object>> getToolUsageStats() {
        try {
            Map<String, Object> stats = mcpHostService.getToolUsageStats();
            log.info("返回工具调用统计：{}", stats);
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取工具调用统计失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取工具调用统计失败：" + e.getMessage());
        }
    }
    
    /**
     * ⭐ 获取错误统计（集成差距5：错误处理和恢复机制）
     */
    @GetMapping("/stats/errors")
    @Operation(summary = "获取错误统计", description = "返回错误类型、重试次数、恢复次数等统计信息")
    public ApiResponse<Map<String, Object>> getErrorStats() {
        try {
            Map<String, Object> stats = mcpHostService.getErrorStats();
            log.info("返回错误统计：{}", stats);
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取错误统计失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取错误统计失败：" + e.getMessage());
        }
    }
    
    /**
     * ⭐ 智能工具推荐（集成差距2：智能工具选择）
     */
    @GetMapping("/tools/recommend")
    @Operation(summary = "智能工具推荐", description = "根据用户请求智能推荐最合适的工具")
    public ApiResponse<List<Map<String, Object>>> recommendTools(@RequestParam String request) {
        try {
            log.info("收到工具推荐请求：{}", request);
            List<Map<String, Object>> recommendations = mcpHostService.recommendTools(request);
            log.info("返回 {} 个推荐工具", recommendations.size());
            return ApiResponse.success(recommendations);
        } catch (Exception e) {
            log.error("工具推荐失败：{}", e.getMessage(), e);
            return ApiResponse.error("工具推荐失败：" + e.getMessage());
        }
    }

    @PostMapping("/sessions/{sessionId}/interrupt")
    @Operation(summary = "中断Agent会话", description = "取消当前任务或追加上下文指令")
    public ApiResponse<String> interruptSession(
            @PathVariable String sessionId,
            @RequestBody AgentInterruptRequest request) {
        try {
            String modeStr = request.getMode();
            if (modeStr == null || modeStr.trim().isEmpty()) {
                return ApiResponse.error("缺少 mode 参数");
            }

            InterruptMode mode;
            try {
                mode = InterruptMode.valueOf(modeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ApiResponse.error("无效的 mode 参数，支持：cancel, append");
            }

            if (mode == InterruptMode.APPEND && (request.getContext() == null || request.getContext().trim().isEmpty())) {
                return ApiResponse.error("append 模式需要提供 context 参数");
            }

            if (!sessionManager.isExecuting(sessionId)) {
                return ApiResponse.error("当前会话未在执行中，无需中断");
            }

            sessionManager.interrupt(sessionId, mode, request.getContext());
            String msg = mode == InterruptMode.CANCEL ? "取消请求已发送" : "追加指令已发送";
            return ApiResponse.success(msg);
        } catch (Exception e) {
            log.error("中断会话失败：{}", e.getMessage(), e);
            return ApiResponse.error("中断会话失败：" + e.getMessage());
        }
    }

    @GetMapping("/sessions/{sessionId}/status")
    @Operation(summary = "获取会话状态", description = "返回当前Agent会话的执行状态")
    public ApiResponse<Map<String, Object>> getSessionStatus(@PathVariable String sessionId) {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("sessionId", sessionId);
            status.put("state", sessionManager.getSessionState(sessionId).name());
            status.put("isExecuting", sessionManager.isExecuting(sessionId));
            return ApiResponse.success(status);
        } catch (Exception e) {
            log.error("获取会话状态失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取会话状态失败：" + e.getMessage());
        }
    }
}
