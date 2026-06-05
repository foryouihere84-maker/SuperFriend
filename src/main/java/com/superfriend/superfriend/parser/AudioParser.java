package com.superfriend.superfriend.parser;

import com.superfriend.superfriend.dto.ParseResult;
import com.tencentcloudapi.asr.v20190614.AsrClient;
import com.tencentcloudapi.asr.v20190614.models.*;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

/**
 * 音频解析器
 * 使用腾讯云 ASR 进行语音识别
 */
@Slf4j
@Component
public class AudioParser implements FileParser {

    @Value("${tencent.asr.secret-id:}")
    private String secretId;

    @Value("${tencent.asr.secret-key:}")
    private String secretKey;

    @Value("${tencent.asr.region:ap-beijing}")
    private String region;

    @Value("${tencent.asr.enabled:false}")
    private boolean enabled;

    private AsrClient asrClient;

    // 支持的音频 MIME 类型
    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav",
            "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/flac",
            "audio/ogg", "audio/aac"
    );

    // 支持的音频格式（腾讯云 ASR 支持）
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList(
            "mp3", "wav", "m4a", "flac", "aac", "ogg"
    );

    @PostConstruct
    public void init() {
        if (enabled && secretId != null && !secretId.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            try {
                Credential cred = new Credential(secretId, secretKey);
                HttpProfile httpProfile = new HttpProfile();
                httpProfile.setEndpoint("asr.tencentcloudapi.com");

                ClientProfile clientProfile = new ClientProfile();
                clientProfile.setHttpProfile(httpProfile);

                asrClient = new AsrClient(cred, region, clientProfile);
                log.info("腾讯云 ASR 客户端初始化成功，region: {}", region);
            } catch (Exception e) {
                log.error("腾讯云 ASR 客户端初始化失败: {}", e.getMessage());
            }
        } else {
            log.info("腾讯云 ASR 未启用或未配置");
        }
    }

    @Override
    public ParseResult parse(String fileUrl, String mimeType) {
        if (!enabled || asrClient == null) {
            return ParseResult.failed("ASR 服务未启用或未正确配置");
        }

        if (!supports(mimeType)) {
            return ParseResult.unsupported(mimeType);
        }

        long startTime = System.currentTimeMillis();

        try {
            log.info("开始 ASR 语音识别: {}", fileUrl);

            // 获取音频格式
            String format = getAudioFormat(mimeType, fileUrl);

            // 使用录音文件识别
            CreateRecTaskRequest req = new CreateRecTaskRequest();
            req.setEngineModelType("16k_zh"); // 16k 中文通用模型
            req.setChannelNum(1L);
            req.setResTextFormat(0L); // 识别结果文本形式
            req.setSourceType(0L); // URL 方式
            req.setUrl(fileUrl);

            // 创建识别任务
            CreateRecTaskResponse createResp = asrClient.CreateRecTask(req);
            Long taskId = createResp.getData().getTaskId();
            log.info("ASR 任务创建成功，taskId: {}", taskId);

            // 轮询获取结果
            String textContent = pollTaskResult(taskId);

            long parseTime = System.currentTimeMillis() - startTime;
            log.info("ASR 识别完成，耗时: {}ms，文本长度: {}", parseTime,
                    textContent != null ? textContent.length() : 0);

            ParseResult result = ParseResult.success(textContent);
            result.setMimeType(mimeType);
            result.setParseTimeMs(parseTime);

            result.setMetadata(new HashMap<>());
            result.getMetadata().put("taskId", taskId);
            result.getMetadata().put("format", format);
            result.getMetadata().put("provider", "tencent-asr");

            return result;

        } catch (TencentCloudSDKException e) {
            log.error("腾讯云 ASR 识别失败: {}", e.getMessage());
            return ParseResult.failed("ASR 识别失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("ASR 识别异常: {}", e.getMessage(), e);
            return ParseResult.failed("ASR 识别异常: " + e.getMessage());
        }
    }

    /**
     * 解析 base64 编码的音频数据
     */
    @Override
    public ParseResult parseBase64(String base64Data, String mimeType) {
        if (!enabled || asrClient == null) {
            return ParseResult.failed("ASR 服务未启用或未正确配置");
        }

        if (!supports(mimeType)) {
            return ParseResult.unsupported(mimeType);
        }

        long startTime = System.currentTimeMillis();

        try {
            log.info("开始 ASR 语音识别 (base64 数据)");

            String format = getAudioFormat(mimeType, null);

            // 使用一句话识别（适合短音频）
            SentenceRecognitionRequest req = new SentenceRecognitionRequest();
            req.setEngSerViceType("16k_zh");
            req.setSourceType(1L); // 语音数据来源为 base64
            req.setVoiceFormat(format);
            req.setData(base64Data);

            SentenceRecognitionResponse resp = asrClient.SentenceRecognition(req);
            String textContent = resp.getResult();

            long parseTime = System.currentTimeMillis() - startTime;
            log.info("ASR 一句话识别完成，耗时: {}ms", parseTime);

            ParseResult result = ParseResult.success(textContent);
            result.setMimeType(mimeType);
            result.setParseTimeMs(parseTime);

            return result;

        } catch (TencentCloudSDKException e) {
            log.error("腾讯云 ASR 一句话识别失败: {}", e.getMessage());
            return ParseResult.failed("ASR 识别失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("ASR 识别异常: {}", e.getMessage(), e);
            return ParseResult.failed("ASR 识别异常: " + e.getMessage());
        }
    }

    /**
     * 轮询获取任务结果
     */
    private String pollTaskResult(Long taskId) throws TencentCloudSDKException, InterruptedException {
        int maxRetries = 60; // 最多等待 60 次
        int interval = 3000; // 每次等待 3 秒

        for (int i = 0; i < maxRetries; i++) {
            Thread.sleep(interval);

            DescribeTaskStatusRequest req = new DescribeTaskStatusRequest();
            req.setTaskId(taskId);

            DescribeTaskStatusResponse resp = asrClient.DescribeTaskStatus(req);
            Long status = resp.getData().getStatus();

            // 状态: 0-等待中, 1-进行中, 2-已完成, 3-失败
            if (status == 2) {
                return resp.getData().getResult();
            } else if (status == 3) {
                // 任务失败
                throw new RuntimeException("ASR 任务失败，状态码: " + status);
            }

            log.debug("ASR 任务状态: {}, 等待中... ({}/{})", status, i + 1, maxRetries);
        }

        throw new RuntimeException("ASR 任务超时");
    }

    /**
     * 获取音频格式
     */
    private String getAudioFormat(String mimeType, String fileUrl) {
        // 从 MIME 类型提取
        if (mimeType != null) {
            if (mimeType.contains("mp3") || mimeType.contains("mpeg")) return "mp3";
            if (mimeType.contains("wav")) return "wav";
            if (mimeType.contains("m4a")) return "m4a";
            if (mimeType.contains("flac")) return "flac";
            if (mimeType.contains("aac")) return "aac";
            if (mimeType.contains("ogg")) return "ogg";
        }

        // 从 URL 提取
        if (fileUrl != null) {
            int lastDot = fileUrl.lastIndexOf('.');
            if (lastDot > 0 && lastDot < fileUrl.length() - 1) {
                String ext = fileUrl.substring(lastDot + 1).toLowerCase();
                int queryIndex = ext.indexOf('?');
                if (queryIndex > 0) {
                    ext = ext.substring(0, queryIndex);
                }
                if (SUPPORTED_FORMATS.contains(ext)) {
                    return ext;
                }
            }
        }

        return "wav"; // 默认格式
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        return SUPPORTED_MIME_TYPES.stream()
                .anyMatch(type -> type.equalsIgnoreCase(mimeType) ||
                        mimeType.toLowerCase().startsWith("audio/"));
    }

    @Override
    public String getName() {
        return "AudioParser-TencentASR";
    }

    @Override
    public List<String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    public boolean isEnabled() {
        return enabled && asrClient != null;
    }
}
