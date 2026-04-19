package com.superfriend.superfriend.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.UUID;

/**
 * 腾讯云 COS 对象存储服务
 * 用于上传知识图谱节点的头像和图片
 */
@Slf4j
@Service
public class TencentCosService {

    @Value("${tencent.cos.enabled:false}")
    private boolean enabled;

    @Value("${tencent.cos.secret-id:}")
    private String secretId;

    @Value("${tencent.cos.secret-key:}")
    private String secretKey;

    @Value("${tencent.cos.bucket-name:}")
    private String bucketName;

    @Value("${tencent.cos.region:ap-beijing}")
    private String region;

    private COSClient cosClient;

    @PostConstruct
    public void init() {
        if (enabled && secretId != null && !secretId.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            try {
                COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
                ClientConfig clientConfig = new ClientConfig(new Region(region));
                clientConfig.setHttpProtocol(HttpProtocol.https);
                cosClient = new COSClient(cred, clientConfig);
                log.info("腾讯云 COS 客户端初始化成功，bucket: {}, region: {}", bucketName, region);
            } catch (Exception e) {
                log.error("腾讯云 COS 客户端初始化失败: {}", e.getMessage());
            }
        } else {
            log.info("腾讯云 COS 未启用或未配置");
        }
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
            log.info("腾讯云 COS 客户端已关闭");
        }
    }

    public boolean isEnabled() {
        return enabled && cosClient != null;
    }

    /**
     * 上传字节数据到 COS
     *
     * @param data        字节数据
     * @param fileName    文件名
     * @param contentType 内容类型
     * @param directory   目录路径
     * @return 上传结果
     */
    public CosUploadResult uploadBytes(byte[] data, String fileName, String contentType, String directory) {
        if (!isEnabled()) {
            log.warn("腾讯云 COS 服务未启用");
            return null;
        }

        String extension = "";
        if (fileName != null && fileName.contains(".")) {
            extension = fileName.substring(fileName.lastIndexOf("."));
        }

        String objectKey = directory + "/" + UUID.randomUUID().toString() + extension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        metadata.setContentType(contentType);

        try {
            PutObjectResult result = cosClient.putObject(
                bucketName,
                objectKey,
                new ByteArrayInputStream(data),
                metadata
            );

            String url = generatePresignedUrl(objectKey, 365 * 24 * 60); // 1年有效期

            log.info("字节数据上传成功: {} -> {}", fileName, objectKey);

            CosUploadResult uploadResult = new CosUploadResult();
            uploadResult.setObjectKey(objectKey);
            uploadResult.setUrl(url);
            uploadResult.setFileName(fileName);
            uploadResult.setFileSize((long) data.length);
            uploadResult.setContentType(contentType);
            uploadResult.setETag(result.getETag());

            return uploadResult;

        } catch (Exception e) {
            log.error("上传字节数据失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从 URL 下载图片并上传到 COS
     *
     * @param imageUrl   图片 URL
     * @param directory  目录路径
     * @param maxSizeBytes 最大文件大小（字节），超过则不上传
     * @return 上传结果
     */
    public CosUploadResult uploadFromUrl(String imageUrl, String directory, long maxSizeBytes) {
        if (!isEnabled()) {
            log.warn("腾讯云 COS 服务未启用");
            return null;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            // 设置常见的请求头，模拟浏览器访问
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Referer", extractReferer(imageUrl));

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                log.warn("下载图片失败: HTTP {}, URL: {}", responseCode, imageUrl);
                return null;
            }

            // 检查文件大小
            long contentLength = connection.getContentLengthLong();
            if (contentLength > maxSizeBytes) {
                log.info("图片大小超过限制: {} bytes > {} bytes, URL: {}", contentLength, maxSizeBytes, imageUrl);
                return null;
            }

            // 获取内容类型
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                contentType = guessContentType(imageUrl);
            }

            // 读取数据
            try (InputStream is = connection.getInputStream()) {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] data = new byte[4096];
                int nRead;
                int totalRead = 0;
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                    totalRead += nRead;
                    // 防止读取超过最大大小
                    if (totalRead > maxSizeBytes) {
                        log.info("图片读取超过限制: {} bytes > {} bytes, URL: {}", totalRead, maxSizeBytes, imageUrl);
                        return null;
                    }
                }
                buffer.flush();
                byte[] imageData = buffer.toByteArray();

                // 生成文件名
                String extension = guessExtension(contentType, imageUrl);
                String fileName = UUID.randomUUID().toString() + extension;

                return uploadBytes(imageData, fileName, contentType, directory);
            }

        } catch (Exception e) {
            log.error("从 URL 上传图片失败: {}, error: {}", imageUrl, e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 从 URL 下载图片并上传到 COS（无大小限制）
     */
    public CosUploadResult uploadFromUrl(String imageUrl, String directory) {
        return uploadFromUrl(imageUrl, directory, Long.MAX_VALUE);
    }

    /**
     * 检查图片 URL 的大小
     *
     * @param imageUrl 图片 URL
     * @return 文件大小（字节），-1 表示无法获取
     */
    public long checkImageSize(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                return connection.getContentLengthLong();
            }
            return -1;
        } catch (Exception e) {
            log.debug("检查图片大小失败: {}, error: {}", imageUrl, e.getMessage());
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 生成访问 URL
     */
    public String generateUrl(String objectKey) {
        if (!isEnabled()) {
            return null;
        }
        // 腾讯云 COS 的标准访问 URL 格式
        return String.format("https://%s.cos.%s.myqcloud.com/%s", bucketName, region, objectKey);
    }

    /**
     * 生成预签名访问 URL（用于私有存储桶）
     *
     * @param objectKey 对象键
     * @param expirationMinutes 过期时间（分钟）
     * @return 预签名 URL
     */
    public String generatePresignedUrl(String objectKey, int expirationMinutes) {
        if (!isEnabled()) {
            return null;
        }
        try {
            java.util.Date expiration = new java.util.Date(System.currentTimeMillis() + expirationMinutes * 60 * 1000L);
            java.net.URL url = cosClient.generatePresignedUrl(bucketName, objectKey, expiration);
            return url.toString();
        } catch (Exception e) {
            log.error("生成预签名 URL 失败: {}", e.getMessage());
            return generateUrl(objectKey);
        }
    }

    /**
     * 下载文件
     */
    public InputStream downloadFile(String objectKey) throws IOException {
        if (!isEnabled()) {
            throw new IllegalStateException("COS 服务未启用");
        }

        COSObject cosObject = cosClient.getObject(bucketName, objectKey);
        return cosObject.getObjectContent();
    }

    /**
     * 删除文件
     */
    public void deleteFile(String objectKey) {
        if (!isEnabled()) {
            throw new IllegalStateException("COS 服务未启用");
        }

        cosClient.deleteObject(bucketName, objectKey);
        log.info("文件删除成功: {}", objectKey);
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String objectKey) {
        if (!isEnabled()) {
            return false;
        }
        return cosClient.doesObjectExist(bucketName, objectKey);
    }

    private String extractReferer(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost() + "/";
        } catch (Exception e) {
            return "";
        }
    }

    private String guessContentType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".gif")) return "image/gif";
        if (lower.contains(".webp")) return "image/webp";
        if (lower.contains(".svg")) return "image/svg+xml";
        return "image/jpeg"; // 默认
    }

    private String guessExtension(String contentType, String url) {
        if (contentType != null) {
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("gif")) return ".gif";
            if (contentType.contains("webp")) return ".webp";
            if (contentType.contains("svg")) return ".svg";
        }
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return ".png";
        if (lower.contains(".gif")) return ".gif";
        if (lower.contains(".webp")) return ".webp";
        return ".jpg";
    }

    @Data
    public static class CosUploadResult {
        private String objectKey;
        private String url;
        private String fileName;
        private Long fileSize;
        private String contentType;
        private String eTag;
    }
}
