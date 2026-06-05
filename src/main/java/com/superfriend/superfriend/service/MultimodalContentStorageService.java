package com.superfriend.superfriend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MultimodalContentStorageService {

    private static final Pattern DATA_URL_PATTERN = Pattern.compile(
        "data:([a-zA-Z0-9]+/[a-zA-Z0-9.+-]+);base64,([A-Za-z0-9+/=]{100,})"
    );

    private static final int MAX_CONTENT_LENGTH = 60000;

    @Data
    public static class ProcessedContent {
        private String content;
        private List<MediaInfo> mediaInfo;

        public ProcessedContent(String content, List<MediaInfo> mediaInfo) {
            this.content = content;
            this.mediaInfo = mediaInfo;
        }
    }

    @Data
    public static class MediaInfo {
        private String type;
        private String mimeType;
        private int length;

        public MediaInfo(String type, String mimeType, int length) {
            this.type = type;
            this.mimeType = mimeType;
            this.length = length;
        }
    }

    public ProcessedContent processContent(String content) {
        if (content == null || content.isEmpty()) {
            return new ProcessedContent(content, new ArrayList<>());
        }

        if (content.length() <= MAX_CONTENT_LENGTH && !containsDataUrl(content)) {
            return new ProcessedContent(content, new ArrayList<>());
        }

        log.info("检测到大数据内容 (length={}), 开始处理 base64 数据", content.length());

        List<MediaInfo> mediaInfoList = new ArrayList<>();
        StringBuffer processedContent = new StringBuffer();
        Matcher matcher = DATA_URL_PATTERN.matcher(content);
        int lastEnd = 0;
        int mediaIndex = 0;

        while (matcher.find()) {
            processedContent.append(content, lastEnd, matcher.start());

            String mimeType = matcher.group(1);
            String base64Data = matcher.group(2);
            int dataLength = base64Data.length();

            String type = getMediaType(mimeType);
            mediaInfoList.add(new MediaInfo(type, mimeType, dataLength));

            mediaIndex++;
            String placeholder = String.format("[%s已发送到前端]", getMediaTypeName(type));
            processedContent.append(placeholder);

            log.debug("替换 base64 数据: type={}, mimeType={}, length={}", type, mimeType, dataLength);

            lastEnd = matcher.end();
        }

        processedContent.append(content.substring(lastEnd));

        String result = processedContent.toString();
        log.info("内容处理完成: 原始长度={}, 处理后长度={}, 媒体数={}",
            content.length(), result.length(), mediaInfoList.size());

        return new ProcessedContent(result, mediaInfoList);
    }

    private boolean containsDataUrl(String content) {
        return DATA_URL_PATTERN.matcher(content).find();
    }

    private String getMediaType(String mimeType) {
        if (mimeType == null) {
            return "file";
        }
        if (mimeType.startsWith("image/")) {
            return "image";
        } else if (mimeType.startsWith("audio/")) {
            return "audio";
        } else if (mimeType.startsWith("video/")) {
            return "video";
        }
        return "file";
    }

    private String getMediaTypeName(String type) {
        switch (type) {
            case "image":
                return "图片";
            case "audio":
                return "音频";
            case "video":
                return "视频";
            default:
                return "媒体文件";
        }
    }

    public boolean needsProcessing(String content) {
        if (content == null) {
            return false;
        }
        return content.length() > MAX_CONTENT_LENGTH || containsDataUrl(content);
    }
}
