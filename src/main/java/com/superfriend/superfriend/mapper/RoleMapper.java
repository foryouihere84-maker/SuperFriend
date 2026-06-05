package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.Role;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RoleMapper {
    @Select("SELECT * FROM roles WHERE role_code = #{roleCode}")
    Role findByRoleCode(String roleCode);

    @Select("SELECT * FROM roles WHERE id = #{id}")
    Role findById(Long id);

    @Select("SELECT * FROM roles")
    List<Role> findAll();
}
