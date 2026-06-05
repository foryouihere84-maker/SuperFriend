package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
<<<<<<< HEAD
import com.superfriend.superfriend.entity.UserFile;
import com.superfriend.superfriend.mapper.UserFileMapper;
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
<<<<<<< HEAD
import javax.annotation.Resource;
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
<<<<<<< HEAD
import java.util.stream.Collectors;
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

@Slf4j
@RestController
@RequestMapping("/api/v16/files")
@Tag(name = "文件上传", description = "文件上传接口")
public class FileController {

    @Value("${app.file.upload-dir:#{null}}")
    private String uploadDir;

    @Value("${app.file.max-size-mb:10}")
    private int maxFileSizeMb;

    private static final String DEFAULT_UPLOAD_DIR = "uploads/temp";
    private Path uploadPath;

<<<<<<< HEAD
    private static final int MAX_STORAGE_MB = 100;

    @Resource
    private UserFileMapper userFileMapper;

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    @PostConstruct
    public void init() {
        String dir = (uploadDir != null && !uploadDir.isEmpty()) ? uploadDir : DEFAULT_UPLOAD_DIR;
        uploadPath = Paths.get(dir);
        try {
            Files.createDirectories(uploadPath);
            log.info("文件上传目录初始化成功: {}", uploadPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", e.getMessage());
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件", description = "上传单个文件，返回临时文件 URL")
    public ApiResponse<FileUploadResult> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false, defaultValue = "general") String type) {

        if (file.isEmpty()) {
            return ApiResponse.error("文件为空");
        }

        long maxSizeBytes = maxFileSizeMb * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            return ApiResponse.error("文件大小超过限制: " + maxFileSizeMb + "MB");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + extension;

            Path typePath = uploadPath.resolve(type);
            if (!Files.exists(typePath)) {
                Files.createDirectories(typePath);
            }

            Path filePath = typePath.resolve(fileName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath);
            }

            log.info("文件上传成功: {} -> {}", originalFilename, filePath);

            FileUploadResult result = new FileUploadResult();
            result.setFileName(originalFilename);
            result.setFileId(fileName);
            result.setFileUrl("temp://" + type + "/" + fileName);
            result.setFileSize(file.getSize());
            result.setContentType(file.getContentType());
            result.setType(detectFileType(file.getContentType(), originalFilename));

            return ApiResponse.success(result);

        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            return ApiResponse.error("文件上传失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "批量上传文件", description = "批量上传多个文件")
    public ApiResponse<BatchUploadResult> uploadFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "type", required = false, defaultValue = "general") String type) {

        if (files == null || files.length == 0) {
            return ApiResponse.error("没有选择文件");
        }

        List<FileUploadResult> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                errors.add(file.getOriginalFilename() + ": 文件为空");
                continue;
            }

            long maxSizeBytes = maxFileSizeMb * 1024L * 1024L;
            if (file.getSize() > maxSizeBytes) {
                errors.add(file.getOriginalFilename() + ": 超过大小限制");
                continue;
            }

            try {
                String originalFilename = file.getOriginalFilename();
                String extension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                String fileName = UUID.randomUUID().toString() + extension;

                Path typePath = uploadPath.resolve(type);
                if (!Files.exists(typePath)) {
                    Files.createDirectories(typePath);
                }

                Path filePath = typePath.resolve(fileName);
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, filePath);
                }

                FileUploadResult result = new FileUploadResult();
                result.setFileName(originalFilename);
                result.setFileId(fileName);
                result.setFileUrl("temp://" + type + "/" + fileName);
                result.setFileSize(file.getSize());
                result.setContentType(file.getContentType());
                result.setType(detectFileType(file.getContentType(), originalFilename));

                results.add(result);

            } catch (IOException e) {
                errors.add(file.getOriginalFilename() + ": " + e.getMessage());
                log.error("文件上传失败: {}", e.getMessage());
            }
        }

        BatchUploadResult batchResult = new BatchUploadResult();
        batchResult.setFiles(results);
        batchResult.setErrors(errors);
        batchResult.setTotalCount(files.length);
        batchResult.setSuccessCount(results.size());

        return ApiResponse.success(batchResult);
    }

    @GetMapping("/download")
    @Operation(summary = "下载临时文件", description = "通过 temp:// URL 下载临时文件")
    public void downloadFile(
            @RequestParam("url") String fileUrl,
            HttpServletResponse response) throws IOException {

        if (fileUrl == null || !fileUrl.startsWith("temp://")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("无效的文件 URL");
            return;
        }

        String path = fileUrl.substring("temp://".length());
        String[] parts = path.split("/", 2);
        if (parts.length != 2) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("无效的文件路径");
            return;
        }

        String type = parts[0];
        String fileName = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name());

        Path filePath = uploadPath.resolve(type).resolve(fileName);

        if (!Files.exists(filePath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("文件不存在");
            return;
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        try (InputStream inputStream = Files.newInputStream(filePath);
             OutputStream outputStream = response.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * 根据 Content-Type 和文件名检测文件类型
     */
    private String detectFileType(String contentType, String fileName) {
        if (contentType != null) {
            if (contentType.startsWith("image/")) return "image";
            if (contentType.startsWith("audio/")) return "audio";
            if (contentType.startsWith("video/")) return "video";
            if (contentType.contains("pdf") || contentType.contains("document") ||
                contentType.contains("sheet") || contentType.contains("presentation") ||
                contentType.contains("word")) return "document";
        }

        if (fileName != null) {
            String ext = fileName.toLowerCase();
            if (ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".png") ||
                ext.endsWith(".gif") || ext.endsWith(".bmp") || ext.endsWith(".webp")) {
                return "image";
            }
            if (ext.endsWith(".mp3") || ext.endsWith(".wav") || ext.endsWith(".m4a") ||
                ext.endsWith(".flac") || ext.endsWith(".aac")) {
                return "audio";
            }
            if (ext.endsWith(".mp4") || ext.endsWith(".avi") || ext.endsWith(".mov") ||
                ext.endsWith(".mkv") || ext.endsWith(".webm")) {
                return "video";
            }
            if (ext.endsWith(".pdf") || ext.endsWith(".docx") || ext.endsWith(".doc") ||
                ext.endsWith(".pptx") || ext.endsWith(".ppt") || ext.endsWith(".xlsx") ||
                ext.endsWith(".xls") || ext.endsWith(".txt") || ext.endsWith(".md")) {
                return "document";
            }
        }

        return "unknown";
    }

    @Data
    public static class FileUploadResult {
        private String fileName;
        private String fileId;
        private String fileUrl;
        private Long fileSize;
        private String contentType;
        private String type;
    }

    @Data
    public static class BatchUploadResult {
        private List<FileUploadResult> files;
        private List<String> errors;
        private int totalCount;
        private int successCount;
    }
<<<<<<< HEAD

    // ==================== 用户文件管理接口 ====================

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户文件列表", description = "获取指定用户的所有活跃文件")
    public ApiResponse<List<UserFileInfo>> getUserFiles(@PathVariable Long userId) {
        try {
            List<UserFile> files = userFileMapper.findByUserId(userId);
            List<UserFileInfo> result = files.stream()
                    .map(this::convertToUserInfo)
                    .collect(Collectors.toList());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取用户文件列表失败: userId={}, error={}", userId, e.getMessage());
            return ApiResponse.error("获取文件列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}/session/{sessionId}")
    @Operation(summary = "获取会话文件列表", description = "获取指定用户在特定会话中的文件")
    public ApiResponse<List<UserFileInfo>> getSessionFiles(
            @PathVariable Long userId,
            @PathVariable String sessionId) {
        try {
            List<UserFile> files = userFileMapper.findByUserIdAndSessionId(userId, sessionId);
            List<UserFileInfo> result = files.stream()
                    .map(this::convertToUserInfo)
                    .collect(Collectors.toList());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取会话文件列表失败: userId={}, sessionId={}, error={}", userId, sessionId, e.getMessage());
            return ApiResponse.error("获取会话文件列表失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/user/{userId}/file/{fileId}")
    @Operation(summary = "删除用户文件", description = "软删除指定的用户文件")
    public ApiResponse<Boolean> deleteUserFile(
            @PathVariable Long userId,
            @PathVariable String fileId) {
        try {
            UserFile file = userFileMapper.findByFileId(fileId);
            if (file == null) {
                return ApiResponse.error("文件不存在");
            }
            if (!file.getUserId().equals(userId)) {
                return ApiResponse.error("无权删除此文件");
            }

            userFileMapper.softDeleteByFileId(fileId);

            if (file.getFilePath() != null) {
                try {
                    Path path = Paths.get(file.getFilePath());
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("删除物理文件失败: {}", file.getFilePath());
                }
            }

            log.info("用户文件已删除: userId={}, fileId={}", userId, fileId);
            return ApiResponse.success(true);
        } catch (Exception e) {
            log.error("删除用户文件失败: userId={}, fileId={}, error={}", userId, fileId, e.getMessage());
            return ApiResponse.error("删除文件失败: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}/stats")
    @Operation(summary = "获取用户存储统计", description = "获取用户的存储空间使用情况")
    public ApiResponse<StorageStats> getStorageStats(@PathVariable Long userId) {
        try {
            UserFileMapper.UserFileStats stats = userFileMapper.getStatsByUserId(userId);
            long totalSize = stats.getTotalSize();
            long fileCount = stats.getFileCount();
            double totalSizeMB = totalSize / (1024.0 * 1024.0);
            double usedPercent = (totalSizeMB / MAX_STORAGE_MB) * 100;

            StorageStats result = new StorageStats();
            result.setUserId(userId);
            result.setTotalSizeBytes(totalSize);
            result.setTotalSizeMB(Math.round(totalSizeMB * 100) / 100.0);
            result.setFileCount(fileCount);
            result.setMaxSizeMB(MAX_STORAGE_MB);
            result.setUsedPercent(Math.round(usedPercent * 100) / 100.0);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取存储统计失败: userId={}, error={}", userId, e.getMessage());
            return ApiResponse.error("获取存储统计失败: " + e.getMessage());
        }
    }

    private UserFileInfo convertToUserInfo(UserFile file) {
        UserFileInfo info = new UserFileInfo();
        info.setFileId(file.getFileId());
        info.setFileName(file.getFileName());
        info.setFileSize(file.getFileSize() != null ? file.getFileSize() : 0L);
        info.setFileType(file.getFileType() != null ? file.getFileType() : "unknown");
        info.setMimeType(file.getMimeType());
        info.setSessionId(file.getSessionId());
        info.setUploadTime(file.getUploadTime() != null ? file.getUploadTime().toString() : null);
        info.setLastAccessTime(file.getLastAccessTime() != null ? file.getLastAccessTime().toString() : null);
        info.setIsSensitive(file.getIsSensitive() != null ? file.getIsSensitive() : false);
        return info;
    }

    @Data
    public static class UserFileInfo {
        private String fileId;
        private String fileName;
        private Long fileSize;
        private String fileType;
        private String mimeType;
        private String sessionId;
        private String uploadTime;
        private String lastAccessTime;
        private Boolean isSensitive;
    }

    @Data
    public static class StorageStats {
        private Long userId;
        private Long totalSizeBytes;
        private Double totalSizeMB;
        private Long fileCount;
        private Integer maxSizeMB;
        private Double usedPercent;
    }
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
}
