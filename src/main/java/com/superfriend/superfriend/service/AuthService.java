package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.ApiResponse;
import com.superfriend.superfriend.dto.AuthData;
import com.superfriend.superfriend.dto.LoginRequest;
import com.superfriend.superfriend.dto.RegisterRequest;
import com.superfriend.superfriend.dto.UserDTO;
import com.superfriend.superfriend.entity.LoginLog;
import com.superfriend.superfriend.entity.Role;
import com.superfriend.superfriend.entity.User;
import com.superfriend.superfriend.entity.UserRole;
import com.superfriend.superfriend.mapper.LoginLogMapper;
import com.superfriend.superfriend.mapper.RoleMapper;
import com.superfriend.superfriend.mapper.UserMapper;
import com.superfriend.superfriend.mapper.UserRoleMapper;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private JwtService jwtService;

    public ApiResponse<AuthData> login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            User user = userMapper.findByUsername(request.getUsername());
            
            if (user == null) {
                logLoginRecord(null, request.getUsername(), "password", httpRequest, false, "用户不存在");
                return ApiResponse.error("用户名或密码错误");
            }

            if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
                logLoginRecord(user.getId(), request.getUsername(), "password", httpRequest, false, "密码错误");
                return ApiResponse.error("用户名或密码错误");
            }

            if (user.getStatus() == 0) {
                logLoginRecord(user.getId(), request.getUsername(), "password", httpRequest, false, "账号已被禁用");
                return ApiResponse.error("账号已被禁用");
            }

            user.setLastLoginTime(LocalDateTime.now());
            userMapper.updateLoginInfo(user);

            String token = jwtService.generateToken(user.getId(), user.getUsername());

            logLoginRecord(user.getId(), request.getUsername(), "password", httpRequest, true, null);

            UserDTO userDTO = convertToUserDTO(user);
            AuthData authData = new AuthData();
            authData.setUser(userDTO);
            authData.setToken(token);

            return ApiResponse.success("登录成功", authData);
        } catch (Exception e) {
            log.error("登录失败", e);
            return ApiResponse.error("登录失败，请稍后重试");
        }
    }

    public ApiResponse<AuthData> register(RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            log.info("注册请求参数: username={}, email={}", request.getUsername(), request.getEmail());
            
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                log.warn("密码不一致");
                return ApiResponse.error("两次输入的密码不一致");
            }

            if (request.getPassword().length() < 6) {
                log.warn("密码长度不足");
                return ApiResponse.error("密码长度至少为6位");
            }

            if (!isValidEmail(request.getEmail())) {
                log.warn("邮箱格式无效: {}", request.getEmail());
                return ApiResponse.error("请输入有效的邮箱地址");
            }

            User existUser = userMapper.findByUsername(request.getUsername());
            if (existUser != null) {
                log.warn("用户名已存在: {}", request.getUsername());
                return ApiResponse.error("用户名已存在");
            }

            User existEmail = userMapper.findByEmail(request.getEmail());
            if (existEmail != null) {
                log.warn("邮箱已被注册: {}", request.getEmail());
                return ApiResponse.error("邮箱已被注册");
            }

            User user = new User();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
            user.setDisplayName(request.getUsername());
            user.setStatus(1);
            user.setCreatedTime(LocalDateTime.now());
            user.setUpdatedTime(LocalDateTime.now());

            log.info("准备插入用户: {}", user.getUsername());
            userMapper.insert(user);
            log.info("用户插入成功，ID: {}", user.getId());

            Role userRole = roleMapper.findByRoleCode("USER");
            if (userRole != null) {
                UserRole userRoleMapping = new UserRole();
                userRoleMapping.setUserId(user.getId());
                userRoleMapping.setRoleId(userRole.getId());
                userRoleMapping.setCreatedTime(LocalDateTime.now());
                userRoleMapper.insert(userRoleMapping);
                log.info("用户角色分配成功");
            } else {
                log.warn("未找到USER角色");
            }

            logLoginRecord(user.getId(), request.getUsername(), "register", httpRequest, true, null);

            String token = jwtService.generateToken(user.getId(), user.getUsername());

            UserDTO userDTO = convertToUserDTO(user);
            AuthData authData = new AuthData();
            authData.setUser(userDTO);
            authData.setToken(token);

            log.info("注册成功: {}", user.getUsername());
            return ApiResponse.success("注册成功", authData);
        } catch (Exception e) {
            log.error("注册失败，用户名: {}, 错误: {}", request.getUsername(), e.getMessage(), e);
            return ApiResponse.error("注册失败，请稍后重试");
        }
    }

    private void logLoginRecord(Long userId, String username, String loginType, 
                              HttpServletRequest httpRequest, boolean status, String errorMessage) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(username);
            loginLog.setLoginType(loginType);
            loginLog.setStatus(status ? 1 : 0);
            loginLog.setErrorMessage(errorMessage);
            loginLog.setCreatedTime(LocalDateTime.now());
            
            if (httpRequest != null) {
                loginLog.setLoginIp(getClientIp(httpRequest));
                loginLog.setUserAgent(httpRequest.getHeader("User-Agent"));
            }
            
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.error("记录登录日志失败", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        return ip;
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return email.matches(emailRegex);
    }

    private UserDTO convertToUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(String.valueOf(user.getId()));
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setDisplayName(user.getDisplayName());
        dto.setAvatar(user.getAvatarUrl());
        dto.setCreatedTime(user.getCreatedTime() != null ? user.getCreatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null);
        dto.setLastLoginTime(user.getLastLoginTime() != null ? user.getLastLoginTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null);
        return dto;
    }
}
