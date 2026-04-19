package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.dto.UserProfileDTO;
import com.superfriend.superfriend.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户画像 Controller
 * 提供用户画像相关的 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/v16/profile")
@CrossOrigin(origins = "*")
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

    /**
     * 获取用户画像
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getUserProfile(@PathVariable Long userId) {
        try {
            UserProfileDTO profile = userProfileService.getUserProfile(userId);
            if (profile == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(success(profile));
        } catch (Exception e) {
            log.error("Failed to get user profile: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 获取或创建用户画像
     */
    @GetMapping("/{userId}/or-create")
    public ResponseEntity<Map<String, Object>> getOrCreateUserProfile(@PathVariable Long userId) {
        try {
            UserProfileDTO profile = userProfileService.getOrCreateUserProfile(userId);
            return ResponseEntity.ok(success(profile));
        } catch (Exception e) {
            log.error("Failed to get or create user profile: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 获取用户画像摘要
     */
    @GetMapping("/{userId}/summary")
    public ResponseEntity<Map<String, Object>> getProfileSummary(@PathVariable Long userId) {
        try {
            UserProfileDTO profile = userProfileService.getUserProfile(userId);
            if (profile == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("userId", userId);
            summary.put("summary", profile.getSummary());
            summary.put("interestCount", profile.getInterests() != null ? profile.getInterests().size() : 0);
            summary.put("traitCount", profile.getTraits() != null ? profile.getTraits().size() : 0);
            summary.put("preferenceCount", profile.getPreferences() != null ? profile.getPreferences().size() : 0);
            summary.put("skillCount", profile.getSkills() != null ? profile.getSkills().size() : 0);
            summary.put("version", profile.getVersion());
            summary.put("updatedAt", profile.getUpdatedTime());

            return ResponseEntity.ok(success(summary));
        } catch (Exception e) {
            log.error("Failed to get profile summary: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 清理用户画像
     * 删除用户的所有画像数据
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> clearUserProfile(@PathVariable Long userId) {
        try {
            userProfileService.clearUserProfile(userId);
            Map<String, Object> result = new HashMap<>();
            result.put("message", "用户画像已清理");
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to clear user profile: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    // ==================== 批量更新 API ====================

    /**
     * 批量更新整个用户画像
     */
    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateFullProfile(
            @PathVariable Long userId,
            @RequestBody UserProfileDTO dto) {
        try {
            UserProfileDTO profile = userProfileService.updateFullProfile(userId, dto);
            return ResponseEntity.ok(success(profile));
        } catch (Exception e) {
            log.error("Failed to update full profile: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 批量更新兴趣列表
     */
    @PutMapping("/{userId}/interests")
    public ResponseEntity<Map<String, Object>> updateInterests(
            @PathVariable Long userId,
            @RequestBody List<UserProfileDTO.InterestItem> items) {
        try {
            List<UserProfileDTO.InterestItem> result = userProfileService.updateInterests(userId, items);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to update interests: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 批量更新性格特征列表
     */
    @PutMapping("/{userId}/traits")
    public ResponseEntity<Map<String, Object>> updateTraits(
            @PathVariable Long userId,
            @RequestBody List<UserProfileDTO.TraitItem> items) {
        try {
            List<UserProfileDTO.TraitItem> result = userProfileService.updateTraits(userId, items);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to update traits: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 批量更新偏好列表
     */
    @PutMapping("/{userId}/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @PathVariable Long userId,
            @RequestBody List<UserProfileDTO.PreferenceItem> items) {
        try {
            List<UserProfileDTO.PreferenceItem> result = userProfileService.updatePreferences(userId, items);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to update preferences: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 批量更新技能列表
     */
    @PutMapping("/{userId}/skills")
    public ResponseEntity<Map<String, Object>> updateSkills(
            @PathVariable Long userId,
            @RequestBody List<UserProfileDTO.SkillItem> items) {
        try {
            List<UserProfileDTO.SkillItem> result = userProfileService.updateSkills(userId, items);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to update skills: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    // ==================== 单项操作 API - 兴趣 ====================

    /**
     * 添加或更新单个兴趣项
     */
    @PostMapping("/{userId}/interests")
    public ResponseEntity<Map<String, Object>> upsertInterest(
            @PathVariable Long userId,
            @RequestBody UserProfileDTO.InterestItem item) {
        try {
            UserProfileDTO.InterestItem result = userProfileService.upsertInterest(userId, item);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to upsert interest: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 删除单个兴趣项
     */
    @DeleteMapping("/{userId}/interests/{name}")
    public ResponseEntity<Map<String, Object>> deleteInterest(
            @PathVariable Long userId,
            @PathVariable String name) {
        try {
            String decodedName = URLDecoder.decode(name, String.valueOf(StandardCharsets.UTF_8));
            boolean deleted = userProfileService.deleteInterest(userId, decodedName);
            Map<String, Object> result = new HashMap<>();
            result.put("deleted", deleted);
            result.put("name", decodedName);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to delete interest: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    // ==================== 单项操作 API - 性格特征 ====================

    /**
     * 添加或更新单个性格特征项
     */
    @PostMapping("/{userId}/traits")
    public ResponseEntity<Map<String, Object>> upsertTrait(
            @PathVariable Long userId,
            @RequestBody UserProfileDTO.TraitItem item) {
        try {
            UserProfileDTO.TraitItem result = userProfileService.upsertTrait(userId, item);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to upsert trait: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 删除单个性格特征项
     */
    @DeleteMapping("/{userId}/traits/{name}")
    public ResponseEntity<Map<String, Object>> deleteTrait(
            @PathVariable Long userId,
            @PathVariable String name) {
        try {
            String decodedName = URLDecoder.decode(name, String.valueOf(StandardCharsets.UTF_8));
            boolean deleted = userProfileService.deleteTrait(userId, decodedName);
            Map<String, Object> result = new HashMap<>();
            result.put("deleted", deleted);
            result.put("name", decodedName);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to delete trait: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    // ==================== 单项操作 API - 偏好 ====================

    /**
     * 添加或更新单个偏好项
     */
    @PostMapping("/{userId}/preferences")
    public ResponseEntity<Map<String, Object>> upsertPreference(
            @PathVariable Long userId,
            @RequestBody UserProfileDTO.PreferenceItem item) {
        try {
            UserProfileDTO.PreferenceItem result = userProfileService.upsertPreference(userId, item);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to upsert preference: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 删除单个偏好项
     */
    @DeleteMapping("/{userId}/preferences/{name}")
    public ResponseEntity<Map<String, Object>> deletePreference(
            @PathVariable Long userId,
            @PathVariable String name) {
        try {
            String decodedName = URLDecoder.decode(name, String.valueOf(StandardCharsets.UTF_8));
            boolean deleted = userProfileService.deletePreference(userId, decodedName);
            Map<String, Object> result = new HashMap<>();
            result.put("deleted", deleted);
            result.put("name", decodedName);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to delete preference: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    // ==================== 单项操作 API - 技能 ====================

    /**
     * 添加或更新单个技能项
     */
    @PostMapping("/{userId}/skills")
    public ResponseEntity<Map<String, Object>> upsertSkill(
            @PathVariable Long userId,
            @RequestBody UserProfileDTO.SkillItem item) {
        try {
            UserProfileDTO.SkillItem result = userProfileService.upsertSkill(userId, item);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to upsert skill: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * 删除单个技能项
     */
    @DeleteMapping("/{userId}/skills/{name}")
    public ResponseEntity<Map<String, Object>> deleteSkill(
            @PathVariable Long userId,
            @PathVariable String name) {
        try {
            String decodedName = URLDecoder.decode(name, String.valueOf(StandardCharsets.UTF_8));
            boolean deleted = userProfileService.deleteSkill(userId, decodedName);
            Map<String, Object> result = new HashMap<>();
            result.put("deleted", deleted);
            result.put("name", decodedName);
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to delete skill: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    // ==================== 摘要操作 API ====================

    /**
     * 更新用户画像摘要
     */
    @PutMapping("/{userId}/summary")
    public ResponseEntity<Map<String, Object>> updateSummary(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        try {
            String summary = body.get("summary");
            userProfileService.updateSummary(userId, summary);
            Map<String, Object> result = new HashMap<>();
            result.put("message", "摘要已更新");
            return ResponseEntity.ok(success(result));
        } catch (Exception e) {
            log.error("Failed to update summary: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> success(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }
}
