package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.AIProcessHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AIProcessHistoryMapper {
    @Select("SELECT * FROM ai_process_history WHERE id = #{id}")
    AIProcessHistory findById(Long id);

    @Select("SELECT * FROM ai_process_history WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<AIProcessHistory> findByUserId(Long userId);

    @Select("SELECT * FROM ai_process_history WHERE user_id = #{userId} AND process_type = #{processType} ORDER BY create_time DESC")
    List<AIProcessHistory> findByUserIdAndProcessType(Long userId, String processType);

    @Insert("INSERT INTO ai_process_history (name, create_time, related_notes, content, process_type, user_id, created_time, updated_time) " +
            "VALUES (#{name}, #{createTime}, #{relatedNotes}, #{content}, #{processType}, #{userId}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AIProcessHistory history);

    @Update("UPDATE ai_process_history SET content = #{content}, updated_time = NOW() WHERE id = #{id}")
    int updateContent(AIProcessHistory history);

    @Delete("DELETE FROM ai_process_history WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM ai_process_history WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
