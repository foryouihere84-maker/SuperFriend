package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.Skill;
import com.superfriend.superfriend.typehandler.MapTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SkillMapper {
    
    @Select("SELECT id, name, description, category, version, author, priority, scope, instructions, parameters, allowed_tools as allowedTools, tags, scripts_json as scriptsJson, resources_json as resourcesJson, timeout, status, is_selected as isSelected, skill_file_path as skillFilePath, execution_count as executionCount, success_rate as successRate, average_execution_time as averageExecutionTime, last_used_time as lastUsedTime, created_time as createdTime, updated_time as updatedTime FROM skills WHERE id = #{id}")
    Skill findById(Long id);

    @Select("SELECT id, name, description, category, version, author, priority, scope, instructions, parameters, allowed_tools as allowedTools, tags, scripts_json as scriptsJson, resources_json as resourcesJson, timeout, status, is_selected as isSelected, skill_file_path as skillFilePath, execution_count as executionCount, success_rate as successRate, average_execution_time as averageExecutionTime, last_used_time as lastUsedTime, created_time as createdTime, updated_time as updatedTime FROM skills WHERE name = #{name}")
    Skill findByName(String name);

    @Select("SELECT id, name, description, category, version, author, priority, scope, instructions, parameters, allowed_tools as allowedTools, tags, scripts_json as scriptsJson, resources_json as resourcesJson, timeout, status, is_selected as isSelected, skill_file_path as skillFilePath, execution_count as executionCount, success_rate as successRate, average_execution_time as averageExecutionTime, last_used_time as lastUsedTime, created_time as createdTime, updated_time as updatedTime FROM skills WHERE scope = #{scope}")
    List<Skill> findByScope(Integer scope);

    @Select("<script>" +
            "SELECT id, name, description, category, version, author, priority, scope, instructions, parameters, " +
            "allowed_tools as allowedTools, tags, scripts_json as scriptsJson, resources_json as resourcesJson, timeout, status, is_selected as isSelected, skill_file_path as skillFilePath, " +
            "execution_count as executionCount, success_rate as successRate, average_execution_time as averageExecutionTime, " +
            "last_used_time as lastUsedTime, created_time as createdTime, updated_time as updatedTime " +
            "FROM skills WHERE scope IN " +
            "<foreach item='scope' collection='scopes' open='(' separator=',' close=')'>" +
            "#{scope}" +
            "</foreach>" +
            "</script>")
    List<Skill> findByScopeIn(@Param("scopes") List<Integer> scopes);

    @Select("SELECT id, name, description, category, version, author, priority, scope, instructions, parameters, allowed_tools as allowedTools, tags, scripts_json as scriptsJson, resources_json as resourcesJson, timeout, status, is_selected as isSelected, skill_file_path as skillFilePath, execution_count as executionCount, success_rate as successRate, average_execution_time as averageExecutionTime, last_used_time as lastUsedTime, created_time as createdTime, updated_time as updatedTime FROM skills WHERE status = 1")
    List<Skill> findAllEnabled();

    @Select("SELECT id, name, description, category, version, author, priority, scope, instructions, parameters, allowed_tools as allowedTools, tags, scripts_json as scriptsJson, resources_json as resourcesJson, timeout, status, is_selected as isSelected, skill_file_path as skillFilePath, execution_count as executionCount, success_rate as successRate, average_execution_time as averageExecutionTime, last_used_time as lastUsedTime, created_time as createdTime, updated_time as updatedTime FROM skills WHERE category = #{category}")
    List<Skill> findByCategory(String category);

    @Select("SELECT id, name, description, category, version, author, priority, scope, instructions, parameters, allowed_tools as allowedTools, tags, scripts_json as scriptsJson, resources_json as resourcesJson, timeout, status, is_selected as isSelected, skill_file_path as skillFilePath, execution_count as executionCount, success_rate as successRate, average_execution_time as averageExecutionTime, last_used_time as lastUsedTime, created_time as createdTime, updated_time as updatedTime FROM skills WHERE name LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%')")
    List<Skill> search(String keyword);

    @Select("SELECT id, name, description, category, version, author, priority, scope, instructions, parameters, allowed_tools as allowedTools, tags, scripts_json as scriptsJson, resources_json as resourcesJson, timeout, status, is_selected as isSelected, skill_file_path as skillFilePath, execution_count as executionCount, success_rate as successRate, average_execution_time as averageExecutionTime, last_used_time as lastUsedTime, created_time as createdTime, updated_time as updatedTime FROM skills WHERE is_selected = 1")
    List<Skill> findSelected();
    
    @Insert("INSERT INTO skills (name, description, category, version, author, priority, scope, instructions, parameters, allowed_tools, tags, scripts_json, resources_json, timeout, status, is_selected, skill_file_path) " +
            "VALUES (#{name}, #{description}, #{category}, #{version}, #{author}, #{priority}, #{scope}, #{instructions}, #{parameters, typeHandler=com.superfriend.superfriend.typehandler.MapTypeHandler}, #{allowedTools, typeHandler=com.superfriend.superfriend.typehandler.ListStringTypeHandler}, #{tags, typeHandler=com.superfriend.superfriend.typehandler.ListStringTypeHandler}, #{scriptsJson}, #{resourcesJson}, #{timeout}, #{status}, #{isSelected}, #{skillFilePath})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Skill skill);

    @Update("UPDATE skills SET name = #{name}, description = #{description}, category = #{category}, version = #{version}, author = #{author}, " +
            "priority = #{priority}, instructions = #{instructions}, parameters = #{parameters, typeHandler=com.superfriend.superfriend.typehandler.MapTypeHandler}, allowed_tools = #{allowedTools, typeHandler=com.superfriend.superfriend.typehandler.ListStringTypeHandler}, " +
            "tags = #{tags, typeHandler=com.superfriend.superfriend.typehandler.ListStringTypeHandler}, scripts_json = #{scriptsJson}, resources_json = #{resourcesJson}, timeout = #{timeout}, status = #{status}, is_selected = #{isSelected}, skill_file_path = #{skillFilePath}, updated_time = NOW() WHERE id = #{id}")
    int update(Skill skill);
    
    @Update("UPDATE skills SET is_selected = #{isSelected} WHERE id = #{id}")
    int updateSelectionStatus(@Param("id") Long id, @Param("isSelected") Boolean isSelected);
    
    @Delete("DELETE FROM skills WHERE id = #{id}")
    int deleteById(Long id);
    
    @Update("UPDATE skills SET execution_count = execution_count + 1, success_rate = #{successRate}, " +
            "average_execution_time = #{averageExecutionTime}, last_used_time = NOW() WHERE id = #{id}")
    int updateStatistics(@Param("id") Long id, @Param("successRate") Double successRate, @Param("averageExecutionTime") Integer averageExecutionTime);
}
