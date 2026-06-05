package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.ChatHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatHistoryMapper {

    @Select("SELECT * FROM chat_history WHERE session_id = #{sessionId}")
    ChatHistory findBySessionId(String sessionId);

    @Select("SELECT * FROM chat_history WHERE user_id = #{userId} ORDER BY updated_time DESC")
    List<ChatHistory> findByUserId(Long userId);

    @Select("SELECT * FROM chat_history WHERE user_id = #{userId} AND mode = #{mode} ORDER BY updated_time DESC")
    List<ChatHistory> findByUserIdAndMode(Long userId, String mode);

    @Insert("INSERT INTO chat_history (session_id, user_id, title, model, mode, message_count, created_time, updated_time) " +
            "VALUES (#{sessionId}, #{userId}, #{title}, #{model}, #{mode}, #{messageCount}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE title = #{title}, model = #{model}, mode = #{mode}, message_count = #{messageCount}, updated_time = NOW()")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int upsert(ChatHistory history);

    @Update("UPDATE chat_history SET message_count = #{messageCount}, updated_time = NOW() WHERE session_id = #{sessionId}")
    int updateMessageCount(@Param("sessionId") String sessionId, @Param("messageCount") int messageCount);

    @Delete("DELETE FROM chat_history WHERE session_id = #{sessionId}")
    int deleteBySessionId(String sessionId);

    @Delete("DELETE FROM chat_history WHERE id = #{id}")
    int deleteById(Long id);
}
