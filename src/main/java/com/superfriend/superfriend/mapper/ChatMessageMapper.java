package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.ChatMessageRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY created_time ASC")
    List<ChatMessageRecord> findBySessionId(String sessionId);

    @Select("SELECT * FROM chat_message WHERE history_id = #{historyId} ORDER BY created_time ASC")
    List<ChatMessageRecord> findByHistoryId(Long historyId);

    @Insert("INSERT INTO chat_message (history_id, session_id, role, content, created_time) " +
            "VALUES (#{historyId}, #{sessionId}, #{role}, #{content}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessageRecord message);

    @Delete("DELETE FROM chat_message WHERE session_id = #{sessionId}")
    int deleteBySessionId(String sessionId);

    @Delete("DELETE FROM chat_message WHERE history_id = #{historyId}")
    int deleteByHistoryId(Long historyId);
}
