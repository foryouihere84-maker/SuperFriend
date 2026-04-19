package com.superfriend.superfriend.agent.tool;

import java.util.List;
import java.util.Map;

public interface ToolSelector {
    List<Tool> selectTools(String userRequest, TaskContext context);
    Tool selectBestTool(List<Tool> candidates, Map<String, Object> parameters);
}
