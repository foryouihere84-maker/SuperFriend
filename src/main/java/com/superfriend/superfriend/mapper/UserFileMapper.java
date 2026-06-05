package com.superfriend.superfriend.mapper;

import com.superfriend.superfriend.entity.UserFile;
import lombok.Data;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserFileMapper {

    @Select("SELECT * FROM user_file WHERE user_id = #{userId} AND status = 'active' ORDER BY upload_time DESC")
    List<UserFile> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_file WHERE user_id = #{userId} AND session_id = #{sessionId} AND status = 'active' ORDER BY upload_time DESC")
    List<UserFile> findByUserIdAndSessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Select("SELECT * FROM user_file WHERE file_id = #{fileId}")
    UserFile findByFileId(@Param("fileId") String fileId);

    @Select("SELECT COUNT(*) as fileCount, COALESCE(SUM(file_size), 0) as totalSize FROM user_file WHERE user_id = #{userId} AND status = 'active'")
    UserFileStats getStatsByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO user_file (user_id, session_id, file_id, file_name, stored_name, file_path, file_size, mime_type, file_type, status, is_sensitive, upload_time, last_access_time, metadata) " +
            "VALUES (#{userId}, #{sessionId}, #{fileId}, #{fileName}, #{storedName}, #{filePath}, #{fileSize}, #{mimeType}, #{fileType}, #{status}, #{isSensitive}, NOW(), NOW(), #{metadata})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserFile userFile);

    @Update("UPDATE user_file SET last_access_time = NOW() WHERE file_id = #{fileId}")
    int updateAccessTime(@Param("fileId") String fileId);

    @Update("UPDATE user_file SET status = 'deleted' WHERE file_id = #{fileId}")
    int softDeleteByFileId(@Param("fileId") String fileId);

    @Delete("DELETE FROM user_file WHERE file_id = #{fileId}")
    int deleteByFileId(@Param("fileId") String fileId);

    @Update("UPDATE user_file SET status = 'expired' WHERE last_access_time < DATE_SUB(NOW(), INTERVAL #{hours} HOUR) AND status = 'active'")
    int markExpired(@Param("hours") int hours);

    @Data
    class UserFileStats {
        private Long fileCount;
        private Long totalSize;
    }
}
