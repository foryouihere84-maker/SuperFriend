package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.SkillScript;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SkillScriptMapper {
    
    @Select("SELECT id, skill_id as skillId, script_type as scriptType, script_name as scriptName, script_content as scriptContent, is_main as isMain, execution_order as executionOrder, created_time as createdTime, updated_time as updatedTime FROM skill_scripts WHERE id = #{id}")
    SkillScript findById(Long id);
    
    @Select("SELECT id, skill_id as skillId, script_type as scriptType, script_name as scriptName, script_content as scriptContent, is_main as isMain, execution_order as executionOrder, created_time as createdTime, updated_time as updatedTime FROM skill_scripts WHERE skill_id = #{skillId} ORDER BY execution_order ASC")
    List<SkillScript> findBySkillId(Long skillId);
    
    @Select("SELECT id, skill_id as skillId, script_type as scriptType, script_name as scriptName, script_content as scriptContent, is_main as isMain, execution_order as executionOrder, created_time as createdTime, updated_time as updatedTime FROM skill_scripts WHERE skill_id = #{skillId} AND script_type = #{scriptType}")
    SkillScript findBySkillIdAndType(@Param("skillId") Long skillId, @Param("scriptType") String scriptType);
    
    @Select("SELECT id, skill_id as skillId, script_type as scriptType, script_name as scriptName, script_content as scriptContent, is_main as isMain, execution_order as executionOrder, created_time as createdTime, updated_time as updatedTime FROM skill_scripts WHERE skill_id = #{skillId} AND is_main = 1")
    SkillScript findMainScript(Long skillId);
    
    @Insert("INSERT INTO skill_scripts (skill_id, script_type, script_name, script_content, is_main, execution_order) " +
            "VALUES (#{skillId}, #{scriptType}, #{scriptName}, #{scriptContent}, #{isMain}, #{executionOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SkillScript script);
    
    @Update("UPDATE skill_scripts SET script_type = #{scriptType}, script_name = #{scriptName}, script_content = #{scriptContent}, is_main = #{isMain}, execution_order = #{executionOrder}, updated_time = NOW() WHERE id = #{id}")
    int update(SkillScript script);
    
    @Delete("DELETE FROM skill_scripts WHERE id = #{id}")
    int deleteById(Long id);
    
    @Delete("DELETE FROM skill_scripts WHERE skill_id = #{skillId}")
    int deleteBySkillId(Long skillId);
}
