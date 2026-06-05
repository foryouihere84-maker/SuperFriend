package com.superfriend.superfriend.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.entity.AIModelConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用图片生成器
 * 支持多种图片生成 API（OpenAI DALL-E、智谱 CogView、通义万相等）
 */
@Slf4j
@Component
public class GenericImageGenerator {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 使用指定模型配置生成图片
     * 
     * @param config 模型配置
     * @param prompt 提示词
     * @param size 图片尺寸
     * @return 生成结果
     */
    public GenerationResult generate(AIModelConfig config, String prompt, String size) {
        if (config == null) {
            return GenerationResult.failed("模型配置为空");
        }

        String provider = config.getProvider();
        if (provider == null) {
            provider = detectProvider(config.getModelId());
        }

        log.info("使用模型 {} 生成图片，provider: {}, size: {}", config.getModelId(), provider, size);

        try {
            switch (provider.toLowerCase()) {
                case "zhipu":
                case "bigmodel":
                    return generateWithZhipu(config, prompt, size);
                case "openai":
                    return generateWithOpenAI(config, prompt, size);
                case "tongyi":
                case "qwen":
                case "alibaba":
                    return generateWithTongyi(config, prompt, size);
                default:
                    return generateWithOpenAICompatible(config, prompt, size);
            }
        } catch (Exception e) {
            log.error("图片生成异常: {}", e.getMessage(), e);
            return GenerationResult.failed("图片生成异常: " + e.getMessage());
        }
    }

    /**
     * 智谱 CogView API
     */
    private GenerationResult generateWithZhipu(AIModelConfig config, String prompt, String size) {
        String apiUrl = "https://open.bigmodel.cn/api/paas/v4/images/generations";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("prompt", prompt);
        requestBody.put("size", size != null ? size : "1024x1024");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        return callImageApi(apiUrl, requestBody, headers, "zhipu");
    }

    /**
     * OpenAI DALL-E API
     */
    private GenerationResult generateWithOpenAI(AIModelConfig config, String prompt, String size) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://api.openai.com/v1/images/generations";
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("prompt", prompt);
        requestBody.put("size", size != null ? size : "1024x1024");
        requestBody.put("n", 1);
        requestBody.put("response_format", "b64_json");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        return callImageApi(apiUrl, requestBody, headers, "openai");
    }

    /**
     * 通义万相 API
     */
    private GenerationResult generateWithTongyi(AIModelConfig config, String prompt, String size) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
        }

        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("style", "<auto>");
        parameters.put("size", size != null ? size : "1024*1024");
        parameters.put("n", 1);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("input", input);
        requestBody.put("parameters", parameters);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());
        headers.set("X-DashScope-Async", "enable");

        return callImageApi(apiUrl, requestBody, headers, "tongyi");
    }

    /**
     * 通用 OpenAI 兼容 API
     */
    private GenerationResult generateWithOpenAICompatible(AIModelConfig config, String prompt, String size) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            return GenerationResult.failed("模型未配置 API URL");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("prompt", prompt);
        if (size != null) {
            requestBody.put("size", size);
        }
        requestBody.put("n", 1);
        requestBody.put("response_format", "b64_json");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        return callImageApi(apiUrl, requestBody, headers, "compatible");
    }

    /**
     * 调用图片生成 API
     */
    private GenerationResult callImageApi(String apiUrl, Map<String, Object> requestBody, 
                                           HttpHeaders headers, String provider) {
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
            long generationTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseImageResponse(response.getBody(), provider, generationTime);
            } else {
                return GenerationResult.failed("图片生成失败: HTTP " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("调用图片 API 失败: {}", e.getMessage());
            return GenerationResult.failed("API 调用失败: " + e.getMessage());
        }
    }

    /**
     * 解析图片生成响应
     */
    private GenerationResult parseImageResponse(String responseBody, String provider, long generationTime) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("未知错误");
                return GenerationResult.failed("API 错误: " + errorMsg);
            }

            String base64Image = null;
            String imageUrl = null;

            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode firstImage = data.get(0);
                
                base64Image = firstImage.path("b64_json").asText(null);
                if (base64Image == null) {
                    base64Image = firstImage.path("b64_image").asText(null);
                }
                imageUrl = firstImage.path("url").asText(null);
            }

            if (base64Image != null && !base64Image.isEmpty()) {
                GenerationResult result = GenerationResult.success(base64Image);
                result.setGenerationTimeMs(generationTime);
                result.setMetadata(new HashMap<>());
                result.getMetadata().put("provider", provider);
                log.info("图片生成成功，provider: {}, 耗时: {}ms", provider, generationTime);
                return result;
            } else if (imageUrl != null && !imageUrl.isEmpty()) {
                GenerationResult result = GenerationResult.successWithUrl(imageUrl);
                result.setGenerationTimeMs(generationTime);
                result.setMetadata(new HashMap<>());
                result.getMetadata().put("provider", provider);
                result = downloadAndConvertToBase64(imageUrl, result);
                log.info("图片生成成功，provider: {}, 耗时: {}ms", provider, generationTime);
                return result;
            } else {
                return GenerationResult.failed("响应中未包含图片数据");
            }
        } catch (Exception e) {
            log.error("解析图片响应失败: {}", e.getMessage());
            return GenerationResult.failed("解析响应失败: " + e.getMessage());
        }
    }

    /**
     * 下载图片并转换为 Base64
     */
    private GenerationResult downloadAndConvertToBase64(String imageUrl, GenerationResult result) {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(imageUrl, byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String base64 = java.util.Base64.getEncoder().encodeToString(response.getBody());
                result.setBase64Image(base64);
            }
        } catch (Exception e) {
            log.warn("下载图片失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 根据模型 ID 推断提供商
     */
    private String detectProvider(String modelId) {
        if (modelId == null) return "openai";
        
        String lowerModelId = modelId.toLowerCase();
        if (lowerModelId.contains("cogview") || lowerModelId.contains("glm")) {
            return "zhipu";
        } else if (lowerModelId.contains("dall") || lowerModelId.contains("gpt")) {
            return "openai";
        } else if (lowerModelId.contains("wanx") || lowerModelId.contains("tongyi")) {
            return "tongyi";
        }
        return "openai";
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

        public static GenerationResult successWithUrl(String imageUrl) {
            GenerationResult result = new GenerationResult();
            result.setSuccess(true);
            result.setImageUrl(imageUrl);
            return result;
        }

        public static GenerationResult failed(String errorMessage) {
            GenerationResult result = new GenerationResult();
            result.setSuccess(false);
            result.setErrorMessage(errorMessage);
            return result;
        }

        public boolean hasImage() {
            return (base64Image != null && !base64Image.isEmpty()) || 
                   (imageUrl != null && !imageUrl.isEmpty());
        }

        public String getDataUrl() {
            if (base64Image != null && !base64Image.isEmpty()) {
                if (base64Image.startsWith("data:image")) {
                    return base64Image;
                }
                return "data:image/png;base64," + base64Image;
            }
            return imageUrl;
        }
    }
}
