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
 * 智谱 CogView-4 图片生成器
 * 
 * 支持模型:
 * - cogview-4: 最新版本，支持中文汉字生成
 * - cogview-4-250304: 指定版本
 * 
 * 支持尺寸:
 * - 预设: 1024x1024, 768x1344, 864x1152, 1344x768, 1152x864, 1440x720, 720x1440
 * - 自定义: 长宽 512-2048px，需被 16 整除，最大像素不超过 2^21
 * 
 * 价格: ¥0.06/次
 * 文档: https://open.bigmodel.cn/dev/api/image-model/cogview
 */
@Slf4j
@Component
public class ZhipuImageGenerator {

    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/images/generations";

    private static final java.util.Set<String> PRESET_SIZES = java.util.Collections.unmodifiableSet(
        new java.util.HashSet<>(java.util.Arrays.asList(
            "1024x1024", "768x1344", "864x1152", "1344x768", "1152x864", "1440x720", "720x1440"
        ))
    );

    private static final int MIN_SIZE = 512;
    private static final int MAX_SIZE = 2048;
    private static final int MAX_PIXELS = 1 << 21; // 2^21 = 2097152

    @Value("${zhipu.api.enabled:false}")
    private boolean enabled;

    @Value("${zhipu.api.key:}")
    private String apiKey;

    @Value("${zhipu.image.model:cogview-4}")
    private String model;

    @Value("${zhipu.image.default-size:1024x1024}")
    private String defaultSize;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("智谱 CogView-4 图片生成器初始化，模型: {}", model);
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("智谱 API Key 未配置，请在 application.yml 中设置 zhipu.api.key");
            }
        } else {
            log.info("智谱 CogView-4 图片生成器未启用");
        }
    }

    /**
     * 文生图
     */
    public GenerationResult generate(String prompt) {
        return generate(prompt, null);
    }

    /**
     * 文生图 - 完整参数
     */
    public GenerationResult generate(String prompt, GenerationOptions options) {
        if (!enabled) {
            return GenerationResult.failed("智谱图片生成服务未启用，请在 application.yml 中设置 zhipu.api.enabled=true");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            return GenerationResult.failed("智谱 API Key 未配置，请在 application.yml 中设置 zhipu.api.key");
        }

        if (prompt == null || prompt.trim().isEmpty()) {
            return GenerationResult.failed("提示词不能为空");
        }

        String size = (options != null && options.getSize() != null) ? options.getSize() : defaultSize;
        String validatedSize = validateSize(size);
        if (validatedSize == null) {
            return GenerationResult.failed("图片尺寸无效: " + size + "。支持格式: 1024x1024, 768x1344 等，长宽范围 512-2048px，需被 16 整除");
        }

        try {
            log.info("开始调用智谱 CogView-4 生成图片，prompt: {}, size: {}", prompt, validatedSize);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);
            requestBody.put("size", validatedSize);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, entity, String.class);
            long generationTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody(), generationTime);
            } else {
                return GenerationResult.failed("图片生成失败: HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("智谱图片生成异常: {}", e.getMessage(), e);
            return GenerationResult.failed("图片生成异常: " + e.getMessage());
        }
    }

    /**
     * 解析智谱 API 响应
     */
    private GenerationResult parseResponse(String responseBody, long generationTime) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("未知错误");
                return GenerationResult.failed("API 错误: " + errorMsg);
            }

            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode firstImage = data.get(0);
                String imageUrl = firstImage.path("url").asText();
                String b64Image = firstImage.path("b64_image").asText(null);

                GenerationResult result;
                if (b64Image != null && !b64Image.isEmpty()) {
                    result = GenerationResult.success(b64Image);
                } else if (imageUrl != null && !imageUrl.isEmpty()) {
                    result = GenerationResult.successWithUrl(imageUrl);
                    result = downloadAndConvertToBase64(imageUrl, result);
                } else {
                    return GenerationResult.failed("响应中未包含图片数据");
                }

                result.setGenerationTimeMs(generationTime);
                result.setMetadata(new HashMap<>());
                result.getMetadata().put("model", model);
                result.getMetadata().put("provider", "zhipu");

                log.info("智谱图片生成成功，耗时: {}ms", generationTime);
                return result;
            } else {
                return GenerationResult.failed("响应中未包含图片数据");
            }

        } catch (Exception e) {
            log.error("解析智谱 API 响应失败: {}", e.getMessage());
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
            log.warn("下载图片失败，将使用 URL: {}", e.getMessage());
        }
        return result;
    }

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }

    public String getProvider() {
        return "zhipu";
    }

    public String getModel() {
        return model;
    }

    /**
     * 验证图片尺寸
     * @param size 尺寸字符串，格式: "宽x高"
     * @return 验证通过返回尺寸，否则返回 null
     */
    private String validateSize(String size) {
        if (size == null || size.isEmpty()) {
            return defaultSize;
        }

        if (PRESET_SIZES.contains(size)) {
            return size;
        }

        String[] parts = size.toLowerCase().split("x");
        if (parts.length != 2) {
            return null;
        }

        try {
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());

            if (width < MIN_SIZE || width > MAX_SIZE || height < MIN_SIZE || height > MAX_SIZE) {
                return null;
            }

            if (width % 16 != 0 || height % 16 != 0) {
                return null;
            }

            long pixels = (long) width * height;
            if (pixels > MAX_PIXELS) {
                return null;
            }

            return width + "x" + height;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取支持的预设尺寸列表
     */
    public java.util.Set<String> getPresetSizes() {
        return PRESET_SIZES;
    }

    /**
     * 生成选项
     */
    @Data
    public static class GenerationOptions {
        private String size;

        public static GenerationOptions defaults() {
            return new GenerationOptions();
        }

        public GenerationOptions withSize(String size) {
            this.size = size;
            return this;
        }

        public GenerationOptions withSize(int width, int height) {
            this.size = width + "x" + height;
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
