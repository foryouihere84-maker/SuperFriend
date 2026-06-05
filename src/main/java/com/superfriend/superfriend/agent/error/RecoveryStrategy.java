package com.superfriend.superfriend.agent.error;

import java.util.Map;

<<<<<<< HEAD
/**
 * 恢复策略接口和实现
 *
 * 【增强版】支持更多错误类型的恢复策略
 */
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
public interface RecoveryStrategy {
    RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context);
}

class NetworkRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
<<<<<<< HEAD
        int attempt = context.containsKey("attempt") ?
            (int) context.get("attempt") : 0;

        if (attempt < 3) {
            long delay = 1000 * (long) Math.pow(2, attempt);
            return RecoveryResult.retry("Network error detected, retrying after " +
                delay + "ms", delay);
        }

        // 超过重试次数，建议使用替代方案
        return RecoveryResult.withAlternative(
            "Network error persists after maximum retries, consider using alternative tools",
            "puppeteer_navigate");
=======
        int attempt = context.containsKey("attempt") ? 
            (int) context.get("attempt") : 0;
        
        if (attempt < 3) {
            long delay = 1000 * (long) Math.pow(2, attempt);
            return RecoveryResult.retry("Network error detected, retrying after " + 
                delay + "ms", delay);
        }
        
        return RecoveryResult.failure("Network error persists after maximum retries");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    }
}

class TimeoutRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
<<<<<<< HEAD
        int attempt = context.containsKey("attempt") ?
            (int) context.get("attempt") : 0;

=======
        int attempt = context.containsKey("attempt") ? 
            (int) context.get("attempt") : 0;
        
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        if (attempt < 3) {
            long delay = 2000 * (long) Math.pow(2, attempt);
            return RecoveryResult.retry("Timeout detected, retrying with increased timeout", delay);
        }
<<<<<<< HEAD

        return RecoveryResult.withAlternative(
            "Operation timed out after maximum retries, try alternative approach",
            "fetch");
=======
        
        return RecoveryResult.failure("Operation timed out after maximum retries");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    }
}

class AuthenticationRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
<<<<<<< HEAD
        // 认证错误通常需要用户干预
        return RecoveryResult.failure("Authentication failed, please refresh credentials or check API keys");
=======
        return RecoveryResult.failure("Authentication failed, please refresh credentials");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    }
}

class RateLimitRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
<<<<<<< HEAD
        int attempt = context.containsKey("attempt") ?
            (int) context.get("attempt") : 0;

        if (attempt < 5) {
            long delay = 5000 * (long) Math.pow(2, attempt);
            return RecoveryResult.retry("Rate limit exceeded, waiting " +
                delay + "ms before retry", delay);
        }

        return RecoveryResult.withAlternative(
            "Rate limit exceeded after maximum retries, try alternative service",
            null);
=======
        int attempt = context.containsKey("attempt") ? 
            (int) context.get("attempt") : 0;
        
        if (attempt < 5) {
            long delay = 5000 * attempt;
            return RecoveryResult.retry("Rate limit exceeded, waiting " + 
                delay + "ms before retry", delay);
        }
        
        return RecoveryResult.failure("Rate limit exceeded after maximum retries");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    }
}

class ServerErrorRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
<<<<<<< HEAD
        int attempt = context.containsKey("attempt") ?
            (int) context.get("attempt") : 0;

        if (attempt < 2) {
            long delay = 3000 * attempt;
            return RecoveryResult.retry("Server error detected, retrying after " +
                delay + "ms", delay);
        }

        return RecoveryResult.failure("Server error persists after maximum retries, service may be unavailable");
    }
}

/**
 * 【新增】验证错误恢复策略
 */
class ValidationErrorRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
        int attempt = context.containsKey("attempt") ?
            (int) context.get("attempt") : 0;

        if (attempt < 2) {
            // 验证错误通常需要调整参数
            return RecoveryResult.withAdjustment(
                "Validation failed, adjusting parameters and retrying",
                "Please check input parameters and ensure they meet the expected format");
        }

        return RecoveryResult.failure("Validation failed after retries, please check input parameters");
    }
}

/**
 * 【新增】资源未找到恢复策略
 */
class ResourceNotFoundRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
        String resourceName = context.containsKey("resourceName") ?
            (String) context.get("resourceName") : "unknown";

        // 资源未找到可能需要重新规划
        return RecoveryResult.replan(
            "Resource not found: " + resourceName + ", need to adjust plan or find alternative resource",
            "Consider searching for alternative resources or adjusting the task plan");
    }
}

/**
 * 【新增】权限拒绝恢复策略
 */
class PermissionDeniedRecoveryStrategy implements RecoveryStrategy {
    @Override
    public RecoveryResult recover(ErrorDiagnosis diagnosis, Map<String, Object> context) {
        String resource = context.containsKey("resource") ?
            (String) context.get("resource") : "unknown resource";

        // 权限问题通常需要跳过或使用替代方案
        return RecoveryResult.skip(
            "Permission denied for: " + resource + ", skipping this step or using alternative approach",
            "Try using a different tool or approach that doesn't require this permission");
=======
        int attempt = context.containsKey("attempt") ? 
            (int) context.get("attempt") : 0;
        
        if (attempt < 2) {
            long delay = 3000 * attempt;
            return RecoveryResult.retry("Server error detected, retrying after " + 
                delay + "ms", delay);
        }
        
        return RecoveryResult.failure("Server error persists after maximum retries");
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    }
}
