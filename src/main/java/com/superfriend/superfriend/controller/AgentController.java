package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.service.AgentSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v16/agent")
public class AgentController {

    @Autowired
    private AgentSessionManager sessionManager;

    @PostMapping("/sessions/{sessionId}/interrupt")
    public ApiResponse<String> interruptSession(
            @PathVariable String sessionId,
            @RequestBody InterruptRequest request) {
        AgentSessionManager.InterruptMode mode = "cancel".equals(request.getMode()) 
                ? AgentSessionManager.InterruptMode.CANCEL 
                : AgentSessionManager.InterruptMode.APPEND;
        sessionManager.interrupt(sessionId, mode, request.getContext());
        return ApiResponse.success("中断请求已发送");
    }

    @GetMapping("/sessions/{sessionId}/status")
    public ApiResponse<Map<String, Object>> getSessionStatus(@PathVariable String sessionId) {
        Map<String, Object> status = new HashMap<>();
        status.put("sessionId", sessionId);
        status.put("isExecuting", sessionManager.isExecuting(sessionId));
        status.put("state", sessionManager.getSessionState(sessionId).name());
        return ApiResponse.success(status);
    }

    public static class InterruptRequest {
        private String mode; // "cancel" or "append"
        private String context; // 追加的内容（仅 append 模式需要）

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }
    }
}
