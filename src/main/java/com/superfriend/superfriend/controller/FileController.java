package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
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
}
