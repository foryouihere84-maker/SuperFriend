package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.KnowledgeRelation;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface KnowledgeRelationMapper {

    @Select("SELECT * FROM knowledge_relation WHERE user_id = #{userId} ORDER BY created_time DESC")
    List<KnowledgeRelation> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM knowledge_relation WHERE id = #{id}")
    KnowledgeRelation findById(@Param("id") Long id);

    @Select("SELECT * FROM knowledge_relation WHERE source_node_id = #{nodeId}")
    List<KnowledgeRelation> findBySourceNodeId(@Param("nodeId") Long nodeId);

    @Select("SELECT * FROM knowledge_relation WHERE target_node_id = #{nodeId}")
    List<KnowledgeRelation> findByTargetNodeId(@Param("nodeId") Long nodeId);

    @Select("SELECT r.*, " +
            "sn.id as source_node_id_col, sn.node_type as source_node_type, sn.name as source_node_name, " +
            "tn.id as target_node_id_col, tn.node_type as target_node_type, tn.name as target_node_name " +
            "FROM knowledge_relation r " +
            "LEFT JOIN knowledge_node sn ON r.source_node_id = sn.id " +
            "LEFT JOIN knowledge_node tn ON r.target_node_id = tn.id " +
            "WHERE r.user_id = #{userId} ORDER BY r.weight DESC")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "sourceNodeId", column = "source_node_id"),
        @Result(property = "targetNodeId", column = "target_node_id"),
        @Result(property = "relationType", column = "relation_type"),
        @Result(property = "properties", column = "properties"),
        @Result(property = "weight", column = "weight"),
        @Result(property = "sourceSessionId", column = "source_session_id"),
        @Result(property = "createdTime", column = "created_time"),
        @Result(property = "sourceNode.id", column = "source_node_id_col"),
        @Result(property = "sourceNode.nodeType", column = "source_node_type"),
        @Result(property = "sourceNode.name", column = "source_node_name"),
        @Result(property = "targetNode.id", column = "target_node_id_col"),
        @Result(property = "targetNode.nodeType", column = "target_node_type"),
        @Result(property = "targetNode.name", column = "target_node_name")
    })
    List<KnowledgeRelation> findByUserIdWithNodes(@Param("userId") Long userId);

    /**
     * 查询全局图谱关系（source_session_id IS NULL）
     */
    @Select("SELECT r.*, " +
            "sn.id as source_node_id_col, sn.node_type as source_node_type, sn.name as source_node_name, " +
            "tn.id as target_node_id_col, tn.node_type as target_node_type, tn.name as target_node_name " +
            "FROM knowledge_relation r " +
            "LEFT JOIN knowledge_node sn ON r.source_node_id = sn.id " +
            "LEFT JOIN knowledge_node tn ON r.target_node_id = tn.id " +
            "WHERE r.user_id = #{userId} AND r.source_session_id IS NULL ORDER BY r.weight DESC")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "sourceNodeId", column = "source_node_id"),
        @Result(property = "targetNodeId", column = "target_node_id"),
        @Result(property = "relationType", column = "relation_type"),
        @Result(property = "properties", column = "properties"),
        @Result(property = "weight", column = "weight"),
        @Result(property = "sourceSessionId", column = "source_session_id"),
        @Result(property = "createdTime", column = "created_time"),
        @Result(property = "sourceNode.id", column = "source_node_id_col"),
        @Result(property = "sourceNode.nodeType", column = "source_node_type"),
        @Result(property = "sourceNode.name", column = "source_node_name"),
        @Result(property = "targetNode.id", column = "target_node_id_col"),
        @Result(property = "targetNode.nodeType", column = "target_node_type"),
        @Result(property = "targetNode.name", column = "target_node_name")
    })
    List<KnowledgeRelation> findGlobalRelationsByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM knowledge_relation WHERE source_node_id = #{sourceNodeId} AND target_node_id = #{targetNodeId} AND relation_type = #{relationType} LIMIT 1")
    KnowledgeRelation findExistingRelation(@Param("sourceNodeId") Long sourceNodeId,
                                          @Param("targetNodeId") Long targetNodeId,
                                          @Param("relationType") String relationType);

    @Insert("INSERT INTO knowledge_relation (user_id, source_node_id, target_node_id, relation_type, properties, weight, source_session_id) " +
            "VALUES (#{userId}, #{sourceNodeId}, #{targetNodeId}, #{relationType}, #{properties}, #{weight}, #{sourceSessionId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgeRelation relation);

    @Update("UPDATE knowledge_relation SET source_node_id = #{sourceNodeId}, target_node_id = #{targetNodeId}, relation_type = #{relationType}, weight = #{weight}, properties = #{properties} WHERE id = #{id}")
    int update(KnowledgeRelation relation);

    @Delete("DELETE FROM knowledge_relation WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Delete("DELETE FROM knowledge_relation WHERE source_node_id = #{nodeId} OR target_node_id = #{nodeId}")
    int deleteByNodeId(@Param("nodeId") Long nodeId);

    @Delete("DELETE FROM knowledge_relation WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM knowledge_relation WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    @Select("SELECT relation_type, COUNT(*) as cnt FROM knowledge_relation WHERE user_id = #{userId} GROUP BY relation_type")
    List<java.util.Map<String, Object>> countByType(@Param("userId") Long userId);

    @Select("SELECT r.* FROM knowledge_relation r " +
            "WHERE r.user_id = #{userId} AND " +
            "(r.source_node_id IN (${nodeIds}) OR r.target_node_id IN (${nodeIds}))")
    List<KnowledgeRelation> findByNodeIds(@Param("userId") Long userId, @Param("nodeIds") String nodeIds);

    // ==================== 双层图谱支持 ====================

    @Select("SELECT * FROM knowledge_relation WHERE user_id = #{userId} AND source_session_id = #{sessionId}")
    List<KnowledgeRelation> findBySessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /**
     * 删除对话级关系（source_session_id 不为空的关系）
     * 注意：这是删除所有对话级关系，不是某个特定会话的
     */
    @Delete("DELETE FROM knowledge_relation WHERE user_id = #{userId} AND source_session_id IS NOT NULL")
    int deleteAllConversationRelations(@Param("userId") Long userId);

    /**
     * 删除全局关系（source_session_id 为空的关系）
     */
    @Delete("DELETE FROM knowledge_relation WHERE user_id = #{userId} AND source_session_id IS NULL")
    int deleteAllGlobalRelations(@Param("userId") Long userId);

    /**
     * 删除指定会话的关系
     */
    @Delete("DELETE FROM knowledge_relation WHERE user_id = #{userId} AND source_session_id = #{sessionId}")
    int deleteBySessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);
}
