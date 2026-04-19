package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.AgentPermissionPolicy;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AgentPermissionPolicyMapper {

    @Select("SELECT * FROM agent_permission_policy WHERE id = #{id}")
    AgentPermissionPolicy findById(Long id);

    @Select("SELECT * FROM agent_permission_policy WHERE user_id = #{userId} ORDER BY created_time DESC")
    List<AgentPermissionPolicy> findByUserId(Long userId);

    @Select("SELECT * FROM agent_permission_policy WHERE user_id IN (0, #{userId}) AND tool_name = #{toolName} ORDER BY user_id DESC, created_time DESC")
    List<AgentPermissionPolicy> findByToolName(@Param("userId") Long userId, @Param("toolName") String toolName);

    @Select("SELECT * FROM agent_permission_policy WHERE user_id IN (0, #{userId}) AND operation_type = #{operationType} ORDER BY user_id DESC, created_time DESC")
    List<AgentPermissionPolicy> findByOperationType(@Param("userId") Long userId, @Param("operationType") String operationType);

    @Select("SELECT * FROM agent_permission_policy WHERE user_id IN (0, #{userId}) " +
            "AND (tool_name IS NULL OR tool_name = #{toolName}) " +
            "AND (operation_type IS NULL OR operation_type = #{operationType}) " +
            "ORDER BY user_id DESC, tool_name DESC, operation_type DESC, created_time DESC")
    List<AgentPermissionPolicy> findMatchingPolicies(@Param("userId") Long userId,
                                                      @Param("toolName") String toolName,
                                                      @Param("operationType") String operationType);

    @Insert("INSERT INTO agent_permission_policy (user_id, tool_name, operation_type, resource_pattern, permission_level, description) " +
            "VALUES (#{userId}, #{toolName}, #{operationType}, #{resourcePattern}, #{permissionLevel}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentPermissionPolicy policy);

    @Update("UPDATE agent_permission_policy SET tool_name = #{toolName}, operation_type = #{operationType}, " +
            "resource_pattern = #{resourcePattern}, permission_level = #{permissionLevel}, description = #{description} " +
            "WHERE id = #{id}")
    int update(AgentPermissionPolicy policy);

    @Delete("DELETE FROM agent_permission_policy WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM agent_permission_policy WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
