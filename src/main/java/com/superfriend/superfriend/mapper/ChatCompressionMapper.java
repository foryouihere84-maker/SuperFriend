package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.ChatCompression;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatCompressionMapper {

    @Select("SELECT * FROM chat_compression WHERE session_id = #{sessionId} ORDER BY created_time DESC")
    List<ChatCompression> findBySessionId(String sessionId);

    @Select("SELECT * FROM chat_compression WHERE session_id = #{sessionId} ORDER BY created_time DESC LIMIT 1")
    ChatCompression findLatestBySessionId(String sessionId);

    @Insert("INSERT INTO chat_compression (session_id, original_message_count, compressed_message_count, " +
            "summary, preserved_tool_results, compression_level, original_tokens, compressed_tokens, " +
            "compression_ratio, created_time) " +
            "VALUES (#{sessionId}, #{originalMessageCount}, #{compressedMessageCount}, " +
            "#{summary}, #{preservedToolResults}, #{compressionLevel}, #{originalTokens}, #{compressedTokens}, " +
            "#{compressionRatio}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatCompression compression);

    @Delete("DELETE FROM chat_compression WHERE session_id = #{sessionId}")
    int deleteBySessionId(String sessionId);
}
