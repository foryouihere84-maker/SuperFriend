package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户画像实体
 * 存储用户的全局画像信息，包括兴趣、性格特征、偏好、技能等
 */
@Data
public class UserProfile {

    private Long id;
    private Long userId;

    /**
     * 兴趣方向 JSON 数组
     * 格式: [{"name":"后端开发","confidence":0.85,"lastSeen":"2026-04-12"}]
     */
    private String interests;

    /**
     * 性格特征 JSON 数组
     * 格式: [{"name":"注重细节","confidence":0.7,"contexts":["代码评审","文档编写"]}]
     */
    private String traits;

    /**
     * 偏好 JSON 数组
     * 格式: [{"name":"简洁代码风格","confidence":0.8,"category":"coding"}]
     */
    private String preferences;

    /**
     * 技能 JSON 数组
     * 格式: [{"name":"Spring Boot","level":"熟练","confidence":0.9}]
     */
    private String skills;

    /**
     * 用户画像摘要文本（LLM生成）
     */
    private String summary;

    /**
     * 版本号（每次更新递增）
     */
    private Integer version;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedTime;
}
