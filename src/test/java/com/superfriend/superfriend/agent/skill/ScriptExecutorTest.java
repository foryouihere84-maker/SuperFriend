package com.superfriend.superfriend.agent.skill;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScriptExecutorTest {
    
    private final ScriptExecutor executor = new ScriptExecutor();
    
    @Test
    void testExecuteScript_FileNotFound() {
        Map<String, Object> params = new HashMap<>();
        File workingDir = new File(System.getProperty("user.dir"));
        
        ScriptExecutor.ScriptExecutionResult result = executor.executeScript(
            "/nonexistent/script.py",
            params,
            workingDir,
            10000
        );
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }
    
    @Test
    void testExecuteScript_EmptyParameters() {
        Map<String, Object> params = new HashMap<>();
        File workingDir = new File(System.getProperty("user.dir"));
        
        ScriptExecutor.ScriptExecutionResult result = executor.executeScript(
            "src/test/resources/test-script.py",
            params,
            workingDir,
            10000
        );
        
        assertNotNull(result);
    }
    
    @Test
    void testExecuteScript_WithParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("test", "value");
        
        File workingDir = new File(System.getProperty("user.dir"));
        
        ScriptExecutor.ScriptExecutionResult result = executor.executeScript(
            "src/test/resources/test-script.py",
            params,
            workingDir,
            10000
        );
        
        assertNotNull(result);
    }
    
    @Test
    void testExecuteScript_Timeout() {
        Map<String, Object> params = new HashMap<>();
        File workingDir = new File(System.getProperty("user.dir"));
        
        ScriptExecutor.ScriptExecutionResult result = executor.executeScript(
            "src/test/resources/slow-script.py",
            params,
            workingDir,
            100
        );
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("timeout"));
    }
}
