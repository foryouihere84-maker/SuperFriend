package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.entity.AIProcessHistory;
import com.superfriend.superfriend.mapper.AIProcessHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class AIProcessHistoryService {
    
    @Autowired
    private AIProcessHistoryMapper historyMapper;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    @Qualifier("asyncTaskExecutor")
    private TaskExecutor taskExecutor;
    
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_CONTENT_LENGTH = 10 * 1024 * 1024;
    
    public AIProcessHistory saveHistory(AIProcessHistory history) {
        if (history.getContent() != null && history.getContent().length() > MAX_CONTENT_LENGTH) {
            log.warn("AI处理历史内容过长，截断为前 {} 字符", MAX_CONTENT_LENGTH);
            history.setContent(history.getContent().substring(0, MAX_CONTENT_LENGTH));
        }
        
        historyMapper.insert(history);
        log.info("AI处理历史记录已保存，ID: {}, 类型: {}", history.getId(), history.getProcessType());
        
        return history;
    }
    
    public AIProcessHistory updateHistoryContent(Long id, String content) {
        AIProcessHistory history = new AIProcessHistory();
        history.setId(id);
        history.setContent(content);
        historyMapper.updateContent(history);
        log.info("AI处理历史内容已更新，ID: {}", id);
        
        return history;
    }
    
    public List<AIProcessHistory> getHistoryByUserId(Long userId) {
        return historyMapper.findByUserId(userId);
    }
    
    public List<AIProcessHistory> getHistoryByUserIdAndType(Long userId, String processType) {
        return historyMapper.findByUserIdAndProcessType(userId, processType);
    }
    
    public AIProcessHistory getHistoryById(Long id) {
        return historyMapper.findById(id);
    }
    
    public void deleteHistory(Long id) {
        AIProcessHistory history = historyMapper.findById(id);
        if (history != null) {
            historyMapper.deleteById(id);
            log.info("AI处理历史记录已删除，ID: {}", id);
        }
    }
    
    public void deleteHistoryByUserId(Long userId) {
        historyMapper.deleteByUserId(userId);
        log.info("用户 {} 的所有AI处理历史记录已删除", userId);
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
}
