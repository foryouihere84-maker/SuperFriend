package com.superfriend.superfriend.dto;

import lombok.Data;

@Data
public class UserDTO {
    private String id;
    private String username;
    private String email;
    private String displayName;
    private String avatar;
    private Long createdTime;
    private Long lastLoginTime;
}
