package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChatCompression {
    private Long id;
    private String sessionId;
    private Integer originalMessageCount;
    private Integer compressedMessageCount;
    private String summary;
    private String preservedToolResults;
    private String compressionLevel;
    private Long originalTokens;
    private Long compressedTokens;
    private BigDecimal compressionRatio;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;
}
