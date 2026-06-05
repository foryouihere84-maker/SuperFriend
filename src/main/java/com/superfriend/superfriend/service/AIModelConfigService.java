package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.AIModelConfigDTO;
import com.superfriend.superfriend.dto.AIModelConfigVO;
import com.superfriend.superfriend.entity.AIModelConfig;
import com.superfriend.superfriend.mapper.AIModelConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AIModelConfigService {

    @Autowired
    private AIModelConfigMapper configMapper;

    private final ConcurrentHashMap<String, AIModelConfig> configCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            List<AIModelConfig> systemDefaults = configMapper.findSystemDefaults();
            for (AIModelConfig config : systemDefaults) {
                configCache.put(config.getConfigId(), config);
            }
            if (!systemDefaults.isEmpty()) {
                log.info("已加载 {} 个系统默认模型配置", systemDefaults.size());
            }
        } catch (Exception e) {
            log.error("加载系统默认模型配置失败：{}", e.getMessage());
        }
    }

    public List<AIModelConfigVO> getAvailableModels(Long userId) {
        List<AIModelConfig> configs = configMapper.findAvailableForUser(userId != null ? userId : 0L);
        List<AIModelConfigVO> result = new ArrayList<>();
        for (AIModelConfig config : configs) {
            result.add(toVO(config));
        }
        return result;
    }

    public List<AIModelConfig> getAvailableModelEntities(Long userId) {
        return configMapper.findAvailableForUser(userId != null ? userId : 0L);
    }

    public List<AIModelConfigVO> getUserModels(Long userId) {
        List<AIModelConfig> configs = configMapper.findByUserId(userId);
        List<AIModelConfigVO> result = new ArrayList<>();
        for (AIModelConfig config : configs) {
            result.add(toVO(config));
        }
        return result;
    }

    public AIModelConfigVO getModel(String configId, Long userId) {
        AIModelConfig config = configMapper.findByConfigIdAndUserId(configId, userId);
        if (config == null) {
            config = configMapper.findByConfigId(configId);
        }
        return config != null ? toVO(config) : null;
    }

    public AIModelConfig getModelEntity(String configId, Long userId) {
        AIModelConfig config = configMapper.findByConfigIdAndUserId(configId, userId);
        if (config == null) {
            config = configMapper.findByConfigId(configId);
        }
        return config;
    }

    public AIModelConfigVO createModel(AIModelConfigDTO dto, Long userId) {
        AIModelConfig config = new AIModelConfig();
        config.setConfigId(generateId());
        config.setUserId(userId);
        config.setName(dto.getName());
        config.setProvider(dto.getProvider() != null ? dto.getProvider() : "custom");
        config.setApiUrl(dto.getApiUrl());
        config.setApiKey(dto.getApiKey());
        config.setModelId(dto.getModelId());
        config.setMaxTokens(dto.getMaxTokens() != null ? dto.getMaxTokens() : 4096);
        config.setTemperature(dto.getTemperature() != null ? dto.getTemperature() : new BigDecimal("0.70"));
        config.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        config.setIsEnabled(dto.getIsEnabled() != null ? dto.getIsEnabled() : true);
        config.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        config.setExtraParams(dto.getExtraParams());

        // 设置多模态能力
        config.setSupportedModalities(dto.getSupportedModalities() != null ? dto.getSupportedModalities() : "text");
        config.setOutputModalities(dto.getOutputModalities() != null ? dto.getOutputModalities() : "text");
        config.setCapabilities(dto.getCapabilities());

        if (Boolean.TRUE.equals(config.getIsDefault())) {
            configMapper.clearDefaultByUserId(userId);
        }

        configMapper.insert(config);
        configCache.put(config.getConfigId(), config);
        log.info("用户 {} 创建模型配置：{}，支持模态：{}", userId, config.getName(), config.getSupportedModalities());
        return toVO(config);
    }

    public AIModelConfigVO updateModel(String configId, AIModelConfigDTO dto, Long userId) {
        AIModelConfig existing = configMapper.findByConfigIdAndUserId(configId, userId);
        if (existing == null) {
            throw new RuntimeException("模型配置不存在");
        }

        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            configMapper.clearDefaultByUserId(userId);
        }

        existing.setName(dto.getName());
        existing.setProvider(dto.getProvider());
        existing.setApiUrl(dto.getApiUrl());
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            existing.setApiKey(dto.getApiKey());
        }
        existing.setModelId(dto.getModelId());
        existing.setMaxTokens(dto.getMaxTokens());
        existing.setTemperature(dto.getTemperature());
        existing.setIsDefault(dto.getIsDefault());
        existing.setIsEnabled(dto.getIsEnabled());
        existing.setSortOrder(dto.getSortOrder());
        existing.setExtraParams(dto.getExtraParams());

        // 更新多模态能力
        if (dto.getSupportedModalities() != null) {
            existing.setSupportedModalities(dto.getSupportedModalities());
        }
        if (dto.getOutputModalities() != null) {
            existing.setOutputModalities(dto.getOutputModalities());
        }
        existing.setCapabilities(dto.getCapabilities());

        configMapper.update(existing);
        configCache.put(existing.getConfigId(), existing);
        log.info("用户 {} 更新模型配置：{}，支持模态：{}", userId, existing.getName(), existing.getSupportedModalities());
        return toVO(existing);
    }

    public void deleteModel(String configId, Long userId) {
        configMapper.deleteByConfigIdAndUserId(configId, userId);
        configCache.remove(configId);
        log.info("用户 {} 删除模型配置：{}", userId, configId);
    }

    public void setDefaultModel(String configId, Long userId) {
        configMapper.clearDefaultByUserId(userId);
        AIModelConfig config = configMapper.findByConfigIdAndUserId(configId, userId);
        if (config != null) {
            config.setIsDefault(true);
            configMapper.update(config);
            configCache.put(config.getConfigId(), config);
        }
    }

    public AIModelConfig getDefaultModel(Long userId) {
        if (userId == null) {
            return null;
        }
        return configMapper.findDefaultByUserId(userId);
    }

    public AIModelConfig resolveModelConfig(String modelIdOrConfigId, Long userId) {
        AIModelConfig config = resolveModelConfigInternal(modelIdOrConfigId, userId);
        if (config != null) {
            config.setApiUrl(sanitizeApiUrl(config.getApiUrl()));
        }
        return config;
    }
    
    private AIModelConfig resolveModelConfigInternal(String modelIdOrConfigId, Long userId) {
        if (modelIdOrConfigId != null && !modelIdOrConfigId.isEmpty()) {
            AIModelConfig config = configMapper.findByConfigId(modelIdOrConfigId);
            if (config != null) {
                return config;
            }

            List<AIModelConfig> allConfigs;
            if (userId != null) {
                allConfigs = configMapper.findAvailableForUser(userId);
            } else {
                allConfigs = configMapper.findAllEnabled();
            }
            for (AIModelConfig c : allConfigs) {
                if (modelIdOrConfigId.equals(c.getModelId())) {
                    return c;
                }
            }
        }

        if (userId != null) {
            AIModelConfig userDefault = configMapper.findDefaultByUserId(userId);
            if (userDefault != null) {
                return userDefault;
            }
        }

        List<AIModelConfig> systemDefaults = configMapper.findSystemDefaults();
        if (systemDefaults != null && !systemDefaults.isEmpty()) {
            return systemDefaults.get(0);
        }

        List<AIModelConfig> allEnabled = configMapper.findAllEnabled();
        if (allEnabled != null && !allEnabled.isEmpty()) {
            return allEnabled.get(0);
        }

        return null;
    }

    public Map<String, Object> testConnection(AIModelConfigDTO dto) {
        Map<String, Object> result = new HashMap<>();
        String provider = detectProvider(dto);
        String modelType = detectModelType(dto);
        log.info("测试连接 - provider: {}, modelType: {}, apiUrl: {}, modelId: {}", provider, modelType, dto.getApiUrl(), dto.getModelId());

        try {
            // 根据模型类型选择测试方法
            switch (modelType) {
                case "image":
                    result = testImageConnection(dto, provider);
                    break;
                case "audio":
                    result = testAudioConnection(dto);
                    break;
                case "video":
                    result = testVideoConnection(dto);
                    break;
                default:
                    // 文本模型
                    switch (provider.toLowerCase()) {
                        case "azure":
                        case "azure-openai":
                            result = testAzureOpenAI(dto);
                            break;
                        case "anthropic":
                        case "claude":
                            result = testAnthropic(dto);
                            break;
                        case "google":
                        case "gemini":
                        case "vertex":
                            result = testGemini(dto);
                            break;
                        case "cohere":
                            result = testCohere(dto);
                            break;
                        case "minimax":
                            result = testMiniMax(dto);
                            break;
                        default:
                            // OpenAI 兼容格式（包括 OpenAI、DeepSeek、智谱、通义、百川、Moonshot、Mistral、Groq、Ollama、OpenRouter 等）
                            result = testOpenAICompatible(dto, provider);
                            break;
                    }
                    break;
            }
        } catch (Exception e) {
            log.warn("模型连接测试失败 [{}]: {}", provider, e.getMessage());
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("provider", provider);
            result.put("modelType", modelType);
        }
        result.put("modelType", modelType);
        return result;
    }

    /**
     * 检测模型类型（文本、图像、音频、视频）
     */
    private String detectModelType(AIModelConfigDTO dto) {
        // 优先检查 supportedModalities 或 outputModalities
        if (dto.getSupportedModalities() != null) {
            String modalities = dto.getSupportedModalities().toLowerCase();
            if (modalities.contains("image") && !modalities.contains("text")) {
                return "image";
            }
        }
        if (dto.getOutputModalities() != null) {
            String output = dto.getOutputModalities().toLowerCase();
            if (output.contains("image")) {
                return "image";
            }
            if (output.contains("audio")) {
                return "audio";
            }
            if (output.contains("video")) {
                return "video";
            }
        }

        // 根据模型 ID 判断
        String modelId = dto.getModelId() != null ? dto.getModelId().toLowerCase() : "";
        String apiUrl = dto.getApiUrl() != null ? dto.getApiUrl().toLowerCase() : "";

        // 图像生成模型
        if (modelId.contains("cogview") || modelId.contains("dall") || modelId.contains("wanx") ||
            modelId.contains("stable-diffusion") || modelId.contains("sd") && modelId.contains("xl") ||
            modelId.contains("midjourney") || modelId.contains("imagen") ||
            apiUrl.contains("/images/generations") || apiUrl.contains("text2image")) {
            return "image";
        }

        // 音频生成模型
        if (modelId.contains("tts") || modelId.contains("glm-tts") || modelId.contains("cogaudio") ||
            modelId.contains("cosyvoice") || modelId.contains("sambert") || modelId.contains("speech") ||
            apiUrl.contains("/audio/speech") || apiUrl.contains("text2audio")) {
            return "audio";
        }

        // 视频生成模型
        if (modelId.contains("cogvideo") || modelId.contains("runway") || modelId.contains("pika") ||
            modelId.contains("sora") || modelId.contains("kling") || modelId.contains("hailuo") ||
            apiUrl.contains("/video/generations") || apiUrl.contains("video-generation")) {
            return "video";
        }

        // 默认为文本模型
        return "text";
    }

    /**
     * 检测模型提供商
     */
    private String detectProvider(AIModelConfigDTO dto) {
        // 优先使用 provider 字段
        if (dto.getProvider() != null && !dto.getProvider().isEmpty()) {
            return dto.getProvider();
        }

        String apiUrl = dto.getApiUrl() != null ? dto.getApiUrl().toLowerCase() : "";
        String modelId = dto.getModelId() != null ? dto.getModelId().toLowerCase() : "";

        // 根据 URL 特征判断
        if (apiUrl.contains("azure") || apiUrl.contains(".openai.azure.com")) {
            return "azure";
        }
        if (apiUrl.contains("anthropic.com") || apiUrl.contains("claude")) {
            return "anthropic";
        }
        if (apiUrl.contains("generativelanguage.googleapis.com") || apiUrl.contains("aiplatform.googleapis.com")) {
            return "gemini";
        }
        if (apiUrl.contains("cohere.ai") || apiUrl.contains("cohere.com")) {
            return "cohere";
        }
        if (apiUrl.contains("deepseek.com")) {
            return "deepseek";
        }
        if (apiUrl.contains("zhipu.cn") || apiUrl.contains("bigmodel.cn")) {
            return "zhipu";
        }
        if (apiUrl.contains("dashscope.aliyuncs.com") || apiUrl.contains("aliyun")) {
            return "qwen";
        }
        if (apiUrl.contains("moonshot.cn") || apiUrl.contains("kimi")) {
            return "moonshot";
        }
        if (apiUrl.contains("baichuan-ai.com") || apiUrl.contains("baichuan")) {
            return "baichuan";
        }
        if (apiUrl.contains("yi.01.ai") || apiUrl.contains("01.ai")) {
            return "lingyi";
        }
        if (apiUrl.contains("minimaxi.com") || apiUrl.contains("minimax")) {
            return "minimax";
        }
        if (apiUrl.contains("aip.baidubce.com") || apiUrl.contains("baidu")) {
            return "wenxin";
        }
        if (apiUrl.contains("hunyuan.tencent.com") || apiUrl.contains("tencent")) {
            return "hunyuan";
        }
        if (apiUrl.contains("spark-api.xf-yun.com") || apiUrl.contains("xf-yun")) {
            return "spark";
        }
        if (apiUrl.contains("mistral.ai") || apiUrl.contains("mistral")) {
            return "mistral";
        }
        if (apiUrl.contains("groq.cloud") || apiUrl.contains("groq")) {
            return "groq";
        }
        if (apiUrl.contains("openrouter.ai")) {
            return "openrouter";
        }
        if (apiUrl.contains("localhost") || apiUrl.contains("127.0.0.1") || apiUrl.contains("/v1/")) {
            return "openai-compatible";
        }

        // 根据 modelId 判断
        if (modelId.contains("gpt") || modelId.contains("o1") || modelId.contains("o3")) {
            return "openai";
        }
        if (modelId.contains("claude")) {
            return "anthropic";
        }
        if (modelId.contains("gemini")) {
            return "gemini";
        }
        if (modelId.contains("deepseek")) {
            return "deepseek";
        }
        if (modelId.contains("glm") || modelId.contains("chatglm")) {
            return "zhipu";
        }
        if (modelId.contains("qwen") || modelId.contains("tongyi")) {
            return "qwen";
        }

        // 默认使用 OpenAI 兼容格式
        return "openai-compatible";
    }

    /**
     * 测试图像生成 API 连接
     */
    private Map<String, Object> testImageConnection(AIModelConfigDTO dto, String provider) {
        Map<String, Object> result = new HashMap<>();
        log.info("测试图像连接 - provider: {}, apiUrl: {}, modelId: {}", provider, dto.getApiUrl(), dto.getModelId());

        java.net.HttpURLConnection connection = null;
        try {
            String apiUrl = normalizeImageUrl(dto.getApiUrl(), provider);
            java.net.URL url = new java.net.URL(apiUrl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            // 设置认证头
            if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + dto.getApiKey());
            }
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000); // 图像生成可能需要更长时间

            // 构建请求体
            String body = buildImageTestBody(dto.getModelId(), provider);
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            if (code == 200 || code == 201) {
                result.put("success", true);
                result.put("message", "图像API连接成功 [" + provider + "]");
                result.put("provider", provider);
            } else {
                String errorBody = readErrorStream(connection);
                // 某些异步API返回202表示任务已提交
                if (code == 202 || errorBody.contains("task") || errorBody.contains("job") || errorBody.contains("request_id")) {
                    result.put("success", true);
                    result.put("message", "图像API连接成功(异步模式) [" + provider + "]");
                    result.put("provider", provider);
                } else {
                    result.put("success", false);
                    result.put("message", "HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody));
                    result.put("provider", provider);
                }
            }
        } catch (Exception e) {
            log.warn("图像API连接测试失败 [{}]: {}", provider, e.getMessage());
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("provider", provider);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * 规范化图像 API URL
     */
    private String normalizeImageUrl(String apiUrl, String provider) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            // 返回默认 URL
            switch (provider.toLowerCase()) {
                case "zhipu":
                    return "https://open.bigmodel.cn/api/paas/v4/images/generations";
                case "openai":
                    return "https://api.openai.com/v1/images/generations";
                case "tongyi":
                case "qwen":
                case "alibaba":
                    return "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
                case "stability":
                case "stabilityai":
                    return "https://api.stability.ai/v1/generation/stable-diffusion-xl-1024-v1-0/text-to-image";
                case "google":
                case "imagen":
                    return "https://generativelanguage.googleapis.com/v1beta/models/imagen-3.0-generate-001:predict";
                case "baidu":
                case "wenxin":
                    return "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/text2image/sd_xl";
                case "tencent":
                case "hunyuan":
                    return "https://api.hunyuan.cloud.tencent.com/v1/images/generations";
                case "xfyun":
                case "spark":
                    return "https://spark-api.xf-yun.com/v3.5/text-to-image";
                case "minimax":
                    return "https://api.minimax.chat/v1/image/generations";
                case "volcengine":
                case "bytedance":
                    return "https://ark.cn-beijing.volces.com/api/v3/images/generations";
                case "midjourney":
                case "midjourney-api":
                    return "https://api.midjourney.com/v1/imagine";
                case "leonardo":
                    return "https://cloud.leonardo.ai/api/rest/v1/generations";
                case "replicate":
                    return "https://api.replicate.com/v1/predictions";
                case "huggingface":
                    return "https://api-inference.huggingface.co/models/stabilityai/stable-diffusion-xl-base-1.0";
                default:
                    return apiUrl;
            }
        }

        String url = apiUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // 如果 URL 已包含完整端点，直接使用
        if (url.contains("/images/generations") || url.contains("text2image") ||
            url.contains("image-synthesis") || url.contains("text-to-image") ||
            url.contains("/imagine") || url.contains("/generations") ||
            url.contains("/predict") || url.contains("/predictions")) {
            return url;
        }

        // 添加标准端点
        if (!url.endsWith("/v1/images/generations")) {
            if (url.endsWith("/v1")) {
                url += "/images/generations";
            } else {
                url += "/v1/images/generations";
            }
        }

        return url;
    }

    /**
     * 构建图像测试请求体
     */
    private String buildImageTestBody(String modelId, String provider) {
        String model = modelId != null && !modelId.isEmpty() ? modelId : getDefaultImageModel(provider);

        switch (provider.toLowerCase()) {
            case "tongyi":
            case "qwen":
            case "alibaba":
                // 通义万相格式
                return "{\"model\":\"" + model + "\",\"input\":{\"prompt\":\"test\"},\"parameters\":{\"style\":\"<auto>\",\"size\":\"512*512\",\"n\":1}}";
            case "stability":
            case "stabilityai":
                // Stability AI 格式
                return "{\"text_prompts\":[{\"text\":\"test\"}],\"cfg_scale\":7,\"height\":512,\"width\":512,\"samples\":1}";
            case "google":
            case "imagen":
                // Google Imagen 格式
                return "{\"instances\":[{\"prompt\":\"test\"}],\"parameters\":{\"sampleCount\":1}}";
            case "baidu":
            case "wenxin":
                // 百度文心一格格式
                return "{\"text\":\"test\",\"resolution\":\"512x512\",\"style\":\"v1\"}";
            case "tencent":
            case "hunyuan":
                // 腾讯混元格式
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\",\"negative_prompt\":\"\",\"width\":512,\"height\":512,\"batch_size\":1}";
            case "xfyun":
            case "spark":
                // 讯飞星火格式
                return "{\"header\":{\"app_id\":\"test\"},\"parameter\":{\"chat\":{\"domain\":\"" + model + "\"}},\"payload\":{\"message\":{\"text\":[{\"role\":\"user\",\"content\":\"画一只猫\"}]}}}";
            case "midjourney":
            case "midjourney-api":
                // Midjourney 格式
                return "{\"prompt\":\"test\",\"aspect_ratio\":\"1:1\",\"process_mode\":\"mixed\"}";
            case "leonardo":
                // Leonardo AI 格式
                return "{\"prompt\":\"test\",\"modelId\":\"ac614f96-1082-45bf-be9d-757f2d31c174\",\"width\":512,\"height\":512,\"num_images\":1}";
            case "replicate":
                // Replicate 格式
                return "{\"version\":\"stability-ai/sdxl:39ed52f2a78e934b3ba6e2a89f5b1c712de7dfea535525255b1aa35c5565e08b\",\"input\":{\"prompt\":\"test\",\"width\":512,\"height\":512}}";
            case "huggingface":
                // HuggingFace 格式
                return "{\"inputs\":\"test\",\"parameters\":{\"negative_prompt\":\"\"}}";
            case "minimax":
                // MiniMax 格式
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\",\"n\":1,\"size\":\"512x512\"}";
            case "volcengine":
            case "bytedance":
                // 火山引擎格式
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\",\"size\":\"512x512\",\"n\":1}";
            default:
                // OpenAI 兼容格式（包括智谱 CogView）
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\",\"size\":\"512x512\",\"n\":1}";
        }
    }

    /**
     * 获取默认图像模型
     */
    private String getDefaultImageModel(String provider) {
        switch (provider.toLowerCase()) {
            case "zhipu":
                return "cogview-4";
            case "openai":
                return "dall-e-3";
            case "tongyi":
            case "qwen":
            case "alibaba":
                return "wanx-v1";
            case "stability":
            case "stabilityai":
                return "stable-diffusion-xl-1024-v1-0";
            case "google":
            case "imagen":
                return "imagen-3.0-generate-001";
            case "baidu":
            case "wenxin":
                return "sd_xl";
            case "tencent":
            case "hunyuan":
                return "hunyuan-image";
            case "xfyun":
            case "spark":
                return "spark-text-to-image";
            case "midjourney":
                return "midjourney";
            case "leonardo":
                return "leonardo-phoenix";
            case "replicate":
                return "stability-ai/sdxl";
            case "minimax":
                return "abab-v1-image";
            case "volcengine":
            case "bytedance":
                return "doubao-image";
            default:
                return "dall-e-3";
        }
    }

    /**
     * 测试 OpenAI 兼容格式的 API
     */
    private Map<String, Object> testOpenAICompatible(AIModelConfigDTO dto, String provider) {
        Map<String, Object> result = new HashMap<>();
        java.net.HttpURLConnection connection = null;
        try {
            String apiUrl = normalizeOpenAIUrl(dto.getApiUrl());
            java.net.URL url = new java.net.URL(apiUrl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            // 设置认证头
            String authHeader = getAuthHeader(provider, dto.getApiKey());
            if (authHeader != null) {
                connection.setRequestProperty("Authorization", authHeader);
            }

            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            String body = buildOpenAITestBody(dto.getModelId(), provider);
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            if (code == 200) {
                result.put("success", true);
                result.put("message", "连接成功 [" + provider + "]");
                result.put("provider", provider);
            } else {
                String errorBody = readErrorStream(connection);
                result.put("success", false);
                result.put("message", "HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody));
                result.put("provider", provider);
            }
        } catch (Exception e) {
            log.warn("OpenAI兼容格式测试失败 [{}]: {}", provider, e.getMessage());
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("provider", provider);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * 测试 Azure OpenAI
     */
    private Map<String, Object> testAzureOpenAI(AIModelConfigDTO dto) {
        Map<String, Object> result = new HashMap<>();
        java.net.HttpURLConnection connection = null;
        try {
            // Azure URL 格式: https://{resource}.openai.azure.com/openai/deployments/{deployment}/chat/completions?api-version=...
            String apiUrl = dto.getApiUrl();
            if (!apiUrl.contains("/chat/completions")) {
                if (!apiUrl.endsWith("/")) {
                    apiUrl += "/";
                }
                apiUrl += "chat/completions?api-version=2024-02-15-preview";
            }

            java.net.URL url = new java.net.URL(apiUrl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("api-key", dto.getApiKey()); // Azure 使用 api-key 头
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            String body = "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":5}";
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            if (code == 200) {
                result.put("success", true);
                result.put("message", "连接成功 [Azure OpenAI]");
                result.put("provider", "azure");
            } else {
                String errorBody = readErrorStream(connection);
                result.put("success", false);
                result.put("message", "HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody));
                result.put("provider", "azure");
            }
        } catch (Exception e) {
            log.warn("Azure OpenAI 测试失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("provider", "azure");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * 测试 Anthropic Claude
     */
    private Map<String, Object> testAnthropic(AIModelConfigDTO dto) {
        Map<String, Object> result = new HashMap<>();
        java.net.HttpURLConnection connection = null;
        try {
            String apiUrl = dto.getApiUrl();
            if (!apiUrl.contains("/messages")) {
                if (apiUrl.endsWith("/")) {
                    apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
                }
                if (!apiUrl.endsWith("/v1/messages")) {
                    apiUrl += "/v1/messages";
                }
            }

            java.net.URL url = new java.net.URL(apiUrl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("x-api-key", dto.getApiKey()); // Anthropic 使用 x-api-key
            connection.setRequestProperty("anthropic-version", "2023-06-01");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            // Claude API 格式
            String modelId = dto.getModelId();
            if (!modelId.startsWith("claude")) {
                modelId = "claude-3-5-sonnet-20241022"; // 默认使用 claude-3.5-sonnet 测试
            }
            String body = "{\"model\":\"" + modelId + "\",\"max_tokens\":5,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            if (code == 200) {
                result.put("success", true);
                result.put("message", "连接成功 [Anthropic Claude]");
                result.put("provider", "anthropic");
            } else {
                String errorBody = readErrorStream(connection);
                result.put("success", false);
                result.put("message", "HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody));
                result.put("provider", "anthropic");
            }
        } catch (Exception e) {
            log.warn("Anthropic 测试失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("provider", "anthropic");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * 测试 Google Gemini
     */
    private Map<String, Object> testGemini(AIModelConfigDTO dto) {
        Map<String, Object> result = new HashMap<>();
        java.net.HttpURLConnection connection = null;
        try {
            String modelId = dto.getModelId();
            if (modelId == null || modelId.isEmpty()) {
                modelId = "gemini-1.5-flash";
            }

            String apiUrl = dto.getApiUrl();
            // Gemini API 格式: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}
            if (!apiUrl.contains("generateContent") && !apiUrl.contains(":generate")) {
                if (apiUrl.contains("generativelanguage.googleapis.com")) {
                    apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent?key=" + dto.getApiKey();
                } else if (!apiUrl.contains("/models/")) {
                    if (apiUrl.endsWith("/")) {
                        apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
                    }
                    apiUrl += "/v1beta/models/" + modelId + ":generateContent?key=" + dto.getApiKey();
                }
            } else if (apiUrl.contains("?key=")) {
                // URL 已包含 key 参数，直接使用
            } else {
                apiUrl += "?key=" + dto.getApiKey();
            }

            java.net.URL url = new java.net.URL(apiUrl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            // Gemini API 格式
            String body = "{\"contents\":[{\"parts\":[{\"text\":\"hi\"}]}],\"generationConfig\":{\"maxOutputTokens\":5}}";
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            if (code == 200) {
                result.put("success", true);
                result.put("message", "连接成功 [Google Gemini]");
                result.put("provider", "gemini");
            } else {
                String errorBody = readErrorStream(connection);
                result.put("success", false);
                result.put("message", "HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody));
                result.put("provider", "gemini");
            }
        } catch (Exception e) {
            log.warn("Gemini 测试失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("provider", "gemini");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * 测试 Cohere
     */
    private Map<String, Object> testCohere(AIModelConfigDTO dto) {
        Map<String, Object> result = new HashMap<>();
        java.net.HttpURLConnection connection = null;
        try {
            String apiUrl = dto.getApiUrl();
            if (!apiUrl.contains("/chat") && !apiUrl.contains("/generate")) {
                if (apiUrl.endsWith("/")) {
                    apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
                }
                apiUrl += "/v1/chat";
            }

            java.net.URL url = new java.net.URL(apiUrl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + dto.getApiKey());
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            String modelId = dto.getModelId();
            if (modelId == null || modelId.isEmpty()) {
                modelId = "command";
            }
            // Cohere API 格式
            String body = "{\"model\":\"" + modelId + "\",\"message\":\"hi\",\"max_tokens\":5}";
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            if (code == 200) {
                result.put("success", true);
                result.put("message", "连接成功 [Cohere]");
                result.put("provider", "cohere");
            } else {
                String errorBody = readErrorStream(connection);
                result.put("success", false);
                result.put("message", "HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody));
                result.put("provider", "cohere");
            }
        } catch (Exception e) {
            log.warn("Cohere 测试失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("provider", "cohere");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * 测试 MiniMax
     */
    private Map<String, Object> testMiniMax(AIModelConfigDTO dto) {
        Map<String, Object> result = new HashMap<>();
        java.net.HttpURLConnection connection = null;
        try {
            String apiUrl = dto.getApiUrl();
            String apiKey = dto.getApiKey();
            String modelId = dto.getModelId();

            // MiniMax API 格式: https://api.minimax.chat/v1/chat/completions
            // 或: https://api.minimaxi.com/v1/chat/completions
            if (!apiUrl.contains("/chat/completions")) {
                if (apiUrl.endsWith("/")) {
                    apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
                }
                if (!apiUrl.endsWith("/v1/chat/completions")) {
                    if (apiUrl.endsWith("/v1")) {
                        apiUrl += "/chat/completions";
                    } else {
                        apiUrl += "/v1/chat/completions";
                    }
                }
            }

            java.net.URL url = new java.net.URL(apiUrl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            if (modelId == null || modelId.isEmpty()) {
                modelId = "abab6.5s-chat"; // MiniMax 默认模型
            }
            // MiniMax 使用 OpenAI 兼容格式
            String body = "{\"model\":\"" + modelId + "\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":5,\"stream\":false}";
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            if (code == 200) {
                result.put("success", true);
                result.put("message", "连接成功 [MiniMax]");
                result.put("provider", "minimax");
            } else {
                String errorBody = readErrorStream(connection);
                result.put("success", false);
                result.put("message", "HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody));
                result.put("provider", "minimax");
            }
        } catch (Exception e) {
            log.warn("MiniMax 测试失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("provider", "minimax");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * 规范化 OpenAI 兼容 API URL
     */
    private String normalizeOpenAIUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            return apiUrl;
        }
        String url = apiUrl.trim();

        // 如果 URL 已经包含完整的端点，直接使用
        if (url.contains("/chat/completions")) {
            return url;
        }

        // 移除末尾的斜杠
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // 添加标准端点
        if (!url.endsWith("/v1/chat/completions")) {
            if (url.endsWith("/v1")) {
                url += "/chat/completions";
            } else {
                url += "/v1/chat/completions";
            }
        }

        return url;
    }

    /**
     * 获取认证头
     */
    private String getAuthHeader(String provider, String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }
        // 绝大多数提供商都使用 Bearer token
        return "Bearer " + apiKey;
    }

    /**
     * 构建 OpenAI 兼容格式的测试请求体
     */
    private String buildOpenAITestBody(String modelId, String provider) {
        StringBuilder body = new StringBuilder();
        body.append("{\"model\":\"").append(modelId).append("\",");
        body.append("\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],");

        // 某些模型可能不支持 max_tokens
        if (!"ollama".equals(provider)) {
            body.append("\"max_tokens\":5,");
        }

        body.append("\"stream\":false}");
        return body.toString();
    }

    /**
     * 读取错误响应流
     */
    private String readErrorStream(java.net.HttpURLConnection connection) {
        try (java.io.InputStream es = connection.getErrorStream();
             java.io.BufferedReader reader = es != null ? new java.io.BufferedReader(
                     new java.io.InputStreamReader(es, java.nio.charset.StandardCharsets.UTF_8)) : null) {
            if (reader != null) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String errorBody = sb.toString();
                return errorBody.substring(0, Math.min(errorBody.length(), 500));
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * 测试音频生成 API 连接
     */
    public Map<String, Object> testAudioConnection(AIModelConfigDTO dto) {
        Map<String, Object> result = new HashMap<>();
        String provider = detectAudioProvider(dto);
        log.info("测试音频连接 - provider: {}, apiUrl: {}, modelId: {}", provider, dto.getApiUrl(), dto.getModelId());

        java.net.HttpURLConnection connection = null;
        try {
            String apiUrl = normalizeAudioUrl(dto.getApiUrl(), provider);
            java.net.URL url = new java.net.URL(apiUrl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            // 设置认证头
            if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + dto.getApiKey());
            }
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);

            // 构建请求体
            String body = buildAudioTestBody(dto.getModelId(), provider);
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            if (code == 200) {
                result.put("success", true);
                result.put("message", "音频API连接成功 [" + provider + "]");
                result.put("provider", provider);
            } else {
                String errorBody = readErrorStream(connection);
                result.put("success", false);
                result.put("message", "HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody));
                result.put("provider", provider);
            }
        } catch (Exception e) {
            log.warn("音频API连接测试失败 [{}]: {}", provider, e.getMessage());
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("provider", provider);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * 测试视频生成 API 连接
     */
    public Map<String, Object> testVideoConnection(AIModelConfigDTO dto) {
        Map<String, Object> result = new HashMap<>();
        String provider = detectVideoProvider(dto);
        log.info("测试视频连接 - provider: {}, apiUrl: {}, modelId: {}", provider, dto.getApiUrl(), dto.getModelId());

        java.net.HttpURLConnection connection = null;
        try {
            String apiUrl = normalizeVideoUrl(dto.getApiUrl(), provider);
            java.net.URL url = new java.net.URL(apiUrl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            // 设置认证头
            if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + dto.getApiKey());
            }
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);

            // 构建请求体
            String body = buildVideoTestBody(dto.getModelId(), provider);
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            if (code == 200 || code == 201) {
                result.put("success", true);
                result.put("message", "视频API连接成功 [" + provider + "]");
                result.put("provider", provider);
            } else {
                String errorBody = readErrorStream(connection);
                // 某些异步API返回特定状态码表示任务已提交
                if (code == 202 || (errorBody.contains("task") || errorBody.contains("job"))) {
                    result.put("success", true);
                    result.put("message", "视频API连接成功(异步模式) [" + provider + "]");
                    result.put("provider", provider);
                } else {
                    result.put("success", false);
                    result.put("message", "HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody));
                    result.put("provider", provider);
                }
            }
        } catch (Exception e) {
            log.warn("视频API连接测试失败 [{}]: {}", provider, e.getMessage());
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("provider", provider);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * 检测音频提供商
     */
    private String detectAudioProvider(AIModelConfigDTO dto) {
        if (dto.getProvider() != null && !dto.getProvider().isEmpty()) {
            return dto.getProvider();
        }

        String apiUrl = dto.getApiUrl() != null ? dto.getApiUrl().toLowerCase() : "";
        String modelId = dto.getModelId() != null ? dto.getModelId().toLowerCase() : "";

        if (apiUrl.contains("bigmodel.cn") || apiUrl.contains("zhipu")) {
            return "zhipu";
        }
        if (apiUrl.contains("openai.com")) {
            return "openai";
        }
        if (apiUrl.contains("dashscope.aliyuncs.com") || apiUrl.contains("aliyun")) {
            return "tongyi";
        }
        if (apiUrl.contains("minimax")) {
            return "minimax";
        }
        if (apiUrl.contains("fish.audio") || apiUrl.contains("fishaudio")) {
            return "fishaudio";
        }
        if (apiUrl.contains("elevenlabs")) {
            return "elevenlabs";
        }
        if (apiUrl.contains("volcengine") || apiUrl.contains("bytedance")) {
            return "volcengine";
        }

        // 根据模型ID判断
        if (modelId.contains("glm-tts") || modelId.contains("cogaudio")) {
            return "zhipu";
        }
        if (modelId.contains("tts") || modelId.contains("gpt")) {
            return "openai";
        }
        if (modelId.contains("cosyvoice") || modelId.contains("sambert")) {
            return "tongyi";
        }

        return "openai-compatible";
    }

    /**
     * 检测视频提供商
     */
    private String detectVideoProvider(AIModelConfigDTO dto) {
        if (dto.getProvider() != null && !dto.getProvider().isEmpty()) {
            return dto.getProvider();
        }

        String apiUrl = dto.getApiUrl() != null ? dto.getApiUrl().toLowerCase() : "";
        String modelId = dto.getModelId() != null ? dto.getModelId().toLowerCase() : "";

        if (apiUrl.contains("bigmodel.cn") || apiUrl.contains("zhipu")) {
            return "zhipu";
        }
        if (apiUrl.contains("dashscope.aliyuncs.com") || apiUrl.contains("aliyun")) {
            return "tongyi";
        }
        if (apiUrl.contains("runwayml")) {
            return "runway";
        }
        if (apiUrl.contains("pika") || apiUrl.contains("pika.art")) {
            return "pika";
        }
        if (apiUrl.contains("luma") || apiUrl.contains("lumalabs")) {
            return "luma";
        }
        if (apiUrl.contains("minimax")) {
            return "minimax";
        }
        if (apiUrl.contains("kling")) {
            return "kling";
        }
        if (apiUrl.contains("hailuo") || apiUrl.contains("minimaxi")) {
            return "hailuo";
        }

        // 根据模型ID判断
        if (modelId.contains("cogvideo")) {
            return "zhipu";
        }
        if (modelId.contains("wanx") || modelId.contains("tongyi")) {
            return "tongyi";
        }
        if (modelId.contains("runway")) {
            return "runway";
        }
        if (modelId.contains("kling")) {
            return "kling";
        }

        return "openai-compatible";
    }

    /**
     * 规范化音频 API URL
     */
    private String normalizeAudioUrl(String apiUrl, String provider) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            // 返回默认 URL
            switch (provider.toLowerCase()) {
                case "zhipu":
                    return "https://open.bigmodel.cn/api/paas/v4/audio/speech";
                case "openai":
                    return "https://api.openai.com/v1/audio/speech";
                case "tongyi":
                case "qwen":
                case "alibaba":
                    return "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2audio/generation";
                case "minimax":
                    return "https://api.minimax.chat/v1/audio/speech";
                case "elevenlabs":
                    return "https://api.elevenlabs.io/v1/text-to-speech";
                case "fishaudio":
                case "fish":
                    return "https://api.fish.audio/v1/tts";
                case "volcengine":
                case "bytedance":
                    return "https://openspeech.bytedance.com/api/v1/tts";
                case "azure":
                case "microsoft":
                    return "https://eastus.tts.speech.microsoft.com/cognitiveservices/v1";
                case "google":
                case "gcp":
                    return "https://texttospeech.googleapis.com/v1/text:synthesize";
                case "baidu":
                    return "https://tsn.baidu.com/text2audio";
                case "tencent":
                case "tencentcloud":
                    return "https://tts.tencentcloudapi.com";
                case "xfyun":
                case "spark":
                    return "https://tts-api.xfyun.cn/v2/tts";
                case "azure-openai":
                    return "https://eastus.openai.azure.com/openai/deployments/tts-1/audio/speech";
                default:
                    return apiUrl;
            }
        }

        String url = apiUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // 如果 URL 已包含完整端点，直接使用
        if (url.contains("/audio/speech") || url.contains("/speech") || url.contains("/text2audio") ||
            url.contains("/tts") || url.contains("/text-to-speech") || url.contains("/synthesize")) {
            return url;
        }

        // 添加标准端点
        if (!url.endsWith("/v1/audio/speech") && !url.endsWith("/audio/speech")) {
            if (url.endsWith("/v1")) {
                url += "/audio/speech";
            } else {
                url += "/v1/audio/speech";
            }
        }

        return url;
    }

    /**
     * 规范化视频 API URL
     */
    private String normalizeVideoUrl(String apiUrl, String provider) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            // 返回默认 URL
            switch (provider.toLowerCase()) {
                case "zhipu":
                    return "https://open.bigmodel.cn/api/paas/v4/video/generations";
                case "tongyi":
                case "qwen":
                case "alibaba":
                    return "https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/generation";
                case "runway":
                    return "https://api.runwayml.com/v1/generate";
                case "pika":
                    return "https://api.pika.art/v1/generate";
                case "luma":
                case "lumalabs":
                    return "https://api.lumalabs.ai/dream-machine/v1/generations";
                case "kling":
                    return "https://api.klingai.com/v1/videos/text2video";
                case "hailuo":
                case "minimaxi":
                    return "https://api.minimaxi.com/v1/video/generation";
                case "minimax":
                    return "https://api.minimax.chat/v1/video/generation";
                case "sora":
                case "openai-video":
                    return "https://api.openai.com/v1/videos/generations";
                case "baidu":
                case "wenxin":
                    return "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/text2video";
                case "tencent":
                case "hunyuan":
                    return "https://api.hunyuan.cloud.tencent.com/v1/videos/generations";
                default:
                    return apiUrl;
            }
        }

        String url = apiUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // 如果 URL 已包含完整端点，直接使用
        if (url.contains("/video/generations") || url.contains("/video-generation") ||
            url.contains("/generations") || url.contains("/text2video") ||
            url.contains("/generate")) {
            return url;
        }

        // 添加标准端点
        if (!url.endsWith("/v1/video/generations")) {
            if (url.endsWith("/v1")) {
                url += "/video/generations";
            } else {
                url += "/v1/video/generations";
            }
        }

        return url;
    }

    /**
     * 构建音频测试请求体
     */
    private String buildAudioTestBody(String modelId, String provider) {
        String model = modelId != null && !modelId.isEmpty() ? modelId : getDefaultAudioModel(provider);

        switch (provider.toLowerCase()) {
            case "tongyi":
            case "qwen":
            case "alibaba":
                // 通义音频格式
                return "{\"model\":\"" + model + "\",\"input\":{\"text\":\"hi\"},\"parameters\":{\"voice\":\"longxiaochun\"}}";
            case "zhipu":
                // 智谱音频格式
                return "{\"model\":\"" + model + "\",\"input\":\"hi\",\"voice\":\"female\"}";
            case "elevenlabs":
                // ElevenLabs 格式
                return "{\"text\":\"hi\",\"model_id\":\"" + model + "\",\"voice_settings\":{\"stability\":0.5,\"similarity_boost\":0.5}}";
            case "fishaudio":
            case "fish":
                // Fish Audio 格式
                return "{\"text\":\"hi\",\"reference_id\":\"" + model + "\"}";
            case "volcengine":
            case "bytedance":
                // 火山引擎格式
                return "{\"app\":{\"appid\":\"test\"},\"user\":{\"uid\":\"test\"},\"audio\":{\"voice_type\":\"" + model + "\",\"encoding\":\"mp3\"},\"request\":{\"text\":\"hi\"}}";
            case "azure":
            case "microsoft":
                // Azure TTS 格式 (SSML)
                return "<speak version='1.0' xml:lang='en-US'><voice xml:lang='en-US' name='en-US-JennyNeural'>hi</voice></speak>";
            case "google":
            case "gcp":
                // Google TTS 格式
                return "{\"input\":{\"text\":\"hi\"},\"voice\":{\"languageCode\":\"en-US\",\"name\":\"en-US-Neural2-C\"},\"audioConfig\":{\"audioEncoding\":\"MP3\"}}";
            case "baidu":
                // 百度语音格式
                return "{\"tex\":\"hi\",\"tok\":\"test\",\"cuid\":\"test\",\"ctp\":1,\"lan\":\"zh\"}";
            case "minimax":
                // MiniMax 音频格式
                return "{\"model\":\"" + model + "\",\"text\":\"hi\",\"voice_id\":\"male-qn-qingse\"}";
            default:
                // OpenAI 兼容格式
                return "{\"model\":\"" + model + "\",\"input\":\"hi\",\"voice\":\"alloy\"}";
        }
    }

    /**
     * 构建视频测试请求体
     */
    private String buildVideoTestBody(String modelId, String provider) {
        String model = modelId != null && !modelId.isEmpty() ? modelId : getDefaultVideoModel(provider);

        switch (provider.toLowerCase()) {
            case "tongyi":
            case "qwen":
            case "alibaba":
                // 通义视频格式
                return "{\"model\":\"" + model + "\",\"input\":{\"text\":\"test\"},\"parameters\":{\"style\":\"<auto>\"}}";
            case "zhipu":
                // 智谱视频格式
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\"}";
            case "runway":
                // Runway 格式
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\",\"duration\":5,\"ratio\":\"16:9\"}";
            case "pika":
                // Pika 格式
                return "{\"prompt\":\"test\",\"guidance_scale\":7.5,\"num_frames\":24}";
            case "luma":
            case "lumalabs":
                // Luma 格式
                return "{\"prompt\":\"test\",\"aspect_ratio\":\"16:9\"}";
            case "kling":
                // 可灵格式
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\",\"duration\":5}";
            case "hailuo":
            case "minimaxi":
                // 海螺/MiniMax格式
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\"}";
            case "sora":
            case "openai-video":
                // Sora 格式
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\",\"duration\":5}";
            case "baidu":
            case "wenxin":
                // 百度视频格式
                return "{\"text\":\"test\",\"resolution\":\"720p\"}";
            case "tencent":
            case "hunyuan":
                // 腾讯混元视频格式
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\",\"negative_prompt\":\"\",\"duration\":5}";
            case "minimax":
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\"}";
            default:
                // OpenAI 兼容格式
                return "{\"model\":\"" + model + "\",\"prompt\":\"test\"}";
        }
    }

    /**
     * 获取默认音频模型
     */
    private String getDefaultAudioModel(String provider) {
        switch (provider.toLowerCase()) {
            case "zhipu":
                return "glm-tts";
            case "openai":
                return "tts-1";
            case "tongyi":
            case "qwen":
            case "alibaba":
                return "cosyvoice-v1";
            case "elevenlabs":
                return "eleven_multilingual_v2";
            case "fishaudio":
            case "fish":
                return "fish-speech-1";
            case "volcengine":
            case "bytedance":
                return "zh_female_shuangkuaisisi_moon_bigtts";
            case "azure":
            case "microsoft":
                return "en-US-JennyNeural";
            case "google":
            case "gcp":
                return "en-US-Neural2-C";
            case "baidu":
                return "0";
            case "minimax":
                return "abab-tts-001";
            default:
                return "tts-1";
        }
    }

    /**
     * 获取默认视频模型
     */
    private String getDefaultVideoModel(String provider) {
        switch (provider.toLowerCase()) {
            case "zhipu":
                return "cogvideox-2";
            case "tongyi":
            case "qwen":
            case "alibaba":
                return "wanx-v1";
            case "runway":
                return "gen3-alpha";
            case "pika":
                return "pika-2.0";
            case "luma":
            case "lumalabs":
                return "ray-2";
            case "kling":
                return "kling-v1";
            case "hailuo":
            case "minimaxi":
                return "hailuo-02";
            case "minimax":
                return "video-01";
            case "sora":
            case "openai-video":
                return "sora";
            case "baidu":
            case "wenxin":
                return "text2video";
            case "tencent":
            case "hunyuan":
                return "hunyuan-video";
            default:
                return "cogvideox-2";
        }
    }

    private AIModelConfigVO toVO(AIModelConfig config) {
        AIModelConfigVO vo = new AIModelConfigVO();
        vo.setConfigId(config.getConfigId());
        vo.setName(config.getName());
        vo.setProvider(config.getProvider());
        vo.setApiUrl(config.getApiUrl());
        vo.setApiKeyMasked(maskApiKey(config.getApiKey()));
        vo.setModelId(config.getModelId());
        vo.setMaxTokens(config.getMaxTokens());
        vo.setTemperature(config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7);
        vo.setIsDefault(config.getIsDefault());
        vo.setIsEnabled(config.getIsEnabled());
        vo.setSortOrder(config.getSortOrder());
        vo.setIsSystem(config.getUserId() != null && config.getUserId() == 0L);

        // 多模态能力
        vo.setSupportedModalities(config.getSupportedModalities());
        vo.setOutputModalities(config.getOutputModalities());
        vo.setCapabilities(config.getCapabilities());

        // 解析为列表
        if (config.getSupportedModalities() != null && !config.getSupportedModalities().isEmpty()) {
            vo.setInputModalities(java.util.Arrays.asList(config.getSupportedModalities().split(",")));
        } else {
            vo.setInputModalities(java.util.Collections.singletonList("text"));
        }

        if (config.getOutputModalities() != null && !config.getOutputModalities().isEmpty()) {
            vo.setOutputModalitiesList(java.util.Arrays.asList(config.getOutputModalities().split(",")));
        } else {
            vo.setOutputModalitiesList(java.util.Collections.singletonList("text"));
        }

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
    
    private String sanitizeApiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            return apiUrl;
        }
        String sanitized = apiUrl.trim();
        while (sanitized.endsWith(",") || sanitized.endsWith("/")) {
            if (sanitized.endsWith(",")) {
                sanitized = sanitized.substring(0, sanitized.length() - 1).trim();
            } else if (sanitized.endsWith("/") && !sanitized.endsWith("//")) {
                break;
            } else {
                break;
            }
        }
        return sanitized;
    }

    @lombok.Data
    public static class ResolvedConfig {
        private String modelId;
        private String apiUrl;
        private String apiKey;
    }

    public ResolvedConfig resolveModelConfigWithKey(String modelIdOrConfigId, Long userId) {
        AIModelConfig config = resolveModelConfig(modelIdOrConfigId, userId);
        if (config == null) {
            return null;
        }
        ResolvedConfig resolved = new ResolvedConfig();
        resolved.setModelId(config.getModelId());
        resolved.setApiUrl(sanitizeApiUrl(config.getApiUrl()));
        resolved.setApiKey(config.getApiKey());
        return resolved;
    }
}
