package com.superfriend.superfriend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Service
public class TencentNewsCacheService {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    private List<NewsItem> cachedNews = new ArrayList<>();
    private LocalDateTime lastUpdateTime;
    private static final int MAX_NEWS_COUNT = 20;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrioSearchService orioSearchService;

    @Value("${news.oriosearch.time-range:day}")
    private String orioSearchTimeRange;

    @Value("${news.oriosearch.queries:AI 人工智能,科技新闻,互联网热点}")
    private String orioSearchQueries;

    public TencentNewsCacheService(OrioSearchService orioSearchService) {
        this.orioSearchService = orioSearchService;
    }

    @Data
    public static class NewsItem {
        private String title;
        private String url;
        private String source;
        private String publishTime;
        private String summary;
        private int rank;
        private long hotIndex;
        private String category;
    }

    @Data
    public static class NewsResponse {
        private boolean success;
        private String message;
        private List<NewsItem> news;
        private String updateTime;
        private int count;
    }

    @PostConstruct
    public void init() {
        if (orioSearchService == null || !orioSearchService.isHealthy()) {
            log.warn("[热点新闻] OrioSearch 服务不可用");
            return;
        }
        fetchAndCacheNewsFromOrioSearch();
    }

    public void fetchAndCacheNews() {
        fetchAndCacheNewsFromOrioSearch();
    }

    private void fetchAndCacheNewsFromOrioSearch() {
        try {
            List<NewsItem> allNews = new ArrayList<>();
            int globalRank = 1;
            
            String[] queries = orioSearchQueries.split(",");
            
            for (String query : queries) {
                query = query.trim();
                if (query.isEmpty()) continue;
                
                OrioSearchService.SearchResult result = orioSearchService.searchNews(query, orioSearchTimeRange);
                
                if (result != null && result.getResults() != null) {
                    for (OrioSearchService.SearchResultItem item : result.getResults()) {
                        if (allNews.size() >= MAX_NEWS_COUNT) break;
                        
                        NewsItem newsItem = new NewsItem();
                        newsItem.setTitle(item.getTitle());
                        newsItem.setUrl(item.getUrl());
                        newsItem.setSource("OrioSearch");
                        newsItem.setSummary(item.getContent() != null ? 
                            (item.getContent().length() > 200 ? item.getContent().substring(0, 200) + "..." : item.getContent()) 
                            : "");
                        newsItem.setCategory(query);
                        newsItem.setRank(globalRank);
                        
                        if (newsItem.getTitle() != null && !newsItem.getTitle().isEmpty()) {
                            allNews.add(newsItem);
                            globalRank++;
                        }
                    }
                }
                
                if (allNews.size() >= MAX_NEWS_COUNT) break;
            }
            
            if (!allNews.isEmpty()) {
                lock.writeLock().lock();
                try {
                    cachedNews = allNews;
                    lastUpdateTime = LocalDateTime.now();
                    log.info("[热点新闻] 已缓存 {} 条新闻", allNews.size());
                } finally {
                    lock.writeLock().unlock();
                }
            } else {
                log.warn("[热点新闻] 未获取到新闻数据");
            }
        } catch (Exception e) {
            log.error("[热点新闻] 获取失败: {}", e.getMessage());
        }
    }

    public NewsResponse getCachedNews() {
        NewsResponse response = new NewsResponse();
        
        lock.readLock().lock();
        try {
            response.setSuccess(true);
            response.setNews(new ArrayList<>(cachedNews));
            response.setCount(cachedNews.size());
            response.setUpdateTime(lastUpdateTime != null ? 
                lastUpdateTime.format(TIME_FORMATTER) : "未更新");
            response.setMessage(cachedNews.isEmpty() ? "暂无新闻数据" : "获取成功");
        } finally {
            lock.readLock().unlock();
        }
        
        return response;
    }

    public int getNewsCount() {
        lock.readLock().lock();
        try {
            return cachedNews.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getLastUpdateTime() {
        lock.readLock().lock();
        try {
            return lastUpdateTime != null ? lastUpdateTime.format(TIME_FORMATTER) : null;
        } finally {
            lock.readLock().unlock();
        }
    }
}
