package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.LLMCompleteResponse;
import com.superfriend.superfriend.dto.LLMRequest;
import com.superfriend.superfriend.dto.UserIntent;
import com.superfriend.superfriend.entity.AIModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 意图分类服务
 * 使用轻量级 LLM 快速分类用户意图
 */
@Slf4j
@Service
public class LLMIntentClassifier {

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    @Autowired
    @Lazy
    private LLMClient llmClient;

    @Value("${intent.classifier.model:}")
    private String classifierModelId;

    @Value("${intent.classifier.enabled:true}")
    private boolean enabled;

    @Value("${intent.classifier.cache-enabled:true}")
    private boolean cacheEnabled;

    @Value("${intent.classifier.cache-ttl-minutes:30}")
    private int cacheTtlMinutes;

    // 意图分类缓存
    private final ConcurrentHashMap<String, CacheEntry> intentCache = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 使用 LLM 分类用户意图
     *
     * @param userText        用户文本
     * @param attachmentTypes 附件类型列表 (image, audio, video, document)
     * @param userId          用户ID
     * @return 意图分类结果
     */
    public UserIntent classifyIntent(String userText, List<String> attachmentTypes, Long userId) {
<<<<<<< HEAD
        return classifyIntent(userText, attachmentTypes, (UserIntentService.IntentContext) null, userId);
    }

    /**
     * 使用 LLM 分类用户意图（支持对话历史）
     *
     * @param userText        用户文本
     * @param attachmentTypes 附件类型列表 (image, audio, video, document)
     * @param historyMessages 对话历史（最近几轮）
     * @param userId          用户ID
     * @return 意图分类结果
     */
    public UserIntent classifyIntent(String userText, List<String> attachmentTypes,
                                      List<Map<String, String>> historyMessages, Long userId) {
        // 兼容旧调用方式
        UserIntentService.IntentContext context = historyMessages != null ?
                new UserIntentService.IntentContext(null, !attachmentTypes.isEmpty(), false, historyMessages) : null;
        return classifyIntent(userText, attachmentTypes, context, userId);
    }

    /**
     * 使用 LLM 分类用户意图（支持完整上下文）
     *
     * @param userText        用户文本
     * @param attachmentTypes 附件类型列表 (image, audio, video, document)
     * @param context         意图分类上下文
     * @param userId          用户ID
     * @return 意图分类结果
     */
    public UserIntent classifyIntent(String userText, List<String> attachmentTypes,
                                      UserIntentService.IntentContext context, Long userId) {
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        if (!enabled) {
            log.debug("LLM 意图分类已禁用");
            return null;
        }

<<<<<<< HEAD
        // 提取历史消息
        List<Map<String, String>> historyMessages = context != null ? context.getHistoryMessages() : null;

        // 检查缓存（缓存 key 包含历史摘要和会话状态，避免上下文不同时复用缓存）
        String cacheKey = buildCacheKey(userText, attachmentTypes, context);
=======
        // 检查缓存
        String cacheKey = buildCacheKey(userText, attachmentTypes);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        if (cacheEnabled) {
            CacheEntry cached = intentCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                log.debug("使用缓存的意图分类结果: {}", cached.intent.getType());
                return cached.intent;
            }
        }

        try {
<<<<<<< HEAD
            // 构建分类请求（包含对话历史和会话状态）
            String prompt = buildClassificationPrompt(userText, attachmentTypes, context);
=======
            // 构建分类请求
            String prompt = buildClassificationPrompt(userText, attachmentTypes);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            UserIntent result = callLLMForClassification(prompt, userId);

            if (result != null && cacheEnabled) {
                // 缓存结果
                intentCache.put(cacheKey, new CacheEntry(result, cacheTtlMinutes * 60 * 1000L));
            }

            return result;

        } catch (Exception e) {
            log.error("LLM 意图分类失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建分类 Prompt
     */
    private String buildClassificationPrompt(String userText, List<String> attachmentTypes) {
<<<<<<< HEAD
        return buildClassificationPrompt(userText, attachmentTypes, (UserIntentService.IntentContext) null);
    }

    /**
     * 构建分类 Prompt（支持对话历史）
     */
    private String buildClassificationPrompt(String userText, List<String> attachmentTypes,
                                              List<Map<String, String>> historyMessages) {
        UserIntentService.IntentContext context = historyMessages != null ?
                new UserIntentService.IntentContext(null, !attachmentTypes.isEmpty(), false, historyMessages) : null;
        return buildClassificationPrompt(userText, attachmentTypes, context);
    }

    /**
     * 构建分类 Prompt（支持完整上下文）
     */
    private String buildClassificationPrompt(String userText, List<String> attachmentTypes,
                                              UserIntentService.IntentContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的意图分类系统。请根据用户输入和附件类型，准确判断用户意图。\n\n");

        // ========== 意图类型定义 ==========
        prompt.append("## 意图类型定义\n\n");
        prompt.append("| 意图 | 说明 | 典型触发词 |\n");
        prompt.append("|------|------|------------|\n");
        prompt.append("| CHAT | 普通文本对话 | 问答、闲聊、咨询 |\n");
        prompt.append("| GENERATE_IMAGE | 生成图片 | 画、绘制、生成图片、创作图像 |\n");
        prompt.append("| GENERATE_AUDIO | 生成音频 | 朗读、语音合成、生成语音 |\n");
        prompt.append("| GENERATE_VIDEO | 生成视频 | 生成视频、制作视频 |\n");
        prompt.append("| GENERATE_DOCUMENT | 生成文档 | 写Word、写Excel、写PPT、生成报告 |\n");
        prompt.append("| PARSE_FILE | 解析文档内容 | 上传PDF/DOCX/XLSX后提问 |\n");
        prompt.append("| PARSE_IMAGE | 图片OCR识别 | 提取图片文字、识别图中文字 |\n");
        prompt.append("| PARSE_AUDIO | 音频转文字 | 语音转文字、转录、听写 |\n");
        prompt.append("| PARSE_VIDEO | 视频解析 | 分析视频内容 |\n");
        prompt.append("| MULTIMODAL_CHAT | 多模态对话 | 上传图片/音频后让AI理解 |\n\n");

        // ========== 核心技术约束 ==========
        prompt.append("## ⚠️ 关键技术约束\n\n");
        prompt.append("**重要事实**：大多数AI模型（包括GPT、Claude、DeepSeek等）无法直接读取文档文件URL。\n");
        prompt.append("因此，当用户上传文档（PDF/DOCX/XLSX/PPTX等）时，系统必须先解析文档内容为文本，再发送给AI。\n\n");
        prompt.append("这意味着：\n");
        prompt.append("- 文档上传 → 必须返回 `PARSE_FILE`\n");
        prompt.append("- 系统会自动解析文档内容，然后连同用户问题一起发给AI回答\n");
        prompt.append("- 用户无需知道这个技术细节，他们只需要得到答案\n\n");

        // ========== 决策树 ==========
        prompt.append("## 决策流程（按顺序判断）\n\n");
        prompt.append("```\n");
        prompt.append("1. 是否上传了文档(document)？\n");
        prompt.append("   ├─ 是 → PARSE_FILE（无论用户说什么）\n");
        prompt.append("   └─ 否 → 继续\n\n");
        prompt.append("2. 是否上传了图片(image)？\n");
        prompt.append("   ├─ 用户要求\"识别文字\"/\"OCR\"/\"提取文字\" → PARSE_IMAGE\n");
        prompt.append("   └─ 其他情况（分析图片、描述图片、图片里有什么）→ MULTIMODAL_CHAT\n\n");
        prompt.append("3. 是否上传了音频(audio)？\n");
        prompt.append("   ├─ 用户要求\"转文字\"/\"转录\"/\"语音识别\" → PARSE_AUDIO\n");
        prompt.append("   └─ 其他情况 → MULTIMODAL_CHAT\n\n");
        prompt.append("4. 是否上传了视频(video)？\n");
        prompt.append("   └─ 是 → PARSE_VIDEO\n\n");
        prompt.append("5. 无文件上传，判断用户文本意图：\n");
        prompt.append("   ├─ 包含\"画\"/\"生成图片\"/\"绘制\" → GENERATE_IMAGE\n");
        prompt.append("   ├─ 包含\"朗读\"/\"生成语音\"/\"语音合成\" → GENERATE_AUDIO\n");
        prompt.append("   ├─ 包含\"生成视频\" → GENERATE_VIDEO\n");
        prompt.append("   ├─ 包含\"写Word\"/\"写Excel\"/\"写PPT\"/\"生成报告\"/\"写文档\" → GENERATE_DOCUMENT\n");
        prompt.append("   └─ 其他 → CHAT\n");
        prompt.append("```\n\n");

        // ========== 典型案例 ==========
        prompt.append("## 典型案例对照表\n\n");
        prompt.append("### 文档场景（全部返回 PARSE_FILE）\n");
        prompt.append("| 用户文本 | 附件 | 正确意图 | 原因 |\n");
        prompt.append("|----------|------|----------|------|\n");
        prompt.append("| \"分析这个报告\" | document | PARSE_FILE | 需要先解析文档内容 |\n");
        prompt.append("| \"总结一下\" | document | PARSE_FILE | 需要先解析文档内容 |\n");
        prompt.append("| \"这个文档讲了什么\" | document | PARSE_FILE | 需要先解析文档内容 |\n");
        prompt.append("| \"帮我基于这个写个报告\" | document | PARSE_FILE | 需要先解析原文档内容 |\n");
        prompt.append("| \"提取关键信息\" | document | PARSE_FILE | 需要先解析文档内容 |\n");
        prompt.append("| \"翻译这个文档\" | document | PARSE_FILE | 需要先解析文档内容 |\n");
        prompt.append("| (空文本) | document | PARSE_FILE | 默认解析文档 |\n\n");

        prompt.append("### 图片场景\n");
        prompt.append("| 用户文本 | 附件 | 正确意图 | 原因 |\n");
        prompt.append("|----------|------|----------|------|\n");
        prompt.append("| \"图片里有什么\" | image | MULTIMODAL_CHAT | 视觉模型直接理解 |\n");
        prompt.append("| \"分析这张图片\" | image | MULTIMODAL_CHAT | 视觉模型直接理解 |\n");
        prompt.append("| \"描述一下\" | image | MULTIMODAL_CHAT | 视觉模型直接理解 |\n");
        prompt.append("| \"识别图片中的文字\" | image | PARSE_IMAGE | 明确要求OCR |\n");
        prompt.append("| \"提取文字\" | image | PARSE_IMAGE | 明确要求OCR |\n");
        prompt.append("| \"OCR识别\" | image | PARSE_IMAGE | 明确要求OCR |\n\n");

        prompt.append("### 音频场景\n");
        prompt.append("| 用户文本 | 附件 | 正确意图 | 原因 |\n");
        prompt.append("|----------|------|----------|------|\n");
        prompt.append("| \"这是什么声音\" | audio | MULTIMODAL_CHAT | 音频理解 |\n");
        prompt.append("| \"分析音频\" | audio | MULTIMODAL_CHAT | 音频理解 |\n");
        prompt.append("| \"转成文字\" | audio | PARSE_AUDIO | 明确要求转写 |\n");
        prompt.append("| \"语音识别\" | audio | PARSE_AUDIO | 明确要求转写 |\n\n");

        prompt.append("### 生成场景（无文件上传）\n");
        prompt.append("| 用户文本 | 正确意图 |\n");
        prompt.append("|----------|----------|\n");
        prompt.append("| \"画一只猫\" | GENERATE_IMAGE |\n");
        prompt.append("| \"帮我生成一张风景图\" | GENERATE_IMAGE |\n");
        prompt.append("| \"朗读这段话\" | GENERATE_AUDIO |\n");
        prompt.append("| \"生成一个视频\" | GENERATE_VIDEO |\n");
        prompt.append("| \"帮我写个Word文档\" | GENERATE_DOCUMENT |\n");
        prompt.append("| \"生成一个Excel表格\" | GENERATE_DOCUMENT |\n");
        prompt.append("| \"做个PPT\" | GENERATE_DOCUMENT |\n\n");

        // ========== 常见错误 ==========
        prompt.append("## ❌ 常见错误（避免）\n\n");
        prompt.append("1. **错误**：用户上传文档+问题 → 返回 MULTIMODAL_CHAT\n");
        prompt.append("   **正确**：应返回 PARSE_FILE（模型无法直接读取文档URL）\n\n");
        prompt.append("2. **错误**：用户上传图片+\"分析一下\" → 返回 PARSE_IMAGE\n");
        prompt.append("   **正确**：应返回 MULTIMODAL_CHAT（视觉模型可直接理解图片）\n\n");
        prompt.append("3. **错误**：用户上传文档+\"帮我写报告\" → 返回 GENERATE_DOCUMENT\n");
        prompt.append("   **正确**：应返回 PARSE_FILE（需要先解析原文档内容）\n\n");
        prompt.append("4. **错误**：用户文本中包含文件名引用（如\"xxx.doc\"）但无附件 → 返回 PARSE_FILE\n");
        prompt.append("   **正确**：应返回 CHAT（文件名只是文本引用，不是实际上传，系统已处理过）\n\n");
        prompt.append("5. **错误**：用户说\"继续\"/\"接着写\"/\"下一步\" → 返回 PARSE_FILE\n");
        prompt.append("   **正确**：应返回 CHAT（这是对话延续，不是新文件解析）\n\n");

        // ========== 上下文判断 ==========
        prompt.append("## 🔍 上下文判断（重要）\n\n");
        prompt.append("当有对话历史时，需要判断用户意图是：\n");
        prompt.append("1. **新任务**：用户开始新的操作（如上传新文件、要求生成内容）\n");
        prompt.append("2. **对话延续**：用户在继续之前的对话（如\"继续\"、\"好的\"、\"下一步\"）\n\n");
        prompt.append("**判断规则**：\n");
        prompt.append("- 如果用户文本很短且无附件，通常是对话延续 → CHAT\n");
        prompt.append("- 如果用户明确提到新操作（生成、画、写），即使有历史也是新任务\n");
        prompt.append("- 文件名出现在文本中 ≠ 文件上传，只有附件类型列表非空才算真正上传\n\n");

        // ========== 搜索判断 ==========
        prompt.append("## 联网搜索判断\n\n");
        prompt.append("`needsSearch` 判断用户是否需要最新实时信息：\n\n");
        prompt.append("**需要搜索 (true)**：\n");
        prompt.append("- 时效性：最新、最近、今天、新闻、动态\n");
        prompt.append("- 实时数据：股价、汇率、天气、比分、航班\n");
        prompt.append("- 最新状态：某人/公司/事件的最新动态\n\n");
        prompt.append("**不需要搜索 (false)**：\n");
        prompt.append("- 代码/技术问题\n");
        prompt.append("- 通用知识问答\n");
        prompt.append("- 创作/翻译/计算\n");
        prompt.append("- 文件处理操作\n");
        prompt.append("- 文档分析（内容已解析，无需联网）\n\n");

        // ========== 图片尺寸 ==========
        prompt.append("## 图片尺寸推断（仅 GENERATE_IMAGE）\n\n");
        prompt.append("| 用途 | 推荐尺寸 | 方向 |\n");
        prompt.append("|------|----------|------|\n");
        prompt.append("| 手机壁纸 | 720x1440 | 竖版 |\n");
        prompt.append("| 电脑壁纸 | 1440x720 | 横版 |\n");
        prompt.append("| 海报/宣传图 | 768x1344 | 竖版 |\n");
        prompt.append("| 横幅/banner | 1344x768 | 横版 |\n");
        prompt.append("| 头像/logo | 1024x1024 | 正方形 |\n");
        prompt.append("| 默认 | 1024x1024 | 正方形 |\n\n");

        // ========== 输入信息 ==========
        prompt.append("## 当前输入\n\n");

        // 提取历史消息和会话状态
        List<Map<String, String>> historyMessages = context != null ? context.getHistoryMessages() : null;
        boolean sessionHasFiles = context != null && context.isSessionHasFiles();

        // 添加会话状态信息
        if (sessionHasFiles) {
            prompt.append("📌 **会话状态**：此会话中已有文件上传并处理过，用户可能在继续之前的对话。\n\n");
        }

        // 添加对话历史（如果有）
        if (historyMessages != null && !historyMessages.isEmpty()) {
            prompt.append("### 对话历史（最近几轮）\n\n");
            int historyCount = Math.min(historyMessages.size(), 5); // 最多显示最近5轮
            int startIndex = Math.max(0, historyMessages.size() - historyCount);
            for (int i = startIndex; i < historyMessages.size(); i++) {
                Map<String, String> msg = historyMessages.get(i);
                String role = msg.get("role");
                String msgContent = msg.get("content");
                if (role != null && msgContent != null) {
                    String roleLabel = "user".equals(role) ? "用户" : "助手";
                    // 截断过长的历史消息
                    if (msgContent.length() > 200) {
                        msgContent = msgContent.substring(0, 200) + "...";
                    }
                    prompt.append("**").append(roleLabel).append("**: ").append(msgContent).append("\n\n");
                }
            }
            prompt.append("---\n\n");
        }

        prompt.append("- **用户文本**: ").append(userText != null && !userText.trim().isEmpty() ? "\"" + userText + "\"" : "(无文本，只上传了文件)").append("\n");
        prompt.append("- **附件类型**: ").append(attachmentTypes != null && !attachmentTypes.isEmpty()
                ? String.join(", ", attachmentTypes) : "(无附件)").append("\n\n");

        // ========== 输出格式 ==========
        prompt.append("## 输出要求\n\n");
        prompt.append("请严格按照以下JSON格式输出，不要添加任何其他内容：\n\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"intent\": \"意图类型（必须从上述10种中选择）\",\n");
        prompt.append("  \"confidence\": 0.95,\n");
        prompt.append("  \"reason\": \"简要说明判断依据（一句话）\",\n");
        prompt.append("  \"needsSearch\": false,\n");
        prompt.append("  \"generationPrompt\": \"生成类意图时，提取用户的生成需求\",\n");
        prompt.append("  \"imageSize\": \"图片生成时的尺寸，如1024x1024\",\n");
        prompt.append("  \"optimizedPrompt\": \"将用户意图优化为更清晰的提示词\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        prompt.append("**注意**：confidence 应该反映你对分类的确信程度（0.0-1.0）。");
=======
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个意图分类专家。请分析用户的请求，判断用户的真实意图。\n\n");
        prompt.append("## 可选意图类型\n\n");
        prompt.append("1. CHAT - 普通文本对话，不需要特殊处理\n");
        prompt.append("2. GENERATE_IMAGE - 生成图片、画图、创建图像\n");
        prompt.append("3. GENERATE_AUDIO - 生成音频、语音合成、朗读\n");
        prompt.append("4. GENERATE_VIDEO - 生成视频\n");
        prompt.append("5. GENERATE_DOCUMENT - 生成文档（写docx、写xlsx、写pptx、写Word、写Excel、写PPT）\n");
        prompt.append("6. PARSE_FILE - 解析文档文件（PDF、DOCX、PPT、XLSX等），提取内容\n");
        prompt.append("7. PARSE_IMAGE - 图片OCR识别，提取图片中的文字\n");
        prompt.append("8. PARSE_AUDIO - 音频转文字，语音识别\n");
        prompt.append("9. PARSE_VIDEO - 视频解析\n");
        prompt.append("10. MULTIMODAL_CHAT - 多模态对话（有图片/音频/视频，让AI理解后回答问题）\n\n");
        prompt.append("## 分类规则\n\n");
        prompt.append("- 用户说\"画一只猫\"、\"生成图片\"、\"帮我画\"等 -> GENERATE_IMAGE\n");
        prompt.append("- 用户说\"朗读这段话\"、\"生成语音\"等 -> GENERATE_AUDIO\n");
        prompt.append("- 用户说\"生成视频\"等 -> GENERATE_VIDEO\n");
        prompt.append("- 用户说\"写一个docx\"、\"写xlsx\"、\"写pptx\"、\"帮我写个Word\"、\"生成Excel\"、\"生成PPT\"、\"帮我写个文档\"等 -> GENERATE_DOCUMENT\n");
        prompt.append("- 用户上传文档（PDF/DOCX等）并要求解析、提取内容 -> PARSE_FILE\n");
        prompt.append("- 用户上传图片并明确要求\"识别文字\"、\"OCR\"、\"提取文字\" -> PARSE_IMAGE\n");
        prompt.append("- 用户上传音频并要求\"转文字\"、\"转录\" -> PARSE_AUDIO\n");
        prompt.append("- 用户上传视频并要求解析 -> PARSE_VIDEO\n");
        prompt.append("- 用户上传图片/音频/视频，问\"这是什么\"、\"分析一下\"、\"描述一下\" -> MULTIMODAL_CHAT\n");
        prompt.append("- 普通文本问答 -> CHAT\n\n");
        prompt.append("## 重要提示\n\n");
        prompt.append("- 如果用户上传了图片但没有明确要求OCR识别文字，应该走 MULTIMODAL_CHAT，让视觉模型直接理解图片\n");
        prompt.append("- \"分析这张图片\"、\"图片里有什么\"是 MULTIMODAL_CHAT，不是 PARSE_IMAGE\n");
        prompt.append("- 只有明确要求\"提取文字\"、\"识别文字\"、\"OCR\"才是 PARSE_IMAGE\n");
        prompt.append("- 如果用户上传了文档(PDF/DOCX等)文件，默认应该走 PARSE_FILE 进行解析\n");
        prompt.append("- 如果用户上传了音频文件但没有明确要求转文字，应该走 MULTIMODAL_CHAT\n");
        prompt.append("- 如果用户只上传了文件没有文本，根据文件类型判断：文档->PARSE_FILE，图片->MULTIMODAL_CHAT，音频->MULTIMODAL_CHAT\n\n");

        // 搜索判断规则
        prompt.append("## 是否需要联网搜索（needsSearch）\n\n");
        prompt.append("判断用户的请求是否需要联网搜索最新信息才能准确回答。\n\n");
        prompt.append("需要搜索 (true) 的条件：\n");
        prompt.append("- 涉及时效性信息：最新、最近、今天、今年、新闻、动态、招聘、股价、天气\n");
        prompt.append("- 涉及具体实时数据：比赛比分、热搜排行、当前汇率、航班信息\n");
        prompt.append("- 关于某个具体人物/公司/事件的最新动态\n\n");
        prompt.append("不需要搜索 (false) 的条件：\n");
        prompt.append("- 代码编写、算法实现、技术实现（\"帮我写快排\"、\"如何配置nginx\"）\n");
        prompt.append("- 通用知识问答（\"什么是Java\"、\"解释一下Spring\"）\n");
        prompt.append("- 创作、翻译、数学计算、逻辑推理\n");
        prompt.append("- 文件处理、图片生成等多模态操作\n\n");

        prompt.append("## 图片尺寸推断规则（仅当意图为 GENERATE_IMAGE 时需要填写）\n\n");
        prompt.append("根据用户提示词的内容推断合适的图片尺寸：\n");
        prompt.append("- 手机壁纸、手机背景 -> 720x1440（竖版手机屏幕）\n");
        prompt.append("- 电脑壁纸、桌面背景 -> 1440x720（横版电脑屏幕）\n");
        prompt.append("- 海报、宣传图、广告 -> 768x1344 或 864x1152（竖版海报）\n");
        prompt.append("- 横幅、banner、头图 -> 1344x768 或 1152x864（横版横幅）\n");
        prompt.append("- 头像、图标、logo -> 1024x1024（正方形）\n");
        prompt.append("- 社交媒体配图、文章配图 -> 1344x768（横版）\n");
        prompt.append("- 照片、绘画、艺术作品 -> 1024x1024（默认正方形）\n");
        prompt.append("- 如果用户明确指定了尺寸（如\"竖版\"、\"横版\"、\"正方形\"），按用户要求选择\n");
        prompt.append("- 如果无法判断，默认使用 1024x1024\n\n");
        prompt.append("## 输入信息\n\n");
        prompt.append("用户文本: ").append(userText != null && !userText.isEmpty() ? userText : "(用户只上传了文件，没有输入文本)").append("\n");
        prompt.append("附件类型: ").append(attachmentTypes != null && !attachmentTypes.isEmpty()
                ? String.join(", ", attachmentTypes) : "(无附件)").append("\n");
        prompt.append("附件数量: ").append(attachmentTypes != null ? attachmentTypes.size() : 0).append("\n\n");
        prompt.append("## 输出格式\n\n");
        prompt.append("请以JSON格式输出，不要输出其他内容：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"intent\": \"意图类型\",\n");
        prompt.append("  \"confidence\": 0.95,\n");
        prompt.append("  \"reason\": \"判断理由\",\n");
        prompt.append("  \"needsSearch\": false,\n");
        prompt.append("  \"generationPrompt\": \"如果是生成类意图，提取用户的生成提示词\",\n");
        prompt.append("  \"imageSize\": \"如果是图片生成意图，推断合适的图片尺寸，如 1024x1024\"\n");
        prompt.append("}\n");
        prompt.append("```");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

        return prompt.toString();
    }

    /**
     * 调用 LLM 进行意图分类
     */
    private UserIntent callLLMForClassification(String prompt, Long userId) {
        try {
            // 获取分类模型配置
            AIModelConfig config = getModelConfig(userId);
            if (config == null) {
                log.warn("未找到意图分类模型配置");
                return null;
            }

            // 构建请求消息
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);

            LLMRequest request = LLMRequest.builder()
                    .model(config.getModelId())
                    .apiUrl(config.getApiUrl())
                    .apiKey(config.getApiKey())
                    .messages(messages)
                    .maxTokens(500)  // 增加 token 限制，避免 generationPrompt 被截断
                    .temperature(0.1)  // 低温度，更确定性的输出
                    .stream(false)
                    .build();

            log.debug("调用 LLM 进行意图分类, model={}", config.getModelId());

            LLMCompleteResponse response = llmClient.chatComplete(request);

            if (!response.isSuccess() || !response.hasContent()) {
                log.warn("LLM 意图分类响应失败: {}", response.getError());
                return null;
            }

            // 解析响应
            return parseIntentFromResponse(response.getContent());

        } catch (Exception e) {
            log.error("调用 LLM 意图分类异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取模型配置
     * 优先使用 chat 模型，避免使用 reasoner 模型（reasoner 输出格式不稳定，不适合意图分类）
     */
    private AIModelConfig getModelConfig(Long userId) {
        // 优先使用配置的分类模型
        if (classifierModelId != null && !classifierModelId.isEmpty()) {
            AIModelConfig config = modelConfigService.getModelEntity(classifierModelId, userId);
            if (config != null) {
                return config;
            }
        }

        // 获取用户可用的所有模型
        List<AIModelConfig> availableModels = modelConfigService.getAvailableModelEntities(userId);
        if (availableModels == null || availableModels.isEmpty()) {
            log.warn("未找到可用的模型配置");
            return null;
        }

        // 过滤掉 reasoner 模型，只选择 chat 模型
        AIModelConfig chatModel = null;
        AIModelConfig userDefault = modelConfigService.getDefaultModel(userId);

        for (AIModelConfig config : availableModels) {
            String modelId = config.getModelId();
            if (modelId == null) continue;

            String modelIdLower = modelId.toLowerCase();

            // 排除 reasoner 模型（reasoner 输出格式不稳定，不适合意图分类）
            if (modelIdLower.contains("reasoner") || modelIdLower.contains("o1") || modelIdLower.contains("o3")) {
                log.debug("跳过 reasoner 模型: {}", modelId);
                continue;
            }

            // 优先选择用户的默认模型（如果不是 reasoner）
            if (userDefault != null && config.getConfigId().equals(userDefault.getConfigId())) {
                log.info("使用用户默认 chat 模型用于意图分类: {}", modelId);
                return config;
            }

            // 记录第一个可用的 chat 模型作为候选
            if (chatModel == null) {
                chatModel = config;
            }
        }

        // 如果找到了 chat 模型，使用它
        if (chatModel != null) {
            log.info("使用 chat 模型用于意图分类: {}", chatModel.getModelId());
            return chatModel;
        }

        // 如果所有模型都是 reasoner，降级使用随机（总比没有好）
        log.warn("未找到 chat 模型，降级使用: {}", RandomUtils.nextInt(0, availableModels.size()));
        return availableModels.get(RandomUtils.nextInt(0, availableModels.size()));
    }

    /**
     * 从 LLM 响应解析意图
     */
    private UserIntent parseIntentFromResponse(String content) {
        try {
            // 提取 JSON
            String json = extractJson(content);
            if (json == null) {
                log.warn("无法从响应中提取 JSON: {}", content);
                return null;
            }

            JsonNode node = objectMapper.readTree(json);

            String intentStr = node.has("intent") ? node.get("intent").asText() : "CHAT";
            double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.5;
            String reason = node.has("reason") ? node.get("reason").asText() : "";
            String generationPrompt = node.has("generationPrompt") ? node.get("generationPrompt").asText() : null;
            String imageSize = node.has("imageSize") ? node.get("imageSize").asText() : null;
            boolean needsSearch = node.has("needsSearch") && node.get("needsSearch").asBoolean(false);
<<<<<<< HEAD
            String optimizedPrompt = node.has("optimizedPrompt") ? node.get("optimizedPrompt").asText() : null;
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

            // 转换为 UserIntent
            UserIntent intent = new UserIntent();
            intent.setType(parseIntentType(intentStr));
            intent.setConfidence(confidence);
            intent.setReason(reason);
            intent.setGenerationPrompt(generationPrompt);
            intent.setImageSize(imageSize);
            intent.setNeedsSearch(needsSearch);
<<<<<<< HEAD
            intent.setOptimizedPrompt(optimizedPrompt);

            log.info("LLM 意图分类结果: type={}, confidence={}, needsSearch={}, imageSize={}, optimizedPrompt={}, reason={}",
                    intent.getType(), confidence, needsSearch, imageSize,
                    optimizedPrompt != null ? optimizedPrompt.substring(0, Math.min(50, optimizedPrompt.length())) + "..." : "null",
                    reason);
=======

            log.info("LLM 意图分类结果: type={}, confidence={}, needsSearch={}, imageSize={}, reason={}",
                    intent.getType(), confidence, needsSearch, imageSize, reason);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

            return intent;

        } catch (Exception e) {
            log.error("解析意图分类响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析意图类型字符串
     */
    private UserIntent.Type parseIntentType(String intentStr) {
        if (intentStr == null) return UserIntent.Type.CHAT;

        switch (intentStr.toUpperCase()) {
            case "GENERATE_IMAGE":
                return UserIntent.Type.GENERATE_IMAGE;
            case "GENERATE_AUDIO":
                return UserIntent.Type.GENERATE_AUDIO;
            case "GENERATE_VIDEO":
                return UserIntent.Type.GENERATE_VIDEO;
            case "GENERATE_DOCUMENT":
                return UserIntent.Type.GENERATE_DOCUMENT;
            case "PARSE_FILE":
                return UserIntent.Type.PARSE_FILE;
            case "PARSE_IMAGE":
                return UserIntent.Type.PARSE_IMAGE;
            case "PARSE_AUDIO":
                return UserIntent.Type.PARSE_AUDIO;
            case "PARSE_VIDEO":
                return UserIntent.Type.PARSE_VIDEO;
            case "MULTIMODAL_CHAT":
                return UserIntent.Type.MULTIMODAL_CHAT;
            default:
                return UserIntent.Type.CHAT;
        }
    }

    /**
     * 从响应中提取 JSON
     * 增强版：处理被截断的响应
     */
    private String extractJson(String content) {
        if (content == null) return null;

        // 尝试直接解析
        content = content.trim();
        if (content.startsWith("{")) {
            int end = content.lastIndexOf("}");
            if (end > 0) {
                return content.substring(0, end + 1);
            }
            // JSON 被截断，尝试补全
            return tryRepairTruncatedJson(content);
        }

        // 尝试提取 ```json ``` 块
        int jsonStart = content.indexOf("```json");
        if (jsonStart >= 0) {
            int jsonEnd = content.indexOf("```", jsonStart + 7);
            if (jsonEnd > jsonStart) {
                return content.substring(jsonStart + 7, jsonEnd).trim();
            }
            // JSON 块未闭合，提取到末尾并尝试修复
            String jsonContent = content.substring(jsonStart + 7).trim();
            return tryRepairTruncatedJson(jsonContent);
        }

        // 尝试提取 ``` ``` 块
        int codeStart = content.indexOf("```");
        if (codeStart >= 0) {
            int codeEnd = content.indexOf("```", codeStart + 3);
            if (codeEnd > codeStart) {
                String code = content.substring(codeStart + 3, codeEnd).trim();
                if (code.startsWith("{")) {
                    return code;
                }
            }
            // 代码块未闭合，提取到末尾
            String code = content.substring(codeStart + 3).trim();
            if (code.startsWith("{")) {
                return tryRepairTruncatedJson(code);
            }
        }

        return null;
    }

    /**
     * 尝试修复被截断的 JSON
     */
    private String tryRepairTruncatedJson(String json) {
        if (json == null || json.isEmpty()) return null;

        json = json.trim();

        // 如果已经是完整 JSON，直接返回
        if (json.endsWith("}")) {
            return json;
        }

        // 尝试补全缺失的闭合括号
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean escape = false;

        for (char c : json.toCharArray()) {
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;
            else if (c == '[') openBrackets++;
            else if (c == ']') openBrackets--;
        }

        // 如果在字符串中被截断，先关闭字符串
        StringBuilder repaired = new StringBuilder(json);
        if (inString) {
            repaired.append("\"");
        }

        // 补全缺失的括号
        for (int i = 0; i < openBrackets; i++) {
            repaired.append("]");
        }
        for (int i = 0; i < openBraces; i++) {
            repaired.append("}");
        }

        String result = repaired.toString();
        log.debug("尝试修复截断的 JSON: {} -> {}", json.length(), result.length());

        return result;
    }

    /**
     * 构建缓存 Key
     */
    private String buildCacheKey(String userText, List<String> attachmentTypes) {
<<<<<<< HEAD
        return buildCacheKey(userText, attachmentTypes, (UserIntentService.IntentContext) null);
    }

    /**
     * 构建缓存 Key（包含对话历史摘要）
     * 使用历史消息的 hash 作为上下文标识，避免相同消息在不同上下文中复用缓存
     */
    private String buildCacheKey(String userText, List<String> attachmentTypes,
                                  List<Map<String, String>> historyMessages) {
        UserIntentService.IntentContext context = historyMessages != null ?
                new UserIntentService.IntentContext(null, !attachmentTypes.isEmpty(), false, historyMessages) : null;
        return buildCacheKey(userText, attachmentTypes, context);
    }

    /**
     * 构建缓存 Key（包含完整上下文）
     * 使用会话状态和历史消息摘要作为上下文标识，避免不同上下文复用缓存
     */
    private String buildCacheKey(String userText, List<String> attachmentTypes,
                                  UserIntentService.IntentContext context) {
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        StringBuilder sb = new StringBuilder();
        sb.append(userText != null ? userText : "");
        if (attachmentTypes != null) {
            sb.append("|").append(String.join(",", attachmentTypes));
        }
<<<<<<< HEAD

        // 添加会话状态到缓存 key
        if (context != null) {
            sb.append("|ctx:");
            sb.append(context.isSessionHasFiles() ? "1" : "0");

            // 添加历史消息的摘要
            List<Map<String, String>> historyMessages = context.getHistoryMessages();
            if (historyMessages != null && !historyMessages.isEmpty()) {
                sb.append(":hist:");
                sb.append(historyMessages.size()).append(":");
                Map<String, String> lastMsg = historyMessages.get(historyMessages.size() - 1);
                if (lastMsg != null && lastMsg.get("content") != null) {
                    sb.append(lastMsg.get("content").hashCode());
                }
            }
        }
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        return sb.toString();
    }

    /**
     * 清理过期缓存
     */
    public void cleanExpiredCache() {
        intentCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        log.debug("清理过期意图缓存，剩余: {}", intentCache.size());
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        final UserIntent intent;
        final long expireTime;

        CacheEntry(UserIntent intent, long ttlMs) {
            this.intent = intent;
            this.expireTime = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
