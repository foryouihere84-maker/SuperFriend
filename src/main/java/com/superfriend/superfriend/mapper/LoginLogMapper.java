package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.LoginLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LoginLogMapper {
    @Insert("INSERT INTO login_logs (user_id, username, login_type, login_ip, user_agent, status, error_message, created_time) " +
            "VALUES (#{userId}, #{username}, #{loginType}, #{loginIp}, #{userAgent}, #{status}, #{errorMessage}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LoginLog loginLog);

    @Select("SELECT * FROM login_logs WHERE user_id = #{userId} ORDER BY created_time DESC LIMIT 10")
    List<LoginLog> findRecentByUserId(Long userId);
}
