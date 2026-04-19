package com.superfriend.superfriend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 百度百科爬取服务
 * 用于获取知识节点的详细描述和图片
 */
@Slf4j
@Service
public class BaiduBaikeService {

    @Value("${baidu-baike.enabled:true}")
    private boolean enabled;

    @Value("${baidu-baike.timeout-ms:15000}")
    private int timeoutMs;

    private static final String BAIKE_SEARCH_URL = "https://baike.baidu.com/search";
    private static final String BAIKE_BASE_URL = "https://baike.baidu.com";

    /**
     * 搜索百度百科词条
     *
     * @param keyword 搜索关键词
     * @return 搜索结果
     */
    public BaikeResult searchBaike(String keyword) {
        if (!enabled) {
            log.debug("百度百科服务未启用");
            return null;
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }

        try {
            // 1. 先尝试直接访问词条页面
            String directUrl = BAIKE_BASE_URL + "/item/" + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8.name());
            BaikeResult directResult = fetchBaikePage(directUrl);
            if (directResult != null && directResult.hasContent()) {
                log.info("直接访问百度百科成功: keyword={}", keyword);
                return directResult;
            }

            // 2. 直接访问失败，尝试搜索
            String searchUrl = BAIKE_SEARCH_URL + "?word=" + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8.name());
            Document searchDoc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(timeoutMs)
                    .get();

            // 3. 从搜索结果中提取第一个词条链接
            Elements resultLinks = searchDoc.select("a.result-link, a[href*=/item/]");
            if (resultLinks.isEmpty()) {
                // 尝试其他选择器
                resultLinks = searchDoc.select("div.result a[href*=/item/]");
            }

            if (!resultLinks.isEmpty()) {
                String firstResultUrl = resultLinks.first().absUrl("href");
                if (firstResultUrl != null && !firstResultUrl.isEmpty()) {
                    log.info("从搜索结果找到词条: keyword={}, url={}", keyword, firstResultUrl);
                    return fetchBaikePage(firstResultUrl);
                }
            }

            log.info("百度百科未找到词条: keyword={}", keyword);
            return null;

        } catch (Exception e) {
            log.error("搜索百度百科失败: keyword={}, error={}", keyword, e.getMessage());
            return null;
        }
    }

    /**
     * 获取百度百科页面内容
     *
     * @param baikeUrl 词条 URL
     * @return 页面内容
     */
    public BaikeResult fetchBaikePage(String baikeUrl) {
        if (!enabled) {
            return null;
        }

        try {
            Document doc = Jsoup.connect(baikeUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(timeoutMs)
                    .get();

            BaikeResult result = new BaikeResult();
            result.setUrl(baikeUrl);

            // 提取标题
            Element titleElement = doc.selectFirst("h1, dd.titleH1, span.title");
            if (titleElement != null) {
                result.setTitle(titleElement.text().trim());
            }

            // 提取摘要描述
            String description = extractDescription(doc);
            result.setDescription(description);

            // 提取详细内容
            String detailedContent = extractDetailedContent(doc);
            result.setDetailedContent(detailedContent);

            // 提取图片
            List<String> images = extractImages(doc);
            result.setImages(images);

            return result;

        } catch (Exception e) {
            log.error("获取百度百科页面失败: url={}, error={}", baikeUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 提取摘要描述
     */
    private String extractDescription(Document doc) {
        // 尝试多种选择器
        String[] selectors = {
            "div.lemma-summary",
            "div.summary",
            "div.lemma-summary-content",
            "meta[name=description]"
        };

        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element != null) {
                if (element.tagName().equals("meta")) {
                    String content = element.attr("content");
                    if (content != null && !content.isEmpty()) {
                        return content.trim();
                    }
                } else {
                    String text = element.text().trim();
                    if (!text.isEmpty()) {
                        // 清理文本
                        text = text.replaceAll("\\[\\d+\\]", ""); // 移除引用标记
                        text = text.replaceAll("\\s+", " ");
                        return text;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 提取详细内容
     */
    private String extractDetailedContent(Document doc) {
        StringBuilder content = new StringBuilder();

        // 提取摘要
        String summary = extractDescription(doc);
        if (summary != null && !summary.isEmpty()) {
            content.append(summary).append("\n\n");
        }

        // 提取正文段落
        Elements paragraphs = doc.select("div.lemma-content p, div.para, div.lemma-content-content p");
        for (Element p : paragraphs) {
            String text = p.text().trim();
            if (!text.isEmpty()) {
                text = text.replaceAll("\\[\\d+\\]", ""); // 移除引用标记
                text = text.replaceAll("\\[编辑\\]", ""); // 移除编辑按钮文字
                text = text.replaceAll("\\s+", " ");
                content.append(text).append("\n");
            }
        }

        // 如果内容过长，截取前 2000 字符
        String result = content.toString().trim();
        if (result.length() > 2000) {
            result = result.substring(0, 2000) + "...";
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * 提取图片 URL 列表
     */
    private List<String> extractImages(Document doc) {
        List<String> images = new ArrayList<>();

        // 提取词条主图
        Element mainImage = doc.selectFirst("div.lemma-summary img, div.summary-pic img, img.picture");
        if (mainImage != null) {
            String src = extractImageUrl(mainImage);
            if (src != null) {
                images.add(src);
            }
        }

        // 提取内容中的图片
        Elements contentImages = doc.select("div.lemma-content img, div.lemma-content-content img");
        for (Element img : contentImages) {
            if (images.size() >= 5) break; // 最多 5 张
            String src = extractImageUrl(img);
            if (src != null && !images.contains(src)) {
                images.add(src);
            }
        }

        return images;
    }

    /**
     * 从图片元素提取 URL
     */
    private String extractImageUrl(Element img) {
        // 尝试多种属性
        String src = img.absUrl("src");
        if (src == null || src.isEmpty()) {
            src = img.absUrl("data-src");
        }
        if (src == null || src.isEmpty()) {
            src = img.absUrl("data-original");
        }

        // 过滤无效 URL
        if (src != null && !src.isEmpty()) {
            // 跳过占位图和空白图
            if (src.contains("blank.gif") || src.contains("placeholder") || src.contains("loading")) {
                return null;
            }
            // 确保是有效的图片 URL
            if (src.startsWith("http://") || src.startsWith("https://")) {
                return src;
            }
        }

        return null;
    }

    /**
     * 百度百科搜索结果
     */
    @Data
    public static class BaikeResult {
        private String url;
        private String title;
        private String description;        // 摘要描述
        private String detailedContent;    // 详细内容
        private List<String> images;       // 图片 URL 列表

        public boolean hasContent() {
            return (description != null && !description.isEmpty()) ||
                   (detailedContent != null && !detailedContent.isEmpty()) ||
                   (images != null && !images.isEmpty());
        }

        public boolean hasImages() {
            return images != null && !images.isEmpty();
        }

        public String getFirstImage() {
            if (images != null && !images.isEmpty()) {
                return images.get(0);
            }
            return null;
        }
    }
}
