package com.superfriend.superfriend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SkillScript {
    private Long id;
    private Long skillId;
    private String scriptType;
    private String scriptName;
    private String scriptContent;
    private Integer isMain;
    private Integer executionOrder;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    
    public enum ScriptType {
        PYTHON("python", ".py"),
        BASH("bash", ".sh"),
        JAVASCRIPT("javascript", ".js"),
        TYPESCRIPT("typescript", ".ts"),
        RUBY("ruby", ".rb"),
        POWERSHELL("powershell", ".ps1");
        
        private final String name;
        private final String extension;
        
        ScriptType(String name, String extension) {
            this.name = name;
            this.extension = extension;
        }
        
        public String getName() { return name; }
        public String getExtension() { return extension; }
        
        public static ScriptType fromExtension(String ext) {
            String extension = ext.startsWith(".") ? ext : "." + ext;
            for (ScriptType type : values()) {
                if (type.extension.equalsIgnoreCase(extension)) {
                    return type;
                }
            }
            return PYTHON;
        }
        
        public static ScriptType fromName(String name) {
            for (ScriptType type : values()) {
                if (type.name.equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return PYTHON;
        }
    }
}
