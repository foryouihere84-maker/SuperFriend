package com.superfriend.superfriend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.constant.ChatMode;
import com.superfriend.superfriend.dto.AIChatRequest;
import com.superfriend.superfriend.dto.AIChatResponse;
import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.entity.AIProcessHistory;
import com.superfriend.superfriend.entity.ChatHistory;
import com.superfriend.superfriend.entity.ChatMessageRecord;
import com.superfriend.superfriend.service.AIService;
import com.superfriend.superfriend.service.AIProcessHistoryService;
import com.superfriend.superfriend.service.ContextMangerService;
import com.superfriend.superfriend.service.SessionFileIndexService;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v16/ai")
@CrossOrigin(origins = "*")
public class AIController {
    
    @Autowired
    private AIService aiService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private AIProcessHistoryService historyService;

    @Autowired
    private ContextMangerService contextMangerService;

    @Autowired
    private SessionFileIndexService sessionFileIndexService;

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_AI_CONTENT_LENGTH = 10 * 1024 * 1024;
    
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chatStream(@RequestBody AIChatRequest request, HttpServletResponse response) {
        log.info("收到 AI 对话请求：sessionId={}, model={}, 历史条数={}",
            request.getSessionId(), request.getModel(),
            request.getHistory() != null ? request.getHistory().size() : 0);

        // 清理 message 中的非法转义字符
        if (request.getMessage() != null) {
            String cleanedMessage = cleanInvalidEscapeChars(request.getMessage());
            request.setMessage(cleanedMessage);
            log.info("已清理 message 中的非法转义字符，原始长度：{}, 清理后长度：{}",
                request.getMessage().length(), cleanedMessage.length());
        }

        log.info("AI 对话内容：{}", request.getMessage());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try {
            StringBuilder assistantContent = new StringBuilder();

            aiService.chatStream(
                request.getMessage(),
                request.getSessionId(),
                request.getModel(),
                request.getHistory(),
                request.getUserId(),
                aiResponse -> {
                    try {
                        String jsonData = objectMapper.writeValueAsString(aiResponse);
                        log.debug("发送数据: {}", jsonData);

                        response.getWriter().write("data: " + jsonData + "\n\n");
                        response.getWriter().flush();

                        if (aiResponse.getContent() != null) {
                            assistantContent.append(aiResponse.getContent());
                        }
                    } catch (IOException e) {
                        log.error("写入响应失败: {}", e.getMessage());
                    }
                }
            );

            response.getWriter().write("data: [DONE]\n\n");
            response.getWriter().flush();

            contextMangerService.saveContext(
                request.getSessionId(),
                request.getUserId(),
                request.getModel(),
                ChatMode.AI,
                request.getMessage(),
                request.getHistory(),
                assistantContent.toString()
            );

            if (request.getUserId() != null) {
                contextMangerService.extractAndSaveMemory(request.getSessionId(), request.getUserId(), request.getModel());
            }

        } catch (Exception e) {
            log.error("流式响应失败: {}", e.getMessage(), e);
            try {
                AIChatResponse errorResponse = new AIChatResponse();
                errorResponse.setContent(null);
                errorResponse.setSessionId(request.getSessionId());
                errorResponse.setModel(request.getModel());
                errorResponse.setDone(true);
                errorResponse.setError("服务器错误: " + e.getMessage());
                
                String errorJson = objectMapper.writeValueAsString(errorResponse);
                response.getWriter().write("data: " + errorJson + "\n\n");
                response.getWriter().flush();
            } catch (IOException ex) {
                log.error("发送错误响应失败: {}", ex.getMessage());
            }
        }
    }

    @PostMapping(value = "/func", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void funcStream(@RequestBody AIChatRequest request, HttpServletResponse response) {
        log.info("收到 AI 函数请求：sessionId={}, model={}, processType={}", 
            request.getSessionId(), request.getModel(), request.getProcessType());
            
        // 清理 message 中的非法转义字符
        if (request.getMessage() != null) {
            String cleanedMessage = cleanInvalidEscapeChars(request.getMessage());
            request.setMessage(cleanedMessage);
            log.info("已清理 message 中的非法转义字符，原始长度：{}, 清理后长度：{}", 
                request.getMessage().length(), cleanedMessage.length());
        }
            
        log.info("AI 函数内容长度：{} 字符", request.getMessage() != null ? request.getMessage().length() : 0);
        
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        
        StringBuilder aiContentBuilder = new StringBuilder();
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            log.info("开始处理AI函数请求...");
            
            aiService.funcStream(
                request.getMessage(),
                request.getSessionId(),
                request.getModel(),
                request.getUserId(),
                aiResponse -> {
                    try {
                        String jsonData = objectMapper.writeValueAsString(aiResponse);
                        log.debug("发送数据: {}", jsonData);
                        
                        response.getWriter().write("data: " + jsonData + "\n\n");
                        response.getWriter().flush();
                        
                        if (aiResponse.getContent() != null) {
                            if (aiContentBuilder.length() + aiResponse.getContent().length() > MAX_AI_CONTENT_LENGTH) {
                                log.warn("AI 输出内容超过最大限制 {}，停止记录", MAX_AI_CONTENT_LENGTH);
                            } else {
                                aiContentBuilder.append(aiResponse.getContent());
                            }
                        }
                    } catch (IOException e) {
                        log.error("写入响应失败: {}", e.getMessage(), e);
                    }
                }
            );
            
            log.info("AI函数请求处理完成，发送 [DONE] 信号");
            response.getWriter().write("data: [DONE]\n\n");
            response.getWriter().flush();
            
            if (Boolean.TRUE.equals(request.getSaveHistory()) && request.getProcessType() != null) {
                String aiContent = aiContentBuilder.toString();
                log.info("AI 输出总长度: {} 字符", aiContent.length());
                
                try {
                    String processTypeName = getProcessTypeName(request.getProcessType());
                    String name = String.format("%s%s", processTypeName, startTime.format(FILE_NAME_FORMATTER));
                    
                    AIProcessHistory history = new AIProcessHistory();
                    history.setName(name);
                    history.setCreateTime(startTime);
                    history.setRelatedNotes(request.getRelatedNotes());
                    history.setContent(aiContent);
                    history.setProcessType(request.getProcessType());
                    history.setUserId(request.getUserId() != null ? request.getUserId() : 1L);
                    
                    historyService.saveHistory(history);
                    log.info("AI 处理历史记录已保存: {}", name);
                } catch (Exception e) {
                    log.error("保存 AI 处理历史失败: {}", e.getMessage(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("流式响应失败: {}", e.getMessage(), e);
            try {
                AIChatResponse errorResponse = new AIChatResponse();
                errorResponse.setContent(null);
                errorResponse.setSessionId(request.getSessionId());
                errorResponse.setModel(request.getModel());
                errorResponse.setDone(true);
                errorResponse.setError("服务器错误: " + e.getMessage());
                
                String errorJson = objectMapper.writeValueAsString(errorResponse);
                response.getWriter().write("data: " + errorJson + "\n\n");
                response.getWriter().flush();
            } catch (IOException ex) {
                log.error("发送错误响应失败: {}", ex.getMessage(), ex);
            }
        }
    }
    
    private String getProcessTypeName(String processType) {
        switch (processType) {
            case "extract":
                return "提炼";
            case "polish":
                return "润色";
            case "summarize":
                return "总结";
            case "expand":
                return "拓展";
            default:
                return "处理";
        }
    }
    
    /**
     * 清理字符串中的非法转义字符
     * 将 Python 风格的 \Uxxxx 转义转换为普通文本，避免 JSON 解析错误
     */
    private String cleanInvalidEscapeChars(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // Remove Python-style escape sequences
        String result = input.replaceAll("\\\\U[0-9a-fA-F]{8}", "");
        result = result.replaceAll("\\\\u[0-9a-fA-F]{4}", "");
        result = result.replaceAll("\\\\x[0-9a-fA-F]{2}", "");
        return result;
    }
    
    @GetMapping("/history")
    public ApiResponse<List<AIProcessHistory>> getHistory(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String processType,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size) {
        try {
            if (userId == null) {
                userId = 1L;
            }
            
            if (size > 100) {
                size = 100;
                log.warn("请求的页面大小超过最大限制，设置为 100");
            }
            
            List<AIProcessHistory> histories;
            if (processType != null && !processType.isEmpty()) {
                histories = historyService.getHistoryByUserIdAndType(userId, processType);
            } else {
                histories = historyService.getHistoryByUserId(userId);
            }
            
            int total = histories.size();
            int fromIndex = page * size;
            int toIndex = Math.min(fromIndex + size, total);
            
            if (fromIndex >= total) {
                log.info("请求的页码超出范围，返回空列表");
                return ApiResponse.success(new ArrayList<>());
            }
            
            List<AIProcessHistory> pagedHistories = histories.subList(fromIndex, toIndex);
            
            log.info("获取 AI 处理历史记录成功，用户ID: {}, 类型: {}, 页码: {}, 总数: {}, 返回: {}", 
                userId, processType, page, total, pagedHistories.size());
            
            return ApiResponse.success(pagedHistories);
        } catch (Exception e) {
            log.error("获取 AI 处理历史记录失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取历史记录失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/history/{id}")
    public ApiResponse<AIProcessHistory> getHistoryById(@PathVariable Long id) {
        try {
            AIProcessHistory history = historyService.getHistoryById(id);
            if (history == null) {
                return ApiResponse.error("历史记录不存在");
            }
            
            log.info("获取 AI 处理历史记录成功，ID: {}", id);
            return ApiResponse.success(history);
        } catch (Exception e) {
            log.error("获取 AI 处理历史记录失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取历史记录失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/history/{id}")
    public ApiResponse<String> deleteHistory(@PathVariable Long id) {
        try {
            historyService.deleteHistory(id);
            log.info("删除 AI 处理历史记录成功，ID: {}", id);
            return ApiResponse.success("删除成功");
        } catch (Exception e) {
            log.error("删除 AI 处理历史记录失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 压缩对话历史
     * 将对话历史交给AI进行总结压缩，返回压缩后的内容
     */
    @PostMapping("/compress-history")
    public void compressHistoryStream(@RequestBody CompressHistoryRequest request, HttpServletResponse response) {
        log.info("收到对话历史压缩请求，历史条数: {}, 历史大小: {} 字节",
            request.getHistory() != null ? request.getHistory().size() : 0,
            request.getHistory() != null ? request.getHistory().toString().length() : 0);

        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try {
            // 构建压缩提示词
            StringBuilder historyText = new StringBuilder();
            if (request.getHistory() != null && !request.getHistory().isEmpty()) {
                for (ChatMessage msg : request.getHistory()) {
                    historyText.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n\n");
                }
            }

            String compressPrompt = "请将以下对话历史进行压缩，生成一个简化的对话摘要。\n\n" +
                "压缩要求：\n" +
                "1. 提取对话的核心内容和关键信息\n" +
                "2. 去除冗余和次要信息\n" +
                "3. 保持对话的连贯性和逻辑性\n" +
                "4. 用简洁的语言概括对话要点\n" +
                "5. 输出格式为一段简洁的摘要文本\n\n" +
                "对话历史：\n" + historyText.toString() + "\n\n" +
                "请输出对话摘要：";

            log.info("开始压缩对话历史...");
            aiService.chatStream(
                compressPrompt,
                request.getSessionId(),
                request.getModel(),
                aiResponse -> {
                    try {
                        String jsonData = objectMapper.writeValueAsString(aiResponse);
                        response.getWriter().write("data: " + jsonData + "\n\n");
                        response.getWriter().flush();
                    } catch (IOException e) {
                        log.error("写入压缩响应失败: {}", e.getMessage());
                    }
                }
            );

            response.getWriter().write("data: [DONE]\n\n");
            response.getWriter().flush();
            log.info("对话历史压缩完成");

        } catch (Exception e) {
            log.error("压缩对话历史失败: {}", e.getMessage(), e);
            try {
                AIChatResponse errorResponse = new AIChatResponse();
                errorResponse.setContent(null);
                errorResponse.setSessionId(request.getSessionId());
                errorResponse.setModel(request.getModel());
                errorResponse.setDone(true);
                errorResponse.setError("压缩失败: " + e.getMessage());

                String errorJson = objectMapper.writeValueAsString(errorResponse);
                response.getWriter().write("data: " + errorJson + "\n\n");
                response.getWriter().flush();
            } catch (IOException ex) {
                log.error("发送错误响应失败: {}", ex.getMessage());
            }
        }
    }

    /**
     * 压缩历史请求DTO
     */
    public static class CompressHistoryRequest {
        private List<ChatMessage> history;
        private String sessionId;
        private String model;

        public List<ChatMessage> getHistory() {
            return history;
        }

        public void setHistory(List<ChatMessage> history) {
            this.history = history;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    /**
     * 聊天消息DTO
     */
    public static class ChatMessage {
        private String role;
        private String content;

        public ChatMessage() {}

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    @GetMapping("/chat-sessions")
    public ApiResponse<List<ChatHistory>> getChatSessions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String mode) {
        try {
            if (userId == null) userId = 1L;
            List<ChatHistory> sessions;
            if (mode != null && !mode.isEmpty()) {
                sessions = contextMangerService.getHistoriesByUserIdAndMode(userId, mode);
            } else {
                sessions = contextMangerService.getHistoriesByUserId(userId);
            }
            return ApiResponse.success(sessions);
        } catch (Exception e) {
            log.error("获取对话列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取对话列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/chat-sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> getChatSessionMessages(@PathVariable String sessionId) {
        try {
            Map<String, Object> result = contextMangerService.getHistoryWithMessages(sessionId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取对话消息失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取对话消息失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/chat-sessions/{sessionId}")
    public ApiResponse<String> deleteChatSession(@PathVariable String sessionId) {
        try {
            contextMangerService.deleteSession(sessionId);
            return ApiResponse.success("删除成功");
        } catch (Exception e) {
            log.error("删除对话历史失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/chat-sessions/batch")
    public ApiResponse<String> batchDeleteChatSessions(@RequestBody Map<String, List<String>> body) {
        try {
            List<String> sessionIds = body.get("sessionIds");
            if (sessionIds == null || sessionIds.isEmpty()) {
                return ApiResponse.error("请选择要删除的对话");
            }
            contextMangerService.batchDeleteSessions(sessionIds);
            log.info("批量删除对话历史成功，数量: {}", sessionIds.size());
            return ApiResponse.success("批量删除成功");
        } catch (Exception e) {
            log.error("批量删除对话历史失败: {}", e.getMessage(), e);
            return ApiResponse.error("批量删除失败: " + e.getMessage());
        }
    }

    @PutMapping("/chat-sessions/{sessionId}/title")
    public ApiResponse<String> renameChatSession(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        try {
            String title = body.get("title");
            if (title == null || title.trim().isEmpty()) {
                return ApiResponse.error("标题不能为空");
            }
            contextMangerService.renameSession(sessionId, title.trim());
            return ApiResponse.success("重命名成功");
        } catch (Exception e) {
            log.error("重命名对话历史失败: {}", e.getMessage(), e);
            return ApiResponse.error("重命名失败: " + e.getMessage());
        }
    }

    @PostMapping("/chat-sessions/{sessionId}/finalize")
    public ApiResponse<String> finalizeChatSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Long> body) {
        try {
            Long userId = body != null ? body.get("userId") : null;
            if (userId == null) {
                userId = 1L;
            }
            contextMangerService.finalizeConversation(sessionId, userId);

            // 清理会话文件索引和临时文件
            sessionFileIndexService.clearSessionFiles(sessionId);

            log.info("对话结束，已触发图谱提炼并清理临时文件: sessionId={}, userId={}", sessionId, userId);
            return ApiResponse.success("图谱提炼已触发");
        } catch (Exception e) {
            log.error("结束对话失败: {}", e.getMessage(), e);
            return ApiResponse.error("结束对话失败: " + e.getMessage());
        }
    }
}
