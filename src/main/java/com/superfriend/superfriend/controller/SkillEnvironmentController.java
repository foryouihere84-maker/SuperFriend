package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.service.JwtService;
import com.superfriend.superfriend.service.SkillEnvironmentManager;
import com.superfriend.superfriend.service.SkillEnvironmentManager.EnvironmentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Skill 环境管理 API
 *
 * 注意：此接口需要 JWT Token 认证
 * 使用方式：在请求头中添加 Authorization: Bearer <token>
 */
@Slf4j
@RestController
@RequestMapping("/api/skills/environment")
@CrossOrigin(origins = "*")
@Tag(name = "Skill Environment", description = "Skill 环境管理接口")
public class SkillEnvironmentController {

    @Autowired
    private SkillEnvironmentManager environmentManager;

    @Autowired
    private JwtService jwtService;

    /**
     * 验证 JWT Token
     */
    private Long validateAndGetUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7);
        if (!jwtService.validateToken(token)) {
            return null;
        }

        return jwtService.getUserIdFromToken(token);
    }

    /**
     * 获取所有 skill 的环境状态
     */
    @GetMapping("/status")
    @Operation(summary = "获取所有环境状态", description = "返回所有 skill 的环境配置状态")
    public ApiResponse<Map<String, EnvironmentStatus>> getAllStatus(HttpServletRequest request) {
        Long userId = validateAndGetUserId(request);
        if (userId == null) {
            log.warn("未授权访问环境状态 API");
            return ApiResponse.error("未授权：请提供有效的 JWT Token");
        }

        log.info("用户 {} 请求获取所有环境状态", userId);
        Map<String, EnvironmentStatus> status = environmentManager.getAllEnvironmentStatus();
        return ApiResponse.success(status);
    }

    /**
     * 获取指定 skill 的环境状态
     */
    @GetMapping("/status/{skillName}")
    @Operation(summary = "获取指定 skill 环境状态")
    public ApiResponse<EnvironmentStatus> getStatus(
            @PathVariable String skillName,
            @RequestParam(defaultValue = "false") boolean autoSetup,
            HttpServletRequest request) {

        Long userId = validateAndGetUserId(request);
        if (userId == null) {
            return ApiResponse.error("未授权：请提供有效的 JWT Token");
        }

        EnvironmentStatus status = environmentManager.checkEnvironment(skillName, autoSetup);
        return ApiResponse.success(status);
    }

    /**
     * 检查并自动配置环境
     */
    @PostMapping("/check/{skillName}")
    @Operation(summary = "检查并自动配置环境", description = "检查 skill 环境依赖，如果缺失则自动安装")
    public ApiResponse<EnvironmentStatus> checkAndSetup(
            @PathVariable String skillName,
            HttpServletRequest request) {

        Long userId = validateAndGetUserId(request);
        if (userId == null) {
            return ApiResponse.error("未授权：请提供有效的 JWT Token");
        }

        log.info("用户 {} 请求检查并配置环境: {}", userId, skillName);
        EnvironmentStatus status = environmentManager.checkEnvironment(skillName, true);
        return ApiResponse.success(status);
    }

    /**
     * 强制重新配置环境
     */
    @PostMapping("/setup/{skillName}")
    @Operation(summary = "强制重新配置环境", description = "重新运行 skill 的环境配置脚本")
    public ApiResponse<EnvironmentStatus> forceSetup(
            @PathVariable String skillName,
            HttpServletRequest request) {

        Long userId = validateAndGetUserId(request);
        if (userId == null) {
            return ApiResponse.error("未授权：请提供有效的 JWT Token");
        }

        log.info("用户 {} 请求强制配置环境: {}", userId, skillName);
        EnvironmentStatus status = environmentManager.forceSetup(skillName);
        return ApiResponse.success(status);
    }

    /**
     * 清除环境缓存
     */
    @DeleteMapping("/cache")
    @Operation(summary = "清除环境缓存", description = "清除所有环境状态缓存，下次检查时会重新检测")
    public ApiResponse<Void> clearCache(HttpServletRequest request) {
        Long userId = validateAndGetUserId(request);
        if (userId == null) {
            return ApiResponse.error("未授权：请提供有效的 JWT Token");
        }

        log.info("用户 {} 请求清除环境缓存", userId);
        environmentManager.clearCache();
        return ApiResponse.success(null);
    }
}
