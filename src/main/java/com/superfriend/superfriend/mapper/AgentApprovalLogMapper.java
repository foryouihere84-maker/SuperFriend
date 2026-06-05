package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.AgentApprovalLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AgentApprovalLogMapper {

    @Select("SELECT * FROM agent_approval_log WHERE id = #{id}")
    AgentApprovalLog findById(Long id);

    @Select("SELECT * FROM agent_approval_log WHERE session_id = #{sessionId} ORDER BY created_time DESC")
    List<AgentApprovalLog> findBySessionId(String sessionId);

    @Select("SELECT * FROM agent_approval_log WHERE user_id = #{userId} ORDER BY created_time DESC LIMIT #{limit}")
    List<AgentApprovalLog> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT * FROM agent_approval_log WHERE decision = #{decision} ORDER BY created_time DESC LIMIT #{limit}")
    List<AgentApprovalLog> findByDecision(@Param("decision") String decision, @Param("limit") int limit);

    @Insert("INSERT INTO agent_approval_log (user_id, session_id, tool_name, operation, arguments, permission_level, decision, decided_by, reason, response_time_ms) " +
            "VALUES (#{userId}, #{sessionId}, #{toolName}, #{operation}, #{arguments}, #{permissionLevel}, #{decision}, #{decidedBy}, #{reason}, #{responseTimeMs})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentApprovalLog log);

    @Delete("DELETE FROM agent_approval_log WHERE created_time < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int cleanOldLogs(int days);
}
