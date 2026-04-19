package com.superfriend.superfriend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ServerCapabilities {
    private boolean hasTools;
    private boolean hasResources;
    private boolean hasPrompts;
    private boolean supportsResourceSubscribe;
    private boolean supportsResourceListChanged;
    private boolean supportsToolListChanged;
    private boolean supportsPromptListChanged;
    private boolean supportsLogging;
    private String protocolVersion;
    private String serverName;
    private String serverVersion;

    public static ServerCapabilities fromInitializeResponse(JsonNode response) {
        ServerCapabilities caps = new ServerCapabilities();
        
        JsonNode result = response.path("result");
        caps.protocolVersion = result.path("protocolVersion").asText("2024-11-05");
        
        JsonNode serverInfo = result.path("serverInfo");
        caps.serverName = serverInfo.path("name").asText("unknown");
        caps.serverVersion = serverInfo.path("version").asText("1.0.0");
        
        JsonNode capabilities = result.path("capabilities");
        
        if (capabilities.has("tools")) {
            caps.hasTools = true;
            JsonNode tools = capabilities.get("tools");
            caps.supportsToolListChanged = tools.has("listChanged") && tools.get("listChanged").asBoolean();
        }
        
        if (capabilities.has("resources")) {
            caps.hasResources = true;
            JsonNode resources = capabilities.get("resources");
            caps.supportsResourceSubscribe = resources.has("subscribe") && resources.get("subscribe").asBoolean();
            caps.supportsResourceListChanged = resources.has("listChanged") && resources.get("listChanged").asBoolean();
        }
        
        if (capabilities.has("prompts")) {
            caps.hasPrompts = true;
            JsonNode prompts = capabilities.get("prompts");
            caps.supportsPromptListChanged = prompts.has("listChanged") && prompts.get("listChanged").asBoolean();
        }
        
        caps.supportsLogging = capabilities.has("logging");
        
        return caps;
    }
}
