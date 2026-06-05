package com.superfriend.superfriend.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class OssService {

    @Value("${aliyun.oss.endpoint:}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name:}")
    private String bucketName;

    @Value("${aliyun.oss.enabled:false}")
    private boolean enabled;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        if (enabled && endpoint != null && !endpoint.isEmpty()) {
            try {
                ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                log.info("阿里云 OSS 客户端初始化成功，bucket: {}", bucketName);
            } catch (Exception e) {
                log.error("阿里云 OSS 客户端初始化失败: {}", e.getMessage());
            }
        } else {
            log.info("阿里云 OSS 未启用或未配置");
        }
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("阿里云 OSS 客户端已关闭");
        }
    }

    public boolean isEnabled() {
        return enabled && ossClient != null;
    }

    public OssUploadResult uploadFile(MultipartFile file, String directory) throws IOException {
        if (!isEnabled()) {
            throw new IllegalStateException("OSS 服务未启用");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String fileName = UUID.randomUUID().toString() + extension;
        String objectKey = directory + "/" + fileName;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        PutObjectResult result = ossClient.putObject(
            bucketName, 
            objectKey, 
            file.getInputStream(), 
            metadata
        );

        String url = generateUrl(objectKey);

        log.info("文件上传成功: {} -> {}", originalFilename, objectKey);

        OssUploadResult uploadResult = new OssUploadResult();
        uploadResult.setObjectKey(objectKey);
        uploadResult.setUrl(url);
        uploadResult.setFileName(originalFilename);
        uploadResult.setFileSize(file.getSize());
        uploadResult.setContentType(file.getContentType());
        uploadResult.setETag(result.getETag());

        return uploadResult;
    }

    public OssUploadResult uploadBytes(byte[] data, String fileName, String contentType, String directory) {
        if (!isEnabled()) {
            throw new IllegalStateException("OSS 服务未启用");
        }

        String extension = "";
        if (fileName != null && fileName.contains(".")) {
            extension = fileName.substring(fileName.lastIndexOf("."));
        }

        String objectKey = directory + "/" + UUID.randomUUID().toString() + extension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        metadata.setContentType(contentType);

        PutObjectResult result = ossClient.putObject(
            bucketName, 
            objectKey, 
            new ByteArrayInputStream(data), 
            metadata
        );

        String url = generateUrl(objectKey);

        log.info("字节数据上传成功: {} -> {}", fileName, objectKey);

        OssUploadResult uploadResult = new OssUploadResult();
        uploadResult.setObjectKey(objectKey);
        uploadResult.setUrl(url);
        uploadResult.setFileName(fileName);
        uploadResult.setFileSize((long) data.length);
        uploadResult.setContentType(contentType);
        uploadResult.setETag(result.getETag());

        return uploadResult;
    }

    public InputStream downloadFile(String objectKey) throws IOException {
        if (!isEnabled()) {
            throw new IllegalStateException("OSS 服务未启用");
        }

        OSSObject ossObject = ossClient.getObject(bucketName, objectKey);
        return ossObject.getObjectContent();
    }

    public byte[] downloadFileAsBytes(String objectKey) throws IOException {
        try (InputStream is = downloadFile(objectKey)) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }

    public void deleteFile(String objectKey) {
        if (!isEnabled()) {
            throw new IllegalStateException("OSS 服务未启用");
        }

        ossClient.deleteObject(bucketName, objectKey);
        log.info("文件删除成功: {}", objectKey);
    }

    public boolean fileExists(String objectKey) {
        if (!isEnabled()) {
            return false;
        }
        return ossClient.doesObjectExist(bucketName, objectKey);
    }

    public String generateUrl(String objectKey) {
        if (!isEnabled()) {
            return null;
        }

        if (endpoint.startsWith("https://")) {
            return "https://" + bucketName + "." + endpoint.substring(8) + "/" + objectKey;
        } else if (endpoint.startsWith("http://")) {
            return "http://" + bucketName + "." + endpoint.substring(7) + "/" + objectKey;
        } else {
            return "https://" + bucketName + "." + endpoint + "/" + objectKey;
        }
    }

    public String generatePresignedUrl(String objectKey, int expirationMinutes) {
        if (!isEnabled()) {
            return null;
        }

        Date expiration = new Date(System.currentTimeMillis() + expirationMinutes * 60 * 1000L);
        URL url = ossClient.generatePresignedUrl(bucketName, objectKey, expiration);
        return url.toString();
    }

    @Data
    public static class OssUploadResult {
        private String objectKey;
        private String url;
        private String fileName;
        private Long fileSize;
        private String contentType;
        private String eTag;
    }
}
