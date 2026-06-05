export enum SandboxErrorCode {
    UNKNOWN = 'UNKNOWN_ERROR',
    INVALID_PARAMS = 'INVALID_PARAMS',
    SESSION_NOT_FOUND = 'SESSION_NOT_FOUND',
    SESSION_EXPIRED = 'SESSION_EXPIRED',
    COMMAND_BLOCKED = 'COMMAND_BLOCKED',
    RATE_LIMITED = 'RATE_LIMITED',
    RESOURCE_INSUFFICIENT = 'RESOURCE_INSUFFICIENT',
    TIMEOUT = 'TIMEOUT',
    PERMISSION_DENIED = 'PERMISSION_DENIED',
    FILE_NOT_FOUND = 'FILE_NOT_FOUND',
    DIRECTORY_NOT_FOUND = 'DIRECTORY_NOT_FOUND',
    PROCESS_ERROR = 'PROCESS_ERROR',
    SYSTEM_ERROR = 'SYSTEM_ERROR',
    INTERNAL_ERROR = 'INTERNAL_ERROR'
}

export interface SandboxErrorDetails {
    code: SandboxErrorCode;
    message: string;
    reason?: string;
    sessionId?: string;
    command?: string;
    exitCode?: number;
    retryable?: boolean;
    retryAfterMs?: number;
    timestamp: string;
    stack?: string;
}

export class SandboxError extends Error {
    public readonly code: SandboxErrorCode;
    public readonly reason?: string;
    public readonly sessionId?: string;
    public readonly command?: string;
    public readonly exitCode?: number;
    public readonly retryable: boolean;
    public readonly retryAfterMs?: number;
    public readonly timestamp: string;

    constructor(details: Partial<SandboxErrorDetails> & { code: SandboxErrorCode; message: string }) {
        super(details.message);
        this.name = 'SandboxError';
        this.code = details.code;
        this.reason = details.reason;
        this.sessionId = details.sessionId;
        this.command = details.command;
        this.exitCode = details.exitCode;
        this.retryable = details.retryable ?? false;
        this.retryAfterMs = details.retryAfterMs;
        this.timestamp = details.timestamp || new Date().toISOString();
        
        if (details.stack) {
            this.stack = details.stack;
        }
    }

    static fromError(error: Error, sessionId?: string, command?: string): SandboxError {
        if (error instanceof SandboxError) {
            return error;
        }

        return new SandboxError({
            code: SandboxErrorCode.INTERNAL_ERROR,
            message: error.message,
            reason: error.stack,
            sessionId,
            command,
            retryable: false
        });
    }

    toJSON(): SandboxErrorDetails {
        return {
            code: this.code,
            message: this.message,
            reason: this.reason,
            sessionId: this.sessionId,
            command: this.command,
            exitCode: this.exitCode,
            retryable: this.retryable,
            retryAfterMs: this.retryAfterMs,
            timestamp: this.timestamp,
            stack: this.stack
        };
    }

    toUserMessage(): string {
        switch (this.code) {
            case SandboxErrorCode.INVALID_PARAMS:
                return `参数错误: ${this.message}`;
            case SandboxErrorCode.SESSION_NOT_FOUND:
                return `会话不存在: ${this.sessionId || '未知'}`;
            case SandboxErrorCode.SESSION_EXPIRED:
                return `会话已过期: ${this.sessionId || '未知'}`;
            case SandboxErrorCode.COMMAND_BLOCKED:
                return `命令被阻止: ${this.reason || this.message}`;
            case SandboxErrorCode.RATE_LIMITED:
                const retryMsg = this.retryAfterMs 
                    ? `，请在 ${Math.ceil(this.retryAfterMs / 1000)} 秒后重试`
                    : '';
                return `请求频率超限${retryMsg}`;
            case SandboxErrorCode.RESOURCE_INSUFFICIENT:
                return '系统资源不足，无法执行命令';
            case SandboxErrorCode.TIMEOUT:
                return `命令执行超时: ${this.command || '未知命令'}`;
            case SandboxErrorCode.PERMISSION_DENIED:
                return `权限不足: ${this.reason || this.message}`;
            case SandboxErrorCode.FILE_NOT_FOUND:
                return `文件不存在: ${this.reason || this.message}`;
            case SandboxErrorCode.DIRECTORY_NOT_FOUND:
                return `目录不存在: ${this.reason || this.message}`;
            case SandboxErrorCode.PROCESS_ERROR:
                return `进程错误: ${this.message}`;
            case SandboxErrorCode.SYSTEM_ERROR:
                return `系统错误: ${this.message}`;
            case SandboxErrorCode.INTERNAL_ERROR:
            default:
                return `内部错误: ${this.message}`;
        }
    }
}

export class ErrorHandler {
    private errorCounts: Map<string, number[]> = new Map();
    private readonly windowMs = 60000;
    private readonly maxErrorsPerWindow = 10;

    handleError(error: Error | SandboxError, sessionId?: string, command?: string): SandboxError {
        const sandboxError = error instanceof SandboxError 
            ? error 
            : SandboxError.fromError(error, sessionId, command);

        this.recordError(sandboxError.code);

        return sandboxError;
    }

    private recordError(code: SandboxErrorCode): void {
        const now = Date.now();
        const key = code;
        const errors = this.errorCounts.get(key) || [];
        
        const recentErrors = errors.filter(t => t > now - this.windowMs);
        recentErrors.push(now);
        
        this.errorCounts.set(key, recentErrors);
    }

    getErrorRate(code?: SandboxErrorCode): number {
        const now = Date.now();
        
        if (code) {
            const errors = this.errorCounts.get(code) || [];
            const recentErrors = errors.filter(t => t > now - this.windowMs);
            return recentErrors.length;
        }

        let total = 0;
        for (const errors of this.errorCounts.values()) {
            const recentErrors = errors.filter(t => t > now - this.windowMs);
            total += recentErrors.length;
        }
        return total;
    }

    isErrorRateExceeded(code?: SandboxErrorCode): boolean {
        const rate = this.getErrorRate(code);
        return rate >= this.maxErrorsPerWindow;
    }

    createBlockedError(command: string, reason: string, sessionId?: string): SandboxError {
        return new SandboxError({
            code: SandboxErrorCode.COMMAND_BLOCKED,
            message: `命令被安全策略阻止`,
            reason,
            command,
            sessionId,
            retryable: false
        });
    }

    createRateLimitError(sessionId: string, retryAfterMs?: number): SandboxError {
        return new SandboxError({
            code: SandboxErrorCode.RATE_LIMITED,
            message: '请求频率超限',
            sessionId,
            retryable: true,
            retryAfterMs
        });
    }

    createTimeoutError(command: string, timeoutMs: number, sessionId?: string): SandboxError {
        return new SandboxError({
            code: SandboxErrorCode.TIMEOUT,
            message: `命令执行超时 (${timeoutMs}ms)`,
            command,
            sessionId,
            retryable: false
        });
    }

    createSessionError(sessionId: string, expired: boolean = false): SandboxError {
        return new SandboxError({
            code: expired ? SandboxErrorCode.SESSION_EXPIRED : SandboxErrorCode.SESSION_NOT_FOUND,
            message: expired ? '会话已过期' : '会话不存在',
            sessionId,
            retryable: false
        });
    }

    createResourceError(sessionId?: string): SandboxError {
        return new SandboxError({
            code: SandboxErrorCode.RESOURCE_INSUFFICIENT,
            message: '系统资源不足',
            sessionId,
            retryable: true,
            retryAfterMs: 5000
        });
    }

    createProcessError(message: string, exitCode: number, command?: string, sessionId?: string): SandboxError {
        return new SandboxError({
            code: SandboxErrorCode.PROCESS_ERROR,
            message,
            exitCode,
            command,
            sessionId,
            retryable: false
        });
    }
}
