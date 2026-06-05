package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import java.io.File;
import java.util.List;
import java.util.Map;

@Data
public class SkillResource {
    private String type; // "script", "template", "example", "reference"
    private String path;
    private String language; // "python", "bash", "javascript", etc.
    private String description;
    private Map<String, Object> parameters;
    private boolean executable;
    private String executionCommand;
    private List<String> dependencies;

    public enum ResourceType {
        SCRIPT("scripts/"),
        TEMPLATE("templates/"),
        EXAMPLE("examples/"),
        REFERENCE("references/"),
        DATA("data/");

        private final String directory;

        ResourceType(String directory) {
            this.directory = directory;
        }

        public String getDirectory() {
            return directory;
        }
    }
}
