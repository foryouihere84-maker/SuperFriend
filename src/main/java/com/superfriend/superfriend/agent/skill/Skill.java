package com.superfriend.superfriend.agent.skill;

import java.util.Map;
import java.util.List;

public interface Skill {
    String getName();
    String getDescription();
    String getCategory();
    String getVersion();
    String getInstructions();
    List<String> getAllowedTools();
    List<String> getTags();
    Map<String, Object> getParameters();
    SkillMetadata getMetadata();
    SkillResult execute(SkillContext context);
    boolean validateContext(SkillContext context);
    boolean isApplicable(String userRequest);
}
