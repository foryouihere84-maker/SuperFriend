package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
public class SkillFactory {
    
    private static final String SKILL_TEMPLATE_MD = 
        "---\n" +
        "name: ${skillName}\n" +
        "description: ${description}\n" +
        "license: Apache-2.0\n" +
        "compatibility: Requires ${compatibility}\n" +
        "metadata:\n" +
        "  author: ${author}\n" +
        "  version: \"1.0.0\"\n" +
        "  tags:\n" +
        "    - ${tag1}\n" +
        "    - ${tag2}\n" +
        "  priority: MEDIUM\n" +
        "allowed-tools: ${tool1}\n" +
        "---\n\n" +
        "# ${skillName} Skill\n\n" +
        "${instructions}\n";

    private static final String PYTHON_SCRIPT_TEMPLATE = 
        "#!/usr/bin/env python3\n" +
        "\"\"\"\n" +
        "${scriptDescription}\n" +
        "\"\"\"\n" +
        "\n" +
        "import argparse\n" +
        "import json\n" +
        "import sys\n" +
        "from typing import Dict, Any\n" +
        "\n" +
        "def main(args):\n" +
        "    \"\"\"\n" +
        "    Main execution function\n" +
        "    \"\"\"\n" +
        "    # TODO: Implement skill logic\n" +
        "    result = {\n" +
        "        \"success\": True,\n" +
        "        \"data\": {},\n" +
        "        \"message\": \"Skill executed successfully\"\n" +
        "    }\n" +
        "    \n" +
        "    print(json.dumps(result, indent=2))\n" +
        "    return 0\n" +
        "\n" +
        "if __name__ == \"__main__\":\n" +
        "    parser = argparse.ArgumentParser(description=\"${scriptDescription}\")\n" +
        "    parser.add_argument(\"--param1\", help=\"Parameter 1 description\")\n" +
        "    parser.add_argument(\"--param2\", help=\"Parameter 2 description\")\n" +
        "    \n" +
        "    args = parser.parse_args()\n" +
        "    sys.exit(main(args))\n";

    public FactoryResult createSkill(FactoryRequest request) {
        log.info("Creating skill: {}", request.getSkillName());

        try {
            validateRequest(request);

            String skillDir = createSkillDirectory(request);
            createSkillFile(skillDir, request);

            if (request.isIncludeScript()) {
                createScriptFile(skillDir, request);
            }

            if (request.isIncludeTemplate()) {
                createTemplateFile(skillDir, request);
            }

            if (request.isIncludeExample()) {
                createExampleFile(skillDir, request);
            }

            FactoryResult result = new FactoryResult();
            result.setSuccess(true);
            result.setSkillName(request.getSkillName());
            result.setSkillPath(skillDir);
            result.setMessage("Skill created successfully");

            log.info("Skill created: {} at {}", request.getSkillName(), skillDir);
            return result;

        } catch (Exception e) {
            log.error("Failed to create skill: {} - {}", request.getSkillName(), e.getMessage(), e);
            FactoryResult result = new FactoryResult();
            result.setSuccess(false);
            result.setError("Failed to create skill: " + e.getMessage());
            return result;
        }
    }

    private void validateRequest(FactoryRequest request) {
        if (request.getSkillName() == null || request.getSkillName().isEmpty()) {
            throw new IllegalArgumentException("Skill name is required");
        }

        if (request.getDescription() == null || request.getDescription().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }

        if (request.getCategory() == null || request.getCategory().isEmpty()) {
            throw new IllegalArgumentException("Category is required");
        }

        if (!request.getSkillName().matches("^[a-z0-9-]+$")) {
            throw new IllegalArgumentException("Skill name must be lowercase letters, numbers, and hyphens only");
        }
    }

    private String createSkillDirectory(FactoryRequest request) throws IOException {
        String basePath = getBasePathForScope(request.getScope());
        Path skillPath = Paths.get(basePath, request.getSkillName());
        
        if (Files.exists(skillPath)) {
            throw new IOException("Skill directory already exists: " + skillPath);
        }

        Files.createDirectories(skillPath);
        return skillPath.toString();
    }

    private void createSkillFile(String skillDir, FactoryRequest request) throws IOException {
        String content = SKILL_TEMPLATE_MD
            .replace("${skillName}", request.getSkillName())
            .replace("${description}", request.getDescription())
            .replace("${author}", request.getAuthor() != null ? request.getAuthor() : "Unknown")
            .replace("${compatibility}", request.getCategory())
            .replace("${tag1}", request.getTags().size() > 0 ? request.getTags().get(0) : "general")
            .replace("${tag2}", request.getTags().size() > 1 ? request.getTags().get(1) : "skill")
            .replace("${tool1}", request.getAllowedTools().size() > 0 ? request.getAllowedTools().get(0) : "filesystem")
            .replace("${instructions}", request.getInstructions() != null ? request.getInstructions() : "TODO: Add instructions");

        Path skillFile = Paths.get(skillDir, "SKILL.md");
        Files.write(skillFile, content.getBytes());
    }

    private void createScriptFile(String skillDir, FactoryRequest request) throws IOException {
        Path scriptsDir = Paths.get(skillDir, "scripts");
        Files.createDirectories(scriptsDir);

        String scriptContent = PYTHON_SCRIPT_TEMPLATE
            .replace("${scriptDescription}", request.getDescription());

        Path scriptFile = scriptsDir.resolve(request.getSkillName().replace("-", "_") + ".py");
        Files.write(scriptFile, scriptContent.getBytes());
    }

    private void createTemplateFile(String skillDir, FactoryRequest request) throws IOException {
        Path templatesDir = Paths.get(skillDir, "templates");
        Files.createDirectories(templatesDir);

        Path templateFile = templatesDir.resolve("template.json");
        Files.write(templateFile, "{\n  \"template\": \"example\"\n}".getBytes());
    }

    private void createExampleFile(String skillDir, FactoryRequest request) throws IOException {
        Path examplesDir = Paths.get(skillDir, "examples");
        Files.createDirectories(examplesDir);

        Path exampleFile = examplesDir.resolve("example.json");
        Files.write(exampleFile, "{\n  \"example\": \"data\"\n}".getBytes());
    }

    private String getBasePathForScope(SkillMetadata.SkillScope scope) {
        switch (scope) {
            case USER:
                return System.getProperty("user.home") + "/.harmonynotes/skills";
            case PROJECT:
                return "skills/project";
            default:
                return "skills/system";
        }
    }

    @Data
    public static class FactoryRequest {
        private String skillName;
        private String description;
        private String category;
        private String author;
        private List<String> tags = new ArrayList<>();
        private List<String> allowedTools = new ArrayList<>();
        private String instructions;
        private boolean includeScript = false;
        private boolean includeTemplate = false;
        private boolean includeExample = false;
        private SkillMetadata.SkillScope scope = SkillMetadata.SkillScope.USER;
    }

    @Data
    public static class FactoryResult {
        private boolean success;
        private String skillName;
        private String skillPath;
        private String message;
        private String error;
    }
}
