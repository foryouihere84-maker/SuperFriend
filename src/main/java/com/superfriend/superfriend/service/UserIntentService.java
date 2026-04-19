package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.AIChatRequest;
import com.superfriend.superfriend.dto.ChatMessageContent;
import com.superfriend.superfriend.dto.UserIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 用户意图识别服务
 * 分析用户请求，识别意图类型（生成图片、解析文件、多模态对话等）
 *
 * 支持两种分类方式：
 * 1. LLM 意图分类（智能、准确）- 优先使用
 * 2. 关键词匹配（快速、无依赖）- 作为后备
 */
@Slf4j
@Service
public class UserIntentService {

    @Autowired
    private FileParseService fileParseService;

    @Autowired
    @Lazy
    private LLMIntentClassifier llmIntentClassifier;

    @Value("${intent.classifier.enabled:true}")
    private boolean llmClassifierEnabled;

    @Value("${intent.classifier.fallback-to-keywords:true}")
    private boolean fallbackToKeywords;

    // ============ 生成意图关键词 ============

    // 图片生成关键词
    private static final List<String> IMAGE_GENERATION_KEYWORDS = Arrays.asList(
            "画", "画图", "画一张", "帮我画", "生成图片", "生成图像",
            "创建图片", "创建图像", "制作图片", "绘制",
            "draw", "draw me", "create image", "generate image",
            "make image", "paint", "illustrate"
    );

    // 音频生成关键词
    private static final List<String> AUDIO_GENERATION_KEYWORDS = Arrays.asList(
            "生成音频", "生成语音", "朗读", "读出来", "说出来",
            "generate audio", "text to speech", "tts", "read aloud"
    );

    // 视频生成关键词
    private static final List<String> VIDEO_GENERATION_KEYWORDS = Arrays.asList(
            "生成视频", "制作视频", "创建视频",
            "generate video", "create video", "make video"
    );

    // 文档生成关键词
    private static final List<String> DOCUMENT_GENERATION_KEYWORDS = Arrays.asList(
            "写docx", "写xlsx", "写pptx", "写word", "生成docx", "生成xlsx", "生成pptx",
            "帮我写", "帮我生成", "帮我创建", "写个文档", "生成文档", "创建文档",
            "生成Excel", "生成Word", "生成PPT", "写个Excel", "写个Word", "写个PPT",
            "generate docx", "generate xlsx", "generate pptx", "create document",
            "write document", "make document", "create doc", "write doc"
    );

    // ============ 文件解析意图关键词 ============

    // 文件解析关键词（需要提取内容，如 OCR/ASR）
    private static final List<String> FILE_PARSE_KEYWORDS = Arrays.asList(
            "解析", "读取", "提取", "转录", "转写",
            "parse", "read", "extract", "transcribe"
    );

    // OCR 关键词（明确需要文字识别）
    private static final List<String> OCR_KEYWORDS = Arrays.asList(
            "识别文字", "识别文字", "提取文字", "读取文字", "文字识别",
            "ocr", "text recognition", "extract text"
    );

    // ASR 关键词
    private static final List<String> ASR_KEYWORDS = Arrays.asList(
            "转文字", "语音转文字", "转录", "转写",
            "transcribe", "speech to text", "asr"
    );

    /**
     * 分析用户意图
     *
     * @param request AI 对话请求
     * @return 用户意图
     */
    public UserIntent analyzeIntent(AIChatRequest request) {
        String text = request.getEffectiveText();
        List<ChatMessageContent> content = request.getContent();
        List<String> images = request.getImages();
        List<String> documents = request.getDocuments();
        List<String> audios = request.getAudios();
        List<String> videos = request.getVideos();
        Long userId = request.getUserId();

        log.info("开始分析用户意图，文本: {}, 图片: {}, 文档: {}, 音频: {}, 视频: {}",
                text != null ? (text.length() > 50 ? text.substring(0, 50) + "..." : text) : "null",
                images != null ? images.size() : 0,
                documents != null ? documents.size() : 0,
                audios != null ? audios.size() : 0,
                videos != null ? videos.size() : 0);

        // 收集附件类型
        List<String> attachmentTypes = collectAttachmentTypes(content, images, documents, audios, videos);

        // 优先使用 LLM 意图分类
        if (llmClassifierEnabled) {
            UserIntent llmIntent = llmIntentClassifier.classifyIntent(text, attachmentTypes, userId);
            if (llmIntent != null && llmIntent.getConfidence() >= 0.7) {
                log.info("LLM 意图分类成功: type={}, confidence={}", llmIntent.getType(), llmIntent.getConfidence());
                llmIntent.setUserText(text);
                // 补充文件信息
                enrichIntentWithFiles(llmIntent, content, images, documents, audios, videos);
                return llmIntent;
            } else if (llmIntent != null) {
                log.info("LLM 意图分类置信度较低: {}, 使用关键词匹配", llmIntent.getConfidence());
            }
        }

        // 后备：使用关键词匹配
        if (!llmClassifierEnabled || fallbackToKeywords) {
            UserIntent keywordIntent = analyzeIntentByKeywords(request, text, content, images, documents, audios, videos);
            if (keywordIntent != null) {
                return keywordIntent;
            }
        }

        // 默认为普通对话
        log.info("检测到普通文本对话");
        UserIntent intent = UserIntent.chat();
        intent.setUserText(text);
        return intent;
    }

    /**
     * 收集附件类型
     */
    private List<String> collectAttachmentTypes(List<ChatMessageContent> content,
                                                 List<String> images, List<String> documents,
                                                 List<String> audios, List<String> videos) {
        List<String> types = new ArrayList<>();

        if (images != null && !images.isEmpty()) {
            types.add("image");
        }
        if (documents != null && !documents.isEmpty()) {
            types.add("document");
        }
        if (audios != null && !audios.isEmpty()) {
            types.add("audio");
        }
        if (videos != null && !videos.isEmpty()) {
            types.add("video");
        }

        if (content != null) {
            for (ChatMessageContent c : content) {
                if (c.isImage() && !types.contains("image")) types.add("image");
                else if (c.isAudio() && !types.contains("audio")) types.add("audio");
                else if (c.isVideo() && !types.contains("video")) types.add("video");
            }
        }

        return types;
    }

    /**
     * 为意图补充文件信息
     */
    private void enrichIntentWithFiles(UserIntent intent,
                                        List<ChatMessageContent> content,
                                        List<String> images, List<String> documents,
                                        List<String> audios, List<String> videos) {
        switch (intent.getType()) {
            case PARSE_FILE:
                if (documents != null && !documents.isEmpty()) {
                    intent.setFileUrl(documents.get(0));
                }
                break;
            case PARSE_IMAGE:
                if (images != null && !images.isEmpty()) {
                    intent.setFileUrl(images.get(0));
                } else if (content != null) {
                    for (ChatMessageContent c : content) {
                        if (c.isImage() && c.getImageUrl() != null) {
                            intent.setFileUrl(c.getImageUrl().getUrl());
                            break;
                        }
                    }
                }
                break;
            case PARSE_AUDIO:
                if (audios != null && !audios.isEmpty()) {
                    intent.setFileUrl(audios.get(0));
                }
                break;
            case PARSE_VIDEO:
                if (videos != null && !videos.isEmpty()) {
                    intent.setFileUrl(videos.get(0));
                }
                break;
            case MULTIMODAL_CHAT:
                // 多模态对话也需要记录文件信息（虽然不解析，但需要传递给模型）
                if (images != null && !images.isEmpty()) {
                    intent.setFileUrl(images.get(0));
                    intent.setMimeType("image/*");
                } else if (audios != null && !audios.isEmpty()) {
                    intent.setFileUrl(audios.get(0));
                    intent.setMimeType("audio/*");
                } else if (videos != null && !videos.isEmpty()) {
                    intent.setFileUrl(videos.get(0));
                    intent.setMimeType("video/*");
                } else if (content != null) {
                    for (ChatMessageContent c : content) {
                        if (c.isImage() && c.getImageUrl() != null) {
                            intent.setFileUrl(c.getImageUrl().getUrl());
                            intent.setMimeType("image/*");
                            break;
                        } else if (c.isAudio() && c.getAudioUrl() != null) {
                            intent.setFileUrl(c.getAudioUrl().getUrl());
                            intent.setMimeType("audio/*");
                            break;
                        } else if (c.isVideo() && c.getVideoUrl() != null) {
                            intent.setFileUrl(c.getVideoUrl().getUrl());
                            intent.setMimeType("video/*");
                            break;
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * 使用关键词匹配分析意图（后备方案）
     */
    private UserIntent analyzeIntentByKeywords(AIChatRequest request, String text,
                                                List<ChatMessageContent> content,
                                                List<String> images, List<String> documents,
                                                List<String> audios, List<String> videos) {
        log.debug("使用关键词匹配分析意图");

        // 1. 检测生成意图（优先级最高）
        UserIntent generationIntent = detectGenerationIntent(text);
        if (generationIntent != null) {
            log.info("关键词匹配检测到生成意图: {}", generationIntent.getType());
            generationIntent.setUserText(text);
            return generationIntent;
        }

        // 2. 检测文档文件解析意图（PDF、DOCX 等）
        if (documents != null && !documents.isEmpty()) {
            log.info("检测到文档文件，返回 PARSE_FILE");
            UserIntent intent = UserIntent.parseFile(documents.get(0), "application/octet-stream", null);
            intent.setUserText(text);
            return intent;
        }

        // 3. 检测音频文件解析意图
        if (audios != null && !audios.isEmpty()) {
            log.info("检测到音频文件，返回 PARSE_AUDIO");
            UserIntent intent = UserIntent.parseAudio(audios.get(0), "audio/*");
            intent.setUserText(text);
            return intent;
        }

        // 4. 检测视频文件解析意图
        if (videos != null && !videos.isEmpty()) {
            log.info("检测到视频文件，返回 PARSE_VIDEO");
            UserIntent intent = UserIntent.parseVideo(videos.get(0), "video/*");
            intent.setUserText(text);
            return intent;
        }

        // 5. 检测图片文件解析意图
        if (hasFiles(content, images)) {
            UserIntent fileIntent = detectFileIntent(text, content, images);
            if (fileIntent != null) {
                log.info("关键词匹配检测到文件解析意图: {}", fileIntent.getType());
                fileIntent.setUserText(text);
                return fileIntent;
            }
        }

        // 6. 检测多模态对话
        if (isMultimodalContent(content, images)) {
            log.info("检测到多模态对话");
            UserIntent intent = UserIntent.multimodalChat();
            intent.setUserText(text);
            return intent;
        }

        return null;
    }

    /**
     * 检测生成意图
     */
    private UserIntent detectGenerationIntent(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String lowerText = text.toLowerCase();

        // 检测图片生成
        for (String keyword : IMAGE_GENERATION_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) {
                // 提取生成提示词
                String prompt = extractGenerationPrompt(text, keyword);
                return UserIntent.generateImage(prompt);
            }
        }

        // 检测音频生成
        for (String keyword : AUDIO_GENERATION_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) {
                String prompt = extractGenerationPrompt(text, keyword);
                UserIntent intent = new UserIntent();
                intent.setType(UserIntent.Type.GENERATE_AUDIO);
                intent.setGenerationPrompt(prompt);
                intent.setConfidence(1.0);
                return intent;
            }
        }

        // 检测视频生成
        for (String keyword : VIDEO_GENERATION_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) {
                String prompt = extractGenerationPrompt(text, keyword);
                UserIntent intent = new UserIntent();
                intent.setType(UserIntent.Type.GENERATE_VIDEO);
                intent.setGenerationPrompt(prompt);
                intent.setConfidence(1.0);
                return intent;
            }
        }

        // 检测文档生成
        for (String keyword : DOCUMENT_GENERATION_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) {
                String prompt = extractGenerationPrompt(text, keyword);
                return UserIntent.generateDocument(prompt);
            }
        }

        return null;
    }

    /**
     * 提取生成提示词
     */
    private String extractGenerationPrompt(String text, String keyword) {
        // 尝试提取关键词后面的内容作为提示词
        int index = text.toLowerCase().indexOf(keyword.toLowerCase());
        if (index >= 0) {
            String after = text.substring(index + keyword.length()).trim();
            // 移除常见的连接词
            after = after.replaceFirst("^(一张|一个|一副|a |an |the )", "").trim();
            if (!after.isEmpty()) {
                return after;
            }
        }
        // 如果提取失败，返回整个文本
        return text;
    }

    /**
     * 检测文件解析意图
     */
    private UserIntent detectFileIntent(String text, List<ChatMessageContent> content, List<String> images) {
        String lowerText = text != null ? text.toLowerCase() : "";

        // 检查是否有明确的 OCR 意图
        boolean hasOcrIntent = containsAny(lowerText, OCR_KEYWORDS);

        // 检查是否有文件解析意图
        boolean hasParseIntent = containsAny(lowerText, FILE_PARSE_KEYWORDS);

        // 检查结构化内容
        if (content != null && !content.isEmpty()) {
            for (ChatMessageContent c : content) {
                if (c.isImage()) {
                    // 只有明确需要 OCR 时才走 OCR 路径
                    if (hasOcrIntent) {
                        String imageUrl = c.getImageUrl() != null ? c.getImageUrl().getUrl() : null;
                        return UserIntent.parseImage(imageUrl);
                    }
                    // 其他情况（包括"分析"、"这是什么"等）都走多模态对话
                    return null;
                } else if (c.isAudio()) {
                    // 音频需要明确转写意图，或者包含解析关键词
                    if (containsAny(lowerText, ASR_KEYWORDS) || hasParseIntent) {
                        String audioUrl = c.getAudioUrl() != null ? c.getAudioUrl().getUrl() : null;
                        return UserIntent.parseAudio(audioUrl, c.getType());
                    }
                    return null;
                } else if (c.isVideo()) {
                    // 视频解析需要明确意图
                    if (hasParseIntent) {
                        String videoUrl = c.getVideoUrl() != null ? c.getVideoUrl().getUrl() : null;
                        return UserIntent.parseVideo(videoUrl, c.getType());
                    }
                    return null;
                }
            }
        }

        // 检查便捷方式的图片
        if (images != null && !images.isEmpty()) {
            // 只有明确需要 OCR 时才走 OCR 路径
            if (hasOcrIntent) {
                return UserIntent.parseImage(images.get(0));
            }
            // 其他情况走多模态对话
        }

        return null;
    }

    /**
     * 判断是否有文件
     */
    private boolean hasFiles(List<ChatMessageContent> content, List<String> images) {
        return (content != null && !content.isEmpty()) ||
                (images != null && !images.isEmpty());
    }

    /**
     * 判断是否为多模态内容
     */
    private boolean isMultimodalContent(List<ChatMessageContent> content, List<String> images) {
        // 检查便捷方式的图片
        if (images != null && !images.isEmpty()) {
            return true;
        }

        // 检查结构化内容
        if (content != null) {
            for (ChatMessageContent c : content) {
                if (c.isMultimodal()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查文本是否包含任意关键词
     */
    private boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isEmpty()) return false;
        String lowerText = text.toLowerCase();
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从请求中提取文件信息
     *
     * @param request AI 对话请求
     * @return 文件信息列表，每个元素为 [url, mimeType]
     */
    public List<String[]> extractFiles(AIChatRequest request) {
        List<String[]> files = new ArrayList<>();

        // 从便捷方式的图片提取
        if (request.getImages() != null) {
            for (String imageUrl : request.getImages()) {
                files.add(new String[]{imageUrl, "image/*"});
            }
        }

        // 从便捷方式的文档提取
        if (request.getDocuments() != null) {
            for (String docUrl : request.getDocuments()) {
                files.add(new String[]{docUrl, "application/octet-stream"});
            }
        }

        // 从便捷方式的音频提取
        if (request.getAudios() != null) {
            for (String audioUrl : request.getAudios()) {
                files.add(new String[]{audioUrl, "audio/*"});
            }
        }

        // 从便捷方式的视频提取
        if (request.getVideos() != null) {
            for (String videoUrl : request.getVideos()) {
                files.add(new String[]{videoUrl, "video/*"});
            }
        }

        // 从结构化内容提取
        if (request.getContent() != null) {
            for (ChatMessageContent c : request.getContent()) {
                if (c.isImage() && c.getImageUrl() != null) {
                    files.add(new String[]{c.getImageUrl().getUrl(), "image/*"});
                } else if (c.isAudio()) {
                    String url = c.getAudioUrl() != null ? c.getAudioUrl().getUrl() : null;
                    if (url != null) {
                        files.add(new String[]{url, "audio/*"});
                    }
                } else if (c.isVideo()) {
                    String url = c.getVideoUrl() != null ? c.getVideoUrl().getUrl() : null;
                    if (url != null) {
                        files.add(new String[]{url, "video/*"});
                    }
                }
            }
        }

        return files;
    }

    /**
     * 简化的意图检测（仅检测是否需要生成图片）
     */
    public boolean needsImageGeneration(String text) {
        if (text == null || text.isEmpty()) return false;
        String lowerText = text.toLowerCase();
        return IMAGE_GENERATION_KEYWORDS.stream()
                .anyMatch(k -> lowerText.contains(k.toLowerCase()));
    }

    /**
     * 简化的意图检测（仅检测是否需要文件解析）
     */
    public boolean needsFileParsing(AIChatRequest request) {
        return hasFiles(request.getContent(), request.getImages());
    }
}
