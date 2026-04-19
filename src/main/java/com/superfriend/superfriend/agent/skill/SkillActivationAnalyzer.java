package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SkillActivationAnalyzer {
    
    @Value("${skills.activation.default-threshold:0.3}")
    private double defaultThreshold;
    
    @Value("${skills.activation.base-score-weight:0.2}")
    private double baseScoreWeight;
    
    @Value("${skills.activation.use-when-score-weight:0.4}")
    private double useWhenScoreWeight;
    
    @Value("${skills.activation.keyword-score-weight:0.3}")
    private double keywordScoreWeight;
    
    @Value("${skills.activation.context-score-weight:0.1}")
    private double contextScoreWeight;
    
    private static final Pattern USE_WHEN_PATTERN = Pattern.compile(
        "USE WHEN\\s+(.+?)(?=\\n|$)", 
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    public ActivationResult analyzeActivation(Skill skill, String userRequest) {
        ActivationResult result = new ActivationResult();
        result.setSkillName(skill.getName());

        double baseScore = calculateBaseScore(skill, userRequest);
        double useWhenScore = extractUseWhenScore(skill, userRequest);
        double keywordScore = calculateKeywordScore(skill, userRequest);
        double contextScore = calculateContextScore(skill, userRequest);

        double finalScore = (baseScore * baseScoreWeight + useWhenScore * useWhenScoreWeight + 
                           keywordScore * keywordScoreWeight + contextScore * contextScoreWeight);

        result.setScore(finalScore);
        result.setActivated(finalScore >= defaultThreshold);
        result.setConfidence(calculateConfidence(finalScore));

        log.debug("Activation analysis for {}: base={}, useWhen={}, keyword={}, context={}, final={}",
            skill.getName(), baseScore, useWhenScore, keywordScore, contextScore, finalScore);

        return result;
    }

    private double calculateBaseScore(Skill skill, String userRequest) {
        String lowerRequest = userRequest.toLowerCase();
        String skillName = skill.getName();
        String category = skill.getCategory();

        double score = 0.0;

        if (skillName != null && lowerRequest.contains(skillName.toLowerCase())) {
            score += 0.5;
        }

        if (category != null && lowerRequest.contains(category.toLowerCase())) {
            score += 0.3;
        }

        return Math.min(score, 1.0);
    }

    private double extractUseWhenScore(Skill skill, String userRequest) {
        if (!(skill instanceof DynamicSkill)) {
            return 0.0;
        }

        DynamicSkill dynamicSkill = (DynamicSkill) skill;
        
        if (dynamicSkill.getConfig() == null) {
            return 0.0;
        }
        
        String description = dynamicSkill.getConfig().getDescription();

        if (description == null) {
            return 0.0;
        }

        Matcher matcher = USE_WHEN_PATTERN.matcher(description);
        if (!matcher.find()) {
            return 0.0;
        }

        String useWhenClause = matcher.group(1).toLowerCase();
        String lowerRequest = userRequest.toLowerCase();

        String[] conditions = useWhenClause.split(",|or|and");
        int matchedConditions = 0;

        for (String condition : conditions) {
            String cleanCondition = condition.trim()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim();

            if (cleanCondition.isEmpty()) continue;

            String[] keywords = cleanCondition.split("\\s+");
            int matchedKeywords = 0;

            for (String keyword : keywords) {
                if (keyword.length() > 2 && lowerRequest.contains(keyword)) {
                    matchedKeywords++;
                }
            }

            if (matchedKeywords >= keywords.length * 0.5) {
                matchedConditions++;
            }
        }

        return matchedConditions > 0 ? 
            Math.min((double) matchedConditions / conditions.length, 1.0) : 0.0;
    }

    private double calculateKeywordScore(Skill skill, String userRequest) {
        String lowerRequest = userRequest.toLowerCase();
        List<String> tags = skill.getTags();

        if (tags == null || tags.isEmpty()) {
            return 0.0;
        }

        int matchedTags = 0;
        for (String tag : tags) {
            String lowerTag = tag.toLowerCase();
            if (lowerRequest.contains(lowerTag)) {
                matchedTags++;
            } else {
                String pattern = "\\b" + Pattern.quote(lowerTag) + "\\b";
                if (lowerRequest.matches(".*" + pattern + ".*")) {
                    matchedTags++;
                }
            }
        }

        return (double) matchedTags / tags.size();
    }

    private double calculateContextScore(Skill skill, String userRequest) {
        double score = 0.0;

        SkillMetadata metadata = skill.getMetadata();
        if (metadata == null) {
            return 0.0;
        }

        double successRate = metadata.getSuccessRate();
        score += successRate * 0.3;

        SkillMetadata.SkillPriority priority = metadata.getPriority();
        if (priority != null) {
            score += (priority.getValue() / 10.0) * 0.2;
        }

        long executionCount = metadata.getExecutionCount();
        if (executionCount > 0) {
            score += Math.min(executionCount / 100.0, 0.5);
        }

        return Math.min(score, 1.0);
    }

    private String calculateConfidence(double score) {
        if (score >= 0.8) {
            return "HIGH";
        } else if (score >= 0.5) {
            return "MEDIUM";
        } else if (score >= 0.3) {
            return "LOW";
        } else {
            return "VERY_LOW";
        }
    }

    public List<ActivationResult> rankSkills(List<Skill> skills, String userRequest) {
        return skills.stream()
            .map(skill -> analyzeActivation(skill, userRequest))
            .filter(ActivationResult::isActivated)
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .collect(Collectors.toList());
    }

    @Data
    public static class ActivationResult {
        private String skillName;
        private double score;
        private boolean activated;
        private String confidence;
        private Map<String, Double> scoreBreakdown;
    }
}
