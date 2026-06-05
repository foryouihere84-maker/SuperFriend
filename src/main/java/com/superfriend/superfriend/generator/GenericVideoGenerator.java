package com.superfriend.superfriend.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.entity.AIModelConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用视频生成器
 * 支持多种视频生成 API（智谱 CogVideoX、OpenAI 兼容等）
 * 处理同步和异步（提交→轮询）两种模式
 */
@Slf4j
@Component
public class GenericVideoGenerator {

    private static final long DEFAULT_POLL_INTERVAL_MS = 5000;
    private static final long DEFAULT_TIMEOUT_MS = 600000;

    private final RestTemplate restTemplate = createRestTemplate(10_000, 60_000);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 使用指定模型配置生成视频
     */
    public VideoGenerationResult generate(AIModelConfig config, String prompt, VideoGenerationOptions options) {
        if (config == null) {
            return VideoGenerationResult.failed("模型配置为空");
        }

        String provider = config.getProvider();
        if (provider == null) {
            provider = detectProvider(config.getModelId());
        }

        log.info("使用模型 {} 生成视频，provider: {}", config.getModelId(), provider);

        try {
            switch (provider.toLowerCase()) {
                case "zhipu":
                case "bigmodel":
                    return generateWithZhipu(config, prompt, options);
                case "tongyi":
                case "qwen":
                case "alibaba":
                    return generateWithAsyncApi(config, prompt, options);
                default:
                    return generateWithOpenAICompatible(config, prompt, options);
            }
        } catch (Exception e) {
            log.error("视频生成异常: {}", e.getMessage(), e);
            return VideoGenerationResult.failed("视频生成异常: " + e.getMessage());
        }
    }

    /**
     * 智谱 CogVideoX API（异步）
     */
    private VideoGenerationResult generateWithZhipu(AIModelConfig config, String prompt, VideoGenerationOptions options) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://open.bigmodel.cn/api/paas/v4/video/generations";
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("prompt", prompt);
        applyVideoOptions(requestBody, options);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        return submitAndPoll(apiUrl, requestBody, headers, config.getApiKey(), "zhipu");
    }

    /**
     * 通用 OpenAI 兼容 API
     * 尝试同步返回，如果响应包含任务 ID 则切换为异步轮询
     */
    private VideoGenerationResult generateWithOpenAICompatible(AIModelConfig config, String prompt, VideoGenerationOptions options) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            return VideoGenerationResult.failed("模型未配置 API URL");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("prompt", prompt);
        applyVideoOptions(requestBody, options);
        requestBody.put("n", 1);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        // 先尝试同步调用
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
            long generationTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());

                // 检查是否为异步响应（包含任务 ID）
                String taskId = root.path("id").asText(null);
                if (taskId == null || "null".equals(taskId)) {
                    taskId = root.path("data").path("id").asText(null);
                }

                if (taskId != null && !taskId.isEmpty() && !"null".equals(taskId)) {
                    // 异步模式，开始轮询
                    return pollForResult(buildResultUrl(apiUrl), taskId, config.getApiKey(), startTime);
                }

                // 同步模式，直接解析
                return parseSyncVideoResponse(root, generationTime, "compatible");
            }

            return VideoGenerationResult.failed("视频生成失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("调用视频 API 失败: {}", e.getMessage());
            return VideoGenerationResult.failed("API 调用失败: " + e.getMessage());
        }
    }

    /**
     * 异步 API（通义等）
     */
    private VideoGenerationResult generateWithAsyncApi(AIModelConfig config, String prompt, VideoGenerationOptions options) {
        String apiUrl = config.getApiUrl();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("prompt", prompt);
        applyVideoOptions(requestBody, options);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        return submitAndPoll(apiUrl, requestBody, headers, config.getApiKey(), "tongyi");
    }

    /**
     * 提交任务并轮询结果
     */
    private VideoGenerationResult submitAndPoll(String submitUrl, Map<String, Object> requestBody,
                                                 HttpHeaders headers, String apiKey, String provider) {
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(submitUrl, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return VideoGenerationResult.failed("提交视频任务失败: HTTP " + response.getStatusCode());
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("未知错误");
                return VideoGenerationResult.failed("API 错误: " + errorMsg);
            }

            String taskId = root.path("id").asText(null);
            if (taskId == null || "null".equals(taskId)) {
                taskId = root.path("data").path("id").asText(null);
            }
            if (taskId == null || "null".equals(taskId)) {
                // 可能是同步返回
                return parseSyncVideoResponse(root, System.currentTimeMillis() - startTime, provider);
            }

            String resultUrl = buildResultUrl(submitUrl);
            return pollForResult(resultUrl, taskId, apiKey, startTime);
        } catch (Exception e) {
            log.error("提交视频任务异常: {}", e.getMessage());
            return VideoGenerationResult.failed("提交任务异常: " + e.getMessage());
        }
    }

    /**
     * 轮询视频生成结果
     */
    private VideoGenerationResult pollForResult(String resultUrl, String taskId, String apiKey, long startTime) {
        long deadline = startTime + DEFAULT_TIMEOUT_MS;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);

        int consecutiveErrors = 0;

        while (System.currentTimeMillis() < deadline) {
            try {
                String url = resultUrl;
                if (!url.contains("?")) {
                    url += "?id=" + taskId;
                } else {
                    url += "&id=" + taskId;
                }

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    consecutiveErrors = 0;
                    JsonNode root = objectMapper.readTree(response.getBody());

                    String status = root.path("task_status").asText(
                            root.path("status").asText("PROCESSING"));

                    switch (status.toUpperCase()) {
                        case "SUCCESS":
                        case "SUCCEEDED":
                        case "COMPLETED":
                            long generationTime = System.currentTimeMillis() - startTime;
                            return parseAsyncVideoResult(root, generationTime);
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

                Thread.sleep(DEFAULT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return VideoGenerationResult.failed("视频生成被中断");
            } catch (Exception e) {
                consecutiveErrors++;
                if (consecutiveErrors >= 10) {
                    return VideoGenerationResult.failed("连续多次查询任务状态失败: " + e.getMessage());
                }
                log.warn("轮询视频状态异常: {}", e.getMessage());
                try {
                    Thread.sleep(DEFAULT_POLL_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return VideoGenerationResult.failed("视频生成被中断");
                }
            }
        }

        return VideoGenerationResult.failed("视频生成超时");
    }

    /**
     * 解析同步视频响应
     */
    private VideoGenerationResult parseSyncVideoResponse(JsonNode root, long generationTime, String provider) {
        try {
            String videoUrl = null;

            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                videoUrl = data.get(0).path("url").asText(null);
            }
            if (videoUrl == null) {
                videoUrl = root.path("url").asText(null);
            }

            if (videoUrl != null && !videoUrl.isEmpty()) {
                VideoGenerationResult result = VideoGenerationResult.success(videoUrl);
                result.setGenerationTimeMs(generationTime);
                result.setMetadata(new HashMap<>());
                result.getMetadata().put("provider", provider);
                log.info("视频生成成功，provider: {}, 耗时: {}ms", provider, generationTime);
                return result;
            }

            return VideoGenerationResult.failed("响应中未包含视频数据");
        } catch (Exception e) {
            return VideoGenerationResult.failed("解析响应失败: " + e.getMessage());
        }
    }

    /**
     * 解析异步视频结果
     */
    private VideoGenerationResult parseAsyncVideoResult(JsonNode root, long generationTime) {
        String videoUrl = null;
        String coverUrl = null;

        // 智谱格式: video_result[0].url
        JsonNode videoResult = root.path("video_result");
        if (videoResult.isArray() && videoResult.size() > 0) {
            videoUrl = videoResult.get(0).path("url").asText(null);
            coverUrl = videoResult.get(0).path("cover_image_url").asText(null);
        }

        // 通用格式: data[0].url
        if (videoUrl == null) {
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                videoUrl = data.get(0).path("url").asText(null);
            }
        }

        if (videoUrl == null) {
            videoUrl = root.path("url").asText(null);
        }

        if (videoUrl != null && !videoUrl.isEmpty()) {
            VideoGenerationResult result = VideoGenerationResult.success(videoUrl);
            result.setCoverImageUrl(coverUrl);
            result.setGenerationTimeMs(generationTime);
            result.setMetadata(new HashMap<>());
            log.info("视频生成成功，耗时: {}ms", generationTime);
            return result;
        }

        return VideoGenerationResult.failed("响应中未包含视频数据");
    }

    /**
     * 从提交 URL 推导结果查询 URL
     */
    private String buildResultUrl(String submitUrl) {
        // /v4/video/generations -> /v4/video/generations-result (只替换最后一次出现)
        int lastIdx = submitUrl.lastIndexOf("/generations");
        if (lastIdx >= 0) {
            return submitUrl.substring(0, lastIdx) + "/generations-result" + submitUrl.substring(lastIdx + "/generations".length());
        }
        return submitUrl + "-result";
    }

    private void applyVideoOptions(Map<String, Object> requestBody, VideoGenerationOptions options) {
        if (options == null) return;
        if (options.getDuration() != null) requestBody.put("duration", options.getDuration());
        if (options.getResolution() != null) requestBody.put("resolution", options.getResolution());
        if (options.getFps() != null) requestBody.put("fps", options.getFps());
    }

    private String detectProvider(String modelId) {
        if (modelId == null) return "openai";
        String lower = modelId.toLowerCase();
        if (lower.contains("cogvideo")) return "zhipu";
        if (lower.contains("wanx") || lower.contains("tongyi")) return "tongyi";
        return "openai";
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