package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinitionForLLM {
    private String name;
    private String description;
    private Map<String, Object> parameters;

    public static ToolDefinitionForLLM fromMcpTool(McpToolDefinition mcpTool) {
        if (mcpTool == null) return null;
        return ToolDefinitionForLLM.builder()
            .name(mcpTool.getName())
            .description(truncateDescription(mcpTool.getDescription()))
            .parameters(buildParameters(mcpTool))
            .build();
    }

    private static String truncateDescription(String desc) {
        if (desc == null) return "";
        return desc.length() > 200 ? desc.substring(0, 197) + "..." : desc;
    }

    private static Map<String, Object> buildParameters(McpToolDefinition mcpTool) {
        Map<String, Object> params = new java.util.HashMap<>();
        McpToolDefinition.JsonSchema schema = mcpTool.getInputSchema();
        params.put("type", schema != null && schema.getType() != null ? schema.getType() : "object");
        if (schema != null && schema.getProperties() != null) {
            params.put("properties", schema.getProperties());
        } else {
            params.put("properties", new java.util.HashMap<>());
        }
        if (schema != null && schema.getRequired() != null && schema.getRequired().length > 0) {
            params.put("required", java.util.Arrays.asList(schema.getRequired()));
        }
        return params;
    }
}
