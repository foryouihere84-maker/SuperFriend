import * as fs from 'fs/promises';
import * as path from 'path';

export interface AuditEntry {
    timestamp: string;
    sessionId: string;
    event: string;
    command?: string;
    workingDirectory?: string;
    exitCode?: number;
    executionTimeMs?: number;
    blocked?: boolean;
    blockedReason?: string;
    error?: string;
}

export interface AuditQueryOptions {
    sessionId?: string;
    event?: string;
    startTime?: string;
    endTime?: string;
    blocked?: boolean;
    limit?: number;
    offset?: number;
}

export interface AuditStats {
    totalExecutions: number;
    successfulExecutions: number;
    failedExecutions: number;
    blockedExecutions: number;
    totalExecutionTimeMs: number;
    avgExecutionTimeMs: number;
    bySession: Map<string, SessionStats>;
}

export interface SessionStats {
    commandCount: number;
    totalExecutionTimeMs: number;
    successCount: number;
    failureCount: number;
    blockedCount: number;
}

export class AuditLogger {
    private auditLogPath: string;
    private writeQueue: AuditEntry[] = [];
    private isWriting: boolean = false;
    private statsCache: Map<string, SessionStats> = new Map();
    private totalStats: { executions: number; totalTime: number } = { executions: 0, totalTime: 0 };

    constructor(sandboxRoot: string) {
        this.auditLogPath = path.join(sandboxRoot, '.audit.log');
    }

    async initialize(): Promise<void> {
        try {
            await fs.mkdir(path.dirname(this.auditLogPath), { recursive: true });
        } catch {
            // ignore
        }
    }

    log(entry: AuditEntry): void {
        this.writeQueue.push(entry);
        this.updateStats(entry);
        this.processQueue();
    }

    logCommandExecuted(
        sessionId: string,
        command: string,
        workingDirectory: string,
        exitCode: number,
        executionTimeMs: number,
        blocked: boolean,
        blockedReason?: string
    ): void {
        this.log({
            timestamp: new Date().toISOString(),
            sessionId,
            event: 'COMMAND_EXECUTED',
            command,
            workingDirectory,
            exitCode,
            executionTimeMs,
            blocked,
            blockedReason
        });
    }

    logSessionCreated(sessionId: string, workingDirectory: string): void {
        this.log({
            timestamp: new Date().toISOString(),
            sessionId,
            event: 'SESSION_CREATED',
            workingDirectory
        });
    }

    logSessionClosed(sessionId: string): void {
        this.log({
            timestamp: new Date().toISOString(),
            sessionId,
            event: 'SESSION_CLOSED'
        });
    }

    logError(sessionId: string, error: string): void {
        this.log({
            timestamp: new Date().toISOString(),
            sessionId,
            event: 'ERROR',
            error
        });
    }

    private updateStats(entry: AuditEntry): void {
        if (entry.event === 'COMMAND_EXECUTED') {
            this.totalStats.executions++;
            this.totalStats.totalTime += entry.executionTimeMs || 0;

            const sessionStats = this.statsCache.get(entry.sessionId) || {
                commandCount: 0,
                totalExecutionTimeMs: 0,
                successCount: 0,
                failureCount: 0,
                blockedCount: 0
            };

            sessionStats.commandCount++;
            sessionStats.totalExecutionTimeMs += entry.executionTimeMs || 0;

            if (entry.blocked) {
                sessionStats.blockedCount++;
            } else if (entry.exitCode === 0) {
                sessionStats.successCount++;
            } else {
                sessionStats.failureCount++;
            }

            this.statsCache.set(entry.sessionId, sessionStats);
        }
    }

    getSessionStats(sessionId: string): SessionStats | undefined {
        return this.statsCache.get(sessionId);
    }

    getTotalStats(): { executions: number; totalTime: number } {
        return { ...this.totalStats };
    }

    async query(options: AuditQueryOptions = {}): Promise<AuditEntry[]> {
        try {
            const content = await fs.readFile(this.auditLogPath, 'utf-8');
            let lines = content.trim().split('\n').filter(l => l);

            const entries: AuditEntry[] = lines.map(line => {
                try {
                    return JSON.parse(line) as AuditEntry;
                } catch {
                    return null;
                }
            }).filter((e): e is AuditEntry => e !== null);

            let filtered = entries;

            if (options.sessionId) {
                filtered = filtered.filter(e => e.sessionId === options.sessionId);
            }

            if (options.event) {
                filtered = filtered.filter(e => e.event === options.event);
            }

            if (options.startTime) {
                const start = new Date(options.startTime).getTime();
                filtered = filtered.filter(e => new Date(e.timestamp).getTime() >= start);
            }

            if (options.endTime) {
                const end = new Date(options.endTime).getTime();
                filtered = filtered.filter(e => new Date(e.timestamp).getTime() <= end);
            }

            if (options.blocked !== undefined) {
                filtered = filtered.filter(e => e.blocked === options.blocked);
            }

            const offset = options.offset || 0;
            const limit = options.limit || 100;

            return filtered.slice(offset, offset + limit);
        } catch {
            return [];
        }
    }

    async getCommandHistory(sessionId: string, limit: number = 50): Promise<AuditEntry[]> {
        return this.query({
            sessionId,
            event: 'COMMAND_EXECUTED',
            limit
        });
    }

    async getStatistics(sessionId?: string): Promise<AuditStats> {
        const entries = await this.query({
            sessionId,
            event: 'COMMAND_EXECUTED',
            limit: 10000
        });

        const stats: AuditStats = {
            totalExecutions: entries.length,
            successfulExecutions: 0,
            failedExecutions: 0,
            blockedExecutions: 0,
            totalExecutionTimeMs: 0,
            avgExecutionTimeMs: 0,
            bySession: new Map()
        };

        for (const entry of entries) {
            stats.totalExecutionTimeMs += entry.executionTimeMs || 0;

            if (entry.blocked) {
                stats.blockedExecutions++;
            } else if (entry.exitCode === 0) {
                stats.successfulExecutions++;
            } else {
                stats.failedExecutions++;
            }

            if (!sessionId) {
                const sessionStats = stats.bySession.get(entry.sessionId) || {
                    commandCount: 0,
                    totalExecutionTimeMs: 0,
                    successCount: 0,
                    failureCount: 0,
                    blockedCount: 0
                };

                sessionStats.commandCount++;
                sessionStats.totalExecutionTimeMs += entry.executionTimeMs || 0;

                if (entry.blocked) {
                    sessionStats.blockedCount++;
                } else if (entry.exitCode === 0) {
                    sessionStats.successCount++;
                } else {
                    sessionStats.failureCount++;
                }

                stats.bySession.set(entry.sessionId, sessionStats);
            }
        }

        stats.avgExecutionTimeMs = stats.totalExecutions > 0
            ? stats.totalExecutionTimeMs / stats.totalExecutions
            : 0;

        return stats;
    }

    private async processQueue(): Promise<void> {
        if (this.isWriting || this.writeQueue.length === 0) {
            return;
        }

        this.isWriting = true;

        try {
            const entries = this.writeQueue.splice(0, this.writeQueue.length);
            const lines = entries.map(e => JSON.stringify(e)).join('\n') + '\n';
            
            await fs.appendFile(this.auditLogPath, lines, 'utf-8');
        } catch (err) {
            console.error('Failed to write audit log:', err);
        } finally {
            this.isWriting = false;
            
            if (this.writeQueue.length > 0) {
                this.processQueue();
            }
        }
    }

    async readRecentLogs(count: number = 100): Promise<AuditEntry[]> {
        try {
            const content = await fs.readFile(this.auditLogPath, 'utf-8');
            const lines = content.trim().split('\n').filter(l => l);
            const recentLines = lines.slice(-count);
            
            return recentLines.map(line => {
                try {
                    return JSON.parse(line) as AuditEntry;
                } catch {
                    return null;
                }
            }).filter((e): e is AuditEntry => e !== null);
        } catch {
            return [];
        }
    }

    async clearOldLogs(daysToKeep: number = 30): Promise<number> {
        try {
            const content = await fs.readFile(this.auditLogPath, 'utf-8');
            const lines = content.trim().split('\n').filter(l => l);

            const cutoff = Date.now() - daysToKeep * 24 * 60 * 60 * 1000;

            const recentLines = lines.filter(line => {
                try {
                    const entry = JSON.parse(line) as AuditEntry;
                    return new Date(entry.timestamp).getTime() >= cutoff;
                } catch {
                    return false;
                }
            });

            await fs.writeFile(this.auditLogPath, recentLines.join('\n') + '\n', 'utf-8');

            return lines.length - recentLines.length;
        } catch {
            return 0;
        }
    }
}
