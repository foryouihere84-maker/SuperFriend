package com.superfriend.superfriend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.GraphFactDTO;
import com.superfriend.superfriend.dto.UserProfileDTO;
import com.superfriend.superfriend.entity.UserProfile;
import com.superfriend.superfriend.mapper.UserProfileMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 用户画像服务
 * 管理用户的全局画像信息，包括兴趣、性格特征、偏好、技能等
 */
@Slf4j
@Service
public class UserProfileService {

    @Autowired
    private UserProfileMapper profileMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private LLMClient llmClient;

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    private static final double CONFIDENCE_INCREMENT = 0.1;
    private static final double MAX_CONFIDENCE = 0.95;
    private static final double TIME_DECAY_FACTOR = 0.95;
    private static final int MAX_ITEMS_PER_CATEGORY = 20;

    // ==================== 查询方法 ====================

    /**
     * 获取用户画像
     */
    public UserProfileDTO getUserProfile(Long userId) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            return null;
        }
        return toDTO(profile);
    }

    /**
     * 获取或创建用户画像
     */
    public UserProfileDTO getOrCreateUserProfile(Long userId) {
        UserProfileDTO dto = getUserProfile(userId);
        if (dto == null) {
            UserProfile profile = new UserProfile();
            profile.setUserId(userId);
            profile.setInterests("[]");
            profile.setTraits("[]");
            profile.setPreferences("[]");
            profile.setSkills("[]");
            profile.setVersion(1);
            profileMapper.insert(profile);
            dto = toDTO(profile);
            log.info("Created new user profile for userId={}", userId);
        }
        return dto;
    }

    // ==================== 更新方法 ====================

    /**
     * 更新用户画像（处理冲突和累积）
     */
    @Transactional
    public void updateUserProfile(Long userId, String sessionId, List<GraphFactDTO> facts) {
        if (facts == null || facts.isEmpty()) {
            return;
        }

        UserProfile profile = getOrCreateProfileEntity(userId);

        for (GraphFactDTO fact : facts) {
            processFact(profile, fact, sessionId);
        }

        profileMapper.update(profile);
        log.info("Updated user profile for userId={}, facts={}", userId, facts.size());
    }

    /**
     * 处理单个事实信息
     */
    private void processFact(UserProfile profile, GraphFactDTO fact, String sessionId) {
        String factType = fact.getFactType();

        switch (factType) {
            case "INTEREST":
                processInterest(profile, fact, sessionId);
                break;
            case "TRAIT":
                processTrait(profile, fact, sessionId);
                break;
            case "PREFERENCE":
                processPreference(profile, fact, sessionId);
                break;
            case "SKILL":
                processSkill(profile, fact, sessionId);
                break;
            default:
                log.warn("Unknown fact type: {}", factType);
        }
    }

    /**
     * 处理兴趣信息
     */
    private void processInterest(UserProfile profile, GraphFactDTO fact, String sessionId) {
        try {
            List<UserProfileDTO.InterestItem> interests = parseInterests(profile.getInterests());
            String key = fact.getFactKey().toLowerCase();

            Optional<UserProfileDTO.InterestItem> existing = interests.stream()
                    .filter(i -> i.getName().toLowerCase().equals(key))
                    .findFirst();

            if (existing.isPresent()) {
                UserProfileDTO.InterestItem item = existing.get();
                BigDecimal newConfidence = accumulateConfidence(item.getConfidence(), fact.getConfidence());
                item.setConfidence(newConfidence);
                item.setLastSeen(LocalDate.now().toString());
                item.setMentionCount(item.getMentionCount() + 1);
            } else {
                UserProfileDTO.InterestItem newItem = new UserProfileDTO.InterestItem();
                newItem.setName(fact.getFactKey());
                newItem.setConfidence(fact.getConfidence());
                newItem.setLastSeen(LocalDate.now().toString());
                newItem.setMentionCount(1);
                interests.add(newItem);
            }

            // 限制数量，按置信度排序
            interests.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
            if (interests.size() > MAX_ITEMS_PER_CATEGORY) {
                interests = interests.subList(0, MAX_ITEMS_PER_CATEGORY);
            }

            profile.setInterests(objectMapper.writeValueAsString(interests));
        } catch (Exception e) {
            log.error("Failed to process interest: {}", e.getMessage());
        }
    }

    /**
     * 处理性格特征信息
     */
    private void processTrait(UserProfile profile, GraphFactDTO fact, String sessionId) {
        try {
            List<UserProfileDTO.TraitItem> traits = parseTraits(profile.getTraits());
            String key = fact.getFactKey().toLowerCase();

            Optional<UserProfileDTO.TraitItem> existing = traits.stream()
                    .filter(t -> t.getName().toLowerCase().equals(key))
                    .findFirst();

            if (existing.isPresent()) {
                UserProfileDTO.TraitItem item = existing.get();
                BigDecimal newConfidence = accumulateConfidence(item.getConfidence(), fact.getConfidence());
                item.setConfidence(newConfidence);
                item.setMentionCount(item.getMentionCount() + 1);

                if (fact.getContexts() != null) {
                    List<String> contexts = new ArrayList<>(item.getContexts() != null ? item.getContexts() : new ArrayList<>());
                    for (String ctx : fact.getContexts()) {
                        if (!contexts.contains(ctx)) {
                            contexts.add(ctx);
                        }
                    }
                    item.setContexts(contexts);
                }
            } else {
                UserProfileDTO.TraitItem newItem = new UserProfileDTO.TraitItem();
                newItem.setName(fact.getFactKey());
                newItem.setConfidence(fact.getConfidence());
                newItem.setContexts(fact.getContexts() != null ? fact.getContexts() : new ArrayList<>());
                newItem.setMentionCount(1);
                traits.add(newItem);
            }

            traits.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
            if (traits.size() > MAX_ITEMS_PER_CATEGORY) {
                traits = traits.subList(0, MAX_ITEMS_PER_CATEGORY);
            }

            profile.setTraits(objectMapper.writeValueAsString(traits));
        } catch (Exception e) {
            log.error("Failed to process trait: {}", e.getMessage());
        }
    }

    /**
     * 处理偏好信息
     */
    private void processPreference(UserProfile profile, GraphFactDTO fact, String sessionId) {
        try {
            List<UserProfileDTO.PreferenceItem> preferences = parsePreferences(profile.getPreferences());
            String key = fact.getFactKey().toLowerCase();

            Optional<UserProfileDTO.PreferenceItem> existing = preferences.stream()
                    .filter(p -> p.getName().toLowerCase().equals(key))
                    .findFirst();

            if (existing.isPresent()) {
                UserProfileDTO.PreferenceItem item = existing.get();
                BigDecimal newConfidence = accumulateConfidence(item.getConfidence(), fact.getConfidence());
                item.setConfidence(newConfidence);
                item.setMentionCount(item.getMentionCount() + 1);
            } else {
                UserProfileDTO.PreferenceItem newItem = new UserProfileDTO.PreferenceItem();
                newItem.setName(fact.getFactKey());
                newItem.setConfidence(fact.getConfidence());
                newItem.setCategory(fact.getCategory());
                newItem.setMentionCount(1);
                preferences.add(newItem);
            }

            preferences.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
            if (preferences.size() > MAX_ITEMS_PER_CATEGORY) {
                preferences = preferences.subList(0, MAX_ITEMS_PER_CATEGORY);
            }

            profile.setPreferences(objectMapper.writeValueAsString(preferences));
        } catch (Exception e) {
            log.error("Failed to process preference: {}", e.getMessage());
        }
    }

    /**
     * 处理技能信息
     */
    private void processSkill(UserProfile profile, GraphFactDTO fact, String sessionId) {
        try {
            List<UserProfileDTO.SkillItem> skills = parseSkills(profile.getSkills());
            String key = fact.getFactKey().toLowerCase();

            Optional<UserProfileDTO.SkillItem> existing = skills.stream()
                    .filter(s -> s.getName().toLowerCase().equals(key))
                    .findFirst();

            if (existing.isPresent()) {
                UserProfileDTO.SkillItem item = existing.get();
                BigDecimal newConfidence = accumulateConfidence(item.getConfidence(), fact.getConfidence());
                item.setConfidence(newConfidence);
                item.setMentionCount(item.getMentionCount() + 1);

                if (fact.getLevel() != null && compareLevels(fact.getLevel(), item.getLevel()) > 0) {
                    item.setLevel(fact.getLevel());
                }
            } else {
                UserProfileDTO.SkillItem newItem = new UserProfileDTO.SkillItem();
                newItem.setName(fact.getFactKey());
                newItem.setLevel(fact.getLevel() != null ? fact.getLevel() : "了解");
                newItem.setConfidence(fact.getConfidence());
                newItem.setMentionCount(1);
                skills.add(newItem);
            }

            skills.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
            if (skills.size() > MAX_ITEMS_PER_CATEGORY) {
                skills = skills.subList(0, MAX_ITEMS_PER_CATEGORY);
            }

            profile.setSkills(objectMapper.writeValueAsString(skills));
        } catch (Exception e) {
            log.error("Failed to process skill: {}", e.getMessage());
        }
    }

    // ==================== 冲突处理 ====================

    /**
     * 累积置信度
     */
    private BigDecimal accumulateConfidence(BigDecimal existing, BigDecimal addition) {
        if (existing == null) existing = BigDecimal.valueOf(0.5);
        if (addition == null) addition = BigDecimal.valueOf(0.5);

        // 累加，但不超过最大值
        BigDecimal newConfidence = existing.add(BigDecimal.valueOf(CONFIDENCE_INCREMENT));
        if (newConfidence.compareTo(BigDecimal.valueOf(MAX_CONFIDENCE)) > 0) {
            newConfidence = BigDecimal.valueOf(MAX_CONFIDENCE);
        }
        return newConfidence.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算时间衰减后的有效置信度
     */
    public BigDecimal calculateEffectiveConfidence(BigDecimal confidence, LocalDateTime lastSeen) {
        if (confidence == null) return BigDecimal.ZERO;
        if (lastSeen == null) return confidence;

        long months = ChronoUnit.MONTHS.between(lastSeen, LocalDateTime.now());
        double decay = Math.pow(TIME_DECAY_FACTOR, months);
        return confidence.multiply(BigDecimal.valueOf(decay)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 比较技能级别
     */
    private int compareLevels(String level1, String level2) {
        int rank1 = getLevelRank(level1);
        int rank2 = getLevelRank(level2);
        return Integer.compare(rank1, rank2);
    }

    private int getLevelRank(String level) {
        if (level == null) return 0;
        switch (level.toLowerCase()) {
            case "精通": return 4;
            case "熟练": return 3;
            case "掌握": return 2;
            case "了解": return 1;
            default: return 0;
        }
    }

    // ==================== 辅助方法 ====================

    private UserProfile getOrCreateProfileEntity(Long userId) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setInterests("[]");
            profile.setTraits("[]");
            profile.setPreferences("[]");
            profile.setSkills("[]");
            profile.setVersion(1);
            profileMapper.insert(profile);
        }
        return profile;
    }

    private List<UserProfileDTO.InterestItem> parseInterests(String json) {
        try {
            if (json == null || json.isEmpty()) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<UserProfileDTO.InterestItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<UserProfileDTO.TraitItem> parseTraits(String json) {
        try {
            if (json == null || json.isEmpty()) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<UserProfileDTO.TraitItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<UserProfileDTO.PreferenceItem> parsePreferences(String json) {
        try {
            if (json == null || json.isEmpty()) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<UserProfileDTO.PreferenceItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<UserProfileDTO.SkillItem> parseSkills(String json) {
        try {
            if (json == null || json.isEmpty()) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<UserProfileDTO.SkillItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private UserProfileDTO toDTO(UserProfile entity) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setInterests(parseInterests(entity.getInterests()));
        dto.setTraits(parseTraits(entity.getTraits()));
        dto.setPreferences(parsePreferences(entity.getPreferences()));
        dto.setSkills(parseSkills(entity.getSkills()));
        dto.setSummary(entity.getSummary());
        dto.setVersion(entity.getVersion());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedTime(entity.getUpdatedTime());
        return dto;
    }

    // ==================== 清理方法 ====================

    /**
     * 清理用户画像
     * 删除用户的所有画像数据，包括兴趣、特质、偏好、技能和摘要
     */
    @Transactional
    public void clearUserProfile(Long userId) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile != null) {
            profileMapper.deleteByUserId(userId);
            log.info("Cleared user profile for userId={}", userId);
        }
    }

    // ==================== 自定义画像方法 ====================

    /**
     * 添加或更新兴趣项
     */
    @Transactional
    public UserProfileDTO.InterestItem upsertInterest(Long userId, UserProfileDTO.InterestItem item) {
        UserProfile profile = getOrCreateProfileEntity(userId);
        List<UserProfileDTO.InterestItem> interests = parseInterests(profile.getInterests());

        String key = item.getName().toLowerCase();
        Optional<UserProfileDTO.InterestItem> existing = interests.stream()
                .filter(i -> i.getName().toLowerCase().equals(key))
                .findFirst();

        if (existing.isPresent()) {
            UserProfileDTO.InterestItem existingItem = existing.get();
            existingItem.setConfidence(item.getConfidence() != null ? item.getConfidence() : BigDecimal.valueOf(0.8));
            existingItem.setLastSeen(LocalDate.now().toString());
            existingItem.setMentionCount(item.getMentionCount() != null ? item.getMentionCount() : existingItem.getMentionCount() + 1);
            item = existingItem;
        } else {
            if (item.getConfidence() == null) item.setConfidence(BigDecimal.valueOf(0.8));
            if (item.getLastSeen() == null) item.setLastSeen(LocalDate.now().toString());
            if (item.getMentionCount() == null) item.setMentionCount(1);
            interests.add(item);
        }

        interests.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
        if (interests.size() > MAX_ITEMS_PER_CATEGORY) {
            interests = interests.subList(0, MAX_ITEMS_PER_CATEGORY);
        }

        try {
            profile.setInterests(objectMapper.writeValueAsString(interests));
            profileMapper.update(profile);
            log.info("Upserted interest for userId={}: {}", userId, item.getName());
        } catch (Exception e) {
            log.error("Failed to upsert interest: {}", e.getMessage());
        }

        return item;
    }

    /**
     * 删除兴趣项
     */
    @Transactional
    public boolean deleteInterest(Long userId, String name) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) return false;

        List<UserProfileDTO.InterestItem> interests = parseInterests(profile.getInterests());
        String key = name.toLowerCase();
        boolean removed = interests.removeIf(i -> i.getName().toLowerCase().equals(key));

        if (removed) {
            try {
                profile.setInterests(objectMapper.writeValueAsString(interests));
                profileMapper.update(profile);
                log.info("Deleted interest for userId={}: {}", userId, name);
            } catch (Exception e) {
                log.error("Failed to delete interest: {}", e.getMessage());
                return false;
            }
        }

        return removed;
    }

    /**
     * 批量更新兴趣列表
     */
    @Transactional
    public List<UserProfileDTO.InterestItem> updateInterests(Long userId, List<UserProfileDTO.InterestItem> items) {
        UserProfile profile = getOrCreateProfileEntity(userId);

        for (UserProfileDTO.InterestItem item : items) {
            if (item.getConfidence() == null) item.setConfidence(BigDecimal.valueOf(0.8));
            if (item.getLastSeen() == null) item.setLastSeen(LocalDate.now().toString());
            if (item.getMentionCount() == null) item.setMentionCount(1);
        }

        items.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
        if (items.size() > MAX_ITEMS_PER_CATEGORY) {
            items = items.subList(0, MAX_ITEMS_PER_CATEGORY);
        }

        try {
            profile.setInterests(objectMapper.writeValueAsString(items));
            profileMapper.update(profile);
            log.info("Updated interests for userId={}, count={}", userId, items.size());
        } catch (Exception e) {
            log.error("Failed to update interests: {}", e.getMessage());
        }

        return items;
    }

    /**
     * 添加或更新性格特征项
     */
    @Transactional
    public UserProfileDTO.TraitItem upsertTrait(Long userId, UserProfileDTO.TraitItem item) {
        UserProfile profile = getOrCreateProfileEntity(userId);
        List<UserProfileDTO.TraitItem> traits = parseTraits(profile.getTraits());

        String key = item.getName().toLowerCase();
        Optional<UserProfileDTO.TraitItem> existing = traits.stream()
                .filter(t -> t.getName().toLowerCase().equals(key))
                .findFirst();

        if (existing.isPresent()) {
            UserProfileDTO.TraitItem existingItem = existing.get();
            existingItem.setConfidence(item.getConfidence() != null ? item.getConfidence() : BigDecimal.valueOf(0.8));
            existingItem.setMentionCount(item.getMentionCount() != null ? item.getMentionCount() : existingItem.getMentionCount() + 1);
            if (item.getContexts() != null) {
                List<String> contexts = new ArrayList<>(existingItem.getContexts() != null ? existingItem.getContexts() : new ArrayList<>());
                for (String ctx : item.getContexts()) {
                    if (!contexts.contains(ctx)) contexts.add(ctx);
                }
                existingItem.setContexts(contexts);
            }
            item = existingItem;
        } else {
            if (item.getConfidence() == null) item.setConfidence(BigDecimal.valueOf(0.8));
            if (item.getContexts() == null) item.setContexts(new ArrayList<>());
            if (item.getMentionCount() == null) item.setMentionCount(1);
            traits.add(item);
        }

        traits.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
        if (traits.size() > MAX_ITEMS_PER_CATEGORY) {
            traits = traits.subList(0, MAX_ITEMS_PER_CATEGORY);
        }

        try {
            profile.setTraits(objectMapper.writeValueAsString(traits));
            profileMapper.update(profile);
            log.info("Upserted trait for userId={}: {}", userId, item.getName());
        } catch (Exception e) {
            log.error("Failed to upsert trait: {}", e.getMessage());
        }

        return item;
    }

    /**
     * 删除性格特征项
     */
    @Transactional
    public boolean deleteTrait(Long userId, String name) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) return false;

        List<UserProfileDTO.TraitItem> traits = parseTraits(profile.getTraits());
        String key = name.toLowerCase();
        boolean removed = traits.removeIf(t -> t.getName().toLowerCase().equals(key));

        if (removed) {
            try {
                profile.setTraits(objectMapper.writeValueAsString(traits));
                profileMapper.update(profile);
                log.info("Deleted trait for userId={}: {}", userId, name);
            } catch (Exception e) {
                log.error("Failed to delete trait: {}", e.getMessage());
                return false;
            }
        }

        return removed;
    }

    /**
     * 批量更新性格特征列表
     */
    @Transactional
    public List<UserProfileDTO.TraitItem> updateTraits(Long userId, List<UserProfileDTO.TraitItem> items) {
        UserProfile profile = getOrCreateProfileEntity(userId);

        for (UserProfileDTO.TraitItem item : items) {
            if (item.getConfidence() == null) item.setConfidence(BigDecimal.valueOf(0.8));
            if (item.getContexts() == null) item.setContexts(new ArrayList<>());
            if (item.getMentionCount() == null) item.setMentionCount(1);
        }

        items.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
        if (items.size() > MAX_ITEMS_PER_CATEGORY) {
            items = items.subList(0, MAX_ITEMS_PER_CATEGORY);
        }

        try {
            profile.setTraits(objectMapper.writeValueAsString(items));
            profileMapper.update(profile);
            log.info("Updated traits for userId={}, count={}", userId, items.size());
        } catch (Exception e) {
            log.error("Failed to update traits: {}", e.getMessage());
        }

        return items;
    }

    /**
     * 添加或更新偏好项
     */
    @Transactional
    public UserProfileDTO.PreferenceItem upsertPreference(Long userId, UserProfileDTO.PreferenceItem item) {
        UserProfile profile = getOrCreateProfileEntity(userId);
        List<UserProfileDTO.PreferenceItem> preferences = parsePreferences(profile.getPreferences());

        String key = item.getName().toLowerCase();
        Optional<UserProfileDTO.PreferenceItem> existing = preferences.stream()
                .filter(p -> p.getName().toLowerCase().equals(key))
                .findFirst();

        if (existing.isPresent()) {
            UserProfileDTO.PreferenceItem existingItem = existing.get();
            existingItem.setConfidence(item.getConfidence() != null ? item.getConfidence() : BigDecimal.valueOf(0.8));
            existingItem.setCategory(item.getCategory() != null ? item.getCategory() : existingItem.getCategory());
            existingItem.setMentionCount(item.getMentionCount() != null ? item.getMentionCount() : existingItem.getMentionCount() + 1);
            item = existingItem;
        } else {
            if (item.getConfidence() == null) item.setConfidence(BigDecimal.valueOf(0.8));
            if (item.getCategory() == null) item.setCategory("general");
            if (item.getMentionCount() == null) item.setMentionCount(1);
            preferences.add(item);
        }

        preferences.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
        if (preferences.size() > MAX_ITEMS_PER_CATEGORY) {
            preferences = preferences.subList(0, MAX_ITEMS_PER_CATEGORY);
        }

        try {
            profile.setPreferences(objectMapper.writeValueAsString(preferences));
            profileMapper.update(profile);
            log.info("Upserted preference for userId={}: {}", userId, item.getName());
        } catch (Exception e) {
            log.error("Failed to upsert preference: {}", e.getMessage());
        }

        return item;
    }

    /**
     * 删除偏好项
     */
    @Transactional
    public boolean deletePreference(Long userId, String name) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) return false;

        List<UserProfileDTO.PreferenceItem> preferences = parsePreferences(profile.getPreferences());
        String key = name.toLowerCase();
        boolean removed = preferences.removeIf(p -> p.getName().toLowerCase().equals(key));

        if (removed) {
            try {
                profile.setPreferences(objectMapper.writeValueAsString(preferences));
                profileMapper.update(profile);
                log.info("Deleted preference for userId={}: {}", userId, name);
            } catch (Exception e) {
                log.error("Failed to delete preference: {}", e.getMessage());
                return false;
            }
        }

        return removed;
    }

    /**
     * 批量更新偏好列表
     */
    @Transactional
    public List<UserProfileDTO.PreferenceItem> updatePreferences(Long userId, List<UserProfileDTO.PreferenceItem> items) {
        UserProfile profile = getOrCreateProfileEntity(userId);

        for (UserProfileDTO.PreferenceItem item : items) {
            if (item.getConfidence() == null) item.setConfidence(BigDecimal.valueOf(0.8));
            if (item.getCategory() == null) item.setCategory("general");
            if (item.getMentionCount() == null) item.setMentionCount(1);
        }

        items.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
        if (items.size() > MAX_ITEMS_PER_CATEGORY) {
            items = items.subList(0, MAX_ITEMS_PER_CATEGORY);
        }

        try {
            profile.setPreferences(objectMapper.writeValueAsString(items));
            profileMapper.update(profile);
            log.info("Updated preferences for userId={}, count={}", userId, items.size());
        } catch (Exception e) {
            log.error("Failed to update preferences: {}", e.getMessage());
        }

        return items;
    }

    /**
     * 添加或更新技能项
     */
    @Transactional
    public UserProfileDTO.SkillItem upsertSkill(Long userId, UserProfileDTO.SkillItem item) {
        UserProfile profile = getOrCreateProfileEntity(userId);
        List<UserProfileDTO.SkillItem> skills = parseSkills(profile.getSkills());

        String key = item.getName().toLowerCase();
        Optional<UserProfileDTO.SkillItem> existing = skills.stream()
                .filter(s -> s.getName().toLowerCase().equals(key))
                .findFirst();

        if (existing.isPresent()) {
            UserProfileDTO.SkillItem existingItem = existing.get();
            existingItem.setConfidence(item.getConfidence() != null ? item.getConfidence() : BigDecimal.valueOf(0.8));
            existingItem.setLevel(item.getLevel() != null ? item.getLevel() : existingItem.getLevel());
            existingItem.setMentionCount(item.getMentionCount() != null ? item.getMentionCount() : existingItem.getMentionCount() + 1);
            item = existingItem;
        } else {
            if (item.getConfidence() == null) item.setConfidence(BigDecimal.valueOf(0.8));
            if (item.getLevel() == null) item.setLevel("了解");
            if (item.getMentionCount() == null) item.setMentionCount(1);
            skills.add(item);
        }

        skills.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
        if (skills.size() > MAX_ITEMS_PER_CATEGORY) {
            skills = skills.subList(0, MAX_ITEMS_PER_CATEGORY);
        }

        try {
            profile.setSkills(objectMapper.writeValueAsString(skills));
            profileMapper.update(profile);
            log.info("Upserted skill for userId={}: {}", userId, item.getName());
        } catch (Exception e) {
            log.error("Failed to upsert skill: {}", e.getMessage());
        }

        return item;
    }

    /**
     * 删除技能项
     */
    @Transactional
    public boolean deleteSkill(Long userId, String name) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) return false;

        List<UserProfileDTO.SkillItem> skills = parseSkills(profile.getSkills());
        String key = name.toLowerCase();
        boolean removed = skills.removeIf(s -> s.getName().toLowerCase().equals(key));

        if (removed) {
            try {
                profile.setSkills(objectMapper.writeValueAsString(skills));
                profileMapper.update(profile);
                log.info("Deleted skill for userId={}: {}", userId, name);
            } catch (Exception e) {
                log.error("Failed to delete skill: {}", e.getMessage());
                return false;
            }
        }

        return removed;
    }

    /**
     * 批量更新技能列表
     */
    @Transactional
    public List<UserProfileDTO.SkillItem> updateSkills(Long userId, List<UserProfileDTO.SkillItem> items) {
        UserProfile profile = getOrCreateProfileEntity(userId);

        for (UserProfileDTO.SkillItem item : items) {
            if (item.getConfidence() == null) item.setConfidence(BigDecimal.valueOf(0.8));
            if (item.getLevel() == null) item.setLevel("了解");
            if (item.getMentionCount() == null) item.setMentionCount(1);
        }

        items.sort((a, b) -> b.getConfidence().compareTo(a.getConfidence()));
        if (items.size() > MAX_ITEMS_PER_CATEGORY) {
            items = items.subList(0, MAX_ITEMS_PER_CATEGORY);
        }

        try {
            profile.setSkills(objectMapper.writeValueAsString(items));
            profileMapper.update(profile);
            log.info("Updated skills for userId={}, count={}", userId, items.size());
        } catch (Exception e) {
            log.error("Failed to update skills: {}", e.getMessage());
        }

        return items;
    }

    /**
     * 批量更新整个用户画像
     */
    @Transactional
    public UserProfileDTO updateFullProfile(Long userId, UserProfileDTO dto) {
        UserProfile profile = getOrCreateProfileEntity(userId);

        if (dto.getInterests() != null) {
            updateInterests(userId, dto.getInterests());
        }
        if (dto.getTraits() != null) {
            updateTraits(userId, dto.getTraits());
        }
        if (dto.getPreferences() != null) {
            updatePreferences(userId, dto.getPreferences());
        }
        if (dto.getSkills() != null) {
            updateSkills(userId, dto.getSkills());
        }
        if (dto.getSummary() != null) {
            profile.setSummary(dto.getSummary());
            profileMapper.update(profile);
        }

        log.info("Updated full profile for userId={}", userId);
        return getUserProfile(userId);
    }

    /**
     * 更新用户画像摘要
     */
    @Transactional
    public void updateSummary(Long userId, String summary) {
        profileMapper.updateSummary(userId, summary);
        log.info("Updated summary for userId={}", userId);
    }

    // ==================== LLM 上下文生成 ====================

    /**
     * 生成用于 LLM 的用户画像上下文（无消息过滤，返回全部画像）
     */
    public String generateUserProfileContextForLLM(Long userId) {
        return generateUserProfileContextForLLM(userId, null);
    }

    /**
     * 生成用于 LLM 的用户画像上下文
     * 根据用户消息筛选相关的画像信息，避免注入无关内容导致对话紊乱
     *
     * @param userId 用户ID
     * @param userMessage 用户当前消息（用于筛选相关画像，为null时返回全部画像）
     * @return 用户画像上下文文本，如果无相关画像则返回空字符串
     */
    public String generateUserProfileContextForLLM(Long userId, String userMessage) {
        if (userId == null) {
            return "";
        }

        UserProfileDTO profile = getUserProfile(userId);
        if (profile == null) {
            return "";
        }

        // 提取用户消息中的关键词
        List<String> keywords = extractKeywords(userMessage);

        StringBuilder sb = new StringBuilder();
        sb.append("👤 用户画像：\n");
        sb.append("```\n");

        boolean hasContent = false;

        // 兴趣 - 按关键词筛选
        if (profile.getInterests() != null && !profile.getInterests().isEmpty()) {
            List<UserProfileDTO.InterestItem> relevantItems = filterByKeywords(
                profile.getInterests(), keywords, item -> item.getName());
            if (!relevantItems.isEmpty()) {
                hasContent = true;
                sb.append("【兴趣方向】\n");
                for (UserProfileDTO.InterestItem item : relevantItems) {
                    sb.append("- ").append(item.getName());
                    if (item.getMentionCount() != null && item.getMentionCount() > 1) {
                        sb.append(" (提及").append(item.getMentionCount()).append("次)");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        // 性格特征 - 按关键词筛选
        if (profile.getTraits() != null && !profile.getTraits().isEmpty()) {
            List<UserProfileDTO.TraitItem> relevantItems = filterByKeywords(
                profile.getTraits(), keywords, item -> item.getName());
            if (!relevantItems.isEmpty()) {
                hasContent = true;
                sb.append("【性格特征】\n");
                for (UserProfileDTO.TraitItem item : relevantItems) {
                    sb.append("- ").append(item.getName());
                    if (item.getContexts() != null && !item.getContexts().isEmpty()) {
                        sb.append(" (").append(String.join("、", item.getContexts())).append(")");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        // 偏好 - 按关键词筛选
        if (profile.getPreferences() != null && !profile.getPreferences().isEmpty()) {
            List<UserProfileDTO.PreferenceItem> relevantItems = filterByKeywords(
                profile.getPreferences(), keywords, item -> item.getName());
            if (!relevantItems.isEmpty()) {
                hasContent = true;
                sb.append("【偏好设置】\n");
                for (UserProfileDTO.PreferenceItem item : relevantItems) {
                    sb.append("- ").append(item.getName());
                    if (item.getCategory() != null && !item.getCategory().isEmpty()) {
                        sb.append(" [").append(item.getCategory()).append("]");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        // 技能 - 按关键词筛选
        if (profile.getSkills() != null && !profile.getSkills().isEmpty()) {
            List<UserProfileDTO.SkillItem> relevantItems = filterByKeywords(
                profile.getSkills(), keywords, item -> item.getName());
            if (!relevantItems.isEmpty()) {
                hasContent = true;
                sb.append("【技能水平】\n");
                for (UserProfileDTO.SkillItem item : relevantItems) {
                    sb.append("- ").append(item.getName());
                    if (item.getLevel() != null && !item.getLevel().isEmpty()) {
                        sb.append(" (").append(item.getLevel()).append(")");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        // 摘要 - 只有关键词匹配时才显示
        if (profile.getSummary() != null && !profile.getSummary().isEmpty()) {
            if (keywords.isEmpty() || containsAnyKeyword(profile.getSummary(), keywords)) {
                hasContent = true;
                sb.append("【综合描述】\n");
                sb.append(profile.getSummary()).append("\n");
            }
        }

        sb.append("```\n");

        if (!hasContent) {
            return "";
        }

        return sb.toString();
    }

    /**
     * 从用户消息中提取关键词
     */
    private List<String> extractKeywords(String message) {
        if (message == null || message.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> keywords = new ArrayList<>();
        String[] tokens = message.replaceAll("[\\uFF0C\\u3002\\uFF01\\uFF1F\\u3001\\uFF1B\\uFF1A\\u201C\\u201D\\u2018\\u2019\\uFF08\\uFF09\\u3010\\u3011\\u300A\\u300B\\s]+", " ")
                .trim().split("\\s+");

        Set<String> stopWords = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "们", "那", "什么", "怎么", "如何", "请", "帮",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "shall", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "and",
            "but", "or", "not", "no", "if", "then", "so", "than", "too", "very"
        ));

        for (String token : tokens) {
            if (token.length() >= 2 && !stopWords.contains(token.toLowerCase())) {
                keywords.add(token.toLowerCase());
            }
        }

        return keywords.size() > 8 ? keywords.subList(0, 8) : keywords;
    }

    /**
     * 根据关键词筛选列表项
     */
    private <T> List<T> filterByKeywords(List<T> items, List<String> keywords, java.util.function.Function<T, String> nameExtractor) {
        if (keywords.isEmpty()) {
            // 无关键词时，返回高置信度的前3项
            return items.stream()
                .sorted((a, b) -> {
                    // 子类需要有 confidence 字段，这里简化处理
                    return 0;
                })
                .limit(3)
                .collect(java.util.stream.Collectors.toList());
        }

        List<T> result = new ArrayList<>();
        for (T item : items) {
            String name = nameExtractor.apply(item);
            if (name != null && containsAnyKeyword(name, keywords)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 检查文本是否包含任意关键词
     */
    private boolean containsAnyKeyword(String text, List<String> keywords) {
        if (text == null || keywords.isEmpty()) {
            return false;
        }
        String lowerText = text.toLowerCase();
        for (String keyword : keywords) {
            if (lowerText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
