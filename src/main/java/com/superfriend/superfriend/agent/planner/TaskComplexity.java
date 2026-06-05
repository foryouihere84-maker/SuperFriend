package com.superfriend.superfriend.agent.planner;

public enum TaskComplexity {
    SIMPLE(1, "Simple task, single step"),
    MODERATE(2, "Moderate task, 2-5 steps"),
    COMPLEX(3, "Complex task, 5-10 steps"),
    VERY_COMPLEX(4, "Very complex task, 10+ steps");
    
    private final int level;
    private final String description;
    
    TaskComplexity(int level, String description) {
        this.level = level;
        this.description = description;
    }
    
    public int getLevel() { return level; }
    public String getDescription() { return description; }
}
