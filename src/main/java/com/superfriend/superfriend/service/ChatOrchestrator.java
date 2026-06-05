package com.superfriend.superfriend.service;

import com.superfriend.superfriend.constant.ChatMode;
import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.strategy.ChatModeStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 聊天编排服务
 * 统一意图路由、文件解析、策略分发
 */
@Slf4j
@Service
public class ChatOrchestrator {

    private final Map<String, ChatModeStrategy> strategyMap;

    // 文件内容最大长度限制（字符数），约 500KB 文本
    private static final int MAX_FILE_CONTENT_LENGTH = 500_0000;

    // 单个文件最大长度限制
    private static final int MAX_SINGLE_FILE_LENGTH = 200_0000;

    // 小文件阈值（50KB），小于此值会预解析
    private static final int SMALL_FILE_THRESHOLD = 5000* 1024;

    @Autowired
    @Lazy
    private UserIntentService userIntentService;

    @Autowired
    @Lazy
    private FileParseService fileParseService;

    @Autowired
    @Lazy
    private MultimodalProcessService multimodalProcessService;

    @Autowired
    @Lazy
    private SessionFileIndexService sessionFileIndexService;

    public ChatOrchestrator(List<ChatModeStrategy> strategies) {
        this.strategyMap = new HashMap<>();
        for (ChatModeStrategy strategy : strategies) {
            strategyMap.put(strategy.getChatMode(), strategy);
        }
        log.info("ChatOrchestrator 初始化，注册策略: {}", strategyMap.keySet());
    }

    /**
     * 核心调度方法 - 统一意图路由
     *
     * @param request  AI 请求
     * @param callback 响应回调
     * @param mode     聊天模式
     * @return 多模态上下文（包含生成结果），用于上层追加内容
     */
    public MultimodalContext dispatch(AIChatRequest request, Consumer<AIChatResponse> callback, String mode) {
        return dispatch(request, null, callback, mode);
    }

    /**
     * 核心调度方法 - 带预识别意图
     */
    public MultimodalContext dispatch(AIChatRequest request, UserIntent intent,
                         Consumer<AIChatResponse> callback, String mode) {
        ChatModeStrategy strategy = strategyMap.get(mode);
        if (strategy == null) {
            sendError(callback, request.getSessionId(), request.getModel(),
                    "未找到对应的聊天模式策略: " + mode);
            return null;
        }

        // 意图识别（如果未提供）
        if (intent == null) {
            intent = userIntentService.analyzeIntent(request);
        }
        log.info("意图识别结果: {} (mode={})", intent.getType(), mode);

        // 统一意图路由
        switch (intent.getType()) {
            case GENERATE_IMAGE:
            case GENERATE_AUDIO:
            case GENERATE_VIDEO:
                // 图片/音频/视频生成走多模态处理服务
                return multimodalProcessService.process(request, intent, callback);

            case GENERATE_DOCUMENT:
                // 文档生成需要工具调用能力，Lite 模式自动升级到 Medium 模式
                if (ChatMode.LITE_TASK.equals(mode)) {
                    log.info("GENERATE_DOCUMENT 意图在 Lite 模式下自动升级到 Medium 模式");
                    ChatModeStrategy mediumStrategy = strategyMap.get(ChatMode.MEDIUM_TASK);
                    if (mediumStrategy != null) {
                        mediumStrategy.executeChat(request, intent, callback);
                    } else {
                        log.warn("Medium 策略未找到，回退到当前策略");
                        strategy.executeChat(request, intent, callback);
                    }
                } else {
                    strategy.executeChat(request, intent, callback);
                }
                return null;

            case PARSE_FILE:
            case PARSE_IMAGE:
            case PARSE_AUDIO:
            case PARSE_VIDEO:
                // 文件解析 -> 增强消息 -> 对话
                handleFileParseAndChat(request, intent, strategy, callback);
                return null;

            case MULTIMODAL_CHAT:
                // 多模态对话
                strategy.executeMultimodalChat(request, intent, callback);
                return null;

            default:
                // 普通对话
                strategy.executeChat(request, intent, callback);
                return null;
        }
    }

    /**
     * 处理文件解析后的对话
     * 使用动态文件索引机制：小文件预解析，大文件按需读取
     */
    private void handleFileParseAndChat(AIChatRequest request, UserIntent intent,
                                         ChatModeStrategy strategy,
                                         Consumer<AIChatResponse> callback) {
        log.info("处理文件解析请求: {}", intent.getType());

        String sessionId = request.getSessionId();

        // Step 1: 获取所有文件 URL
        List<String[]> files = userIntentService.extractFiles(request);

        // 如果 intent 中有文件URL，优先使用
        if (intent.getFileUrl() != null && !intent.getFileUrl().isEmpty()) {
            // 检查是否已存在相同的 URL，避免重复解析
            boolean alreadyExists = false;
            for (String[] f : files) {
                if (intent.getFileUrl().equals(f[0])) {
                    alreadyExists = true;
                    break;
                }
            }
            if (!alreadyExists) {
                files.add(0, new String[]{intent.getFileUrl(), intent.getMimeType()});
            }
        }

        if (files.isEmpty()) {
            sendError(callback, sessionId, request.getModel(), "未找到要解析的文件");
            return;
        }

        // Step 2: 发送解析状态
        AIChatResponse parsingStatus = new AIChatResponse();
        parsingStatus.setType("thinking");
        parsingStatus.setContent(files.size() > 1 ?
            "正在处理 " + files.size() + " 个文件..." : "正在处理文件...");
        parsingStatus.setSessionId(sessionId);
        parsingStatus.setModel(request.getModel());
        callback.accept(parsingStatus);

        // Step 3: 注册文件到索引（小文件预解析，大文件仅存储元信息）
        int successCount = 0;
        int failCount = 0;
        List<FileIndexEntry> registeredFiles = new ArrayList<>();

        for (String[] file : files) {
            String fileUrl = file[0];
            String mimeType = file[1];
            String fileName = extractFileName(fileUrl);
            Long fileSize = estimateFileSize(fileUrl);

            // 生成文件 ID
            String fileId = UUID.randomUUID().toString().substring(0, 8);

            try {
                // 注册文件到索引
                FileIndexEntry entry = sessionFileIndexService.registerFile(
                        sessionId, fileId, fileName, mimeType, fileSize, fileUrl);

                registeredFiles.add(entry);
                successCount++;
                log.info("文件 {} 注册成功: fileId={}, size={}, preParsed={}",
                        fileName, fileId, fileSize, entry.getPreParsed());
            } catch (Exception e) {
                log.error("文件 {} 注册失败: {}", fileName, e.getMessage());
                failCount++;
            }
        }

        if (successCount == 0) {
            sendError(callback, sessionId, request.getModel(), "所有文件处理失败");
            return;
        }

        log.info("文件处理完成: 成功 {} 个, 失败 {} 个", successCount, failCount);

        // Step 4: 构建文件列表摘要（注入到 prompt，让 LLM 知道有哪些文件可用）
        String fileListPrompt = buildFileListPrompt(sessionId, registeredFiles);

        // Step 5: 构建增强消息
        String userText = request.getEffectiveText();
        StringBuilder enhancedMessage = new StringBuilder();

        if (userText != null && !userText.isEmpty()) {
            enhancedMessage.append(userText);
        }

        enhancedMessage.append(fileListPrompt);

        // Step 6: 更新请求中的用户消息
        request.setMessage(enhancedMessage.toString());

        // Step 7: 调用策略执行
        strategy.executeFileParsedChat(request, intent, enhancedMessage.toString(), callback);
    }

    /**
     * 构建文件列表摘要（注入到 prompt）
     */
    private String buildFileListPrompt(String sessionId, List<FileIndexEntry> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【用户上传的文件】\n");
        sb.append("当前会话 ID: `").append(sessionId).append("`\n\n");
        sb.append("用户上传了以下文件：\n\n");

        for (FileIndexEntry file : files) {
            sb.append("- **").append(file.getFileName()).append("**\n");
            sb.append("  - 文件 ID: `").append(file.getFileId()).append("`\n");
            sb.append("  - 类型: ").append(file.getFileTypeDisplayName()).append("\n");
            sb.append("  - 大小: ").append(file.getFormattedSize()).append("\n");

            if (file.getSummary() != null && !file.getSummary().isEmpty()) {
                sb.append("  - 摘要: ").append(file.getSummary()).append("\n");
            }
            if (file.getLineCount() != null) {
                sb.append("  - 行数: ").append(file.getLineCount()).append("\n");
            }
            if (file.getPageCount() != null) {
                sb.append("  - 页数: ").append(file.getPageCount()).append("\n");
            }

            if (file.getPreParsed() != null && file.getPreParsed()) {
                sb.append("  - 状态: ✅ 已预解析，可直接读取\n");
            } else {
                sb.append("  - 状态: 📄 大文件，需按需读取\n");
            }
            sb.append("\n");
        }

        sb.append("**可用工具**：\n");
        sb.append("- `list_files`: 列出所有文件\n");
        sb.append("- `read_file`: 读取文件内容（可指定行号范围）\n");
        sb.append("- `search_file`: 在文件中搜索关键词\n");
        sb.append("- `get_file_info`: 获取文件详细信息\n");
        sb.append("- `get_file_path`: 获取文件的本地绝对路径（传给 run_skill_script 时必须使用）\n");
        sb.append("\n");

        sb.append("⚠️ **重要**：调用 `read_file`、`get_file_info`、`search_file`、`get_file_path` 工具时，必须传入 `session_id` 参数：`").append(sessionId).append("`\n\n");

        sb.append("📋 **何时应该主动读取文件**：\n");
        sb.append("1. 用户要求分析、总结、解释文件内容时\n");
        sb.append("2. 用户的问题需要参考文件内容才能回答时\n");
        sb.append("3. 用户上传文件后说\"帮我看看\"、\"分析一下\"等模糊请求时\n");
        sb.append("4. 用户要求提取文件中的特定信息时\n");
        sb.append("\n");

        sb.append("💡 **建议**：\n");
        sb.append("- 如果用户没有明确要求处理文件，先询问用户是否需要分析文件\n");
        sb.append("- 读取文件前，先用 `get_file_info` 了解文件详情\n");
        sb.append("- 对于大文件，可以分段读取（使用 start_line 和 end_line 参数）\n");
        sb.append("\n");

        sb.append("🔧 **使用 Skill 处理文件时的步骤**：\n");
        sb.append("1. 先用 `get_file_path` 获取文件的本地绝对路径\n");
        sb.append("2. 将获取到的本地路径作为 `run_skill_script` 的 `input` 参数传入\n");
        sb.append("3. 例如：`get_file_path(file_id=\"xxx\")` -> 得到路径 -> `run_skill_script(skill_name=\"minimax-docx\", parameters={\"input\": \"获取到的路径\", \"output\": \"output.docx\"})`\n");

        return sb.toString();
    }

    /**
     * 估算文件大小
     * 对于无法获取大小的文件，返回 -1 表示"未知大小，按大文件处理"
     */
    private Long estimateFileSize(String fileUrl) {
        if (fileUrl == null) return -1L;

        // 对于 temp:// 协议的文件，尝试获取实际大小
        if (fileUrl.startsWith("temp://")) {
            try {
                String path = fileUrl.substring("temp://".length());
                String[] parts = path.split("/", 2);
                if (parts.length == 2) {
                    java.nio.file.Path localPath = java.nio.file.Paths.get("uploads", "temp", parts[0], parts[1]);
                    if (java.nio.file.Files.exists(localPath)) {
                        return java.nio.file.Files.size(localPath);
                    }
                }
            } catch (Exception e) {
                log.debug("无法获取文件大小: {}", fileUrl);
            }
        }

        // 对于 HTTP/HTTPS URL，尝试 HEAD 请求获取 Content-Length
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            try {
                java.net.URL url = new java.net.URL(fileUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int contentLength = conn.getContentLength();
                conn.disconnect();
                if (contentLength > 0) {
                    return (long) contentLength;
                }
            } catch (Exception e) {
                log.debug("无法通过 HTTP HEAD 获取文件大小: {}", fileUrl);
            }
        }

        // 对于 data: URL，估算 base64 解码后的大小
        if (fileUrl.startsWith("data:")) {
            try {
                int base64Start = fileUrl.indexOf("base64,");
                if (base64Start > 0) {
                    String base64Data = fileUrl.substring(base64Start + 7);
                    // base64 编码后大小约为原始数据的 4/3 倍
                    return (long) (base64Data.length() * 3 / 4);
                }
            } catch (Exception e) {
                log.debug("无法估算 data URL 大小: {}", fileUrl);
            }
        }

        // 无法获取大小，返回 -1 表示未知（按大文件处理，不预解析）
        return -1L;
    }

    /**
     * 从URL中提取文件名
     */
    private String extractFileName(String url) {
        if (url == null || url.isEmpty()) {
            return "未知文件";
        }
        // 处理 temp:// 协议
        if (url.startsWith("temp://")) {
            url = url.substring(7);
        }
        // 提取最后一个路径段
        int lastSlash = url.lastIndexOf('/');
        int lastBackslash = url.lastIndexOf('\\');
        int lastSep = Math.max(lastSlash, lastBackslash);
        if (lastSep >= 0 && lastSep < url.length() - 1) {
            return url.substring(lastSep + 1);
        }
        return url;
    }

    private void sendError(Consumer<AIChatResponse> callback, String sessionId, String model, String message) {
        AIChatResponse errorResponse = new AIChatResponse();
        errorResponse.setType("error");
        errorResponse.setContent(message);
        errorResponse.setSessionId(sessionId);
        errorResponse.setModel(model);
        errorResponse.setDone(true);
        callback.accept(errorResponse);
    }
}
