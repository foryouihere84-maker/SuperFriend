package com.superfriend.superfriend.agent.retry;

import com.superfriend.superfriend.agent.tool.ToolResult;

@FunctionalInterface
public interface RetryableOperation {
    ToolResult execute() throws Exception;
}
