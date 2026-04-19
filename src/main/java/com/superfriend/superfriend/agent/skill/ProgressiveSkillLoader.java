package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ProgressiveSkillLoader {
    
    private final Map<String, SkillMetadata> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, String> instructionsCache = new ConcurrentHashMap<>();
    private final Map<String, List<SkillResource>> resourcesCache = new ConcurrentHashMap<>();
    private final Map<String, LoadState> loadStates = new ConcurrentHashMap<>();

    public enum LoadLevel {
        METADATA_ONLY,
        INSTRUCTIONS_LOADED,
        RESOURCES_LOADED,
        FULLY_LOADED
    }

    @Data
    public static class LoadState {
        private LoadLevel level;
        private long loadedTime;
        private long lastAccessedTime;
        private int accessCount;
    }

    public SkillMetadata loadMetadata(String skillName, String skillFilePath) {
        if (metadataCache.containsKey(skillName)) {
            updateAccessTime(skillName);
            return metadataCache.get(skillName);
        }

        try {
            SkillMetadata metadata = parseMetadata(skillFilePath);
            metadataCache.put(skillName, metadata);

            LoadState state = new LoadState();
            state.setLevel(LoadLevel.METADATA_ONLY);
            state.setLoadedTime(System.currentTimeMillis());
            state.setLastAccessedTime(System.currentTimeMillis());
            state.setAccessCount(1);
            loadStates.put(skillName, state);

            log.debug("Loaded metadata for skill: {}", skillName);
            return metadata;

        } catch (Exception e) {
            log.error("Failed to load metadata for skill: {} - {}", skillName, e.getMessage());
            return null;
        }
    }

    public String loadInstructions(String skillName, String skillFilePath) {
        LoadState state = loadStates.get(skillName);
        if (state != null && state.getLevel().ordinal() >= LoadLevel.INSTRUCTIONS_LOADED.ordinal()) {
            updateAccessTime(skillName);
            return instructionsCache.get(skillName);
        }

        try {
            String instructions = parseInstructions(skillFilePath);
            instructionsCache.put(skillName, instructions);

            if (state != null) {
                state.setLevel(LoadLevel.INSTRUCTIONS_LOADED);
                state.setLoadedTime(System.currentTimeMillis());
            }

            log.debug("Loaded instructions for skill: {}", skillName);
            return instructions;

        } catch (Exception e) {
            log.error("Failed to load instructions for skill: {} - {}", skillName, e.getMessage());
            return null;
        }
    }

    public List<SkillResource> loadResources(String skillName, String skillDirectory) {
        LoadState state = loadStates.get(skillName);
        if (state != null && state.getLevel().ordinal() >= LoadLevel.RESOURCES_LOADED.ordinal()) {
            updateAccessTime(skillName);
            return resourcesCache.get(skillName);
        }

        try {
            List<SkillResource> resources = scanResources(skillDirectory);
            resourcesCache.put(skillName, resources);

            if (state != null) {
                state.setLevel(LoadLevel.RESOURCES_LOADED);
                state.setLoadedTime(System.currentTimeMillis());
            }

            log.debug("Loaded {} resources for skill: {}", resources.size(), skillName);
            return resources;

        } catch (Exception e) {
            log.error("Failed to load resources for skill: {} - {}", skillName, e.getMessage());
            return Collections.emptyList();
        }
    }

    public void unloadSkill(String skillName) {
        metadataCache.remove(skillName);
        instructionsCache.remove(skillName);
        resourcesCache.remove(skillName);
        loadStates.remove(skillName);
        log.info("Unloaded skill: {}", skillName);
    }

    public void unloadUnusedSkills(long maxIdleTimeMs) {
        long now = System.currentTimeMillis();
        loadStates.forEach((skillName, state) -> {
            if (now - state.getLastAccessedTime() > maxIdleTimeMs) {
                unloadSkill(skillName);
            }
        });
    }

    public LoadLevel getLoadLevel(String skillName) {
        LoadState state = loadStates.get(skillName);
        return state != null ? state.getLevel() : null;
    }

    public Map<String, LoadLevel> getAllLoadLevels() {
        Map<String, LoadLevel> levels = new HashMap<>();
        loadStates.forEach((skillName, state) -> 
            levels.put(skillName, state.getLevel())
        );
        return levels;
    }

    private void updateAccessTime(String skillName) {
        LoadState state = loadStates.get(skillName);
        if (state != null) {
            state.setLastAccessedTime(System.currentTimeMillis());
            state.setAccessCount(state.getAccessCount() + 1);
        }
    }

    private SkillMetadata parseMetadata(String skillFilePath) {
        // Implementation will parse YAML frontmatter from SKILL.md or skill.yaml
        // This is a simplified version
        SkillMetadata metadata = new SkillMetadata();
        metadata.setName(skillFilePath);
        return metadata;
    }

    private String parseInstructions(String skillFilePath) {
        // Implementation will parse Markdown instructions from SKILL.md
        // This is a simplified version
        return "";
    }

    private List<SkillResource> scanResources(String skillDirectory) {
        // Implementation will scan scripts/, templates/, examples/ directories
        // This is a simplified version
        return new ArrayList<>();
    }

    public void clearCache() {
        metadataCache.clear();
        instructionsCache.clear();
        resourcesCache.clear();
        loadStates.clear();
        log.info("Cleared all skill caches");
    }
}
