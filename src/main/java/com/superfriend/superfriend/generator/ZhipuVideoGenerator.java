package com.superfriend.superfriend.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 智谱 CogVideoX 视频生成器
 *
 * 异步流程: 提交任务 → 轮询状态 → 获取结果
 *
 * 支持模型:
 * - cogvideox-2: 2 秒视频生成
 * - cogvideox-5: 5 秒视频生成
 *
 * 文档: https://open.bigmodel.cn/dev/api/video-model/cogvideox
 */
@Slf4j
@Component
public class ZhipuVideoGenerator {

    private static final String SUBMIT_URL = "https://open.bigmodel.cn/api/paas/v4/video/generations";
    private static final String RESULT_URL = "https://open.bigmodel.cn/api/paas/v4/video/generations-result";

    @Value("${zhipu.api.enabled:false}")
    private boolean enabled;

    @Value("${zhipu.api.key:}")
    private String apiKey;

    @Value("${zhipu.video.model:cogvideox-2}")
    private String model;

    @Value("${zhipu.video.poll-interval-ms:5000}")
    private long pollIntervalMs;

    @Value("${zhipu.video.timeout-ms:600000}")
    private long timeoutMs;

    private final RestTemplate restTemplate = createRestTemplate(10_000, 60_000);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("智谱 CogVideoX 视频生成器初始化，模型: {}, 轮询间隔: {}ms, 超时: {}ms",
                    model, pollIntervalMs, timeoutMs);
        } else {
            log.info("智谱 CogVideoX 视频生成器未启用");
        }
    }

    public VideoGenerationResult generate(String prompt) {
        return generate(prompt, null);
    }

    public VideoGenerationResult generate(String prompt, VideoGenerationOptions options) {
        if (!enabled) {
            return VideoGenerationResult.failed("智谱视频生成服务未启用");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            return VideoGenerationResult.failed("智谱 API Key 未配置");
        }

        if (prompt == null || prompt.trim().isEmpty()) {
            return VideoGenerationResult.failed("提示词不能为空");
        }

        try {
            log.info("开始调用智谱 CogVideoX 生成视频，prompt: {}", prompt);

            // 1. 提交任务
            String taskId = submitTask(prompt, options);
            if (taskId == null) {
                return VideoGenerationResult.failed("提交视频生成任务失败");
            }

            log.info("视频生成任务已提交，taskId: {}", taskId);

            // 2. 轮询等待结果
            return pollForResult(taskId);

        } catch (Exception e) {
            log.error("智谱视频生成异常: {}", e.getMessage(), e);
            return VideoGenerationResult.failed("视频生成异常: " + e.getMessage());
        }
    }

    /**
     * 提交视频生成任务
     */
    private String submitTask(String prompt, VideoGenerationOptions options) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);

            if (options != null) {
                if (options.getDuration() != null) {
                    requestBody.put("duration", options.getDuration());
                }
                if (options.getResolution() != null) {
                    requestBody.put("resolution", options.getResolution());
                }
                if (options.getFps() != null) {
                    requestBody.put("fps", options.getFps());
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(SUBMIT_URL, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());

                if (root.has("error")) {
                    String errorMsg = root.path("error").path("message").asText("未知错误");
                    log.error("提交视频任务失败: {}", errorMsg);
                    return null;
                }

                String taskId = root.path("id").asText(null);
                if (taskId == null || taskId.isEmpty() || "null".equals(taskId)) {
                    taskId = root.path("data").path("id").asText(null);
                }
                if (taskId == null || taskId.isEmpty() || "null".equals(taskId)) {
                    log.error("提交视频任务响应中无有效任务 ID");
                    return null;
                }
                return taskId;
            }

            log.error("提交视频任务失败: HTTP {}", response.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("提交视频任务异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 轮询获取视频生成结果
     */
    private VideoGenerationResult pollForResult(String taskId) {
        long startTime = System.currentTimeMillis();
        long deadline = startTime + timeoutMs;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);

        int consecutiveErrors = 0;

        while (System.currentTimeMillis() < deadline) {
            try {
                String url = RESULT_URL + "?id=" + taskId;
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    consecutiveErrors = 0;
                    JsonNode root = objectMapper.readTree(response.getBody());

                    String taskStatus = root.path("task_status").asText(
                            root.path("status").asText("PROCESSING"));

                    log.debug("视频任务 {} 状态: {}", taskId, taskStatus);

                    switch (taskStatus.toUpperCase()) {
                        case "SUCCESS":
                        case "SUCCEEDED":
                        case "COMPLETED":
                            long generationTime = System.currentTimeMillis() - startTime;
                            return parseVideoResult(root, generationTime);

                        case "FAIL":
                        case "FAILED":
                            String errorMsg = root.path("error").path("message").asText("视频生成失败");
                            return VideoGenerationResult.failed(errorMsg);

                        default:
                            break;
                    }
                } else {
                    consecutiveErrors++;
                }

                if (consecutiveErrors >= 10) {
                    return VideoGenerationResult.failed("连续多次查询任务状态失败");
                }

                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return VideoGenerationResult.failed("视频生成被中断");
            } catch (Exception e) {
                consecutiveErrors++;
                if (consecutiveErrors >= 10) {
                    return VideoGenerationResult.failed("连续多次查询任务状态失败: " + e.getMessage());
                }
                log.warn("轮询视频任务状态异常: {}", e.getMessage());
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return VideoGenerationResult.failed("视频生成被中断");
                }
            }
        }

        return VideoGenerationResult.failed("视频生成超时（" + (timeoutMs / 1000) + "秒）");
    }

    /**
     * 解析视频生成结果
     */
    private VideoGenerationResult parseVideoResult(JsonNode root, long generationTime) {
        try {
            JsonNode videoResult = root.path("video_result");
            if (videoResult.isArray() && videoResult.size() > 0) {
                JsonNode firstVideo = videoResult.get(0);
                String videoUrl = firstVideo.path("url").asText(null);
                String coverUrl = firstVideo.path("cover_image_url").asText(null);

                if (videoUrl != null && !videoUrl.isEmpty()) {
                    VideoGenerationResult result = VideoGenerationResult.success(videoUrl);
                    result.setCoverImageUrl(coverUrl);
                    result.setGenerationTimeMs(generationTime);
                    result.setMetadata(new HashMap<>());
                    result.getMetadata().put("model", model);
                    result.getMetadata().put("provider", "zhipu");

                    log.info("智谱视频生成成功，耗时: {}ms", generationTime);
                    return result;
                }
            }

            // 尝试其他字段格式
            String videoUrl = root.path("data").path("url").asText(null);
            if (videoUrl == null || videoUrl.isEmpty()) {
                videoUrl = root.path("url").asText(null);
            }

            if (videoUrl != null && !videoUrl.isEmpty()) {
                VideoGenerationResult result = VideoGenerationResult.success(videoUrl);
                result.setGenerationTimeMs(generationTime);
                result.setMetadata(new HashMap<>());
                result.getMetadata().put("model", model);
                result.getMetadata().put("provider", "zhipu");
                log.info("智谱视频生成成功，耗时: {}ms", generationTime);
                return result;
            }

            return VideoGenerationResult.failed("响应中未包含视频数据");
        } catch (Exception e) {
            log.error("解析视频结果失败: {}", e.getMessage());
            return VideoGenerationResult.failed("解析结果失败: " + e.getMessage());
        }
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

    @Data
    public static class VideoGenerationResult {
        private boolean success;
        private String videoUrl;
        private String dataUrl;
        private String coverImageUrl;
        private String errorMessage;
        private Long generationTimeMs;
        private Map<String, Object> metadata;

        public static VideoGenerationResult success(String videoUrl) {
            VideoGenerationResult result = new VideoGenerationResult();
            result.setSuccess(true);
            result.setVideoUrl(videoUrl);
            return result;
        }

        public static VideoGenerationResult failed(String errorMessage) {
            VideoGenerationResult result = new VideoGenerationResult();
            result.setSuccess(false);
            result.setErrorMessage(errorMessage);
            return result;
        }

        public boolean hasVideo() {
            return (videoUrl != null && !videoUrl.isEmpty()) ||
                   (dataUrl != null && !dataUrl.isEmpty());
        }
    }

    private static RestTemplate createRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}