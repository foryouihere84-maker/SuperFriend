package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.service.BashSandboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HealthController {

    @Autowired(required = false)
    private BashSandboxService bashSandboxService;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        log.info("健康检查请求");
        
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "UP");
        healthInfo.put("timestamp", LocalDateTime.now().toString());
        healthInfo.put("service", "HarmonyNotes");
        healthInfo.put("version", "1.0.0");
        
        return ApiResponse.success(healthInfo);
    }

    @GetMapping("/health/sandbox")
    public ApiResponse<Map<String, Object>> sandboxHealth() {
        log.info("沙箱健康检查请求");
        
        Map<String, Object> healthInfo = new HashMap<>();
        
        if (bashSandboxService == null) {
            healthInfo.put("status", "DISABLED");
            healthInfo.put("message", "Bash 沙箱服务未启用");
            return ApiResponse.success(healthInfo);
        }

        boolean available = bashSandboxService.isAvailable();
        
        healthInfo.put("status", available ? "UP" : "DOWN");
        healthInfo.put("timestamp", LocalDateTime.now().toString());
        healthInfo.put("available", available);
        
        try {
            List<BashSandboxService.SessionInfo> sessions = bashSandboxService.listSessions().get();
            healthInfo.put("activeSessions", sessions != null ? sessions.size() : 0);
        } catch (Exception e) {
            healthInfo.put("activeSessions", 0);
            healthInfo.put("sessionError", e.getMessage());
        }
        
        return ApiResponse.success(healthInfo);
    }
}
