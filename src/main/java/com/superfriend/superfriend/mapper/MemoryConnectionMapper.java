package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.MemoryConnection;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MemoryConnectionMapper {

    @Select("SELECT * FROM memory_connection WHERE id = #{id}")
    MemoryConnection findById(Long id);

    @Select("SELECT * FROM memory_connection WHERE source_memory_id = #{memoryId}")
    List<MemoryConnection> findBySourceMemoryId(Long memoryId);

    @Select("SELECT * FROM memory_connection WHERE target_memory_id = #{memoryId}")
    List<MemoryConnection> findByTargetMemoryId(Long memoryId);

    @Select("SELECT * FROM memory_connection WHERE user_id = #{userId}")
    List<MemoryConnection> findByUserId(Long userId);

    @Select("SELECT * FROM memory_connection WHERE source_memory_id = #{sourceId} AND target_memory_id = #{targetId}")
    MemoryConnection findBySourceAndTarget(Long sourceId, Long targetId);

    @Insert("INSERT INTO memory_connection (user_id, source_memory_id, target_memory_id, connection_type, strength) " +
            "VALUES (#{userId}, #{sourceMemoryId}, #{targetMemoryId}, #{connectionType}, #{strength}) " +
            "ON DUPLICATE KEY UPDATE connection_type = #{connectionType}, strength = #{strength}")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOrUpdate(MemoryConnection connection);

    @Update("UPDATE memory_connection SET strength = #{strength} WHERE id = #{id}")
    int updateStrength(Long id, java.math.BigDecimal strength);

    @Delete("DELETE FROM memory_connection WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM memory_connection WHERE source_memory_id = #{memoryId} OR target_memory_id = #{memoryId}")
    int deleteByMemoryId(Long memoryId);

    @Delete("DELETE FROM memory_connection WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}