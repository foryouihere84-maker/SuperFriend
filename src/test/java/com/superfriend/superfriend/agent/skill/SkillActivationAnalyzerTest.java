package com.superfriend.superfriend.agent.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillActivationAnalyzerTest {
    
    private SkillActivationAnalyzer analyzer;
    private Skill testSkill;
    
    @BeforeEach
    void setUp() {
        analyzer = new SkillActivationAnalyzer();
        
        File skillDir = new File(System.getProperty("java.io.tmpdir"), "test-skill");
        skillDir.mkdirs();
        
        SkillConfig config = new SkillConfig("test-skill", "Test skill for unit testing");
        config.setCategory("test");
        config.setTags(java.util.Arrays.asList("unit", "testing"));
        config.setDescription("USE WHEN test task or unit testing");
        
        testSkill = new TestDynamicSkill(
            config,
            skillDir,
            SkillMetadata.SkillScope.USER
        );
    }
    
    private static class TestDynamicSkill extends DynamicSkill {
        public TestDynamicSkill(SkillConfig config, File skillDirectory, SkillMetadata.SkillScope scope) {
            super(config, skillDirectory, scope);
        }
        
        @Override
        public boolean isApplicable(String userRequest) {
            if (userRequest == null || userRequest.isEmpty()) {
                return false;
            }
            
            String lowerRequest = userRequest.toLowerCase();
            
            SkillConfig config = getConfig();
            
            for (String tag : config.getTags()) {
                if (lowerRequest.contains(tag.toLowerCase())) {
                    return true;
                }
            }
            
            if (lowerRequest.contains(config.getName().toLowerCase())) {
                return true;
            }
            
            String category = config.getCategory().toLowerCase();
            if (lowerRequest.contains(category)) {
                return true;
            }
            
            return false;
        }
    }
    
    @Test
    void testAnalyzeActivation_ExactMatch() {
        String userRequest = "请使用 test-skill 来处理这个任务";
        
        SkillActivationAnalyzer.ActivationResult result = analyzer.analyzeActivation(testSkill, userRequest);
        
        assertNotNull(result);
        assertTrue(result.isActivated(), "Skill should be activated for exact name match");
        assertTrue(result.getScore() >= 0.2, "Score should be above threshold");
    }
    
    @Test
    void testAnalyzeActivation_CategoryMatch() {
        String userRequest = "这是一个 test 类型的任务，需要使用 test-skill";
        
        SkillActivationAnalyzer.ActivationResult result = analyzer.analyzeActivation(testSkill, userRequest);
        
        assertNotNull(result);
        assertTrue(result.isActivated(), "Skill should be activated for combined keyword and name match");
    }
    
    @Test
    void testAnalyzeActivation_NoMatch() {
        String userRequest = "这是一个完全不相关的任务";
        
        SkillActivationAnalyzer.ActivationResult result = analyzer.analyzeActivation(testSkill, userRequest);
        
        assertNotNull(result);
        assertFalse(result.isActivated(), "Skill should not be activated for unrelated request");
    }
    
    @Test
    void testRankSkills() {
        File skillDir1 = new File(System.getProperty("java.io.tmpdir"), "high-priority-skill");
        skillDir1.mkdirs();
        File skillDir2 = new File(System.getProperty("java.io.tmpdir"), "low-priority-skill");
        skillDir2.mkdirs();
        
        SkillConfig config1 = new SkillConfig("high-priority-skill", "High priority skill");
        config1.setCategory("test");
        config1.setTags(java.util.Arrays.asList("high", "priority"));
        config1.setDescription("USE WHEN high priority task");

        SkillConfig config2 = new SkillConfig("low-priority-skill", "Low priority skill");
        config2.setCategory("test");
        config2.setTags(java.util.Arrays.asList("low", "priority"));
        config2.setDescription("USE WHEN low priority task");
        
        TestDynamicSkill skill1 = new TestDynamicSkill(
            config1,
            skillDir1,
            SkillMetadata.SkillScope.USER
        );
        
        TestDynamicSkill skill2 = new TestDynamicSkill(
            config2,
            skillDir2,
            SkillMetadata.SkillScope.USER
        );
        
        List<Skill> skills = java.util.Arrays.asList(skill1, skill2);
        String userRequest = "high priority task";
        
        List<SkillActivationAnalyzer.ActivationResult> results = analyzer.rankSkills(skills, userRequest);
        
        assertNotNull(results);
        assertFalse(results.isEmpty(), "At least one skill should match with high relevance");
        assertEquals(skill1.getName(), results.get(0).getSkillName(), "Highest priority skill should be ranked first");
    }
}
