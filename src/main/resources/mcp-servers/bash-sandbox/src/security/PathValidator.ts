import * as path from 'path';

export class PathValidator {
    private sandboxRoot: string;
    private sessionRoot: string;

    constructor(sandboxRoot: string, sessionRoot: string) {
        this.sandboxRoot = path.resolve(sandboxRoot);
        this.sessionRoot = path.resolve(sessionRoot);
    }

    validate(filePath: string): { valid: boolean; reason?: string } {
        try {
            const normalized = path.normalize(filePath);
            const resolved = path.resolve(this.sessionRoot, normalized);
            
            if (!resolved.startsWith(this.sandboxRoot)) {
                return {
                    valid: false,
                    reason: `路径不在沙箱允许范围内: ${filePath}`
                };
            }
            
            if (!resolved.startsWith(this.sessionRoot)) {
                return {
                    valid: false,
                    reason: `路径不在当前会话目录内: ${filePath}`
                };
            }
            
            if (this.isSensitiveFile(resolved)) {
                return {
                    valid: false,
                    reason: `禁止访问敏感文件: ${filePath}`
                };
            }
            
            return { valid: true };
        } catch (error) {
            return {
                valid: false,
                reason: `路径解析失败: ${error}`
            };
        }
    }

    private isSensitiveFile(filePath: string): boolean {
        const fileName = path.basename(filePath).toLowerCase();
        return fileName.startsWith('.') && 
            (fileName.includes('key') || 
             fileName.includes('secret') ||
             fileName.includes('password') ||
             fileName.includes('credential'));
    }

    isWithinSandbox(filePath: string): boolean {
        const resolved = path.resolve(filePath);
        return resolved.startsWith(this.sandboxRoot);
    }

    isWithinSession(filePath: string): boolean {
        const resolved = path.resolve(filePath);
        return resolved.startsWith(this.sessionRoot);
    }
}
