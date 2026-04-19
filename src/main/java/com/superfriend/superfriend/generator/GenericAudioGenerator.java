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
 * 通用音频生成器
 * 支持多种 TTS API（智谱 GLM-TTS、OpenAI TTS、Edge TTS 等）
 */
@Slf4j
@Component
public class GenericAudioGenerator {

    private final RestTemplate restTemplate = createRestTemplate(10_000, 120_000);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 使用指定模型配置生成音频
     */
    public AudioGenerationResult generate(AIModelConfig config, String text, AudioGenerationOptions options) {
        if (config == null) {
            return AudioGenerationResult.failed("模型配置为空");
        }

        String provider = config.getProvider();
        if (provider == null) {
            provider = detectProvider(config.getModelId());
        }

        log.info("使用模型 {} 生成音频，provider: {}", config.getModelId(), provider);

        try {
            switch (provider.toLowerCase()) {
                case "zhipu":
                case "bigmodel":
                    return generateWithZhipu(config, text, options);
                case "openai":
                    return generateWithOpenAI(config, text, options);
                case "tongyi":
                case "qwen":
                case "alibaba":
                    return generateWithTongyi(config, text, options);
                case "edge":
                case "microsoft":
                    return generateWithOpenAICompatible(config, text, options);
                default:
                    return generateWithOpenAICompatible(config, text, options);
            }
        } catch (Exception e) {
            log.error("音频生成异常: {}", e.getMessage(), e);
            return AudioGenerationResult.failed("音频生成异常: " + e.getMessage());
        }
    }

    /**
     * 智谱 GLM-TTS API
     */
    private AudioGenerationResult generateWithZhipu(AIModelConfig config, String text, AudioGenerationOptions options) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://open.bigmodel.cn/api/paas/v4/audio/speech";
        }

        String voice = (options != null && options.getVoice() != null) ? options.getVoice() : "female";
        double speed = (options != null) ? options.getSpeed() : 1.0;
        String format = (options != null && options.getResponseFormat() != null) ? options.getResponseFormat() : "mp3";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("input", text);
        requestBody.put("voice", voice);
        requestBody.put("speed", speed);
        requestBody.put("response_format", format);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        return callAudioApi(apiUrl, requestBody, headers, format, "zhipu");
    }

    /**
     * OpenAI TTS API
     */
    private AudioGenerationResult generateWithOpenAI(AIModelConfig config, String text, AudioGenerationOptions options) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://api.openai.com/v1/audio/speech";
        }

        String voice = (options != null && options.getVoice() != null) ? options.getVoice() : "alloy";
        double speed = (options != null) ? options.getSpeed() : 1.0;
        String format = (options != null && options.getResponseFormat() != null) ? options.getResponseFormat() : "mp3";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("input", text);
        requestBody.put("voice", voice);
        requestBody.put("speed", speed);
        requestBody.put("response_format", format);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        return callAudioApi(apiUrl, requestBody, headers, format, "openai");
    }

    /**
     * 通义 CosyVoice TTS API
     */
    private AudioGenerationResult generateWithTongyi(AIModelConfig config, String text, AudioGenerationOptions options) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2audio/generation";
        }

        String voice = (options != null && options.getVoice() != null) ? options.getVoice() : "longxiaochun";
        String format = (options != null && options.getResponseFormat() != null) ? options.getResponseFormat() : "mp3";

        Map<String, Object> input = new HashMap<>();
        input.put("text", text);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("voice", voice);
        parameters.put("format", format);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("input", input);
        requestBody.put("parameters", parameters);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        return callAudioApi(apiUrl, requestBody, headers, format, "tongyi");
    }

    /**
     * 通用 OpenAI 兼容 API
     */
    private AudioGenerationResult generateWithOpenAICompatible(AIModelConfig config, String text, AudioGenerationOptions options) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            return AudioGenerationResult.failed("模型未配置 API URL");
        }

        String voice = (options != null && options.getVoice() != null) ? options.getVoice() : "alloy";
        double speed = (options != null) ? options.getSpeed() : 1.0;
        String format = (options != null && options.getResponseFormat() != null) ? options.getResponseFormat() : "mp3";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelId());
        requestBody.put("input", text);
        requestBody.put("voice", voice);
        requestBody.put("speed", speed);
        requestBody.put("response_format", format);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        return callAudioApi(apiUrl, requestBody, headers, format, "compatible");
    }

    /**
     * 调用音频生成 API
     * 大多数 TTS API 返回二进制音频流
     */
    private AudioGenerationResult callAudioApi(String apiUrl, Map<String, Object> requestBody,
                                                HttpHeaders headers, String format, String provider) {
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            long startTime = System.currentTimeMillis();

            // 先尝试以 byte[] 接收（大多数 TTS API 返回二进制流）
            ResponseEntity<byte[]> response = restTemplate.postForEntity(apiUrl, entity, byte[].class);
            long generationTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                byte[] audioData = response.getBody();

                // 检查是否为 JSON 响应（某些 API 可能返回 JSON 包含 base64）
                String mimeType = getMimeType(format);
                String base64Audio;
                String dataUrl;

                if (audioData.length > 0 && audioData[0] == '{') {
                    // JSON 响应，尝试解析
                    return parseJsonAudioResponse(new String(audioData, java.nio.charset.StandardCharsets.UTF_8),
                            format, provider, generationTime);
                }

                base64Audio = java.util.Base64.getEncoder().encodeToString(audioData);
                dataUrl = "data:" + mimeType + ";base64," + base64Audio;

                AudioGenerationResult result = AudioGenerationResult.success(base64Audio, dataUrl);
                result.setMimeType(mimeType);
                result.setFormat(format);
                result.setGenerationTimeMs(generationTime);
                result.setMetadata(new HashMap<>());
                result.getMetadata().put("provider", provider);

                log.info("音频生成成功，provider: {}, 耗时: {}ms, 大小: {} bytes", provider, generationTime, audioData.length);
                return result;
            } else {
                return AudioGenerationResult.failed("音频生成失败: HTTP " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("调用音频 API 失败: {}", e.getMessage());
            return AudioGenerationResult.failed("API 调用失败: " + e.getMessage());
        }
    }

    /**
     * 解析 JSON 格式的音频响应
     */
    private AudioGenerationResult parseJsonAudioResponse(String responseBody, String format,
                                                          String provider, long generationTime) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("未知错误");
                return AudioGenerationResult.failed("API 错误: " + errorMsg);
            }

            // 通义风格: output.audio
            JsonNode audioNode = root.path("output").path("audio");
            if (audioNode.isMissingNode()) {
                audioNode = root.path("audio");
            }

            String audioUrl = audioNode.path("url").asText(null);
            String base64Data = audioNode.path("data").asText(null);

            if (base64Data != null && !base64Data.isEmpty()) {
                String mimeType = getMimeType(format);
                String dataUrl = "data:" + mimeType + ";base64," + base64Data;

                AudioGenerationResult result = AudioGenerationResult.success(base64Data, dataUrl);
                result.setMimeType(mimeType);
                result.setFormat(format);
                result.setGenerationTimeMs(generationTime);
                result.setMetadata(new HashMap<>());
                result.getMetadata().put("provider", provider);
                return result;
            } else if (audioUrl != null && !audioUrl.isEmpty()) {
                // 下载音频
                return downloadAudio(audioUrl, format, provider, generationTime);
            }

            return AudioGenerationResult.failed("响应中未包含音频数据");
        } catch (Exception e) {
            log.error("解析音频 JSON 响应失败: {}", e.getMessage());
            return AudioGenerationResult.failed("解析响应失败: " + e.getMessage());
        }
    }

    /**
     * 下载音频并转换为 Base64
     */
    private AudioGenerationResult downloadAudio(String audioUrl, String format, String provider, long generationTime) {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(audioUrl, byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                byte[] audioData = response.getBody();
                String mimeType = getMimeType(format);
                String base64Audio = java.util.Base64.getEncoder().encodeToString(audioData);
                String dataUrl = "data:" + mimeType + ";base64," + base64Audio;

                AudioGenerationResult result = AudioGenerationResult.success(base64Audio, dataUrl);
                result.setMimeType(mimeType);
                result.setFormat(format);
                result.setGenerationTimeMs(generationTime);
                result.setMetadata(new HashMap<>());
                result.getMetadata().put("provider", provider);
                return result;
            }
        } catch (Exception e) {
            log.warn("下载音频失败: {}", e.getMessage());
        }
        return AudioGenerationResult.failed("下载音频失败");
    }

    /**
     * 根据模型 ID 推断提供商
     */
    private String detectProvider(String modelId) {
        if (modelId == null) return "openai";

        String lower = modelId.toLowerCase();
        if (lower.contains("glm-tts") || lower.contains("cogaudio")) {
            return "zhipu";
        } else if (lower.contains("tts") || lower.contains("gpt")) {
            return "openai";
        } else if (lower.contains("cosyvoice") || lower.contains("sambert")) {
            return "tongyi";
        }
        return "openai";
    }

    private String getMimeType(String format) {
        if (format == null) return "audio/mpeg";
        switch (format.toLowerCase()) {
            case "wav": return "audio/wav";
            case "pcm": return "audio/pcm";
            case "opus": return "audio/opus";
            case "aac": return "audio/aac";
            case "flac": return "audio/flac";
            default: return "audio/mpeg";
        }
    }

    @Data
    public static class AudioGenerationResult {
        private boolean success;
        private String base64Audio;
        private String dataUrl;
        private String mimeType;
        private String format;
        private String errorMessage;
        private Long generationTimeMs;
        private Map<String, Object> metadata;

        public static AudioGenerationResult success(String base64Audio, String dataUrl) {
            AudioGenerationResult result = new AudioGenerationResult();
            result.setSuccess(true);
            result.setBase64Audio(base64Audio);
            result.setDataUrl(dataUrl);
            return result;
        }

        public static AudioGenerationResult failed(String errorMessage) {
            AudioGenerationResult result = new AudioGenerationResult();
            result.setSuccess(false);
            result.setErrorMessage(errorMessage);
            return result;
        }

        public boolean hasAudio() {
            return (base64Audio != null && !base64Audio.isEmpty()) ||
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
