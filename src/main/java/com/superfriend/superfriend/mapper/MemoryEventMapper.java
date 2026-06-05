package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.MemoryEvent;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemoryEventMapper {

    @Select("SELECT * FROM memory_event WHERE id = #{id}")
    MemoryEvent findById(Long id);

    @Select("SELECT * FROM memory_event WHERE memory_id = #{memoryId} ORDER BY event_time DESC")
    List<MemoryEvent> findByMemoryId(Long memoryId);

    @Select("SELECT * FROM memory_event WHERE user_id = #{userId} ORDER BY event_time DESC LIMIT #{limit}")
    List<MemoryEvent> findByUserId(Long userId, int limit);

    @Select("SELECT * FROM memory_event WHERE user_id = #{userId} AND event_time >= #{startTime} AND event_time < #{endTime} ORDER BY event_time DESC")
    List<MemoryEvent> findByTimeRange(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    @Select("SELECT * FROM memory_event WHERE user_id = #{userId} AND event_type = #{eventType} ORDER BY event_time DESC")
    List<MemoryEvent> findByUserIdAndType(Long userId, String eventType);

    @Insert("INSERT INTO memory_event (user_id, memory_id, event_type, event_time, event_data, session_id) " +
            "VALUES (#{userId}, #{memoryId}, #{eventType}, #{eventTime}, #{eventData}, #{sessionId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MemoryEvent event);

    @Delete("DELETE FROM memory_event WHERE memory_id = #{memoryId}")
    int deleteByMemoryId(Long memoryId);

    @Delete("DELETE FROM memory_event WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}