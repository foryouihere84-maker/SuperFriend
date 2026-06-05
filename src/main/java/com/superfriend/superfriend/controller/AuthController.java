package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.dto.AuthData;
import com.superfriend.superfriend.dto.LoginRequest;
import com.superfriend.superfriend.dto.RegisterRequest;
import com.superfriend.superfriend.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthData> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        log.info("用户登录请求: {}", request.getUsername());
        ApiResponse<AuthData> response = authService.login(request, httpRequest);
        log.info("用户登录结果: {}", response.getSuccess());
        return response;
    }

    @PostMapping("/regist")
    public ApiResponse<AuthData> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        log.info("用户注册请求: {}", request.getUsername());
        ApiResponse<AuthData> response = authService.register(request, httpRequest);
        log.info("用户注册结果: {}", response.getSuccess());
        return response;
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(HttpServletRequest httpRequest) {
        log.info("用户退出登录");
        return ApiResponse.success("退出登录成功");
    }
}
