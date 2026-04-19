package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.ChatMessageContent;
import com.superfriend.superfriend.entity.AIModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 模型能力服务
 * 负责检测模型能力、选择合适的模型
 */
@Slf4j
@Service
public class ModelCapabilityService {

    @Autowired
    private AIModelConfigService modelConfigService;

    /**
     * 模型能力枚举
     */
    public enum Modality {
        TEXT("text"),
        IMAGE("image"),
        AUDIO("audio"),
        VIDEO("video");

        private final String value;

        Modality(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Modality fromValue(String value) {
            for (Modality m : values()) {
                if (m.value.equalsIgnoreCase(value)) {
                    return m;
                }
            }
            return TEXT;
        }
    }

    /**
     * 检测消息内容所需的模态类型
     *
     * @param contents 消息内容列表
     * @return 所需的模态类型集合
     */
    public Set<Modality> detectRequiredModalities(List<ChatMessageContent> contents) {
        Set<Modality> modalities = new HashSet<>();
        modalities.add(Modality.TEXT); // 文本始终需要

        if (contents == null || contents.isEmpty()) {
            return modalities;
        }

        for (ChatMessageContent content : contents) {
            if (content.isImage()) {
                modalities.add(Modality.IMAGE);
            } else if (content.isAudio()) {
                modalities.add(Modality.AUDIO);
            } else if (content.isVideo()) {
                modalities.add(Modality.VIDEO);
            }
        }

        return modalities;
    }

    /**
     * 检测 AIChatRequest 是否需要多模态
     *
     * @param content 内容列表
     * @param images 图片列表（便捷方式）
     * @return 所需的模态类型集合
     */
    public Set<Modality> detectRequiredModalitiesFromRequest(
            List<ChatMessageContent> content,
            List<String> images) {

        Set<Modality> modalities = new HashSet<>();
        modalities.add(Modality.TEXT);

        // 检查便捷方式的图片
        if (images != null && !images.isEmpty()) {
            modalities.add(Modality.IMAGE);
        }

        // 检查结构化内容
        if (content != null && !content.isEmpty()) {
            modalities.addAll(detectRequiredModalities(content));
        }

        return modalities;
    }

    /**
     * 判断模型是否支持所需的模态
     *
     * @param config 模型配置
     * @param requiredModalities 所需模态
     * @return 是否支持
     */
    public boolean isModelCapable(AIModelConfig config, Set<Modality> requiredModalities) {
        if (config == null || requiredModalities == null) {
            return false;
        }

        for (Modality modality : requiredModalities) {
            if (!config.supportsModality(modality.getValue())) {
                log.debug("模型 {} 不支持 {} 模态", config.getModelId(), modality.getValue());
                return false;
            }
        }

        return true;
    }

    /**
     * 选择支持所需模态的模型
     *
     * @param userId 用户ID
     * @param requiredModalities 所需模态
     * @param preferredModelId 首选模型ID（可选）
     * @return 匹配的模型配置，如果没有匹配则返回 null
     */
    public AIModelConfig selectCapableModel(
            Long userId,
            Set<Modality> requiredModalities,
            String preferredModelId) {

        // 1. 如果指定了首选模型，检查是否支持
        if (preferredModelId != null && !preferredModelId.isEmpty()) {
            AIModelConfig preferred = modelConfigService.resolveModelConfig(preferredModelId, userId);
            if (preferred != null && isModelCapable(preferred, requiredModalities)) {
                log.info("首选模型 {} 支持所需模态 {}", preferredModelId, requiredModalities);
                return preferred;
            }
            if (preferred != null) {
                log.info("首选模型 {} 不支持所需模态 {}，将查找其他模型", preferredModelId, requiredModalities);
            }
        }

        // 2. 获取用户可用的所有模型，找到支持所需模态的模型
        List<AIModelConfig> availableModels = modelConfigService.getAvailableModelEntities(userId);

        // 按优先级排序：用户默认 > 系统默认 > 其他
        AIModelConfig userDefault = modelConfigService.getDefaultModel(userId);

        // 先检查用户默认模型
        if (userDefault != null && isModelCapable(userDefault, requiredModalities)) {
            log.info("用户默认模型 {} 支持所需模态 {}", userDefault.getModelId(), requiredModalities);
            return userDefault;
        }

        // 遍历所有可用模型
        for (AIModelConfig config : availableModels) {
            if (isModelCapable(config, requiredModalities)) {
                log.info("选择模型 {} 支持所需模态 {}", config.getModelId(), requiredModalities);
                return config;
            }
        }

        // 3. 没有找到支持所需模态的模型
        log.warn("未找到支持所需模态 {} 的模型，用户ID: {}", requiredModalities, userId);
        return null;
    }

    /**
     * 选择支持所需模态的模型（简化版）
     *
     * @param userId 用户ID
     * @param requiredModalities 所需模态
     * @return 匹配的模型配置
     */
    public AIModelConfig selectCapableModel(Long userId, Set<Modality> requiredModalities) {
        return selectCapableModel(userId, requiredModalities, null);
    }

    /**
     * 自动检测并选择合适的模型
     *
     * @param userId 用户ID
     * @param content 消息内容列表
     * @param images 图片列表
     * @param preferredModelId 首选模型ID
     * @return 模型选择结果
     */
    public ModelSelectionResult autoSelectModel(
            Long userId,
            List<ChatMessageContent> content,
            List<String> images,
            String preferredModelId) {

        // 检测所需模态
        Set<Modality> requiredModalities = detectRequiredModalitiesFromRequest(content, images);

        // 选择模型
        AIModelConfig selectedModel = selectCapableModel(userId, requiredModalities, preferredModelId);

        ModelSelectionResult result = new ModelSelectionResult();
        result.setRequiredModalities(requiredModalities);
        result.setSelectedModel(selectedModel);
        result.setModelSwitched(selectedModel != null &&
                preferredModelId != null &&
                !preferredModelId.equals(selectedModel.getConfigId()) &&
                !preferredModelId.equals(selectedModel.getModelId()));

        if (result.isModelSwitched()) {
            log.info("模型自动切换：{} -> {}，原因：需要支持 {}",
                    preferredModelId, selectedModel.getModelId(), requiredModalities);
        }

        return result;
    }

    /**
     * 模型选择结果
     */
    @lombok.Data
    public static class ModelSelectionResult {
        /**
         * 所需的模态类型
         */
        private Set<Modality> requiredModalities;

        /**
         * 选中的模型配置
         */
        private AIModelConfig selectedModel;

        /**
         * 是否发生了模型切换
         */
        private boolean modelSwitched;

        /**
         * 是否需要多模态
         */
        public boolean isMultimodalRequired() {
            return requiredModalities != null && requiredModalities.size() > 1;
        }

        /**
         * 是否成功找到模型
         */
        public boolean isSuccessful() {
            return selectedModel != null;
        }
    }

    /**
     * 获取模型能力描述
     *
     * @param config 模型配置
     * @return 能力描述
     */
    public Map<String, Object> getModelCapabilities(AIModelConfig config) {
        Map<String, Object> capabilities = new LinkedHashMap<>();

        capabilities.put("modelId", config.getModelId());
        capabilities.put("name", config.getName());
        capabilities.put("provider", config.getProvider());

        // 输入模态
        List<String> inputModalities = new ArrayList<>();
        if (config.supportsModality("text")) inputModalities.add("text");
        if (config.supportsModality("image")) inputModalities.add("image");
        if (config.supportsModality("audio")) inputModalities.add("audio");
        if (config.supportsModality("video")) inputModalities.add("video");
        capabilities.put("inputModalities", inputModalities);

        // 输出模态
        List<String> outputModalities = new ArrayList<>();
        if (config.supportsOutputModality("text")) outputModalities.add("text");
        if (config.supportsOutputModality("image")) outputModalities.add("image");
        if (config.supportsOutputModality("audio")) outputModalities.add("audio");
        if (config.supportsOutputModality("video")) outputModalities.add("video");
        capabilities.put("outputModalities", outputModalities);

        return capabilities;
    }
}
