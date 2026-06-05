package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class BatchExecutionResult {
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private Long totalExecutionTime;
    private List<BatchExecutionItem> results;
    
    @Data
    public static class BatchExecutionItem {
        private String skillName;
        private Boolean success;
        private Object data;
        private String error;
        private Long executionTime;
        private String errorCode;
        private String suggestion;
    }
    
    public static BatchExecutionResult of(List<BatchExecutionItem> results) {
        BatchExecutionResult result = new BatchExecutionResult();
        result.setResults(results);
        result.setTotalCount(results.size());
        result.setSuccessCount((int) results.stream().filter(BatchExecutionItem::getSuccess).count());
        result.setFailureCount((int) results.stream().filter(r -> !r.getSuccess()).count());
        result.setTotalExecutionTime(results.stream().mapToLong(BatchExecutionItem::getExecutionTime).sum());
        return result;
    }
}
