package com.superfriend.superfriend.agent.tool;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    String getCategory();
    double getCost();
    double getReliability();
    ToolResult execute(Map<String, Object> parameters);
    boolean validateParameters(Map<String, Object> parameters);
}
