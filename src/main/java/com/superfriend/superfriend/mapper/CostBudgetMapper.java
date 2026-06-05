package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.CostBudget;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CostBudgetMapper {

    @Select("SELECT * FROM cost_budget WHERE id = #{id}")
    CostBudget findById(Long id);

    @Select("SELECT * FROM cost_budget WHERE user_id = #{userId}")
    CostBudget findByUserId(Long userId);

    @Insert("INSERT INTO cost_budget (user_id, daily_budget, monthly_budget, session_budget, " +
            "reflection_budget_ratio, thought_budget_ratio, strict_mode, alert_enabled, alert_threshold, " +
            "created_time, updated_time) " +
            "VALUES (#{userId}, #{dailyBudget}, #{monthlyBudget}, #{sessionBudget}, " +
            "#{reflectionBudgetRatio}, #{thoughtBudgetRatio}, #{strictMode}, #{alertEnabled}, #{alertThreshold}, " +
            "NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CostBudget budget);

    @Update("UPDATE cost_budget SET daily_budget = #{dailyBudget}, monthly_budget = #{monthlyBudget}, " +
            "session_budget = #{sessionBudget}, reflection_budget_ratio = #{reflectionBudgetRatio}, " +
            "thought_budget_ratio = #{thoughtBudgetRatio}, strict_mode = #{strictMode}, " +
            "alert_enabled = #{alertEnabled}, alert_threshold = #{alertThreshold}, updated_time = NOW() " +
            "WHERE id = #{id}")
    int update(CostBudget budget);

    @Delete("DELETE FROM cost_budget WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM cost_budget WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
