package com.superfriend.superfriend.generator;

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
 * 智谱 GLM-TTS 音频生成器
 *
 * 支持模型:
 * - glm-tts: 文本转语音
 *
 * 支持音色:
 * - female: 女声
 * - male: 男声
 *
 * 支持格式: wav (默认), mp3, pcm
 *
 * 文档: https://open.bigmodel.cn/dev/api/audio/tts
 */
@Slf4j
@Component
public class ZhipuAudioGenerator {

    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/audio/speech";

    @Value("${zhipu.api.enabled:false}")
    private boolean enabled;

    @Value("${zhipu.api.key:}")
    private String apiKey;

    @Value("${zhipu.audio.model:glm-tts}")
    private String model;

    @Value("${zhipu.audio.default-voice:female}")
    private String defaultVoice;

    @Value("${zhipu.audio.default-speed:1.0}")
    private double defaultSpeed;

    @Value("${zhipu.audio.default-format:mp3}")
    private String defaultFormat;

    private final RestTemplate restTemplate = createRestTemplate(10_000, 120_000);

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("智谱 GLM-TTS 音频生成器初始化，模型: {}", model);
        } else {
            log.info("智谱 GLM-TTS 音频生成器未启用");
        }
    }

    public AudioGenerationResult generate(String text) {
        return generate(text, null);
    }

    public AudioGenerationResult generate(String text, AudioGenerationOptions options) {
        if (!enabled) {
            return AudioGenerationResult.failed("智谱音频生成服务未启用");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            return AudioGenerationResult.failed("智谱 API Key 未配置");
        }

        if (text == null || text.trim().isEmpty()) {
            return AudioGenerationResult.failed("文本内容不能为空");
        }

        try {
            String voice = (options != null && options.getVoice() != null) ? options.getVoice() : defaultVoice;
            double speed = (options != null) ? options.getSpeed() : defaultSpeed;
            String format = (options != null && options.getResponseFormat() != null) ? options.getResponseFormat() : defaultFormat;

            log.info("开始调用智谱 GLM-TTS 生成音频，text length: {}, voice: {}", text.length(), voice);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", text);
            requestBody.put("voice", voice);
            requestBody.put("speed", speed);
            requestBody.put("response_format", format);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<byte[]> response = restTemplate.postForEntity(API_URL, entity, byte[].class);
            long generationTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().length > 0) {
                byte[] audioData = response.getBody();
                String mimeType = getMimeType(format);
                String base64Audio = java.util.Base64.getEncoder().encodeToString(audioData);
                String dataUrl = "data:" + mimeType + ";base64," + base64Audio;

                AudioGenerationResult result = AudioGenerationResult.success(base64Audio, dataUrl);
                result.setMimeType(mimeType);
                result.setFormat(format);
                result.setGenerationTimeMs(generationTime);
                result.setMetadata(new HashMap<>());
                result.getMetadata().put("model", model);
                result.getMetadata().put("provider", "zhipu");
                result.getMetadata().put("voice", voice);

                log.info("智谱音频生成成功，耗时: {}ms, 大小: {} bytes", generationTime, audioData.length);
                return result;
            } else {
                return AudioGenerationResult.failed("音频生成失败: HTTP " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("智谱音频生成异常: {}", e.getMessage(), e);
            return AudioGenerationResult.failed("音频生成异常: " + e.getMessage());
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