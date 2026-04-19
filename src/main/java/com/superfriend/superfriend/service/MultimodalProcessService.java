package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.entity.AIModelConfig;
import com.superfriend.superfriend.generator.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 多模态处理服务
 * 协调意图识别、文件解析、图片生成等多模态处理流程
 */
@Slf4j
@Service
public class MultimodalProcessService {

    @Autowired
    private UserIntentService userIntentService;

    @Autowired
    private FileParseService fileParseService;

    @Autowired
    private ImageGenerator imageGenerator;

    @Autowired
    private ZhipuImageGenerator zhipuImageGenerator;

    @Autowired
    private GenericImageGenerator genericImageGenerator;

    @Autowired
    private ZhipuAudioGenerator zhipuAudioGenerator;

    @Autowired
    private GenericAudioGenerator genericAudioGenerator;

    @Autowired
    private ZhipuVideoGenerator zhipuVideoGenerator;

    @Autowired
    private GenericVideoGenerator genericVideoGenerator;

    @Autowired
    @Lazy
    private ModelCapabilityService modelCapabilityService;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    /**
     * 处理多模态请求
     *
     * @param request   AI 对话请求
     * @param onResponse 响应回调
     * @return 多模态上下文
     */
    public MultimodalContext process(AIChatRequest request, Consumer<AIChatResponse> onResponse) {
        return process(request, null, onResponse);
    }

    /**
     * 处理多模态请求（带已识别的意图）
     *
     * @param request   AI 对话请求
     * @param intent    已识别的用户意图（可为 null，为 null 时内部重新识别）
     * @param onResponse 响应回调
     * @return 多模态上下文
     */
    public MultimodalContext process(AIChatRequest request, UserIntent intent, Consumer<AIChatResponse> onResponse) {
        log.info("开始处理多模态请求");

        MultimodalContext context = MultimodalContext.create();

        try {
            // 1. 意图识别（如果调用方已提供则直接使用，避免重复识别）
            if (intent == null) {
                intent = userIntentService.analyzeIntent(request);
            }
            context.setUserIntent(intent);
            context.advanceTo(MultimodalContext.ProcessingStage.INTENT_DETECTED);

            log.info("意图识别结果: {}", intent.getType());

            // 2. 根据意图类型处理
            switch (intent.getType()) {
                case GENERATE_IMAGE:
                    handleImageGeneration(request, intent, context, onResponse);
                    break;

                case GENERATE_AUDIO:
                    handleAudioGeneration(request, intent, context, onResponse);
                    break;

                case GENERATE_VIDEO:
                    handleVideoGeneration(request, intent, context, onResponse);
                    break;

                case GENERATE_DOCUMENT:
                    // 文档生成由 Agent 的 ReAct 循环通过 skills（如 minimax-docx）处理
                    // 这里只需要记录意图，让对话继续
                    log.info("检测到文档生成意图，由 Agent 通过 skill 处理");
                    context.setEnhancedMessage(request.getEffectiveText());
                    break;

                case PARSE_FILE:
                case PARSE_IMAGE:
                case PARSE_AUDIO:
                case PARSE_VIDEO:
                    handleFileParsing(request, intent, context);
                    break;

                case MULTIMODAL_CHAT:
                    handleMultimodalChat(request, context);
                    break;

                default:
                    // 普通对话，不需要额外处理
                    context.setEnhancedMessage(request.getEffectiveText());
            }

            return context;

        } catch (Exception e) {
            log.error("多模态处理失败: {}", e.getMessage(), e);
            context.advanceTo(MultimodalContext.ProcessingStage.FAILED);
            return context;
        }
    }

    /**
     * 处理图片生成
     */
    private void handleImageGeneration(AIChatRequest request, UserIntent intent,
                                        MultimodalContext context,
                                        Consumer<AIChatResponse> onResponse) {
        context.advanceTo(MultimodalContext.ProcessingStage.GENERATING);

        String prompt = intent.getGenerationPrompt();
        if (prompt == null || prompt.isEmpty()) {
            prompt = request.getEffectiveText();
        }

        log.info("开始生成图片，提示词: {}", prompt);

        // 发送状态更新
        AIChatResponse statusResponse = new AIChatResponse();
        statusResponse.setType("generating");
        statusResponse.setContent("正在生成图片...");
        statusResponse.setSessionId(request.getSessionId());
        onResponse.accept(statusResponse);

        // 获取图片尺寸
        String size = intent.getImageSize();
        if (size == null || size.isEmpty()) {
            size = request.getImageSize();
        }
        if (size != null && !size.isEmpty()) {
            log.info("使用图片尺寸: {}", size);
        }

        String imageDataUrl = null;
        Map<String, Object> metadata = null;
        Long generationTimeMs = null;
        String errorMessage = null;

        // 1. 遍历用户配置的所有模型，找到支持图片输出的模型逐一尝试
        List<AIModelConfig> userModels = modelConfigService.getAvailableModelEntities(request.getUserId());
        if (userModels != null && !userModels.isEmpty()) {
            for (AIModelConfig model : userModels) {
                // 检查模型是否启用
                if (model.getIsEnabled() == null || !model.getIsEnabled()) {
                    continue;
                }
                // 检查模型是否支持图片输出
                if (!model.supportsImageOutput()) {
                    log.debug("模型 {} 不支持图片输出，跳过", model.getModelId());
                    continue;
                }

                log.info("尝试使用用户模型生成图片: {} ({})", model.getName(), model.getModelId());
                try {
                    GenericImageGenerator.GenerationResult result = genericImageGenerator.generate(model, prompt, size);
                    if (result.isSuccess() && result.hasImage()) {
                        imageDataUrl = result.getDataUrl();
                        metadata = result.getMetadata();
                        generationTimeMs = result.getGenerationTimeMs();
                        log.info("用户模型 {} 图片生成成功，耗时: {}ms", model.getModelId(), result.getGenerationTimeMs());
                        break; // 成功则退出循环
                    } else {
                        log.warn("用户模型 {} 图片生成失败: {}", model.getModelId(), result.getErrorMessage());
                        if (errorMessage == null) {
                            errorMessage = result.getErrorMessage();
                        }
                    }
                } catch (Exception e) {
                    log.warn("用户模型 {} 图片生成异常: {}", model.getModelId(), e.getMessage());
                    if (errorMessage == null) {
                        errorMessage = e.getMessage();
                    }
                }
            }
        }

        // 2. 如果用户模型不支持或失败，使用系统配置的智谱 CogView-4
        if (imageDataUrl == null && zhipuImageGenerator.isEnabled()) {
            log.info("使用智谱 CogView-4 生成图片");
            ZhipuImageGenerator.GenerationOptions options = new ZhipuImageGenerator.GenerationOptions();
            if (size != null && !size.isEmpty()) {
                options.setSize(size);
            }
            ZhipuImageGenerator.GenerationResult result = zhipuImageGenerator.generate(prompt, options);
            if (result.isSuccess() && result.hasImage()) {
                imageDataUrl = result.getDataUrl();
                metadata = result.getMetadata();
                generationTimeMs = result.getGenerationTimeMs();
                log.info("智谱图片生成成功，耗时: {}ms", result.getGenerationTimeMs());
            } else {
                if (errorMessage == null) {
                    errorMessage = result.getErrorMessage();
                }
                log.warn("智谱图片生成失败: {}", result.getErrorMessage());
            }
        }

        // 3. 最后尝试 Stable Diffusion（本地部署）
        if (imageDataUrl == null && imageGenerator.isEnabled()) {
            log.info("使用 Stable Diffusion 生成图片");
            ImageGenerator.GenerationResult result = imageGenerator.txt2img(prompt);
            if (result.isSuccess() && result.hasImage()) {
                imageDataUrl = result.getDataUrl();
                metadata = result.getMetadata();
                generationTimeMs = result.getGenerationTimeMs();
                log.info("Stable Diffusion 图片生成成功，耗时: {}ms", result.getGenerationTimeMs());
            } else {
                if (errorMessage == null) {
                    errorMessage = result.getErrorMessage();
                }
                log.warn("Stable Diffusion 图片生成失败: {}", result.getErrorMessage());
            }
        }

        if (imageDataUrl != null) {
            // 生成资源 ID
            String resourceId = "img-" + java.util.UUID.randomUUID().toString().substring(0, 8);

            context.setGeneratedImageUrl(imageDataUrl);
            context.setGeneratedMediaUrl(imageDataUrl);
            context.setGeneratedResourceId(resourceId);
            context.setGeneratedResourceType("image");
            context.advanceTo(MultimodalContext.ProcessingStage.COMPLETED);

            // 返回生成的图片
            AIChatResponse imageResponse = new AIChatResponse();
            imageResponse.setType("image");
            imageResponse.setContent(imageDataUrl);
            imageResponse.setSessionId(request.getSessionId());
            imageResponse.setResourceId(resourceId);  // 设置资源 ID
            // 【修复】设置生成元数据
            if (generationTimeMs != null) {
                imageResponse.setGenerationTimeMs(generationTimeMs);
            }
            if (metadata != null && !metadata.isEmpty()) {
                imageResponse.setMetadata(metadata);
                imageResponse.setMultimodalContent(java.util.Collections.singletonList(
                    AIChatResponse.ResponseContent.image(imageDataUrl)
                ));
            }
            onResponse.accept(imageResponse);
        } else {
            log.error("图片生成失败: {}", errorMessage);

            AIChatResponse errorResponse = new AIChatResponse();
            errorResponse.setType("error");
            if (errorMessage != null) {
                errorResponse.setContent("图片生成失败: " + errorMessage);
            } else {
                errorResponse.setContent("图片生成服务未启用，请配置智谱 API Key 或 Stable Diffusion");
            }
            errorResponse.setSessionId(request.getSessionId());
            onResponse.accept(errorResponse);
        }
    }

    /**
     * 处理音频生成
     */
    private void handleAudioGeneration(AIChatRequest request, UserIntent intent,
                                        MultimodalContext context,
                                        Consumer<AIChatResponse> onResponse) {
        context.advanceTo(MultimodalContext.ProcessingStage.GENERATING);

        String text = intent.getGenerationPrompt();
        if (text == null || text.isEmpty()) {
            text = request.getEffectiveText();
        }

        log.info("开始生成音频，文本长度: {}", text.length());

        // 发送状态更新
        AIChatResponse statusResponse = new AIChatResponse();
        statusResponse.setType("generating");
        statusResponse.setContent("正在生成音频...");
        statusResponse.setSessionId(request.getSessionId());
        onResponse.accept(statusResponse);

        String audioDataUrl = null;
        Long generationTimeMs = null;
        String errorMessage = null;

        // 1. 遍历用户配置的所有模型，找到支持音频输出的模型逐一尝试
        List<AIModelConfig> userModels = modelConfigService.getAvailableModelEntities(request.getUserId());
        if (userModels != null && !userModels.isEmpty()) {
            for (AIModelConfig model : userModels) {
                // 检查模型是否启用
                if (model.getIsEnabled() == null || !model.getIsEnabled()) {
                    continue;
                }
                // 检查模型是否支持音频输出
                if (!model.supportsAudioOutput()) {
                    log.debug("模型 {} 不支持音频输出，跳过", model.getModelId());
                    continue;
                }

                log.info("尝试使用用户模型生成音频: {} ({})", model.getName(), model.getModelId());
                try {
                    GenericAudioGenerator.AudioGenerationResult result =
                            genericAudioGenerator.generate(model, text, new AudioGenerationOptions());
                    if (result.isSuccess() && result.hasAudio()) {
                        audioDataUrl = result.getDataUrl();
                        generationTimeMs = result.getGenerationTimeMs();
                        log.info("用户模型 {} 音频生成成功，耗时: {}ms", model.getModelId(), result.getGenerationTimeMs());
                        break; // 成功则退出循环
                    } else {
                        log.warn("用户模型 {} 音频生成失败: {}", model.getModelId(), result.getErrorMessage());
                        if (errorMessage == null) {
                            errorMessage = result.getErrorMessage();
                        }
                    }
                } catch (Exception e) {
                    log.warn("用户模型 {} 音频生成异常: {}", model.getModelId(), e.getMessage());
                    if (errorMessage == null) {
                        errorMessage = e.getMessage();
                    }
                }
            }
        }

        // 2. 如果用户模型不支持或失败，使用系统配置的智谱 GLM-TTS
        if (audioDataUrl == null && zhipuAudioGenerator.isEnabled()) {
            log.info("使用智谱 GLM-TTS 生成音频");
            ZhipuAudioGenerator.AudioGenerationResult result = zhipuAudioGenerator.generate(text);
            if (result.isSuccess() && result.hasAudio()) {
                audioDataUrl = result.getDataUrl();
                generationTimeMs = result.getGenerationTimeMs();
                log.info("智谱音频生成成功，耗时: {}ms", result.getGenerationTimeMs());
            } else {
                if (errorMessage == null) {
                    errorMessage = result.getErrorMessage();
                }
                log.warn("智谱音频生成失败: {}", result.getErrorMessage());
            }
        }

        if (audioDataUrl != null) {
            // 生成资源 ID
            String resourceId = "audio-" + java.util.UUID.randomUUID().toString().substring(0, 8);

            context.setGeneratedMediaUrl(audioDataUrl);
            context.setGeneratedResourceId(resourceId);
            context.setGeneratedResourceType("audio");
            context.advanceTo(MultimodalContext.ProcessingStage.COMPLETED);

            AIChatResponse audioResponse = new AIChatResponse();
            audioResponse.setType("audio");
            audioResponse.setContent(audioDataUrl);
            audioResponse.setSessionId(request.getSessionId());
            audioResponse.setResourceId(resourceId);  // 设置资源 ID
            // 【修复】设置生成元数据
            if (generationTimeMs != null) {
                audioResponse.setGenerationTimeMs(generationTimeMs);
            }
            audioResponse.setMultimodalContent(Collections.singletonList(
                    AIChatResponse.ResponseContent.audio(audioDataUrl)
            ));
            onResponse.accept(audioResponse);
        } else {
            log.error("音频生成失败: {}", errorMessage);

            AIChatResponse errorResponse = new AIChatResponse();
            errorResponse.setType("error");
            if (errorMessage != null) {
                errorResponse.setContent("音频生成失败: " + errorMessage);
            } else {
                errorResponse.setContent("音频生成服务未启用，请配置智谱 API Key 或支持音频输出的模型");
            }
            errorResponse.setSessionId(request.getSessionId());
            onResponse.accept(errorResponse);
        }
    }

    /**
     * 处理视频生成
     */
    private void handleVideoGeneration(AIChatRequest request, UserIntent intent,
                                        MultimodalContext context,
                                        Consumer<AIChatResponse> onResponse) {
        context.advanceTo(MultimodalContext.ProcessingStage.GENERATING);

        String prompt = intent.getGenerationPrompt();
        if (prompt == null || prompt.isEmpty()) {
            prompt = request.getEffectiveText();
        }

        log.info("开始生成视频，提示词: {}", prompt);

        // 发送状态更新
        AIChatResponse statusResponse = new AIChatResponse();
        statusResponse.setType("generating");
        statusResponse.setContent("正在生成视频，这可能需要几分钟...");
        statusResponse.setSessionId(request.getSessionId());
        onResponse.accept(statusResponse);

        String videoUrl = null;
        Long generationTimeMs = null;
        String errorMessage = null;

        // 1. 遍历用户配置的所有模型，找到支持视频输出的模型逐一尝试
        List<AIModelConfig> userModels = modelConfigService.getAvailableModelEntities(request.getUserId());
        if (userModels != null && !userModels.isEmpty()) {
            for (AIModelConfig model : userModels) {
                // 检查模型是否启用
                if (model.getIsEnabled() == null || !model.getIsEnabled()) {
                    continue;
                }
                // 检查模型是否支持视频输出
                if (!model.supportsVideoOutput()) {
                    log.debug("模型 {} 不支持视频输出，跳过", model.getModelId());
                    continue;
                }

                log.info("尝试使用用户模型生成视频: {} ({})", model.getName(), model.getModelId());
                try {
                    GenericVideoGenerator.VideoGenerationResult result =
                            genericVideoGenerator.generate(model, prompt, new VideoGenerationOptions());
                    if (result.isSuccess() && result.hasVideo()) {
                        videoUrl = result.getVideoUrl();
                        generationTimeMs = result.getGenerationTimeMs();
                        log.info("用户模型 {} 视频生成成功，耗时: {}ms", model.getModelId(), result.getGenerationTimeMs());
                        break; // 成功则退出循环
                    } else {
                        log.warn("用户模型 {} 视频生成失败: {}", model.getModelId(), result.getErrorMessage());
                        if (errorMessage == null) {
                            errorMessage = result.getErrorMessage();
                        }
                    }
                } catch (Exception e) {
                    log.warn("用户模型 {} 视频生成异常: {}", model.getModelId(), e.getMessage());
                    if (errorMessage == null) {
                        errorMessage = e.getMessage();
                    }
                }
            }
        }

        // 2. 如果用户模型不支持或失败，使用系统配置的智谱 CogVideoX
        if (videoUrl == null && zhipuVideoGenerator.isEnabled()) {
            log.info("使用智谱 CogVideoX 生成视频");
            ZhipuVideoGenerator.VideoGenerationResult result = zhipuVideoGenerator.generate(prompt);
            if (result.isSuccess() && result.hasVideo()) {
                videoUrl = result.getVideoUrl();
                generationTimeMs = result.getGenerationTimeMs();
                log.info("智谱视频生成成功，耗时: {}ms", result.getGenerationTimeMs());
            } else {
                if (errorMessage == null) {
                    errorMessage = result.getErrorMessage();
                }
                log.warn("智谱视频生成失败: {}", result.getErrorMessage());
            }
        }

        if (videoUrl != null) {
            // 生成资源 ID
            String resourceId = "video-" + java.util.UUID.randomUUID().toString().substring(0, 8);

            context.setGeneratedMediaUrl(videoUrl);
            context.setGeneratedResourceId(resourceId);
            context.setGeneratedResourceType("video");
            context.advanceTo(MultimodalContext.ProcessingStage.COMPLETED);

            AIChatResponse videoResponse = new AIChatResponse();
            videoResponse.setType("video");
            videoResponse.setContent(videoUrl);
            videoResponse.setSessionId(request.getSessionId());
            videoResponse.setResourceId(resourceId);  // 设置资源 ID
            // 【修复】设置生成元数据
            if (generationTimeMs != null) {
                videoResponse.setGenerationTimeMs(generationTimeMs);
            }
            videoResponse.setMultimodalContent(Collections.singletonList(
                    AIChatResponse.ResponseContent.video(videoUrl)
            ));
            onResponse.accept(videoResponse);
        } else {
            log.error("视频生成失败: {}", errorMessage);

            AIChatResponse errorResponse = new AIChatResponse();
            errorResponse.setType("error");
            if (errorMessage != null) {
                errorResponse.setContent("视频生成失败: " + errorMessage);
            } else {
                errorResponse.setContent("视频生成服务未启用，请配置智谱 API Key 或支持视频输出的模型");
            }
            errorResponse.setSessionId(request.getSessionId());
            onResponse.accept(errorResponse);
        }
    }

    /**
     * 处理文件解析
     */
    private void handleFileParsing(AIChatRequest request, UserIntent intent,
                                    MultimodalContext context) {
        context.advanceTo(MultimodalContext.ProcessingStage.PARSING_FILE);

        String fileUrl = intent.getFileUrl();
        String mimeType = intent.getMimeType();

        if (fileUrl == null || fileUrl.isEmpty()) {
            // 尝试从请求中提取文件
            List<String[]> files = userIntentService.extractFiles(request);
            if (!files.isEmpty()) {
                fileUrl = files.get(0)[0];
                mimeType = files.get(0)[1];
            }
        }

        if (fileUrl == null) {
            log.warn("未找到要解析的文件");
            context.setEnhancedMessage(request.getEffectiveText());
            return;
        }

        log.info("开始解析文件: {}", fileUrl);

        ParseResult parseResult = fileParseService.parse(fileUrl, mimeType);
        context.setParseResult(parseResult);
        context.advanceTo(MultimodalContext.ProcessingStage.FILE_PARSED);

        if (parseResult.isSuccess() && parseResult.hasContent()) {
            // 构建增强消息
            String enhancedMessage = buildEnhancedMessage(request.getEffectiveText(), parseResult);
            context.setEnhancedMessage(enhancedMessage);

            log.info("文件解析成功，内容长度: {}", parseResult.getTextContent().length());
        } else {
            log.warn("文件解析失败或无内容: {}", parseResult.getErrorMessage());
            context.setEnhancedMessage(request.getEffectiveText());
        }
    }

    /**
     * 处理多模态对话
     */
    private void handleMultimodalChat(AIChatRequest request, MultimodalContext context) {
        // 检测需要的模态类型
        List<String> requiredModalities = detectRequiredModalities(request);
        context.setRequiredModalities(requiredModalities);

        // 检查是否需要切换模型
        if (request.getModel() != null) {
            AIModelConfig config = modelConfigService.resolveModelConfig(request.getModel(), request.getUserId());
            if (config != null && !isModelCapable(config, requiredModalities)) {
                // 需要切换模型
                context.setModelSwitchRequired(true);
                // 推荐支持所需模态的模型
                AIModelConfig recommended = modelCapabilityService.selectCapableModel(
                        request.getUserId(),
                        modelCapabilityService.detectRequiredModalitiesFromRequest(
                                request.getContent(), request.getImages()),
                        null
                );
                if (recommended != null) {
                    context.setRecommendedModelId(recommended.getConfigId());
                }
            }
        }

        context.setEnhancedMessage(request.getEffectiveText());
    }

    /**
     * 构建增强消息（注入解析内容）
     */
    private String buildEnhancedMessage(String originalText, ParseResult parseResult) {
        StringBuilder sb = new StringBuilder();

        if (originalText != null && !originalText.isEmpty()) {
            sb.append(originalText);
        }

        if (parseResult.hasContent()) {
            sb.append("\n\n--- 文件内容 ---\n");
            sb.append(parseResult.getFormattedContentForLLM());
        }

        return sb.toString();
    }

    /**
     * 检测需要的模态类型
     */
    private List<String> detectRequiredModalities(AIChatRequest request) {
        List<String> modalities = new ArrayList<>();
        modalities.add("text"); // 文本始终需要

        // 检查便捷方式的图片
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            modalities.add("image");
        }

        // 检查结构化内容
        if (request.getContent() != null) {
            for (ChatMessageContent c : request.getContent()) {
                if (c.isImage() && !modalities.contains("image")) {
                    modalities.add("image");
                } else if (c.isAudio() && !modalities.contains("audio")) {
                    modalities.add("audio");
                } else if (c.isVideo() && !modalities.contains("video")) {
                    modalities.add("video");
                }
            }
        }

        return modalities;
    }

    /**
     * 检查模型是否支持所需模态
     */
    private boolean isModelCapable(AIModelConfig config, List<String> requiredModalities) {
        for (String modality : requiredModalities) {
            if (!config.supportsModality(modality)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否需要多模态处理
     */
    public boolean needsMultimodalProcessing(AIChatRequest request) {
        UserIntent intent = userIntentService.analyzeIntent(request);
        return intent.getType() != UserIntent.Type.CHAT;
    }

    /**
     * 判断是否为图片生成请求
     */
    public boolean isImageGenerationRequest(AIChatRequest request) {
        UserIntent intent = userIntentService.analyzeIntent(request);
        return intent.getType() == UserIntent.Type.GENERATE_IMAGE;
    }

    /**
     * 判断是否需要文件解析
     */
    public boolean needsFileParsing(AIChatRequest request) {
        UserIntent intent = userIntentService.analyzeIntent(request);
        return intent.isFileParsing();
    }
}
