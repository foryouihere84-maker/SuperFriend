package com.superfriend.superfriend.dto;

import lombok.Data;

@Data
public class AuthData {
    private UserDTO user;
    private String token;
}
