package com.superfriend.superfriend.agent.error;

import java.util.List;

public class ErrorDiagnosis {
    private ErrorRecord errorRecord;
    private ErrorSeverity severity;
    private String rootCause;
    private List<String> recoveryStrategies;
    private List<String> preventionMeasures;
    
    public ErrorDiagnosis(ErrorRecord errorRecord) {
        this.errorRecord = errorRecord;
        this.recoveryStrategies = new java.util.ArrayList<>();
        this.preventionMeasures = new java.util.ArrayList<>();
    }
    
    public ErrorRecord getErrorRecord() { return errorRecord; }
    public ErrorSeverity getSeverity() { return severity; }
    public String getRootCause() { return rootCause; }
    public List<String> getRecoveryStrategies() { return recoveryStrategies; }
    public List<String> getPreventionMeasures() { return preventionMeasures; }
    
    public void setSeverity(ErrorSeverity severity) { this.severity = severity; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public void setRecoveryStrategies(List<String> recoveryStrategies) { 
        this.recoveryStrategies = recoveryStrategies; 
    }
    public void setPreventionMeasures(List<String> preventionMeasures) { 
        this.preventionMeasures = preventionMeasures; 
    }
}
