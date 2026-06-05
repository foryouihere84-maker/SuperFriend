package com.superfriend.superfriend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PasswordReset {
    private Long id;
    private Long userId;
    private String token;
    private LocalDateTime expiresAt;
    private Integer used;
    private LocalDateTime createdTime;
}
