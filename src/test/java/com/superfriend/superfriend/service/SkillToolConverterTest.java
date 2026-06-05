package com.superfriend.superfriend.service;

import com.superfriend.superfriend.agent.skill.*;
import com.superfriend.superfriend.dto.McpToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SkillToolConverterTest {

    private SkillToolConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SkillToolConverter();
    }

    @Test
    void testConvertSkillToToolDefinition() {
        SkillConfig config = new SkillConfig();
        config.setName("code-review");
        config.setDescription("Analyze code quality and provide improvement suggestions");
        config.setCategory("development");
        config.setVersion("1.0.0");
        config.setTags(Arrays.asList("code-review", "quality", "analysis"));
        config.setAllowedTools(Arrays.asList("Read", "Write", "Glob", "Grep"));

        Map<String, Object> parameters = new HashMap<>();
        Map<String, Object> targetFileParam = new HashMap<>();
        targetFileParam.put("type", "string");
        targetFileParam.put("description", "Path to the file to review");
        targetFileParam.put("required", true);
        parameters.put("targetFile", targetFileParam);

        Map<String, Object> outputFileParam = new HashMap<>();
        outputFileParam.put("type", "string");
        outputFileParam.put("description", "Path to save the review report");
        outputFileParam.put("required", false);
        parameters.put("outputFile", outputFileParam);

        config.setParameters(parameters);

        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test-skill");
        tempDir.mkdirs();
        DynamicSkill skill = new DynamicSkill(config, tempDir, SkillMetadata.SkillScope.SYSTEM);

        McpToolDefinition tool = converter.convertSkillToToolDefinition(skill);

        assertNotNull(tool);
        assertEquals("skill__code-review", tool.getName());
        assertEquals("skills", tool.getServerName());
        assertNotNull(tool.getDescription());
        assertTrue(tool.getDescription().contains("Analyze code quality"));
        assertTrue(tool.getDescription().contains("Category: development"));
        assertTrue(tool.getDescription().contains("Tags: code-review, quality, analysis"));
        assertTrue(tool.getDescription().contains("Uses tools: Read, Write, Glob, Grep"));

        assertNotNull(tool.getInputSchema());
        assertEquals("object", tool.getInputSchema().getType());
        assertNotNull(tool.getInputSchema().getProperties());
        assertTrue(tool.getInputSchema().getProperties().containsKey("targetFile"));
        assertTrue(tool.getInputSchema().getProperties().containsKey("outputFile"));
    }

    @Test
    void testConvertSkillsToToolDefinitions() {
        List<Skill> skills = new ArrayList<>();

        SkillConfig config1 = new SkillConfig();
        config1.setName("code-review");
        config1.setDescription("Code review skill");
        config1.setCategory("development");
        config1.setTags(Arrays.asList("review"));

        SkillConfig config2 = new SkillConfig();
        config2.setName("api-tester");
        config2.setDescription("API testing skill");
        config2.setCategory("testing");
        config2.setTags(Arrays.asList("api", "test"));

        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test-skills");
        tempDir.mkdirs();

        skills.add(new DynamicSkill(config1, tempDir, SkillMetadata.SkillScope.SYSTEM));
        skills.add(new DynamicSkill(config2, tempDir, SkillMetadata.SkillScope.SYSTEM));

        List<McpToolDefinition> tools = converter.convertSkillsToToolDefinitions(skills);

        assertNotNull(tools);
        assertEquals(2, tools.size());

        assertEquals("skill__code-review", tools.get(0).getName());
        assertEquals("skill__api-tester", tools.get(1).getName());
    }

    @Test
    void testConvertEmptySkills() {
        List<McpToolDefinition> tools = converter.convertSkillsToToolDefinitions(null);
        assertNotNull(tools);
        assertTrue(tools.isEmpty());

        tools = converter.convertSkillsToToolDefinitions(new ArrayList<>());
        assertNotNull(tools);
        assertTrue(tools.isEmpty());
    }

    @Test
    void testIsSkillTool() {
        assertTrue(SkillToolConverter.isSkillTool("skill__code-review"));
        assertTrue(SkillToolConverter.isSkillTool("skill__api-tester"));
        assertFalse(SkillToolConverter.isSkillTool("filesystem__read_file"));
        assertFalse(SkillToolConverter.isSkillTool("load_skill"));
        assertFalse(SkillToolConverter.isSkillTool(null));
    }

    @Test
    void testExtractSkillName() {
        assertEquals("code-review", SkillToolConverter.extractSkillName("skill__code-review"));
        assertEquals("api-tester", SkillToolConverter.extractSkillName("skill__api-tester"));
        assertEquals("filesystem__read", SkillToolConverter.extractSkillName("filesystem__read"));
    }

    @Test
    void testSkillWithNoParameters() {
        SkillConfig config = new SkillConfig();
        config.setName("simple-skill");
        config.setDescription("A simple skill with no parameters");

        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test-skill");
        tempDir.mkdirs();
        DynamicSkill skill = new DynamicSkill(config, tempDir, SkillMetadata.SkillScope.SYSTEM);

        McpToolDefinition tool = converter.convertSkillToToolDefinition(skill);

        assertNotNull(tool);
        assertEquals("skill__simple-skill", tool.getName());
        assertNotNull(tool.getInputSchema());
        assertEquals("object", tool.getInputSchema().getType());
    }
}
