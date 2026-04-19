package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.ToolWeight;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ToolWeightMapper {

    @Select("SELECT * FROM tool_weight WHERE id = #{id}")
    ToolWeight findById(Long id);

    @Select("SELECT * FROM tool_weight WHERE user_id = #{userId} AND tool_name = #{toolName}")
    ToolWeight findByUserAndTool(@Param("userId") Long userId, @Param("toolName") String toolName);

    @Select("SELECT * FROM tool_weight WHERE user_id = #{userId} ORDER BY weight DESC")
    List<ToolWeight> findByUserId(Long userId);

    @Select("SELECT * FROM tool_weight WHERE user_id = #{userId} ORDER BY weight DESC LIMIT #{limit}")
    List<ToolWeight> findTopTools(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT * FROM tool_weight WHERE tool_name = #{toolName} ORDER BY weight DESC LIMIT #{limit}")
    List<ToolWeight> findGlobalTopUsersForTool(@Param("toolName") String toolName, @Param("limit") int limit);

    @Insert("INSERT INTO tool_weight (user_id, tool_name, weight, success_count, failure_count, average_execution_time, created_time, updated_time) " +
            "VALUES (#{userId}, #{toolName}, #{weight}, #{successCount}, #{failureCount}, #{averageExecutionTime}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ToolWeight toolWeight);

    @Update("UPDATE tool_weight SET weight = #{weight}, success_count = #{successCount}, failure_count = #{failureCount}, " +
            "average_execution_time = #{averageExecutionTime}, updated_time = NOW() WHERE id = #{id}")
    int update(ToolWeight toolWeight);

    @Update("UPDATE tool_weight SET weight = weight + #{delta}, success_count = success_count + 1, updated_time = NOW() WHERE user_id = #{userId} AND tool_name = #{toolName}")
    int incrementSuccess(@Param("userId") Long userId, @Param("toolName") String toolName, @Param("delta") Double delta);

    @Update("UPDATE tool_weight SET weight = GREATEST(0, weight - #{delta}), failure_count = failure_count + 1, updated_time = NOW() WHERE user_id = #{userId} AND tool_name = #{toolName}")
    int incrementFailure(@Param("userId") Long userId, @Param("toolName") String toolName, @Param("delta") Double delta);

    @Delete("DELETE FROM tool_weight WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT AVG(weight) FROM tool_weight WHERE tool_name = #{toolName}")
    Double getGlobalAverageWeight(String toolName);
}
