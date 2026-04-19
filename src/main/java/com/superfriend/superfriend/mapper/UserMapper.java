package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    @Select("SELECT * FROM users WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT * FROM users WHERE email = #{email}")
    User findByEmail(String email);

    @Select("SELECT * FROM users WHERE id = #{id}")
    User findById(Long id);

    @Insert("INSERT INTO users (username, email, password, display_name, status, created_time, updated_time) " +
            "VALUES (#{username}, #{email}, #{password}, #{displayName}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE users SET last_login_time = NOW(), last_login_ip = #{lastLoginIp}, updated_time = NOW() WHERE id = #{id}")
    int updateLoginInfo(User user);

    @Update("UPDATE users SET display_name = #{displayName}, avatar_url = #{avatarUrl}, updated_time = NOW() WHERE id = #{id}")
    int update(User user);

    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(Long id);
}
