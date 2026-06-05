package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.AgentTrustBoundary;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AgentTrustBoundaryMapper {

    @Select("SELECT * FROM agent_trust_boundary WHERE id = #{id}")
    AgentTrustBoundary findById(Long id);

    @Select("SELECT * FROM agent_trust_boundary WHERE user_id = #{userId} ORDER BY created_time DESC")
    List<AgentTrustBoundary> findByUserId(Long userId);

    @Select("SELECT * FROM agent_trust_boundary WHERE user_id = #{userId} AND boundary_type = #{boundaryType} ORDER BY created_time DESC")
    List<AgentTrustBoundary> findByUserIdAndType(@Param("userId") Long userId, @Param("boundaryType") String boundaryType);

    @Insert("INSERT INTO agent_trust_boundary (user_id, boundary_name, boundary_type, pattern, trust_level) " +
            "VALUES (#{userId}, #{boundaryName}, #{boundaryType}, #{pattern}, #{trustLevel})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentTrustBoundary boundary);

    @Update("UPDATE agent_trust_boundary SET boundary_name = #{boundaryName}, boundary_type = #{boundaryType}, " +
            "pattern = #{pattern}, trust_level = #{trustLevel} WHERE id = #{id}")
    int update(AgentTrustBoundary boundary);

    @Delete("DELETE FROM agent_trust_boundary WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM agent_trust_boundary WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
