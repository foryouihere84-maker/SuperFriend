package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.AgentTaskPlan;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AgentTaskPlanMapper {

    @Select("SELECT * FROM agent_task_plan WHERE plan_id = #{planId}")
    AgentTaskPlan findByPlanId(String planId);

    @Select("SELECT * FROM agent_task_plan WHERE session_id = #{sessionId} ORDER BY created_time DESC")
    List<AgentTaskPlan> findBySessionId(String sessionId);

    @Select("SELECT * FROM agent_task_plan WHERE user_id = #{userId} ORDER BY created_time DESC LIMIT #{limit}")
    List<AgentTaskPlan> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT * FROM agent_task_plan WHERE status = #{status} ORDER BY created_time DESC")
    List<AgentTaskPlan> findByStatus(String status);

    @Insert("INSERT INTO agent_task_plan (plan_id, session_id, user_id, original_request, plan_summary, status, total_steps, completed_steps, created_time, started_time, completed_time) " +
            "VALUES (#{planId}, #{sessionId}, #{userId}, #{originalRequest}, #{planSummary}, #{status}, #{totalSteps}, #{completedSteps}, NOW(), #{startedTime}, #{completedTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentTaskPlan plan);

    @Update("UPDATE agent_task_plan SET plan_summary = #{planSummary}, status = #{status}, total_steps = #{totalSteps}, completed_steps = #{completedSteps}, started_time = #{startedTime}, completed_time = #{completedTime} WHERE plan_id = #{planId}")
    int updateByPlanId(AgentTaskPlan plan);

    @Update("UPDATE agent_task_plan SET status = #{status}, completed_steps = #{completedSteps}, completed_time = #{completedTime} WHERE plan_id = #{planId}")
    int updateStatus(@Param("planId") String planId, @Param("status") String status,
                     @Param("completedSteps") int completedSteps, @Param("completedTime") Object completedTime);

    @Delete("DELETE FROM agent_task_plan WHERE plan_id = #{planId}")
    int deleteByPlanId(String planId);
}
