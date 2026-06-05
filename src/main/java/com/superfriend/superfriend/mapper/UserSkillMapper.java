package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.UserSkill;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserSkillMapper {
    
    @Select("SELECT id, user_id as userId, skill_id as skillId, is_enabled as isEnabled, is_selected as isSelected, custom_priority as customPriority, custom_tags as customTags, created_time as createdTime, updated_time as updatedTime FROM user_skills WHERE id = #{id}")
    UserSkill findById(Long id);
    
    @Select("SELECT id, user_id as userId, skill_id as skillId, is_enabled as isEnabled, is_selected as isSelected, custom_priority as customPriority, custom_tags as customTags, created_time as createdTime, updated_time as updatedTime FROM user_skills WHERE user_id = #{userId} AND skill_id = #{skillId}")
    UserSkill findByUserIdAndSkillId(@Param("userId") Long userId, @Param("skillId") Long skillId);
    
    @Select("SELECT id, user_id as userId, skill_id as skillId, is_enabled as isEnabled, is_selected as isSelected, custom_priority as customPriority, custom_tags as customTags, created_time as createdTime, updated_time as updatedTime FROM user_skills WHERE user_id = #{userId}")
    List<UserSkill> findByUserId(Long userId);
    
    @Select("SELECT id, user_id as userId, skill_id as skillId, is_enabled as isEnabled, is_selected as isSelected, custom_priority as customPriority, custom_tags as customTags, created_time as createdTime, updated_time as updatedTime FROM user_skills WHERE skill_id = #{skillId}")
    List<UserSkill> findBySkillId(Long skillId);
    
    @Select("SELECT id, user_id as userId, skill_id as skillId, is_enabled as isEnabled, is_selected as isSelected, custom_priority as customPriority, custom_tags as customTags, created_time as createdTime, updated_time as updatedTime FROM user_skills WHERE user_id = #{userId} AND is_enabled = 1")
    List<UserSkill> findEnabledByUserId(Long userId);
    
    @Select("SELECT id, user_id as userId, skill_id as skillId, is_enabled as isEnabled, is_selected as isSelected, custom_priority as customPriority, custom_tags as customTags, created_time as createdTime, updated_time as updatedTime FROM user_skills WHERE user_id = #{userId} AND is_selected = 1")
    List<UserSkill> findSelectedByUserId(Long userId);
    
    @Insert("INSERT INTO user_skills (user_id, skill_id, is_enabled, is_selected, custom_priority, custom_tags) " +
            "VALUES (#{userId}, #{skillId}, #{isEnabled}, #{isSelected}, #{customPriority}, #{customTags})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserSkill userSkill);
    
    @Update("UPDATE user_skills SET is_enabled = #{isEnabled}, is_selected = #{isSelected}, custom_priority = #{customPriority}, custom_tags = #{customTags}, updated_time = NOW() WHERE id = #{id}")
    int update(UserSkill userSkill);
    
    @Delete("DELETE FROM user_skills WHERE id = #{id}")
    int deleteById(Long id);
    
    @Delete("DELETE FROM user_skills WHERE user_id = #{userId} AND skill_id = #{skillId}")
    int deleteByUserIdAndSkillId(@Param("userId") Long userId, @Param("skillId") Long skillId);
    
    @Delete("DELETE FROM user_skills WHERE skill_id = #{skillId}")
    int deleteBySkillId(Long skillId);
    
    @Update("UPDATE user_skills SET is_enabled = 0, updated_time = NOW() WHERE user_id = #{userId} AND skill_id = #{skillId}")
    int disableSkill(@Param("userId") Long userId, @Param("skillId") Long skillId);
    
    @Update("UPDATE user_skills SET is_enabled = 1, updated_time = NOW() WHERE user_id = #{userId} AND skill_id = #{skillId}")
    int enableSkill(@Param("userId") Long userId, @Param("skillId") Long skillId);
    
    @Update("UPDATE user_skills SET is_selected = #{isSelected}, updated_time = NOW() WHERE user_id = #{userId} AND skill_id = #{skillId}")
    int updateSelectionStatus(@Param("userId") Long userId, @Param("skillId") Long skillId, @Param("isSelected") Boolean isSelected);
}
