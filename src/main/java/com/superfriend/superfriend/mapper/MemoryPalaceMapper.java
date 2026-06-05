package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.MemoryPalace;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemoryPalaceMapper {

    // ==================== 查询 ====================

    @Select("SELECT * FROM memory_palace WHERE id = #{id}")
    MemoryPalace findById(Long id);

    @Select("SELECT * FROM memory_palace WHERE user_id = #{userId} AND status = 'ACTIVE' ORDER BY effective_score DESC, memory_time DESC")
    List<MemoryPalace> findByUserId(Long userId);

    @Select("SELECT * FROM memory_palace WHERE user_id = #{userId} AND memory_type = #{memoryType} AND status = 'ACTIVE' ORDER BY effective_score DESC")
    List<MemoryPalace> findByUserIdAndType(Long userId, String memoryType);

    @Select("SELECT * FROM memory_palace WHERE user_id = #{userId} AND category = #{category} AND status = 'ACTIVE' ORDER BY effective_score DESC")
    List<MemoryPalace> findByUserIdAndCategory(Long userId, String category);

    @Select("SELECT * FROM memory_palace WHERE user_id = #{userId} AND session_id = #{sessionId} ORDER BY memory_time DESC")
    List<MemoryPalace> findBySessionId(Long userId, String sessionId);

    @Select("SELECT * FROM memory_palace WHERE user_id = #{userId} AND memory_time >= #{startTime} AND memory_time < #{endTime} AND status = 'ACTIVE' ORDER BY memory_time DESC")
    List<MemoryPalace> findByTimeRange(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    @Select("SELECT * FROM memory_palace WHERE user_id = #{userId} AND (title LIKE #{keyword} OR content LIKE #{keyword} OR keywords LIKE #{keyword}) AND status = 'ACTIVE' ORDER BY effective_score DESC LIMIT #{limit}")
    List<MemoryPalace> searchByKeyword(Long userId, String keyword, int limit);

    @Select("SELECT * FROM memory_palace WHERE user_id = #{userId} AND effective_score < #{threshold} AND memory_type != 'CORE' AND status = 'ACTIVE' ORDER BY effective_score ASC")
    List<MemoryPalace> findExpiredMemories(Long userId, BigDecimal threshold);

    @Select("SELECT * FROM memory_palace WHERE user_id = #{userId} AND status = 'ACTIVE' ORDER BY effective_score DESC LIMIT #{limit}")
    List<MemoryPalace> findTopMemories(Long userId, int limit);

    @Select("SELECT COUNT(*) FROM memory_palace WHERE user_id = #{userId} AND status = 'ACTIVE'")
    int countByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM memory_palace WHERE user_id = #{userId} AND memory_type = #{memoryType} AND status = 'ACTIVE'")
    int countByUserIdAndType(Long userId, String memoryType);

    @Select("SELECT MIN(memory_time) FROM memory_palace WHERE user_id = #{userId} AND status = 'ACTIVE'")
    LocalDateTime findOldestMemoryTime(Long userId);

    @Select("SELECT MAX(memory_time) FROM memory_palace WHERE user_id = #{userId} AND status = 'ACTIVE'")
    LocalDateTime findNewestMemoryTime(Long userId);

    @Select("SELECT AVG(effective_score) FROM memory_palace WHERE user_id = #{userId} AND status = 'ACTIVE'")
    BigDecimal findAverageEffectiveScore(Long userId);

    @Select("SELECT DISTINCT DATE(memory_time) as date FROM memory_palace WHERE user_id = #{userId} AND status = 'ACTIVE' AND memory_time >= #{startTime} AND memory_time < #{endTime} ORDER BY date DESC")
    List<LocalDate> findDistinctDates(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    // ==================== 插入 ====================

    @Insert("INSERT INTO memory_palace (user_id, memory_type, category, title, content, keywords, " +
            "memory_time, session_id, importance, confidence, access_count, reinforce_count, " +
            "decay_rate, last_access_time, effective_score, related_memory_ids, source_node_id, " +
            "emotion_tag, context_tags, source_text, status, created_time) " +
            "VALUES (#{userId}, #{memoryType}, #{category}, #{title}, #{content}, #{keywords}, " +
            "#{memoryTime}, #{sessionId}, #{importance}, #{confidence}, #{accessCount}, #{reinforceCount}, " +
            "#{decayRate}, #{lastAccessTime}, #{effectiveScore}, #{relatedMemoryIds}, #{sourceNodeId}, " +
            "#{emotionTag}, #{contextTags}, #{sourceText}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MemoryPalace memory);

    // ==================== 更新 ====================

    @Update("UPDATE memory_palace SET memory_type = #{memoryType}, category = #{category}, " +
            "title = #{title}, content = #{content}, keywords = #{keywords}, " +
            "importance = #{importance}, confidence = #{confidence}, " +
            "access_count = #{accessCount}, reinforce_count = #{reinforceCount}, " +
            "decay_rate = #{decayRate}, last_access_time = #{lastAccessTime}, " +
            "effective_score = #{effectiveScore}, related_memory_ids = #{relatedMemoryIds}, " +
            "emotion_tag = #{emotionTag}, context_tags = #{contextTags}, " +
            "status = #{status}, updated_time = NOW() WHERE id = #{id}")
    int update(MemoryPalace memory);

    @Update("UPDATE memory_palace SET access_count = access_count + 1, last_access_time = NOW(), updated_time = NOW() WHERE id = #{id}")
    int incrementAccessCount(Long id);

    @Update("UPDATE memory_palace SET reinforce_count = reinforce_count + 1, effective_score = #{effectiveScore}, updated_time = NOW() WHERE id = #{id}")
    int incrementReinforceCount(Long id, BigDecimal effectiveScore);

    @Update("UPDATE memory_palace SET memory_type = #{memoryType}, decay_rate = #{decayRate}, updated_time = NOW() WHERE id = #{id}")
    int updateMemoryType(Long id, String memoryType, BigDecimal decayRate);

    @Update("UPDATE memory_palace SET effective_score = #{effectiveScore}, updated_time = NOW() WHERE id = #{id}")
    int updateEffectiveScore(Long id, BigDecimal effectiveScore);

    @Update("UPDATE memory_palace SET status = #{status}, updated_time = NOW() WHERE id = #{id}")
    int updateStatus(Long id, String status);

    // ==================== 删除 ====================

    @Delete("DELETE FROM memory_palace WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM memory_palace WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);

    @Delete("DELETE FROM memory_palace WHERE user_id = #{userId} AND session_id = #{sessionId}")
    int deleteBySessionId(Long userId, String sessionId);

    // ==================== 批量操作 ====================

    @Update("UPDATE memory_palace SET status = 'ARCHIVED', updated_time = NOW() WHERE user_id = #{userId} AND effective_score < #{threshold} AND memory_type != 'CORE' AND status = 'ACTIVE'")
    int archiveExpiredMemories(Long userId, BigDecimal threshold);
}