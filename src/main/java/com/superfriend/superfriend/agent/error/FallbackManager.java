package com.superfriend.superfriend.agent.error;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FallbackManager {
    
    private final Map<String, List<String>> fallbackChains;
    
    public FallbackManager() {
        this.fallbackChains = new HashMap<>();
        initializeFallbackChains();
    }
    
    private void initializeFallbackChains() {
        fallbackChains.put("web_search", Arrays.asList("web_search_backup", "local_search"));
        fallbackChains.put("data_analysis", Arrays.asList("data_analysis_backup", "simple_analysis"));
        fallbackChains.put("image_processing", Arrays.asList("image_processing_backup", "basic_image_ops"));
    }
    
    public String getFallbackTool(String primaryTool) {
        List<String> chain = fallbackChains.get(primaryTool);
        if (chain == null || chain.isEmpty()) {
            return null;
        }
        return chain.get(0);
    }
    
    public List<String> getFallbackChain(String primaryTool) {
        return fallbackChains.getOrDefault(primaryTool, Collections.emptyList());
    }
    
    public void registerFallbackChain(String primaryTool, List<String> fallbackTools) {
        fallbackChains.put(primaryTool, new ArrayList<>(fallbackTools));
    }
    
    public boolean hasFallback(String toolName) {
        return fallbackChains.containsKey(toolName) && 
               !fallbackChains.get(toolName).isEmpty();
    }
}
