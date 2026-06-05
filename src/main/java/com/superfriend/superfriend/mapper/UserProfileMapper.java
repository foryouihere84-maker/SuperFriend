package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.UserProfile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserProfileMapper {

    @Select("SELECT * FROM user_profile WHERE user_id = #{userId}")
    UserProfile findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_profile WHERE id = #{id}")
    UserProfile findById(@Param("id") Long id);

    @Insert("INSERT INTO user_profile (user_id, interests, traits, preferences, skills, summary, version) " +
            "VALUES (#{userId}, #{interests}, #{traits}, #{preferences}, #{skills}, #{summary}, #{version})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserProfile profile);

    @Update("UPDATE user_profile SET interests = #{interests}, traits = #{traits}, " +
            "preferences = #{preferences}, skills = #{skills}, summary = #{summary}, " +
            "version = version + 1 WHERE user_id = #{userId}")
    int update(UserProfile profile);

    @Update("UPDATE user_profile SET summary = #{summary}, version = version + 1 WHERE user_id = #{userId}")
    int updateSummary(@Param("userId") Long userId, @Param("summary") String summary);

    @Delete("DELETE FROM user_profile WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
