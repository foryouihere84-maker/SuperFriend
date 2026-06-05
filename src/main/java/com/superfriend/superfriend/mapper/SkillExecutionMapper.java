package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.SkillExecution;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SkillExecutionMapper {
<<<<<<< HEAD

    @Select("SELECT * FROM skill_executions WHERE id = #{id}")
    SkillExecution findById(Long id);

=======
    
    @Select("SELECT * FROM skill_executions WHERE id = #{id}")
    SkillExecution findById(Long id);
    
    @Select("SELECT * FROM skill_executions WHERE skill_id = #{skillId}")
    List<SkillExecution> findBySkillId(Long skillId);
    
    @Select("SELECT * FROM skill_executions WHERE user_id = #{userId}")
    List<SkillExecution> findByUserId(Long userId);
    
    @Select("SELECT * FROM skill_executions WHERE session_id = #{sessionId}")
    List<SkillExecution> findBySessionId(String sessionId);
    
    @Select("SELECT * FROM skill_executions WHERE success = #{success}")
    List<SkillExecution> findBySuccess(Integer success);
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    @Insert("INSERT INTO skill_executions (skill_id, session_id, user_id, user_request, result, success, execution_time, error_message) " +
            "VALUES (#{skillId}, #{sessionId}, #{userId}, #{userRequest}, #{result}, #{success}, #{executionTime}, #{errorMessage})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SkillExecution execution);
<<<<<<< HEAD

    @Update("UPDATE skill_executions SET result = #{result}, success = #{success}, execution_time = #{executionTime}, error_message = #{errorMessage} WHERE id = #{id}")
    int update(SkillExecution execution);

    @Delete("DELETE FROM skill_executions WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM skill_executions WHERE skill_id = #{skillId}")
    int deleteBySkillId(Long skillId);

=======
    
    @Update("UPDATE skill_executions SET result = #{result}, success = #{success}, execution_time = #{executionTime}, error_message = #{errorMessage} WHERE id = #{id}")
    int update(SkillExecution execution);
    
    @Delete("DELETE FROM skill_executions WHERE id = #{id}")
    int deleteById(Long id);
    
    @Delete("DELETE FROM skill_executions WHERE skill_id = #{skillId}")
    int deleteBySkillId(Long skillId);
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    @Delete("DELETE FROM skill_executions WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
