package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.service.ObservabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v16/observability")
@CrossOrigin(origins = "*")
@Tag(name = "可观测性", description = "Langfuse/Arize 可观测性集成管理")
public class ObservabilityController {

    @Autowired
    private ObservabilityService observabilityService;

    @GetMapping("/status")
    @Operation(summary = "获取可观测性状态", description = "获取 Langfuse 和 Arize 的启用状态")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("observabilityEnabled", observabilityService.isObservabilityEnabled());
        status.put("activeTraces", observabilityService.getActiveTracesCount());
        return ResponseEntity.ok(status);
    }

    @GetMapping("/trace/{traceId}")
    @Operation(summary = "获取追踪上下文", description = "获取指定追踪的上下文信息")
    public ResponseEntity<?> getTraceContext(@PathVariable String traceId) {
        ObservabilityService.TraceContext ctx = observabilityService.getTraceContext(traceId);
        if (ctx == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ctx);
    }
}
