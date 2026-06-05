package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OrioSearch 搜索服务
 * 自托管搜索聚合器，兼容 Tavily API
 */
@Slf4j
@Service
public class OrioSearchService {

    @Value("${oriosearch.enabled:true}")
    private boolean enabled;

    @Value("${oriosearch.base-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${oriosearch.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${oriosearch.max-results:5}")
    private int maxResults;

    @Value("${oriosearch.include-answer:true}")
    private boolean includeAnswer;

    @Value("${oriosearch.rerank-results:false}")
    private boolean rerankResults;

    @Value("${oriosearch.include-images:true}")
    private boolean includeImages;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行搜索
     *
     * @param query 搜索查询
     * @return 搜索结果
     */
    public SearchResult search(String query) {
        return searchWithTimeRange(query, null);
    }

    /**
     * 执行带时间过滤的搜索
     *
     * @param query 搜索查询
     * @param timeRange 时间过滤范围: "day", "week", "month", "year" 或 null 表示不限制
     * @return 搜索结果
     */
    public SearchResult searchWithTimeRange(String query, String timeRange) {
        return searchWithTimeRangeAndEngines(query, timeRange, null);
    }

    /**
     * 执行带时间过滤和指定引擎的搜索
     *
     * @param query 搜索查询
     * @param timeRange 时间过滤范围: "day", "week", "month", "year" 或 null 表示不限制
     * @param engines 指定搜索引擎，如 "bing news"，null 表示使用默认引擎
     * @return 搜索结果
     */
    public SearchResult searchWithTimeRangeAndEngines(String query, String timeRange, String engines) {
        if (!enabled) {
            log.debug("OrioSearch 未启用");
            return null;
        }

        if (query == null || query.trim().isEmpty()) {
            log.warn("搜索查询为空");
            return null;
        }

        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            requestBody.put("max_results", maxResults);
            requestBody.put("include_answer", includeAnswer);
            requestBody.put("rerank_results", rerankResults);
            requestBody.put("include_images", includeImages);

            if (timeRange != null && !timeRange.isEmpty()) {
                requestBody.put("time_range", timeRange);
            }

            // 如果指定了引擎，添加到请求中（SearXNG 格式）
            if (engines != null && !engines.isEmpty()) {
                requestBody.put("engines", engines);
            }

            String response = postRequest(baseUrl + "/search", requestBody);

            if (response == null || response.isEmpty()) {
                log.warn("OrioSearch 返回空响应");
                return null;
            }

            // 打印原始响应用于调试（截取前500字符）
            log.debug("OrioSearch 原始响应: {}", response.substring(0, Math.min(500, response.length())));

            JsonNode jsonNode = objectMapper.readTree(response);
            SearchResult result = parseSearchResult(jsonNode);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("OrioSearch 搜索完成: query={}, results={}, images={}, elapsed={}ms",
                    query.substring(0, Math.min(50, query.length())),
                    result.getResults() != null ? result.getResults().size() : 0,
                    result.getImages() != null ? result.getImages().size() : 0,
                    elapsed);

            // 打印图片 URL 用于调试
            if (result.getImages() != null && !result.getImages().isEmpty()) {
                log.debug("搜索返回的图片 URL:");
                for (int i = 0; i < Math.min(3, result.getImages().size()); i++) {
                    log.debug("  [{}] {}", i, result.getImages().get(i));
                }
            }

            return result;

        } catch (Exception e) {
            log.error("OrioSearch 搜索失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从 URL 提取内容
     *
     * @param urls URL 列表
     * @return 提取结果
     */
    public ExtractResult extract(List<String> urls) {
        if (!enabled) {
            log.debug("OrioSearch 未启用");
            return null;
        }

        if (urls == null || urls.isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("urls", urls);

            String response = postRequest(baseUrl + "/extract", requestBody);

            if (response == null || response.isEmpty()) {
                return null;
            }

            JsonNode jsonNode = objectMapper.readTree(response);
            return parseExtractResult(jsonNode);

        } catch (Exception e) {
            log.error("OrioSearch 提取失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 检查服务健康状态
     */
    public boolean isHealthy() {
        if (!enabled) {
            return false;
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/health").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode == 200;
        } catch (Exception e) {
            log.warn("OrioSearch 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 搜索新闻（默认使用当天时间过滤）
     *
     * @param query 新闻搜索查询
     * @return 新闻搜索结果
     */
    public SearchResult searchNews(String query) {
        return searchNews(query, "day", null);
    }

    /**
     * 搜索新闻（带时间过滤）
     *
     * @param query 新闻搜索查询
     * @param timeRange 时间过滤范围: "day", "week", "month", "year"
     * @return 新闻搜索结果
     */
    public SearchResult searchNews(String query, String timeRange) {
        return searchNews(query, timeRange, null);
    }

    /**
     * 搜索新闻（带时间过滤和指定引擎）
     *
     * @param query 新闻搜索查询
     * @param timeRange 时间过滤范围: "day", "week", "month", "year"
     * @param engines 指定搜索引擎，如 "bing news"，null 表示使用默认引擎
     * @return 新闻搜索结果
     */
    public SearchResult searchNews(String query, String timeRange, String engines) {
        if (!enabled) {
            log.debug("OrioSearch 未启用");
            return null;
        }

        if (query == null || query.trim().isEmpty()) {
            log.warn("新闻搜索查询为空");
            return null;
        }

        log.info("[OrioSearch新闻] 开始搜索新闻: query={}, timeRange={}, engines={}", query, timeRange, engines);
        
        // 不在关键词中拼接 " news"，SearXNG 不认这种写法
        // 正确做法：
        // 1. 不带引擎（走默认）：/search?q=科技新闻&time_range=day
        // 2. 指定引擎：/search?q=科技新闻&engines=bing news
        return searchWithTimeRangeAndEngines(query, timeRange, engines);
    }

    /**
     * 获取用于 LLM 的搜索结果文本
     */
    public String getSearchContextForLLM(String query) {
        SearchResult result = search(query);
        if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        if (result.getAnswer() != null && !result.getAnswer().isEmpty()) {
            sb.append("## AI 摘要\n");
            sb.append(result.getAnswer()).append("\n\n");
        }

        sb.append("## 搜索结果\n");
        int index = 1;
        for (SearchResultItem item : result.getResults()) {
            sb.append(index++).append(". ");
            if (item.getTitle() != null) {
                sb.append("**").append(item.getTitle()).append("**\n");
            }
            if (item.getContent() != null) {
                sb.append(item.getContent()).append("\n");
            }
            if (item.getUrl() != null) {
                sb.append("来源: ").append(item.getUrl()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String postRequest(String url, Map<String, Object> body) {
        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);

            String jsonBody = objectMapper.writeValueAsString(body);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                log.warn("OrioSearch 请求失败: HTTP {}", responseCode);
                return null;
            }

            try (InputStream is = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }

        } catch (Exception e) {
            log.error("OrioSearch 请求异常: {}", e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private SearchResult parseSearchResult(JsonNode json) {
        SearchResult result = new SearchResult();

        if (json.has("answer")) {
            result.setAnswer(json.get("answer").asText());
        }

        List<SearchResultItem> items = new ArrayList<>();
        if (json.has("results") && json.get("results").isArray()) {
            for (JsonNode itemNode : json.get("results")) {
                SearchResultItem item = new SearchResultItem();
                if (itemNode.has("title")) {
                    item.setTitle(itemNode.get("title").asText());
                }
                if (itemNode.has("url")) {
                    item.setUrl(itemNode.get("url").asText());
                }
                if (itemNode.has("content")) {
                    item.setContent(itemNode.get("content").asText());
                }
                if (itemNode.has("raw_content")) {
                    item.setRawContent(itemNode.get("raw_content").asText());
                }
                items.add(item);
            }
        }
        result.setResults(items);

        // 解析图片列表
        List<String> images = new ArrayList<>();
        if (json.has("images") && json.get("images").isArray()) {
            for (JsonNode imageNode : json.get("images")) {
                if (imageNode.isObject() && imageNode.has("url")) {
                    images.add(imageNode.get("url").asText());
                } else if (imageNode.isTextual()) {
                    images.add(imageNode.asText());
                }
            }
        }
        result.setImages(images);

        return result;
    }

    private ExtractResult parseExtractResult(JsonNode json) {
        ExtractResult result = new ExtractResult();
        List<ExtractResultItem> items = new ArrayList<>();

        if (json.has("results") && json.get("results").isArray()) {
            for (JsonNode itemNode : json.get("results")) {
                ExtractResultItem item = new ExtractResultItem();
                if (itemNode.has("url")) {
                    item.setUrl(itemNode.get("url").asText());
                }
                if (itemNode.has("raw_content")) {
                    item.setRawContent(itemNode.get("raw_content").asText());
                }
                items.add(item);
            }
        }
        result.setResults(items);

        return result;
    }

    // ==================== 数据类 ====================

    @Data
    public static class SearchResult {
        private String answer;
        private List<SearchResultItem> results;
        private List<String> images;  // 图片 URL 列表
    }

    @Data
    public static class SearchResultItem {
        private String title;
        private String url;
        private String content;
        private String rawContent;
    }

    @Data
    public static class ExtractResult {
        private List<ExtractResultItem> results;
    }

    @Data
    public static class ExtractResultItem {
        private String url;
        private String rawContent;
    }

}
