package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.KnowledgeNode;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface KnowledgeNodeMapper {

    @Select("SELECT * FROM knowledge_node WHERE user_id = #{userId} ORDER BY updated_time DESC")
    List<KnowledgeNode> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM knowledge_node WHERE user_id = #{userId} AND node_type = #{nodeType} ORDER BY updated_time DESC")
    List<KnowledgeNode> findByUserIdAndType(@Param("userId") Long userId, @Param("nodeType") String nodeType);

    @Select("SELECT * FROM knowledge_node WHERE id = #{id}")
    KnowledgeNode findById(@Param("id") Long id);

    @Select("SELECT * FROM knowledge_node WHERE user_id = #{userId} AND name = #{name} LIMIT 1")
    KnowledgeNode findByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);

    /**
     * 按用户ID、scope和名称查询节点（用于唯一性检查）
     * 全局图谱和对话图谱中，相同名称的节点只能存在一个
     */
    @Select("SELECT * FROM knowledge_node WHERE user_id = #{userId} AND scope = #{scope} AND name = #{name} LIMIT 1")
    KnowledgeNode findByUserIdAndScopeAndName(@Param("userId") Long userId, @Param("scope") String scope, @Param("name") String name);

    @Select("SELECT * FROM knowledge_node WHERE user_id = #{userId} AND " +
            "(name LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%') " +
            "OR keywords LIKE CONCAT('%', #{keyword}, '%') OR properties LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY importance DESC, confidence DESC, access_count DESC LIMIT #{limit}")
    List<KnowledgeNode> searchByKeyword(@Param("userId") Long userId,
                                        @Param("keyword") String keyword,
                                        @Param("limit") int limit);

    @Select("SELECT DISTINCT n.* FROM knowledge_node n " +
            "LEFT JOIN knowledge_relation r ON n.id = r.source_node_id OR n.id = r.target_node_id " +
            "WHERE n.user_id = #{userId} AND r.id IS NOT NULL " +
            "ORDER BY n.access_count DESC LIMIT #{limit}")
    List<KnowledgeNode> findMostConnected(@Param("userId") Long userId, @Param("limit") int limit);

    @Insert("INSERT INTO knowledge_node (user_id, node_type, scope, name, description, avatar, image, detailed_description, keywords, importance, properties, source_session_id, confidence, access_count, last_accessed_time) " +
            "VALUES (#{userId}, #{nodeType}, #{scope}, #{name}, #{description}, #{avatar}, #{image}, #{detailedDescription}, #{keywords}, #{importance}, #{properties}, #{sourceSessionId}, #{confidence}, #{accessCount}, #{lastAccessedTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgeNode node);

    @Update("UPDATE knowledge_node SET node_type = #{nodeType}, scope = #{scope}, name = #{name}, description = #{description}, " +
            "avatar = #{avatar}, image = #{image}, detailed_description = #{detailedDescription}, " +
            "keywords = #{keywords}, importance = #{importance}, properties = #{properties}, " +
            "confidence = #{confidence}, updated_time = CURRENT_TIMESTAMP WHERE id = #{id}")
    int update(KnowledgeNode node);

    @Update("UPDATE knowledge_node SET access_count = access_count + 1, last_accessed_time = CURRENT_TIMESTAMP WHERE id = #{id}")
    int incrementAccessCount(@Param("id") Long id);

    @Delete("DELETE FROM knowledge_node WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Delete("DELETE FROM knowledge_node WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM knowledge_node WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    @Select("SELECT node_type, COUNT(*) as cnt FROM knowledge_node WHERE user_id = #{userId} GROUP BY node_type")
    List<java.util.Map<String, Object>> countByType(@Param("userId") Long userId);

    @Select("SELECT * FROM knowledge_node WHERE user_id = #{userId} ORDER BY confidence DESC, access_count DESC LIMIT #{limit}")
    List<KnowledgeNode> findTopByConfidence(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT * FROM knowledge_node WHERE user_id = #{userId} AND source_session_id = #{sessionId} ORDER BY created_time DESC")
    List<KnowledgeNode> findBySessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Select("SELECT * FROM knowledge_node WHERE user_id = #{userId} ORDER BY importance DESC, updated_time DESC LIMIT #{limit}")
    List<KnowledgeNode> findRecentByImportance(@Param("userId") Long userId, @Param("limit") int limit);

    // ==================== 双层图谱支持 ====================

    @Select("SELECT * FROM knowledge_node WHERE user_id = #{userId} AND scope = #{scope} ORDER BY updated_time DESC")
    List<KnowledgeNode> findByUserIdAndScope(@Param("userId") Long userId, @Param("scope") String scope);

    /**
     * 查询对话级图谱节点
     * 按 userId + sessionId + scope=CONVERSATION 过滤，排除全局图谱节点
     */
    @Select("SELECT * FROM knowledge_node WHERE user_id = #{userId} AND source_session_id = #{sessionId} AND scope = 'CONVERSATION' ORDER BY created_time DESC")
    List<KnowledgeNode> findConversationNodes(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Delete("DELETE FROM knowledge_node WHERE user_id = #{userId} AND source_session_id = #{sessionId} AND scope = 'CONVERSATION'")
    int deleteConversationNodes(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /**
     * 按 scope 删除节点
     */
    @Delete("DELETE FROM knowledge_node WHERE user_id = #{userId} AND scope = #{scope}")
    int deleteByUserIdAndScope(@Param("userId") Long userId, @Param("scope") String scope);

    /**
     * 删除所有对话级图谱节点（scope = CONVERSATION）
     */
    @Delete("DELETE FROM knowledge_node WHERE user_id = #{userId} AND scope = 'CONVERSATION'")
    int deleteAllConversationNodes(@Param("userId") Long userId);

    /**
     * 删除所有全局图谱节点（scope = GLOBAL）
     */
    @Delete("DELETE FROM knowledge_node WHERE user_id = #{userId} AND scope = 'GLOBAL'")
    int deleteAllGlobalNodes(@Param("userId") Long userId);
}
