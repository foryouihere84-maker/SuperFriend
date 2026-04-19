package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.EmbeddingModelConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EmbeddingModelConfigMapper {

    @Select("SELECT * FROM embedding_model_config WHERE config_id = #{configId}")
    EmbeddingModelConfig findByConfigId(String configId);

    @Select("SELECT * FROM embedding_model_config WHERE is_enabled = 1 ORDER BY sort_order ASC, created_time ASC")
    List<EmbeddingModelConfig> findAllEnabled();

    @Select("SELECT * FROM embedding_model_config WHERE is_default = 1 AND is_enabled = 1 LIMIT 1")
    EmbeddingModelConfig findDefault();

    @Select("SELECT * FROM embedding_model_config WHERE provider = #{provider} AND is_enabled = 1 ORDER BY sort_order ASC LIMIT 1")
    EmbeddingModelConfig findByProvider(String provider);

    @Select("SELECT * FROM embedding_model_config ORDER BY sort_order ASC, created_time ASC")
    List<EmbeddingModelConfig> findAll();

    @Insert("INSERT INTO embedding_model_config (config_id, name, provider, api_url, api_key, model_id, dimensions, max_input_tokens, is_default, is_enabled, sort_order) " +
            "VALUES (#{configId}, #{name}, #{provider}, #{apiUrl}, #{apiKey}, #{modelId}, #{dimensions}, #{maxInputTokens}, #{isDefault}, #{isEnabled}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EmbeddingModelConfig config);

    @Update("UPDATE embedding_model_config SET name = #{name}, provider = #{provider}, api_url = #{apiUrl}, api_key = #{apiKey}, model_id = #{modelId}, " +
            "dimensions = #{dimensions}, max_input_tokens = #{maxInputTokens}, is_default = #{isDefault}, is_enabled = #{isEnabled}, sort_order = #{sortOrder} " +
            "WHERE config_id = #{configId}")
    int update(EmbeddingModelConfig config);

    @Update("UPDATE embedding_model_config SET is_default = 0 WHERE is_default = 1")
    int clearDefault();

    @Delete("DELETE FROM embedding_model_config WHERE config_id = #{configId}")
    int deleteByConfigId(String configId);
}
