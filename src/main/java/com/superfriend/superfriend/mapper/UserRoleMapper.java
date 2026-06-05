package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.UserRole;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserRoleMapper {
    @Insert("INSERT INTO user_roles (user_id, role_id, created_time) VALUES (#{userId}, #{roleId}, NOW())")
    int insert(UserRole userRole);

    @Select("SELECT * FROM user_roles WHERE user_id = #{userId}")
    List<UserRole> findByUserId(Long userId);

    @Delete("DELETE FROM user_roles WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
