package com.superfriend.superfriend.agent.skill;

import com.superfriend.superfriend.constant.SkillErrorCode;
import com.superfriend.superfriend.dto.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class WorkflowExecutor {

    @Autowired
    private SkillExecutor skillExecutor;

    @Autowired
    private SkillRegistry skillRegistry;

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public WorkflowResult execute(SkillWorkflow workflow, SkillContext baseContext) {
        long startTime = System.currentTimeMillis();
        String workflowId = workflow.getWorkflowId() != null ? 
            workflow.getWorkflowId() : UUID.randomUUID().toString();
        
        log.info("开始执行工作流: id={}, name={}, steps={}", 
            workflowId, workflow.getName(), workflow.getSteps().size());
        
        Map<String, Object> context = new HashMap<>();
        if (workflow.getContext() != null) {
            context.putAll(workflow.getContext());
        }
        context.putAll(baseContext.getVariables());
        
        List<WorkflowResult.StepResult> stepResults = new ArrayList<>();
        Map<String, Object> outputs = new HashMap<>();
        
        try {
            for (SkillWorkflow.WorkflowStep step : workflow.getSteps()) {
                if (!shouldExecuteStep(step, context)) {
                    log.info("跳过步骤: stepId={}, skillName={}", step.getStepId(), step.getSkillName());
                    continue;
                }
                
                WorkflowResult.StepResult stepResult = executeStep(step, context, baseContext, workflowId);
                stepResults.add(stepResult);
                
                if (stepResult.getSuccess()) {
                    if (step.getOutputKey() != null && stepResult.getOutput() != null) {
                        context.put(step.getOutputKey(), stepResult.getOutput());
                        outputs.put(step.getOutputKey(), stepResult.getOutput());
                    }
                } else {
                    if (workflow.getConfig() != null && workflow.getConfig().getStopOnError()) {
                        log.warn("工作流因步骤失败而停止: stepId={}, error={}", 
                            step.getStepId(), stepResult.getError());
                        return WorkflowResult.failure(workflowId, stepResult.getError(), 
                            stepResults, System.currentTimeMillis() - startTime);
                    }
                    
                    if (step.getErrorHandler() != null) {
                        WorkflowResult.StepResult fallbackResult = handleStepError(step, context, baseContext, workflowId);
                        stepResults.add(fallbackResult);
                        if (fallbackResult.getSuccess() && step.getOutputKey() != null) {
                            context.put(step.getOutputKey(), fallbackResult.getOutput());
                            outputs.put(step.getOutputKey(), fallbackResult.getOutput());
                        }
                    }
                }
            }
            
            log.info("工作流执行完成: id={}, success={}, time={}ms", 
                workflowId, true, System.currentTimeMillis() - startTime);
            return WorkflowResult.success(workflowId, outputs, stepResults, 
                System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            log.error("工作流执行异常: id={}, error={}", workflowId, e.getMessage(), e);
            return WorkflowResult.failure(workflowId, "工作流执行异常: " + e.getMessage(), 
                stepResults, System.currentTimeMillis() - startTime);
        }
    }
    
    private boolean shouldExecuteStep(SkillWorkflow.WorkflowStep step, Map<String, Object> context) {
        if (step.getCondition() == null) {
            return true;
        }
        
        try {
            SkillWorkflow.WorkflowCondition condition = step.getCondition();
            StandardEvaluationContext evalContext = new StandardEvaluationContext();
            evalContext.setVariables(context);
            
            if ("expression".equals(condition.getType())) {
                Boolean result = expressionParser.parseExpression(
                    condition.getExpression()).getValue(evalContext, Boolean.class);
                return result != null && result;
            } else if ("equals".equals(condition.getType())) {
                Object value = expressionParser.parseExpression(
                    condition.getExpression()).getValue(evalContext);
                return Objects.equals(String.valueOf(value), condition.getExpectedValue());
            }
            
            return true;
        } catch (Exception e) {
            log.warn("条件评估失败: stepId={}, error={}", step.getStepId(), e.getMessage());
            return false;
        }
    }
    
    private WorkflowResult.StepResult executeStep(SkillWorkflow.WorkflowStep step, 
            Map<String, Object> context, SkillContext baseContext, String workflowId) {
        long stepStartTime = System.currentTimeMillis();
        WorkflowResult.StepResult stepResult = new WorkflowResult.StepResult();
        stepResult.setStepId(step.getStepId());
        stepResult.setSkillName(step.getSkillName());
        
        try {
            SkillContext stepContext = new SkillContext();
            stepContext.setSessionId(baseContext.getSessionId());
            stepContext.setUserId(baseContext.getUserId());
            stepContext.setUserRequest(baseContext.getUserRequest());
            
            Map<String, Object> parameters = new HashMap<>();
            if (step.getParameters() != null) {
                parameters.putAll(step.getParameters());
            }
            
            if (step.getInputKey() != null && context.containsKey(step.getInputKey())) {
                parameters.put("input", context.get(step.getInputKey()));
            }
            
            stepContext.setParameters(parameters);
            stepContext.setVariables(context);
            
            int retryCount = step.getRetryCount() != null ? step.getRetryCount() : 0;
            SkillResult result = null;
            Exception lastException = null;
            
            for (int attempt = 0; attempt <= retryCount; attempt++) {
                try {
                    result = skillExecutor.execute(step.getSkillName(), stepContext);
                    if (result.isSuccess()) {
                        break;
                    }
                } catch (Exception e) {
                    lastException = e;
                    if (attempt < retryCount) {
                        log.warn("步骤执行失败，准备重试: stepId={}, attempt={}/{}", 
                            step.getStepId(), attempt + 1, retryCount);
                        Thread.sleep(1000 * (attempt + 1));
                    }
                }
            }
            
            if (result != null && result.isSuccess()) {
                stepResult.setSuccess(true);
                stepResult.setOutput(result.getData());
                stepResult.setExecutionTime(System.currentTimeMillis() - stepStartTime);
                log.info("步骤执行成功: workflowId={}, stepId={}, skillName={}, time={}ms", 
                    workflowId, step.getStepId(), step.getSkillName(), stepResult.getExecutionTime());
            } else {
                String error = result != null ? result.getError() : 
                    (lastException != null ? lastException.getMessage() : "未知错误");
                stepResult.setSuccess(false);
                stepResult.setError(error);
                stepResult.setExecutionTime(System.currentTimeMillis() - stepStartTime);
                log.error("步骤执行失败: workflowId={}, stepId={}, error={}", 
                    workflowId, step.getStepId(), error);
            }
            
        } catch (Exception e) {
            stepResult.setSuccess(false);
            stepResult.setError("步骤执行异常: " + e.getMessage());
            stepResult.setExecutionTime(System.currentTimeMillis() - stepStartTime);
            log.error("步骤执行异常: workflowId={}, stepId={}, error={}", 
                workflowId, step.getStepId(), e.getMessage(), e);
        }
        
        return stepResult;
    }
    
    private WorkflowResult.StepResult handleStepError(SkillWorkflow.WorkflowStep step,
            Map<String, Object> context, SkillContext baseContext, String workflowId) {
        log.info("执行错误处理: stepId={}, strategy={}", step.getStepId(), step.getErrorHandler().getStrategy());
        
        SkillWorkflow.WorkflowErrorHandler handler = step.getErrorHandler();
        
        if ("fallback".equals(handler.getStrategy()) && handler.getFallbackSkill() != null) {
            SkillWorkflow.WorkflowStep fallbackStep = new SkillWorkflow.WorkflowStep();
            fallbackStep.setStepId(step.getStepId() + "_fallback");
            fallbackStep.setSkillName(handler.getFallbackSkill());
            fallbackStep.setParameters(handler.getFallbackParameters());
            fallbackStep.setInputKey(step.getInputKey());
            fallbackStep.setOutputKey(step.getOutputKey());
            
            return executeStep(fallbackStep, context, baseContext, workflowId);
        }
        
        WorkflowResult.StepResult errorResult = new WorkflowResult.StepResult();
        errorResult.setStepId(step.getStepId());
        errorResult.setSkillName(step.getSkillName());
        errorResult.setSuccess(false);
        errorResult.setError("错误处理策略未实现: " + handler.getStrategy());
        errorResult.setExecutionTime(0L);
        return errorResult;
    }
}
