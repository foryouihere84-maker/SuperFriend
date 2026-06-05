package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.AIModelConfigDTO;
import com.superfriend.superfriend.dto.AIModelConfigVO;
import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.entity.AIModelConfig;
import com.superfriend.superfriend.service.AIModelConfigService;
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
@RequestMapping("/api/v16/model-config")
@CrossOrigin(origins = "*")
@Tag(name = "模型配置管理", description = "AI 模型配置的增删改查与切换")
<<<<<<< HEAD
public class
AIModelConfigController {
=======
public class AIModelConfigController {
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

    @Autowired
    private AIModelConfigService modelConfigService;

    @GetMapping("/available")
    @Operation(summary = "获取可用模型列表", description = "获取系统默认 + 用户自定义的所有可用模型")
    public ApiResponse<List<AIModelConfigVO>> getAvailableModels(@RequestParam(required = false) Long userId) {
        try {
            List<AIModelConfigVO> models = modelConfigService.getAvailableModels(userId);
            return ApiResponse.success(models);
        } catch (Exception e) {
            log.error("获取可用模型列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取可用模型列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/user")
    @Operation(summary = "获取用户自定义模型列表", description = "获取当前用户创建的所有模型配置")
    public ApiResponse<List<AIModelConfigVO>> getUserModels(@RequestParam Long userId) {
        try {
            List<AIModelConfigVO> models = modelConfigService.getUserModels(userId);
            return ApiResponse.success(models);
        } catch (Exception e) {
            log.error("获取用户模型列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取用户模型列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/{configId}")
    @Operation(summary = "获取模型详情", description = "根据 configId 获取模型配置详情")
    public ApiResponse<AIModelConfigVO> getModel(@PathVariable String configId, @RequestParam(required = false) Long userId) {
        try {
            AIModelConfigVO model = modelConfigService.getModel(configId, userId);
            if (model == null) {
                return ApiResponse.error("模型配置不存在");
            }
            return ApiResponse.success(model);
        } catch (Exception e) {
            log.error("获取模型详情失败：{}", e.getMessage(), e);
            return ApiResponse.error("获取模型详情失败：" + e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "创建模型配置", description = "用户创建新的模型配置")
    public ApiResponse<AIModelConfigVO> createModel(@RequestBody AIModelConfigDTO dto, @RequestParam Long userId) {
        try {
            if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                return ApiResponse.error("模型名称不能为空");
            }
            if (dto.getApiUrl() == null || dto.getApiUrl().trim().isEmpty()) {
                return ApiResponse.error("API 地址不能为空");
            }
            if (dto.getModelId() == null || dto.getModelId().trim().isEmpty()) {
                return ApiResponse.error("模型标识不能为空");
            }
            AIModelConfigVO model = modelConfigService.createModel(dto, userId);
            return ApiResponse.success(model);
        } catch (Exception e) {
            log.error("创建模型配置失败：{}", e.getMessage(), e);
            return ApiResponse.error("创建模型配置失败：" + e.getMessage());
        }
    }

    @PutMapping("/{configId}")
    @Operation(summary = "更新模型配置", description = "更新用户自定义的模型配置")
    public ApiResponse<AIModelConfigVO> updateModel(
            @PathVariable String configId,
            @RequestBody AIModelConfigDTO dto,
            @RequestParam Long userId) {
        try {
            AIModelConfigVO model = modelConfigService.updateModel(configId, dto, userId);
            return ApiResponse.success(model);
        } catch (Exception e) {
            log.error("更新模型配置失败：{}", e.getMessage(), e);
            return ApiResponse.error("更新模型配置失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{configId}")
    @Operation(summary = "删除模型配置", description = "删除用户自定义的模型配置")
    public ApiResponse<Void> deleteModel(@PathVariable String configId, @RequestParam Long userId) {
        try {
            modelConfigService.deleteModel(configId, userId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除模型配置失败：{}", e.getMessage(), e);
            return ApiResponse.error("删除模型配置失败：" + e.getMessage());
        }
    }

    @PutMapping("/{configId}/default")
    @Operation(summary = "设为默认模型", description = "将指定模型设为当前用户的默认模型")
    public ApiResponse<Void> setDefaultModel(@PathVariable String configId, @RequestParam Long userId) {
        try {
            modelConfigService.setDefaultModel(configId, userId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("设置默认模型失败：{}", e.getMessage(), e);
            return ApiResponse.error("设置默认模型失败：" + e.getMessage());
        }
    }

    @PostMapping("/test")
    @Operation(summary = "测试模型连接", description = "测试模型配置的 API 连接是否可用")
    public ApiResponse<Map<String, Object>> testConnection(@RequestBody AIModelConfigDTO dto) {
        try {
            Map<String, Object> result = modelConfigService.testConnection(dto);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("测试连接失败：{}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "测试失败：" + e.getMessage());
            return ApiResponse.success(result);
        }
    }

    @GetMapping("/test/{configId}")
    @Operation(summary = "按配置ID测试连接", description = "使用数据库中存储的真实 API Key 测试连接")
    public ApiResponse<Map<String, Object>> testConnectionByConfigId(@PathVariable String configId) {
        try {
            AIModelConfig config = modelConfigService.getModelEntity(configId, null);
            if (config == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "配置不存在");
                return ApiResponse.success(result);
            }

            AIModelConfigDTO dto = new AIModelConfigDTO();
            dto.setApiUrl(config.getApiUrl());
            dto.setApiKey(config.getApiKey());
            dto.setModelId(config.getModelId());
            dto.setProvider(config.getProvider());
            dto.setSupportedModalities(config.getSupportedModalities());

            String keyPreview = dto.getApiKey() != null && dto.getApiKey().length() > 4
                    ? dto.getApiKey().substring(0, 4) + "****"
                    : "(empty)";
            log.info("测试连接 configId={}, apiUrl={}, modelId={}, apiKey={}", configId, dto.getApiUrl(), dto.getModelId(), keyPreview);

            Map<String, Object> result = modelConfigService.testConnection(dto);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("测试连接失败：{}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "测试失败：" + e.getMessage());
            return ApiResponse.success(result);
        }
    }

    @PostMapping("/test-audio")
    @Operation(summary = "测试音频生成API", description = "测试 TTS 音频生成 API 连接是否可用")
    public ApiResponse<Map<String, Object>> testAudioConnection(@RequestBody AIModelConfigDTO dto) {
        try {
            Map<String, Object> result = modelConfigService.testAudioConnection(dto);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("测试音频连接失败：{}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "测试失败：" + e.getMessage());
            return ApiResponse.success(result);
        }
    }

    @PostMapping("/test-video")
    @Operation(summary = "测试视频生成API", description = "测试视频生成 API 连接是否可用")
    public ApiResponse<Map<String, Object>> testVideoConnection(@RequestBody AIModelConfigDTO dto) {
        try {
            Map<String, Object> result = modelConfigService.testVideoConnection(dto);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("测试视频连接失败：{}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "测试失败：" + e.getMessage());
            return ApiResponse.success(result);
        }
    }

    @GetMapping("/test-audio/{configId}")
    @Operation(summary = "按配置ID测试音频连接", description = "使用数据库配置测试音频生成API")
    public ApiResponse<Map<String, Object>> testAudioConnectionByConfigId(@PathVariable String configId) {
        try {
            AIModelConfig config = modelConfigService.getModelEntity(configId, null);
            if (config == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "配置不存在");
                return ApiResponse.success(result);
            }

            AIModelConfigDTO dto = new AIModelConfigDTO();
            dto.setApiUrl(config.getApiUrl());
            dto.setApiKey(config.getApiKey());
            dto.setModelId(config.getModelId());
            dto.setProvider(config.getProvider());

            Map<String, Object> result = modelConfigService.testAudioConnection(dto);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("测试音频连接失败：{}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "测试失败：" + e.getMessage());
            return ApiResponse.success(result);
        }
    }

    @GetMapping("/test-video/{configId}")
    @Operation(summary = "按配置ID测试视频连接", description = "使用数据库配置测试视频生成API")
    public ApiResponse<Map<String, Object>> testVideoConnectionByConfigId(@PathVariable String configId) {
        try {
            AIModelConfig config = modelConfigService.getModelEntity(configId, null);
            if (config == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "配置不存在");
                return ApiResponse.success(result);
            }

            AIModelConfigDTO dto = new AIModelConfigDTO();
            dto.setApiUrl(config.getApiUrl());
            dto.setApiKey(config.getApiKey());
            dto.setModelId(config.getModelId());
            dto.setProvider(config.getProvider());

            Map<String, Object> result = modelConfigService.testVideoConnection(dto);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("测试视频连接失败：{}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "测试失败：" + e.getMessage());
            return ApiResponse.success(result);
        }
    }
}
