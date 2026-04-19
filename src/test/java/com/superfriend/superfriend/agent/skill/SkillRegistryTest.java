package com.superfriend.superfriend.agent.skill;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryTest {
    
    @Test
    void testGetAllSkills_Empty() {
        SkillRegistry registry = new SkillRegistry();
        
        List<Skill> skills = registry.getAllSkills();
        
        assertNotNull(skills);
        assertTrue(skills.isEmpty());
    }
    
    @Test
    void testRegisterAndUnregisterSkill() {
        SkillRegistry registry = new SkillRegistry();
        
        File skillDir = new File(System.getProperty("java.io.tmpdir"), "test-skill");
        skillDir.mkdirs();
        
        SkillConfig config = new SkillConfig("test-skill", "Test skill");
        config.setCategory("test");
        config.setTags(java.util.Arrays.asList("test"));
        
        TestDynamicSkill skill = new TestDynamicSkill(
            config,
            skillDir,
            SkillMetadata.SkillScope.USER
        );
        
        registry.register(skill);
        
        Skill retrievedSkill = registry.getSkill("test-skill");
        assertNotNull(retrievedSkill);
        assertEquals("test-skill", retrievedSkill.getName());
        
        registry.unregister("test-skill");
        
        Skill removedSkill = registry.getSkill("test-skill");
        assertNull(removedSkill);
    }
    
    @Test
    void testGetSkillsByCategory() {
        SkillRegistry registry = new SkillRegistry();
        
        File skillDir1 = new File(System.getProperty("java.io.tmpdir"), "skill1");
        skillDir1.mkdirs();
        File skillDir2 = new File(System.getProperty("java.io.tmpdir"), "skill2");
        skillDir2.mkdirs();
        File skillDir3 = new File(System.getProperty("java.io.tmpdir"), "skill3");
        skillDir3.mkdirs();
        
        SkillConfig config1 = new SkillConfig("skill1", "Skill 1");
        config1.setCategory("categoryA");
        config1.setTags(java.util.Arrays.asList("categoryA"));
        SkillConfig config2 = new SkillConfig("skill2", "Skill 2");
        config2.setCategory("categoryA");
        config2.setTags(java.util.Arrays.asList("categoryA"));
        SkillConfig config3 = new SkillConfig("skill3", "Skill 3");
        config3.setCategory("categoryB");
        config3.setTags(java.util.Arrays.asList("categoryB"));
        
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
        TestDynamicSkill skill3 = new TestDynamicSkill(
            config3,
            skillDir3,
            SkillMetadata.SkillScope.USER
        );
        
        registry.register(skill1);
        registry.register(skill2);
        registry.register(skill3);
        
        List<Skill> categoryASkills = registry.getSkillsByCategory("categoryA");
        
        assertNotNull(categoryASkills);
        assertEquals(2, categoryASkills.size());
    }
    
    @Test
    void testSearchSkills() {
        SkillRegistry registry = new SkillRegistry();
        
        File skillDir = new File(System.getProperty("java.io.tmpdir"), "search-skill");
        skillDir.mkdirs();
        
        SkillConfig config = new SkillConfig("search-skill", "Skill for searching");
        config.setCategory("test");
        config.setTags(java.util.Arrays.asList("search"));
        
        TestDynamicSkill skill1 = new TestDynamicSkill(
            config,
            skillDir,
            SkillMetadata.SkillScope.USER
        );
        
        registry.register(skill1);
        
        List<Skill> results = registry.searchSkills("search");
        
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
    }
    
    @Test
    void testGetSkillCount() {
        SkillRegistry registry = new SkillRegistry();
        
        assertEquals(0, registry.getSkillCount());
        
        File skillDir = new File(System.getProperty("java.io.tmpdir"), "test-skill");
        skillDir.mkdirs();
        
        SkillConfig config = new SkillConfig("test-skill", "Test skill");
        config.setCategory("test");
        config.setTags(java.util.Arrays.asList("test"));
        
        TestDynamicSkill skill = new TestDynamicSkill(
            config,
            skillDir,
            SkillMetadata.SkillScope.USER
        );
        
        registry.register(skill);
        
        assertEquals(1, registry.getSkillCount());
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
}
