package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.AgentTaskStep;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AgentTaskStepMapper {

    @Select("SELECT * FROM agent_task_step WHERE plan_id = #{planId} ORDER BY step_number ASC")
    List<AgentTaskStep> findByPlanId(String planId);

    @Select("SELECT * FROM agent_task_step WHERE step_id = #{stepId}")
    AgentTaskStep findByStepId(String stepId);

    @Select("SELECT * FROM agent_task_step WHERE plan_id = #{planId} AND status = #{status} ORDER BY step_number ASC")
    List<AgentTaskStep> findByPlanIdAndStatus(@Param("planId") String planId, @Param("status") String status);

    @Insert("INSERT INTO agent_task_step (step_id, plan_id, step_number, description, status, tool_name, tool_arguments, result, error, execution_time_ms, created_time, completed_time) " +
            "VALUES (#{stepId}, #{planId}, #{stepNumber}, #{description}, #{status}, #{toolName}, #{toolArguments}, #{result}, #{error}, #{executionTimeMs}, NOW(), #{completedTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentTaskStep step);

    @Update("UPDATE agent_task_step SET status = #{status}, result = #{result}, error = #{error}, execution_time_ms = #{executionTimeMs}, completed_time = NOW() WHERE step_id = #{stepId}")
    int updateResult(@Param("stepId") String stepId, @Param("status") String status,
                     @Param("result") String result, @Param("error") String error,
                     @Param("executionTimeMs") Integer executionTimeMs);

    @Update("UPDATE agent_task_step SET status = #{status} WHERE step_id = #{stepId}")
    int updateStatus(@Param("stepId") String stepId, @Param("status") String status);

    @Update("UPDATE agent_task_step SET description = #{description}, tool_name = #{toolName}, status = #{status} WHERE step_id = #{stepId}")
    int updateByStepId(AgentTaskStep step);

    @Delete("DELETE FROM agent_task_step WHERE plan_id = #{planId}")
    int deleteByPlanId(String planId);
}
