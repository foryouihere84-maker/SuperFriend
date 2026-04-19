package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.ToolTransition;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ToolTransitionMapper {

    @Select("SELECT * FROM tool_transition WHERE id = #{id}")
    ToolTransition findById(Long id);

    @Select("SELECT * FROM tool_transition WHERE user_id = #{userId} AND from_tool = #{fromTool} AND to_tool = #{toTool}")
    ToolTransition findByTransition(@Param("userId") Long userId, @Param("fromTool") String fromTool, @Param("toTool") String toTool);

    @Select("SELECT * FROM tool_transition WHERE user_id = #{userId} AND from_tool = #{fromTool} ORDER BY weight DESC")
    List<ToolTransition> findNextTools(@Param("userId") Long userId, @Param("fromTool") String fromTool);

    @Select("SELECT * FROM tool_transition WHERE user_id = #{userId} AND from_tool = #{fromTool} ORDER BY weight DESC LIMIT #{limit}")
    List<ToolTransition> findTopNextTools(@Param("userId") Long userId, @Param("fromTool") String fromTool, @Param("limit") int limit);

    @Select("SELECT * FROM tool_transition WHERE user_id = #{userId} ORDER BY weight DESC")
    List<ToolTransition> findByUserId(Long userId);

    @Insert("INSERT INTO tool_transition (user_id, from_tool, to_tool, weight, transition_count, created_time, updated_time) " +
            "VALUES (#{userId}, #{fromTool}, #{toTool}, #{weight}, 1, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ToolTransition transition);

    @Update("UPDATE tool_transition SET weight = #{weight}, transition_count = transition_count + 1, updated_time = NOW() WHERE id = #{id}")
    int update(ToolTransition transition);

    @Update("UPDATE tool_transition SET weight = weight + #{delta}, transition_count = transition_count + 1, updated_time = NOW() " +
            "WHERE user_id = #{userId} AND from_tool = #{fromTool} AND to_tool = #{toTool}")
    int incrementWeight(@Param("userId") Long userId, @Param("fromTool") String fromTool, @Param("toTool") String toTool, @Param("delta") Double delta);

    @Delete("DELETE FROM tool_transition WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT AVG(weight) FROM tool_transition WHERE from_tool = #{fromTool} AND to_tool = #{toTool}")
    Double getGlobalAverageWeight(@Param("fromTool") String fromTool, @Param("toTool") String toTool);
}
