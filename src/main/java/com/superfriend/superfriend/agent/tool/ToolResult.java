package com.superfriend.superfriend.agent.tool;

public class ToolResult {
    private boolean success;
    private Object data;
    private String error;
    private long executionTime;
    
    public static ToolResult success(Object data, long executionTime) {
        ToolResult result = new ToolResult();
        result.success = true;
        result.data = data;
        result.executionTime = executionTime;
        return result;
    }
    
    public static ToolResult failure(String error, long executionTime) {
        ToolResult result = new ToolResult();
        result.success = false;
        result.error = error;
        result.executionTime = executionTime;
        return result;
    }
    
    public boolean isSuccess() { return success; }
    public Object getData() { return data; }
    public String getError() { return error; }
    public long getExecutionTime() { return executionTime; }
}
