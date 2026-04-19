package com.superfriend.superfriend.agent.tool;

public class ToolMetadata {
    private String name;
    private String description;
    private String category;
    private double cost;
    private double reliability;
    private long totalExecutions;
    private long successfulExecutions;
    
    public ToolMetadata(String name, String description, String category, 
                        double cost, double reliability) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.cost = cost;
        this.reliability = reliability;
        this.totalExecutions = 0;
        this.successfulExecutions = 0;
    }
    
    public void recordExecution(boolean success) {
        totalExecutions++;
        if (success) {
            successfulExecutions++;
        }
        updateReliability();
    }
    
    private void updateReliability() {
        if (totalExecutions > 0) {
            this.reliability = (double) successfulExecutions / totalExecutions;
        }
    }
    
    public void setReliability(double reliability) {
        this.reliability = reliability;
    }
    
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public double getCost() { return cost; }
    public double getReliability() { return reliability; }
}
