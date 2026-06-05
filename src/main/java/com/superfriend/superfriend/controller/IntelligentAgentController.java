package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.agent.IntelligentAgentService;
import com.superfriend.superfriend.agent.planner.*;
import com.superfriend.superfriend.agent.tool.*;
import com.superfriend.superfriend.agent.planner.TaskAnalysis;
import com.superfriend.superfriend.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/agent")
public class IntelligentAgentController {
    
    @Autowired
    private IntelligentAgentService agentService;
    
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processRequest(@RequestBody Map<String, String> request) {
        String userRequest = request.get("request");
        if (userRequest == null || userRequest.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Request parameter is required");
            return ResponseEntity.badRequest().body(error);
        }
        
        Map<String, Object> result = agentService.processRequest(userRequest);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/tools/suggest")
    public ResponseEntity<List<Tool>> suggestTools(@RequestParam String request) {
        List<Tool> suggestedTools = agentService.suggestTools(request);
        return ResponseEntity.ok(suggestedTools);
    }
    
    @GetMapping("/tools")
    public ResponseEntity<List<Tool>> getAllTools() {
        List<Tool> tools = agentService.getAllTools();
        return ResponseEntity.ok(tools);
    }
    
    @GetMapping("/tools/{name}")
    public ResponseEntity<Map<String, Object>> getTool(@PathVariable String name) {
        Tool tool = agentService.getTool(name);
        if (tool == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Tool not found: " + name);
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("name", tool.getName());
        result.put("description", tool.getDescription());
        result.put("category", tool.getCategory());
        result.put("cost", tool.getCost());
        result.put("reliability", tool.getReliability());
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/tools/{name}/execute")
    public ResponseEntity<Map<String, Object>> executeTool(
            @PathVariable String name,
            @RequestBody Map<String, Object> parameters) {
        Map<String, Object> result = agentService.executeTool(name, parameters);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/analyze")
    public ResponseEntity<TaskAnalysis> analyzeTask(@RequestBody Map<String, String> request) {
        String userRequest = request.get("request");
        if (userRequest == null || userRequest.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        Map<String, Object> result = agentService.processRequest(userRequest);
        TaskAnalysis analysis = (TaskAnalysis) result.get("analysis");
        return ResponseEntity.ok(analysis);
    }
}
