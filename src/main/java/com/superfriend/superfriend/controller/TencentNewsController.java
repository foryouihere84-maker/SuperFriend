package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.service.TencentNewsCacheService;
import com.superfriend.superfriend.service.TencentNewsCacheService.NewsResponse;
import com.superfriend.superfriend.service.OrioSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v16/news")
@CrossOrigin(origins = "*")
@Tag(name = "热点新闻", description = "热点新闻资讯接口（支持OrioSearch和TianAPI）")
public class TencentNewsController {

    @Autowired
    private TencentNewsCacheService tencentNewsCacheService;

    @Autowired
    private OrioSearchService orioSearchService;

    @Value("${news.source:oriosearch}")
    private String newsSource;

    @Value("${news.oriosearch.time-range:day}")
    private String orioSearchTimeRange;

    @GetMapping("/hot")
    @Operation(summary = "获取热点新闻", description = "获取缓存的热点新闻列表（最多10条）")
    public ResponseEntity<NewsResponse> getHotNews() {
        log.info("[腾讯新闻API] 收到获取热点新闻请求");
        NewsResponse response = tencentNewsCacheService.getCachedNews();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新新闻", description = "手动触发刷新新闻缓存")
    public ResponseEntity<Map<String, Object>> refreshNews() {
        log.info("[腾讯新闻API] 收到手动刷新新闻请求");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            tencentNewsCacheService.fetchAndCacheNews();
            result.put("success", true);
            result.put("message", "新闻刷新成功");
            result.put("count", tencentNewsCacheService.getNewsCount());
            result.put("updateTime", tencentNewsCacheService.getLastUpdateTime());
        } catch (Exception e) {
            log.error("[腾讯新闻API] 刷新新闻失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "新闻刷新失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    @Operation(summary = "获取新闻服务状态", description = "获取新闻缓存服务的当前状态")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("newsCount", tencentNewsCacheService.getNewsCount());
        status.put("lastUpdateTime", tencentNewsCacheService.getLastUpdateTime());
        status.put("newsSource", newsSource);
        status.put("orioSearchTimeRange", orioSearchTimeRange);
        status.put("orioSearchHealthy", orioSearchService.isHealthy());
        status.put("status", "running");
        
        return ResponseEntity.ok(status);
    }
}
