package com.superfriend.superfriend.service;

import com.superfriend.superfriend.entity.KnowledgeNode;
import com.superfriend.superfriend.mapper.KnowledgeNodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识节点丰富化服务
 * 异步为知识图谱节点获取头像、图片和详细描述
 *
 * 核心逻辑：
 * 1. 使用 OrioSearch 搜索图片和详细描述
 * 2. 详细描述中附上百度百科链接（Markdown 格式）
 * 3. 图片上传到腾讯云 COS
 */
@Slf4j
@Service
public class NodeEnrichmentService {

    @Autowired
    private KnowledgeNodeMapper nodeMapper;

    @Autowired
    private TencentCosService tencentCosService;

    @Autowired
    private OrioSearchService orioSearchService;

    @Value("${node-enrichment.enabled:true}")
    private boolean enabled;

    @Value("${node-enrichment.avatar-max-size:1048576}")
    private long avatarMaxSize;

    @Value("${node-enrichment.image-max-size:3145728}")
    private long imageMaxSize;

    @Value("${node-enrichment.image-count:2}")
    private int imageCount;

    // 正在丰富化中的节点ID集合（防止重复提交）
    private static final ConcurrentHashMap<Long, Long> enrichingNodes = new ConcurrentHashMap<>();

    // 不适合获取图片的节点类型
    private static final Set<String> NO_IMAGE_TYPES = new HashSet<String>() {{
        add("ERROR");
        add("SOLUTION");
        add("PREFERENCE");
    }};

    // 图片 URL 提取正则（更宽松的匹配）
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
        "(https?:[^\\s\"'<>]+?\\.(?:jpg|jpeg|png|gif|webp)(?:\\?[^\\s\"'<>]*)?)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 异步丰富化节点
     */
    @Async("asyncTaskExecutor")
    public void enrichNodeAsync(Long nodeId) {
        if (!enabled) {
            log.debug("节点丰富化服务未启用");
            return;
        }

        if (nodeId == null) {
            return;
        }

        // 防止重复提交：使用 putIfAbsent 检查是否已在处理中
        long currentTime = System.currentTimeMillis();
        Long existingTime = enrichingNodes.putIfAbsent(nodeId, currentTime);
        if (existingTime != null) {
            // 检查是否已超过 5 分钟（可能是之前的任务卡住了）
            if (currentTime - existingTime < 5 * 60 * 1000) {
                log.warn("节点正在丰富化中，跳过重复请求: nodeId={}", nodeId);
                return;
            } else {
                // 超过 5 分钟，可能是卡住了，允许重新处理
                log.warn("节点丰富化可能超时，允许重新处理: nodeId={}, lastTime={}", nodeId, existingTime);
                enrichingNodes.put(nodeId, currentTime);
            }
        }

        try {
            log.info("开始节点丰富化: nodeId={}", nodeId);

            // 1. 获取节点信息
            KnowledgeNode node = nodeMapper.findById(nodeId);
            if (node == null) {
                log.warn("节点不存在: nodeId={}", nodeId);
                return;
            }

            log.info("节点信息: name={}, type={}", node.getName(), node.getNodeType());

            // 2. 检查是否需要丰富化（某些类型不需要图片）
            boolean needImage = !NO_IMAGE_TYPES.contains(node.getNodeType());

            // 3. 构建搜索关键词
            String searchQuery = buildSearchQuery(node);
            log.info("搜索关键词: {}", searchQuery);

            // 4. 使用 OrioSearch 搜索信息
            OrioSearchService.SearchResult searchResult = performSearch(searchQuery);

            // 打印搜索结果摘要
            if (searchResult != null) {
                log.info("搜索结果: answer={}, resultsCount={}",
                    searchResult.getAnswer() != null ? "有" : "无",
                    searchResult.getResults() != null ? searchResult.getResults().size() : 0);

                if (searchResult.getResults() != null) {
                    for (int i = 0; i < Math.min(3, searchResult.getResults().size()); i++) {
                        OrioSearchService.SearchResultItem item = searchResult.getResults().get(i);
                        log.info("结果[{}]: url={}, contentLen={}, rawContentLen={}",
                            i, item.getUrl(),
                            item.getContent() != null ? item.getContent().length() : 0,
                            item.getRawContent() != null ? item.getRawContent().length() : 0);
                    }
                }
            }

            // 5. 获取头像
            String avatarUrl = null;
            if (needImage) {
                avatarUrl = fetchAvatar(searchQuery, searchResult);
                log.info("头像获取结果: {}", avatarUrl != null ? avatarUrl : "失败");
            }

            // 6. 获取相关图片
            String imageUrls = null;
            if (needImage) {
                imageUrls = fetchImages(searchQuery, searchResult);
                log.info("图片获取结果: {}", imageUrls != null ? imageUrls : "失败");
            }

            // 7. 获取详细描述（包含百度百科链接）
            String detailedDesc = fetchDetailedDescription(searchQuery, searchResult);

            // 8. 更新节点
            updateNode(nodeId, avatarUrl, imageUrls, detailedDesc);

            log.info("节点丰富化完成: nodeId={}, avatar={}, images={}, desc={}",
                    nodeId,
                    avatarUrl != null ? "yes" : "no",
                    imageUrls != null ? "yes" : "no",
                    detailedDesc != null ? "yes" : "no");

        } catch (Exception e) {
            log.error("节点丰富化失败: nodeId={}, error={}", nodeId, e.getMessage(), e);
        } finally {
            // 完成后移除标记
            enrichingNodes.remove(nodeId);
        }
    }

    /**
     * 检查节点是否正在丰富化中
     */
    public boolean isNodeEnriching(Long nodeId) {
        if (nodeId == null) return false;
        Long startTime = enrichingNodes.get(nodeId);
        if (startTime == null) return false;
        // 超过 5 分钟视为不再处理中
        return System.currentTimeMillis() - startTime < 5 * 60 * 1000;
    }

    /**
     * 构建搜索关键词
     */
    private String buildSearchQuery(KnowledgeNode node) {
        StringBuilder query = new StringBuilder();

        if (node.getName() != null && !node.getName().isEmpty()) {
            query.append(node.getName());
        }

        if (node.getDescription() != null && !node.getDescription().isEmpty()) {
            String desc = node.getDescription();
            if (desc.length() > 30) {
                desc = desc.substring(0, 30);
            }
            query.append(" ").append(desc);
        }

        return query.toString().trim();
    }

    /**
     * 执行 OrioSearch 搜索
     */
    private OrioSearchService.SearchResult performSearch(String query) {
        try {
            return orioSearchService.search(query);
        } catch (Exception e) {
            log.warn("OrioSearch 搜索失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取头像
     */
    private String fetchAvatar(String query, OrioSearchService.SearchResult searchResult) {
        log.debug("开始获取头像...");

        // 1. 优先使用搜索结果中返回的图片
        if (searchResult != null && searchResult.getImages() != null && !searchResult.getImages().isEmpty()) {
            log.debug("搜索结果返回 {} 张图片", searchResult.getImages().size());
            for (String imageUrl : searchResult.getImages()) {
                log.debug("尝试头像图片: {}", imageUrl);

                long size = tencentCosService.checkImageSize(imageUrl);
                log.debug("图片大小: {} bytes", size);

                if (size > 0 && size <= avatarMaxSize) {
                    TencentCosService.CosUploadResult result = uploadImageToCos(imageUrl, "avatars", avatarMaxSize);
                    if (result != null) {
                        log.info("获取头像成功: {}", result.getUrl());
                        return result.getUrl();
                    }
                } else if (size > avatarMaxSize) {
                    log.debug("图片太大，跳过: {} > {}", size, avatarMaxSize);
                }
            }
        } else {
            log.debug("搜索结果未返回图片");
        }

        // 2. 备用方案：从搜索结果页面中提取图片
        List<String> imagesFromPages = extractImagesFromSearchResults(searchResult, 5);
        log.debug("从搜索结果提取到 {} 张图片", imagesFromPages.size());

        for (String imageUrl : imagesFromPages) {
            log.debug("尝试头像图片: {}", imageUrl);
            long size = tencentCosService.checkImageSize(imageUrl);
            log.debug("图片大小: {} bytes", size);

            if (size > 0 && size <= avatarMaxSize) {
                TencentCosService.CosUploadResult result = uploadImageToCos(imageUrl, "avatars", avatarMaxSize);
                if (result != null) {
                    log.info("获取头像成功: {}", result.getUrl());
                    return result.getUrl();
                }
            }
        }

        log.warn("获取头像失败: 没有找到合适的图片");
        return null;
    }

    /**
     * 获取相关图片
     */
    private String fetchImages(String query, OrioSearchService.SearchResult searchResult) {
        log.debug("开始获取相关图片...");
        List<String> uploadedUrls = new ArrayList<>();

        // 1. 优先使用搜索结果中返回的图片
        if (searchResult != null && searchResult.getImages() != null && !searchResult.getImages().isEmpty()) {
            log.debug("搜索结果返回 {} 张图片", searchResult.getImages().size());
            for (String imageUrl : searchResult.getImages()) {
                if (uploadedUrls.size() >= imageCount) break;

                log.debug("尝试图片: {}", imageUrl);

                long size = tencentCosService.checkImageSize(imageUrl);
                log.debug("图片大小: {} bytes", size);

                if (size > 0 && size <= imageMaxSize) {
                    TencentCosService.CosUploadResult result = uploadImageToCos(imageUrl, "images", imageMaxSize);
                    if (result != null) {
                        uploadedUrls.add(result.getUrl());
                        log.debug("图片上传成功: {}", result.getUrl());
                    }
                }
            }
        }

        // 2. 如果图片不足，从搜索结果页面中提取
        if (uploadedUrls.size() < imageCount) {
            List<String> imagesFromPages = extractImagesFromSearchResults(searchResult, imageCount + 2);
            log.debug("从搜索结果提取到 {} 张图片", imagesFromPages.size());

            for (String imageUrl : imagesFromPages) {
                if (uploadedUrls.size() >= imageCount) break;

                log.debug("尝试图片: {}", imageUrl);
                long size = tencentCosService.checkImageSize(imageUrl);

                if (size > 0 && size <= imageMaxSize) {
                    TencentCosService.CosUploadResult result = uploadImageToCos(imageUrl, "images", imageMaxSize);
                    if (result != null) {
                        uploadedUrls.add(result.getUrl());
                    }
                }
            }
        }

        if (uploadedUrls.isEmpty()) {
            log.warn("获取图片失败: 没有找到合适的图片");
            return null;
        }

        return String.join(",", uploadedUrls);
    }

    /**
     * 从搜索结果页面中提取图片 URL
     */
    private List<String> extractImagesFromSearchResults(OrioSearchService.SearchResult searchResult, int maxCount) {
        List<String> imageUrls = new ArrayList<>();

        if (searchResult == null || searchResult.getResults() == null) {
            log.debug("搜索结果为空");
            return imageUrls;
        }

        for (OrioSearchService.SearchResultItem item : searchResult.getResults()) {
            if (imageUrls.size() >= maxCount) break;

            // 从 raw_content 中提取图片 URL
            if (item.getRawContent() != null && !item.getRawContent().isEmpty()) {
                List<String> extracted = extractImageUrls(item.getRawContent());
                log.debug("从 raw_content 提取到 {} 张图片", extracted.size());
                for (String url : extracted) {
                    if (imageUrls.size() >= maxCount) break;
                    if (!imageUrls.contains(url) && isValidImageUrl(url)) {
                        imageUrls.add(url);
                    }
                }
            } else {
                log.debug("raw_content 为空");
            }

            // 也尝试从 content 中提取（有些搜索结果可能直接包含图片 URL）
            if (item.getContent() != null && !item.getContent().isEmpty()) {
                List<String> extracted = extractImageUrls(item.getContent());
                for (String url : extracted) {
                    if (imageUrls.size() >= maxCount) break;
                    if (!imageUrls.contains(url) && isValidImageUrl(url)) {
                        imageUrls.add(url);
                    }
                }
            }
        }

        return imageUrls;
    }

    /**
     * 从 HTML 内容中提取图片 URL
     */
    private List<String> extractImageUrls(String htmlContent) {
        List<String> urls = new ArrayList<>();
        if (htmlContent == null || htmlContent.isEmpty()) {
            return urls;
        }

        Matcher matcher = IMAGE_URL_PATTERN.matcher(htmlContent);
        while (matcher.find()) {
            String url = matcher.group();
            // 过滤掉一些明显不适合的图片
            String lowerUrl = url.toLowerCase();
            if (!lowerUrl.contains("avatar") && !lowerUrl.contains("icon")
                && !lowerUrl.contains("loading") && !lowerUrl.contains("placeholder")
                && !lowerUrl.contains("tracking") && !lowerUrl.contains("pixel")
                && !lowerUrl.contains("blank.gif") && !lowerUrl.contains("spacer.gif")) {
                urls.add(url);
            }
        }

        return urls;
    }

    /**
     * 验证图片 URL 是否有效
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        return !lower.contains("blank.gif") && !lower.contains("spacer.gif")
            && !lower.contains("tracking") && !lower.contains("pixel");
    }

    /**
     * 获取详细描述
     */
    private String fetchDetailedDescription(String query, OrioSearchService.SearchResult searchResult) {
        StringBuilder content = new StringBuilder();

        // 1. 如果有 AI 摘要，添加到开头
        if (searchResult != null && searchResult.getAnswer() != null && !searchResult.getAnswer().isEmpty()) {
            content.append(searchResult.getAnswer()).append("\n\n");
        }

        // 2. 从搜索结果中提取内容
        if (searchResult != null && searchResult.getResults() != null) {
            for (OrioSearchService.SearchResultItem item : searchResult.getResults()) {
                if (item.getContent() != null && !item.getContent().isEmpty()) {
                    content.append(item.getContent()).append("\n\n");
                }
                if (content.length() > 1000) {
                    break;
                }
            }
        }

        // 3. 添加百度百科链接（Markdown 格式，可点击）
        String baikeUrl = getBaikeUrl(query);
        if (baikeUrl != null) {
            content.append("\n---\n\n");
            content.append("**参考资料：**\n");
            content.append("- [详细信息](").append(baikeUrl).append(") - 百度百科\n");
        }

        String result = content.toString().trim();
        if (result.isEmpty()) {
            return null;
        }

        // 限制长度
        if (result.length() > 2000) {
            result = result.substring(0, 2000) + "...";
        }

        return result;
    }

    /**
     * 获取百度百科链接
     */
    private String getBaikeUrl(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        try {
            String searchTerm = query.trim().split("\\s+")[0];
            String encodedQuery = java.net.URLEncoder.encode(searchTerm, "UTF-8");
            return "https://baike.baidu.com/item/" + encodedQuery;
        } catch (Exception e) {
            log.debug("构建百度百科链接失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 上传图片到腾讯云 COS
     */
    private TencentCosService.CosUploadResult uploadImageToCos(String imageUrl, String directory, long maxSize) {
        if (!tencentCosService.isEnabled()) {
            log.warn("腾讯云 COS 未启用，跳过上传");
            return null;
        }

        TencentCosService.CosUploadResult result = tencentCosService.uploadFromUrl(imageUrl, "knowledge-graph/" + directory, maxSize);
        if (result == null) {
            log.warn("上传图片失败: {}", imageUrl);
        }
        return result;
    }

    /**
     * 更新节点
     */
    private void updateNode(Long nodeId, String avatar, String images, String detailedDescription) {
        KnowledgeNode node = nodeMapper.findById(nodeId);
        if (node == null) {
            return;
        }

        boolean updated = false;

        if (avatar != null && !avatar.isEmpty()) {
            node.setAvatar(avatar);
            updated = true;
        }

        if (images != null && !images.isEmpty()) {
            node.setImage(images);
            updated = true;
        }

        if (detailedDescription != null && !detailedDescription.isEmpty()) {
            node.setDetailedDescription(detailedDescription);
            updated = true;
        }

        if (updated) {
            nodeMapper.update(node);
            log.debug("节点更新成功: nodeId={}", nodeId);
        }
    }
}
