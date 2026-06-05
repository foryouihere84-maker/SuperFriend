package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.EmbeddingModelConfigDTO;
import com.superfriend.superfriend.dto.EmbeddingModelConfigVO;
import com.superfriend.superfriend.entity.EmbeddingModelConfig;
import com.superfriend.superfriend.mapper.EmbeddingModelConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EmbeddingModelConfigService {

    @Autowired
    private EmbeddingModelConfigMapper configMapper;

    private final ConcurrentHashMap<String, EmbeddingModelConfig> configCache = new ConcurrentHashMap<>();
    private volatile EmbeddingModelConfig defaultConfig = null;

    @PostConstruct
    public void init() {
        try {
            List<EmbeddingModelConfig> configs = configMapper.findAllEnabled();
            for (EmbeddingModelConfig config : configs) {
                configCache.put(config.getConfigId(), config);
                if (Boolean.TRUE.equals(config.getIsDefault())) {
                    defaultConfig = config;
                }
            }
            if (!configs.isEmpty()) {
                log.info("已加载 {} 个 Embedding 模型配置", configs.size());
            }
        } catch (Exception e) {
            log.error("加载 Embedding 模型配置失败：{}", e.getMessage());
        }
    }

    public List<EmbeddingModelConfigVO> getAllConfigs() {
        List<EmbeddingModelConfig> configs = configMapper.findAll();
        List<EmbeddingModelConfigVO> result = new ArrayList<>();
        for (EmbeddingModelConfig config : configs) {
            result.add(toVO(config));
        }
        return result;
    }

    public List<EmbeddingModelConfigVO> getEnabledConfigs() {
        List<EmbeddingModelConfig> configs = configMapper.findAllEnabled();
        List<EmbeddingModelConfigVO> result = new ArrayList<>();
        for (EmbeddingModelConfig config : configs) {
            result.add(toVO(config));
        }
        return result;
    }

    public EmbeddingModelConfigVO getConfig(String configId) {
        EmbeddingModelConfig config = configMapper.findByConfigId(configId);
        return config != null ? toVO(config) : null;
    }

    public EmbeddingModelConfig getConfigEntity(String configId) {
        if (configId == null || configId.isEmpty()) {
            return getDefaultConfigEntity();
        }
        EmbeddingModelConfig config = configCache.get(configId);
        if (config == null) {
            config = configMapper.findByConfigId(configId);
        }
        return config;
    }

    public EmbeddingModelConfig getDefaultConfigEntity() {
        if (defaultConfig != null) {
            return defaultConfig;
        }
        defaultConfig = configMapper.findDefault();
        return defaultConfig;
    }

    public EmbeddingModelConfig getConfigByProvider(String provider) {
        return configMapper.findByProvider(provider);
    }

    public EmbeddingModelConfigVO createConfig(EmbeddingModelConfigDTO dto) {
        EmbeddingModelConfig config = new EmbeddingModelConfig();
        config.setConfigId(generateId());
        config.setName(dto.getName());
        config.setProvider(dto.getProvider() != null ? dto.getProvider() : "custom");
        config.setApiUrl(dto.getApiUrl());
        config.setApiKey(dto.getApiKey());
        config.setModelId(dto.getModelId());
        config.setDimensions(dto.getDimensions() != null ? dto.getDimensions() : 1536);
        config.setMaxInputTokens(dto.getMaxInputTokens() != null ? dto.getMaxInputTokens() : 8191);
        config.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        config.setIsEnabled(dto.getIsEnabled() != null ? dto.getIsEnabled() : true);
        config.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);

        if (Boolean.TRUE.equals(config.getIsDefault())) {
            configMapper.clearDefault();
            defaultConfig = null;
        }

        configMapper.insert(config);
        configCache.put(config.getConfigId(), config);
        
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            defaultConfig = config;
        }
        
        log.info("创建 Embedding 模型配置：{}", config.getName());
        return toVO(config);
    }

    public EmbeddingModelConfigVO updateConfig(String configId, EmbeddingModelConfigDTO dto) {
        EmbeddingModelConfig existing = configMapper.findByConfigId(configId);
        if (existing == null) {
            throw new RuntimeException("Embedding 模型配置不存在");
        }

        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            configMapper.clearDefault();
            defaultConfig = null;
        }

        existing.setName(dto.getName());
        existing.setProvider(dto.getProvider());
        existing.setApiUrl(dto.getApiUrl());
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            existing.setApiKey(dto.getApiKey());
        }
        existing.setModelId(dto.getModelId());
        existing.setDimensions(dto.getDimensions());
        existing.setMaxInputTokens(dto.getMaxInputTokens());
        existing.setIsDefault(dto.getIsDefault());
        existing.setIsEnabled(dto.getIsEnabled());
        existing.setSortOrder(dto.getSortOrder());

        configMapper.update(existing);
        configCache.put(existing.getConfigId(), existing);
        
        if (Boolean.TRUE.equals(existing.getIsDefault())) {
            defaultConfig = existing;
        }
        
        log.info("更新 Embedding 模型配置：{}", existing.getName());
        return toVO(existing);
    }

    public void deleteConfig(String configId) {
        EmbeddingModelConfig config = configMapper.findByConfigId(configId);
        if (config != null && Boolean.TRUE.equals(config.getIsDefault())) {
            defaultConfig = null;
        }
        configMapper.deleteByConfigId(configId);
        configCache.remove(configId);
        log.info("删除 Embedding 模型配置：{}", configId);
    }

    public void setDefaultConfig(String configId) {
        configMapper.clearDefault();
        EmbeddingModelConfig config = configMapper.findByConfigId(configId);
        if (config != null) {
            config.setIsDefault(true);
            configMapper.update(config);
            configCache.put(config.getConfigId(), config);
            defaultConfig = config;
        }
    }

    public void refreshCache() {
        configCache.clear();
        defaultConfig = null;
        init();
    }

    private EmbeddingModelConfigVO toVO(EmbeddingModelConfig config) {
        EmbeddingModelConfigVO vo = new EmbeddingModelConfigVO();
        vo.setConfigId(config.getConfigId());
        vo.setName(config.getName());
        vo.setProvider(config.getProvider());
        vo.setApiUrl(config.getApiUrl());
        vo.setApiKeyMasked(maskApiKey(config.getApiKey()));
        vo.setModelId(config.getModelId());
        vo.setDimensions(config.getDimensions());
        vo.setMaxInputTokens(config.getMaxInputTokens());
        vo.setIsDefault(config.getIsDefault());
        vo.setIsEnabled(config.getIsEnabled());
        vo.setSortOrder(config.getSortOrder());
        return vo;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return apiKey != null ? "****" : "";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
