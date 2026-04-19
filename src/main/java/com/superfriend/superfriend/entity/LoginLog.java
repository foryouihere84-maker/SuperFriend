package com.superfriend.superfriend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LoginLog {
    private Long id;
    private Long userId;
    private String username;
    private String loginType;
    private String loginIp;
    private String userAgent;
    private Integer status;
    private String errorMessage;
    private LocalDateTime createdTime;
}
