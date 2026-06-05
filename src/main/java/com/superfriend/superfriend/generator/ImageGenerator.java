package com.superfriend.superfriend.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 图片生成器
 * 使用 Stable Diffusion API 生成图片
 *
 * 支持:
 * - Stable Diffusion WebUI API (AUTOMATIC1111)
 * - ComfyUI API
 * - 其他兼容 SD API 的服务
 */
@Slf4j
@Component
public class ImageGenerator {

    @Value("${sd.api.url:http://localhost:7860}")
    private String apiUrl;

    @Value("${sd.api.key:}")
    private String apiKey;

    @Value("${sd.api.enabled:false}")
    private boolean enabled;

    @Value("${sd.default.steps:30}")
    private int defaultSteps;

    @Value("${sd.default.cfg-scale:7.0}")
    private double defaultCfgScale;

    @Value("${sd.default.width:512}")
    private int defaultWidth;

    @Value("${sd.default.height:512}")
    private int defaultHeight;

    @Value("${sd.default.sampler:Euler a}")
    private String defaultSampler;

    @Value("${sd.default.negative-prompt:}")
    private String defaultNegativePrompt;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("Stable Diffusion 图片生成器初始化，API URL: {}", apiUrl);
            // 检查服务是否可用
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(
                        apiUrl + "/sdapi/v1/sd-models", String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Stable Diffusion 服务连接成功");
                }
            } catch (Exception e) {
                log.warn("Stable Diffusion 服务连接失败: {}", e.getMessage());
            }
        } else {
            log.info("Stable Diffusion 图片生成器未启用");
        }
    }

    /**
     * 文生图 (txt2img)
     */
    public GenerationResult txt2img(String prompt) {
        return txt2img(prompt, null);
    }

    /**
     * 文生图 (txt2img) - 完整参数
     */
    public GenerationResult txt2img(String prompt, GenerationOptions options) {
        if (!enabled) {
            return GenerationResult.failed("图片生成服务未启用");
        }

        try {
            log.info("开始生成图片，prompt: {}", prompt);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("prompt", prompt);
            requestBody.put("negative_prompt",
                    options != null && options.getNegativePrompt() != null ?
                            options.getNegativePrompt() : defaultNegativePrompt);
            requestBody.put("steps",
                    options != null && options.getSteps() != null ?
                            options.getSteps() : defaultSteps);
            requestBody.put("cfg_scale",
                    options != null && options.getCfgScale() != null ?
                            options.getCfgScale() : defaultCfgScale);
            requestBody.put("width",
                    options != null && options.getWidth() != null ?
                            options.getWidth() : defaultWidth);
            requestBody.put("height",
                    options != null && options.getHeight() != null ?
                            options.getHeight() : defaultHeight);
            requestBody.put("sampler_name",
                    options != null && options.getSampler() != null ?
                            options.getSampler() : defaultSampler);

            // 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + apiKey);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            String url = apiUrl + "/sdapi/v1/txt2img";

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            long generationTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody(), generationTime);
            } else {
                return GenerationResult.failed("图片生成失败: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("图片生成异常: {}", e.getMessage(), e);
            return GenerationResult.failed("图片生成异常: " + e.getMessage());
        }
    }

    /**
     * 图生图 (img2img)
     */
    public GenerationResult img2img(String prompt, String initImage, double denoisingStrength) {
        if (!enabled) {
            return GenerationResult.failed("图片生成服务未启用");
        }

        try {
            log.info("开始 img2img 生成，prompt: {}", prompt);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("prompt", prompt);
            requestBody.put("init_images", java.util.Collections.singletonList(initImage));
            requestBody.put("denoising_strength", denoisingStrength);
            requestBody.put("steps", defaultSteps);
            requestBody.put("cfg_scale", defaultCfgScale);
            requestBody.put("width", defaultWidth);
            requestBody.put("height", defaultHeight);
            requestBody.put("sampler_name", defaultSampler);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + apiKey);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            String url = apiUrl + "/sdapi/v1/img2img";

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            long generationTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody(), generationTime);
            } else {
                return GenerationResult.failed("图片生成失败: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("img2img 生成异常: {}", e.getMessage(), e);
            return GenerationResult.failed("图片生成异常: " + e.getMessage());
        }
    }

    /**
     * 解析 SD API 响应
     */
    private GenerationResult parseResponse(String responseBody, long generationTime) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 获取生成的图片（base64）
            JsonNode images = root.path("images");
            if (images.isArray() && images.size() > 0) {
                String base64Image = images.get(0).asText();

                // 获取参数信息
                JsonNode parameters = root.path("parameters");

                GenerationResult result = GenerationResult.success(base64Image);
                result.setGenerationTimeMs(generationTime);
                result.setMetadata(new HashMap<>());
                result.getMetadata().put("steps", parameters.path("steps").asInt(defaultSteps));
                result.getMetadata().put("cfg_scale", parameters.path("cfg_scale").asDouble(defaultCfgScale));
                result.getMetadata().put("sampler", parameters.path("sampler_name").asText(defaultSampler));

                log.info("图片生成成功，耗时: {}ms", generationTime);
                return result;
            } else {
                return GenerationResult.failed("未生成图片");
            }

        } catch (Exception e) {
            log.error("解析 SD API 响应失败: {}", e.getMessage());
            return GenerationResult.failed("解析响应失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用的模型列表
     */
    public java.util.List<String> getAvailableModels() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    apiUrl + "/sdapi/v1/sd-models", String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode models = objectMapper.readTree(response.getBody());
                java.util.List<String> modelNames = new java.util.ArrayList<>();
                for (JsonNode model : models) {
                    modelNames.add(model.path("title").asText());
                }
                return modelNames;
            }
        } catch (Exception e) {
            log.warn("获取模型列表失败: {}", e.getMessage());
        }
        return java.util.Collections.emptyList();
    }

    /**
     * 切换模型
     */
    public boolean switchModel(String modelName) {
        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("sd_model_checkpoint", modelName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    apiUrl + "/sdapi/v1/options", entity, String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("切换模型失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 生成选项
     */
    @Data
    public static class GenerationOptions {
        private String negativePrompt;
        private Integer steps;
        private Double cfgScale;
        private Integer width;
        private Integer height;
        private String sampler;
        private Long seed;
        private String model;

        public static GenerationOptions defaults() {
            return new GenerationOptions();
        }

        public GenerationOptions withNegativePrompt(String negativePrompt) {
            this.negativePrompt = negativePrompt;
            return this;
        }

        public GenerationOptions withSteps(int steps) {
            this.steps = steps;
            return this;
        }

        public GenerationOptions withSize(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public GenerationOptions withSampler(String sampler) {
            this.sampler = sampler;
            return this;
        }
    }

    /**
     * 生成结果
     */
    @Data
    public static class GenerationResult {
        private boolean success;
        private String base64Image;
        private String imageUrl;
        private String errorMessage;
        private Long generationTimeMs;
        private Map<String, Object> metadata;

        public static GenerationResult success(String base64Image) {
            GenerationResult result = new GenerationResult();
            result.setSuccess(true);
            result.setBase64Image(base64Image);
            return result;
        }

        public static GenerationResult failed(String errorMessage) {
            GenerationResult result = new GenerationResult();
            result.setSuccess(false);
            result.setErrorMessage(errorMessage);
            return result;
        }

        public boolean hasImage() {
            return base64Image != null && !base64Image.isEmpty();
        }

        /**
         * 获取 Data URL 格式的图片
         */
        public String getDataUrl() {
            if (base64Image != null && !base64Image.isEmpty()) {
                if (base64Image.startsWith("data:image")) {
                    return base64Image;
                }
                return "data:image/png;base64," + base64Image;
            }
            return null;
        }
    }
}
