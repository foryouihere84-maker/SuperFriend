package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.Map;

/**
 * MCP Servers 配置文件结构
 */
@Data
public class McpServersFile {
    /**
     * MCP Servers 映射表，key 为 server 名称
     */
    private Map<String, McpServerConfig> mcpServers;
}
