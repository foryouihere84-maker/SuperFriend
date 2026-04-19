package com.superfriend.superfriend.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.ParseResult;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 图片解析器
 * 使用腾讯云 OCR 进行文字识别
 */
@Slf4j
@Component
public class ImageParser implements FileParser {

    @Value("${tencent.ocr.secret-id:}")
    private String secretId;

    @Value("${tencent.ocr.secret-key:}")
    private String secretKey;

    @Value("${tencent.ocr.region:ap-beijing}")
    private String region;

    @Value("${tencent.ocr.enabled:false}")
    private boolean enabled;

    private OcrClient ocrClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 支持的图片 MIME 类型
    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif",
            "image/bmp", "image/webp", "image/tiff"
    );

    @PostConstruct
    public void init() {
        if (enabled && secretId != null && !secretId.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            try {
                Credential cred = new Credential(secretId, secretKey);
                HttpProfile httpProfile = new HttpProfile();
                httpProfile.setEndpoint("ocr.tencentcloudapi.com");

                ClientProfile clientProfile = new ClientProfile();
                clientProfile.setHttpProfile(httpProfile);

                ocrClient = new OcrClient(cred, region, clientProfile);
                log.info("腾讯云 OCR 客户端初始化成功，region: {}", region);
            } catch (Exception e) {
                log.error("腾讯云 OCR 客户端初始化失败: {}", e.getMessage());
            }
        } else {
            log.info("腾讯云 OCR 未启用或未配置");
        }
    }

    @Override
    public ParseResult parse(String fileUrl, String mimeType) {
        if (!enabled || ocrClient == null) {
            return ParseResult.failed("OCR 服务未启用或未正确配置");
        }

        if (!supports(mimeType)) {
            return ParseResult.unsupported(mimeType);
        }

        long startTime = System.currentTimeMillis();

        try {
            log.info("开始 OCR 识别: {}", fileUrl);

            // 使用通用文字识别
            GeneralAccurateOCRRequest req = new GeneralAccurateOCRRequest();
            req.setImageUrl(fileUrl);

            GeneralAccurateOCRResponse resp = ocrClient.GeneralAccurateOCR(req);

            // 提取文字内容
            StringBuilder textContent = new StringBuilder();
            if (resp.getTextDetections() != null) {
                for (TextDetection detection : resp.getTextDetections()) {
                    textContent.append(detection.getDetectedText()).append("\n");
                }
            }

            long parseTime = System.currentTimeMillis() - startTime;
            log.info("OCR 识别完成，耗时: {}ms，识别出 {} 个文本块", parseTime,
                    resp.getTextDetections() != null ? resp.getTextDetections().length : 0);

            ParseResult result = ParseResult.success(textContent.toString().trim());
            result.setMimeType(mimeType);
            result.setParseTimeMs(parseTime);

            // 添加元数据
            result.setMetadata(new java.util.HashMap<>());
            result.getMetadata().put("textBlockCount", resp.getTextDetections() != null ? resp.getTextDetections().length : 0);
            result.getMetadata().put("provider", "tencent-ocr");

            return result;

        } catch (TencentCloudSDKException e) {
            log.error("腾讯云 OCR 识别失败: {}", e.getMessage());
            return ParseResult.failed("OCR 识别失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("OCR 识别异常: {}", e.getMessage(), e);
            return ParseResult.failed("OCR 识别异常: " + e.getMessage());
        }
    }

    @Override
    public ParseResult parseBase64(String base64Data, String mimeType) {
        if (!enabled || ocrClient == null) {
            return ParseResult.failed("OCR 服务未启用或未正确配置");
        }

        if (!supports(mimeType)) {
            return ParseResult.unsupported(mimeType);
        }

        long startTime = System.currentTimeMillis();

        try {
            log.info("开始 OCR 识别 (base64 数据)");

            GeneralAccurateOCRRequest req = new GeneralAccurateOCRRequest();
            req.setImageBase64(base64Data);

            GeneralAccurateOCRResponse resp = ocrClient.GeneralAccurateOCR(req);

            StringBuilder textContent = new StringBuilder();
            if (resp.getTextDetections() != null) {
                for (TextDetection detection : resp.getTextDetections()) {
                    textContent.append(detection.getDetectedText()).append("\n");
                }
            }

            long parseTime = System.currentTimeMillis() - startTime;
            log.info("OCR 识别完成，耗时: {}ms", parseTime);

            ParseResult result = ParseResult.success(textContent.toString().trim());
            result.setMimeType(mimeType);
            result.setParseTimeMs(parseTime);

            return result;

        } catch (TencentCloudSDKException e) {
            log.error("腾讯云 OCR 识别失败: {}", e.getMessage());
            return ParseResult.failed("OCR 识别失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("OCR 识别异常: {}", e.getMessage(), e);
            return ParseResult.failed("OCR 识别异常: " + e.getMessage());
        }
    }

    /**
     * 表格识别
     */
    public ParseResult parseTable(String fileUrl) {
        if (!enabled || ocrClient == null) {
            return ParseResult.failed("OCR 服务未启用或未正确配置");
        }

        try {
            log.info("开始表格 OCR 识别: {}", fileUrl);

            TableOCRRequest req = new TableOCRRequest();
            req.setImageUrl(fileUrl);

            TableOCRResponse resp = ocrClient.TableOCR(req);

            // 表格识别返回的是 TextTable 数组，需要使用正确的类型
            StringBuilder textContent = new StringBuilder();

            // 使用反射或直接获取响应字符串
            if (resp.getTextDetections() != null) {
                // TextTable 和 TextDetection 结构不同，使用 toString 获取内容
                for (Object item : resp.getTextDetections()) {
                    if (item != null) {
                        textContent.append(item.toString()).append("\n");
                    }
                }
            }

            if (textContent.length() == 0) {
                textContent.append("表格识别完成");
            }

            ParseResult result = ParseResult.success(textContent.toString());
            result.setMetadata(new java.util.HashMap<>());
            result.getMetadata().put("provider", "tencent-ocr-table");

            return result;

        } catch (Exception e) {
            log.error("表格 OCR 识别失败: {}", e.getMessage());
            return ParseResult.failed("表格 OCR 识别失败: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        return SUPPORTED_MIME_TYPES.stream()
                .anyMatch(type -> type.equalsIgnoreCase(mimeType) ||
                        mimeType.toLowerCase().startsWith(type.split("/")[0] + "/"));
    }

    @Override
    public String getName() {
        return "ImageParser-TencentOCR";
    }

    @Override
    public List<String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    public boolean isEnabled() {
        return enabled && ocrClient != null;
    }
}
