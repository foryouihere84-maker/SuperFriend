package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.AIModelConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AIModelConfigMapper {

    @Select("SELECT * FROM ai_model_config WHERE config_id = #{configId}")
    AIModelConfig findByConfigId(String configId);

    @Select("SELECT * FROM ai_model_config WHERE user_id = #{userId} AND is_enabled = 1 ORDER BY sort_order ASC, created_time ASC")
    List<AIModelConfig> findByUserId(Long userId);

    @Select("SELECT * FROM ai_model_config WHERE user_id = #{userId} AND is_default = 1 AND is_enabled = 1 LIMIT 1")
    AIModelConfig findDefaultByUserId(Long userId);

    @Select("SELECT * FROM ai_model_config WHERE user_id = 0 AND is_enabled = 1 ORDER BY sort_order ASC, created_time ASC")
    List<AIModelConfig> findSystemDefaults();

    @Select("SELECT * FROM ai_model_config WHERE user_id = 0 AND is_default = 1 AND is_enabled = 1 LIMIT 1")
    AIModelConfig findSystemDefault();

    @Select("SELECT * FROM ai_model_config WHERE config_id = #{configId} AND user_id = #{userId}")
    AIModelConfig findByConfigIdAndUserId(@Param("configId") String configId, @Param("userId") Long userId);

    @Insert("INSERT INTO ai_model_config (config_id, user_id, name, provider, api_url, api_key, model_id, max_tokens, temperature, is_default, is_enabled, sort_order, extra_params, supported_modalities, output_modalities, capabilities) " +
            "VALUES (#{configId}, #{userId}, #{name}, #{provider}, #{apiUrl}, #{apiKey}, #{modelId}, #{maxTokens}, #{temperature}, #{isDefault}, #{isEnabled}, #{sortOrder}, #{extraParams}, #{supportedModalities}, #{outputModalities}, #{capabilities})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AIModelConfig config);

    @Update("UPDATE ai_model_config SET name = #{name}, provider = #{provider}, api_url = #{apiUrl}, api_key = #{apiKey}, model_id = #{modelId}, " +
            "max_tokens = #{maxTokens}, temperature = #{temperature}, is_default = #{isDefault}, is_enabled = #{isEnabled}, sort_order = #{sortOrder}, extra_params = #{extraParams}, " +
            "supported_modalities = #{supportedModalities}, output_modalities = #{outputModalities}, capabilities = #{capabilities} " +
            "WHERE config_id = #{configId} AND user_id = #{userId}")
    int update(AIModelConfig config);

    @Update("UPDATE ai_model_config SET is_default = 0 WHERE user_id = #{userId} AND is_default = 1")
    int clearDefaultByUserId(Long userId);

    @Delete("DELETE FROM ai_model_config WHERE config_id = #{configId} AND user_id = #{userId}")
    int deleteByConfigIdAndUserId(@Param("configId") String configId, @Param("userId") Long userId);

    @Select("SELECT * FROM ai_model_config WHERE user_id = #{userId} AND is_enabled = 1 ORDER BY sort_order ASC, created_time ASC")
    List<AIModelConfig> findAvailableForUser(Long userId);

    @Select("SELECT * FROM ai_model_config WHERE is_enabled = 1 ORDER BY user_id ASC, sort_order ASC, created_time ASC")
    List<AIModelConfig> findAllEnabled();

    /**
     * 查找支持指定模态的模型
     * @param modality 模态类型
     * @param userId 用户ID
     * @return 支持该模态的模型列表
     */
    @Select("SELECT * FROM ai_model_config WHERE (user_id = #{userId} OR user_id = 0) AND is_enabled = 1 " +
            "AND (supported_modalities LIKE CONCAT('%', #{modality}, '%') OR supported_modalities IS NULL OR supported_modalities = 'text') " +
            "ORDER BY is_default DESC, sort_order ASC, created_time ASC")
    List<AIModelConfig> findBySupportedModality(@Param("modality") String modality, @Param("userId") Long userId);
}
