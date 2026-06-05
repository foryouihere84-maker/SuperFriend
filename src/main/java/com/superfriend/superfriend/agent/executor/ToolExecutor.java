package com.superfriend.superfriend.agent.executor;

import com.superfriend.superfriend.agent.retry.RetryStrategy;
import com.superfriend.superfriend.agent.tool.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final RetryStrategy retryStrategy;

    public ToolExecutor(ToolRegistry toolRegistry, RetryStrategy retryStrategy) {
        this.toolRegistry = toolRegistry;
        this.retryStrategy = retryStrategy;
    }

    public ToolResult executeTool(Tool tool, Map<String, Object> parameters) {
        if (!tool.validateParameters(parameters)) {
            return ToolResult.failure("Invalid parameters", 0);
        }

        return retryStrategy.executeWithRetry(() -> {
            long startTime = System.currentTimeMillis();
            ToolResult result = tool.execute(parameters);
            long executionTime = System.currentTimeMillis() - startTime;

            toolRegistry.updateToolReliability(tool.getName(), result.isSuccess());

            return result;
        }, tool.getName());
    }
}
