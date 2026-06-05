package com.superfriend.superfriend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserFile {
    private Long id;
    private Long userId;
    private String sessionId;

    private String fileId;
    private String fileName;
    private String storedName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private String fileType;

    private String status;
    private Boolean isSensitive;

    private LocalDateTime uploadTime;
    private LocalDateTime lastAccessTime;
    private LocalDateTime expireTime;

    private String metadata;
}
