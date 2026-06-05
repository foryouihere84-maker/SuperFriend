import { isCommandAllowed, extractMainCommand } from './CommandWhitelist.js';
import { checkBlocked, BlockedPattern, Severity } from './CommandBlacklist.js';
import { filterSensitiveEnvVars } from './SensitiveEnvVars.js';

export interface ValidationResult {
    allowed: boolean;
    reason?: string;
    severity?: Severity;
    blockedPattern?: BlockedPattern;
}

export class SecurityFilter {
    private isWindows: boolean;
    private maxCommandLength: number;
    private useBashShell: boolean;  // 是否使用bash shell

    constructor(isWindows: boolean, maxCommandLength: number = 8192, useBashShell: boolean = false) {
        this.isWindows = isWindows;
        this.maxCommandLength = maxCommandLength;
        this.useBashShell = useBashShell;
    }

    /**
     * 设置是否使用bash shell
     * 当Windows使用Git Bash时，应该设置为true
     */
    setBashShell(useBash: boolean): void {
        this.useBashShell = useBash;
    }

    validate(command: string): ValidationResult {
        if (!command || command.trim().length === 0) {
            return {
                allowed: false,
                reason: '命令不能为空',
                severity: Severity.LOW
            };
        }

        if (command.length > this.maxCommandLength) {
            return {
                allowed: false,
                reason: `命令长度超过限制（最大 ${this.maxCommandLength} 字符）`,
                severity: Severity.LOW
            };
        }

        const blockedPattern = checkBlocked(command);
        if (blockedPattern) {
            return {
                allowed: false,
                reason: `命令被阻止: ${blockedPattern.description}`,
                severity: blockedPattern.severity,
                blockedPattern
            };
        }

        const mainCommand = extractMainCommand(command);
        // 当使用bash shell时，使用Linux命令检测逻辑
        const effectiveIsWindows = this.isWindows && !this.useBashShell;
        if (!isCommandAllowed(command, effectiveIsWindows)) {
            return {
                allowed: false,
                reason: `命令不在允许列表中: ${mainCommand}`,
                severity: Severity.MEDIUM
            };
        }

        return { allowed: true };
    }

    validateEnvironment(env: Record<string, string>): Record<string, string> {
        return filterSensitiveEnvVars(env);
    }

    isCommandSafe(command: string): boolean {
        return this.validate(command).allowed;
    }
}
