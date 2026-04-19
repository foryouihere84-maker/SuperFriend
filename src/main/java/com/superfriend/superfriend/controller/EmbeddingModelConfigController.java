package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.dto.EmbeddingModelConfigDTO;
import com.superfriend.superfriend.dto.EmbeddingModelConfigVO;
import com.superfriend.superfriend.service.EmbeddingModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/embedding-config")
@CrossOrigin(origins = "*")
@Tag(name = "Embedding模型配置管理", description = "Embedding 模型配置的增删改查")
public class EmbeddingModelConfigController {

    @Autowired
    private EmbeddingModelConfigService configService;

    @GetMapping("/all")
    @Operation(summary = "获取所有配置", description = "获取所有 Embedding 模型配置")
    public ApiResponse<List<EmbeddingModelConfigVO>> getAllConfigs() {
        try {
            List<EmbeddingModelConfigVO> configs = configService.getAllConfigs();
            return ApiResponse.success(configs);
        } catch (Exception e) {
            log.error("获取 Embedding 配置列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取配置列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/enabled")
    @Operation(summary = "获取启用的配置", description = "获取所有启用的 Embedding 模型配置")
    public ApiResponse<List<EmbeddingModelConfigVO>> getEnabledConfigs() {
        try {
            List<EmbeddingModelConfigVO> configs = configService.getEnabledConfigs();
            return ApiResponse.success(configs);
        } catch (Exception e) {
            log.error("获取启用的 Embedding 配置列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取配置列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/{configId}")
    @Operation(summary = "获取配置详情", description = "根据 configId 获取 Embedding 模型配置详情")
    public ApiResponse<EmbeddingModelConfigVO> getConfig(@PathVariable String configId) {
        try {
            EmbeddingModelConfigVO config = configService.getConfig(configId);
            if (config == null) {
                return ApiResponse.error("配置不存在");
            }
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("获取 Embedding 配置详情失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取配置详情失败：" + e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "创建配置", description = "创建新的 Embedding 模型配置")
    public ApiResponse<EmbeddingModelConfigVO> createConfig(@RequestBody EmbeddingModelConfigDTO dto) {
        try {
            if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                return ApiResponse.error("配置名称不能为空");
            }
            if (dto.getApiUrl() == null || dto.getApiUrl().trim().isEmpty()) {
                return ApiResponse.error("API 地址不能为空");
            }
            if (dto.getModelId() == null || dto.getModelId().trim().isEmpty()) {
                return ApiResponse.error("模型标识不能为空");
            }
            EmbeddingModelConfigVO config = configService.createConfig(dto);
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("创建 Embedding 配置失败：{}", e.getMessage(), e);
            return ApiResponse.error("创建配置失败：" + e.getMessage());
        }
    }

    @PutMapping("/{configId}")
    @Operation(summary = "更新配置", description = "更新 Embedding 模型配置")
    public ApiResponse<EmbeddingModelConfigVO> updateConfig(
            @PathVariable String configId,
            @RequestBody EmbeddingModelConfigDTO dto) {
        try {
            EmbeddingModelConfigVO config = configService.updateConfig(configId, dto);
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("更新 Embedding 配置失败：{}", e.getMessage(), e);
            return ApiResponse.error("更新配置失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{configId}")
    @Operation(summary = "删除配置", description = "删除 Embedding 模型配置")
    public ApiResponse<Void> deleteConfig(@PathVariable String configId) {
        try {
            configService.deleteConfig(configId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除 Embedding 配置失败：{}", e.getMessage(), e);
            return ApiResponse.error("删除配置失败：" + e.getMessage());
        }
    }

    @PutMapping("/{configId}/default")
    @Operation(summary = "设为默认配置", description = "将指定配置设为默认")
    public ApiResponse<Void> setDefaultConfig(@PathVariable String configId) {
        try {
            configService.setDefaultConfig(configId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("设置默认配置失败：{}", e.getMessage(), e);
            return ApiResponse.error("设置默认配置失败：" + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新缓存", description = "刷新 Embedding 配置缓存")
    public ApiResponse<Map<String, Object>> refreshCache() {
        try {
            configService.refreshCache();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "缓存刷新成功");
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("刷新缓存失败：{}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "刷新失败：" + e.getMessage());
            return ApiResponse.success(result);
        }
    }
}
