import { CommandExecutor, ExecuteOptions, ExecuteResult } from '../executor/index.js';
import { Session } from '../session/index.js';
import { ResourceLimits } from '../resource/index.js';
import { logger } from '../utils/index.js';

export interface BatchCommand {
    id: string;
    command: string;
    workingDirectory?: string;
    timeout?: number;
    environment?: Record<string, string>;
    continueOnError?: boolean;
}

export interface BatchResult {
    id: string;
    success: boolean;
    result?: ExecuteResult;
    error?: string;
    skipped?: boolean;
    skipReason?: string;
}

export interface BatchExecutionOptions {
    commands: BatchCommand[];
    sessionId: string;
    session: Session;
    limits: ResourceLimits;
    parallel?: boolean;
    maxParallel?: number;
    stopOnFirstError?: boolean;
    timeout?: number;
}

export interface BatchExecutionReport {
    sessionId: string;
    totalCount: number;
    successCount: number;
    failureCount: number;
    skippedCount: number;
    totalExecutionTimeMs: number;
    results: BatchResult[];
    startedAt: string;
    completedAt: string;
}

export class BatchExecutor {
    private commandExecutor: CommandExecutor;

    constructor(commandExecutor: CommandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    async executeBatch(options: BatchExecutionOptions): Promise<BatchExecutionReport> {
        const {
            commands,
            sessionId,
            session,
            limits,
            parallel = false,
            maxParallel = 3,
            stopOnFirstError = false,
            timeout
        } = options;

        const startedAt = new Date().toISOString();
        const results: BatchResult[] = [];
        let hasError = false;

        logger.info('Starting batch execution', {
            sessionId,
            commandCount: commands.length,
            parallel,
            maxParallel
        });

        if (parallel) {
            const batches = this.createBatches(commands, maxParallel);
            
            for (const batch of batches) {
                if (hasError && stopOnFirstError) {
                    for (const cmd of batch) {
                        results.push({
                            id: cmd.id,
                            success: false,
                            skipped: true,
                            skipReason: 'Stopped due to previous error'
                        });
                    }
                    continue;
                }

                const batchResults = await Promise.all(
                    batch.map(cmd => this.executeSingleCommand(cmd, session, limits, timeout))
                );

                for (const result of batchResults) {
                    results.push(result);
                    if (!result.success && !result.skipped) {
                        hasError = true;
                    }
                }
            }
        } else {
            for (const cmd of commands) {
                if (hasError && stopOnFirstError) {
                    results.push({
                        id: cmd.id,
                        success: false,
                        skipped: true,
                        skipReason: 'Stopped due to previous error'
                    });
                    continue;
                }

                const result = await this.executeSingleCommand(cmd, session, limits, timeout);
                results.push(result);

                if (!result.success && !result.skipped) {
                    hasError = true;
                    if (stopOnFirstError) {
                        logger.warn('Stopping batch execution due to error', {
                            sessionId,
                            commandId: cmd.id
                        });
                    }
                }
            }
        }

        const completedAt = new Date().toISOString();
        const totalExecutionTimeMs = results.reduce(
            (sum, r) => sum + (r.result?.executionTimeMs || 0),
            0
        );

        const report: BatchExecutionReport = {
            sessionId,
            totalCount: commands.length,
            successCount: results.filter(r => r.success && !r.skipped).length,
            failureCount: results.filter(r => !r.success && !r.skipped).length,
            skippedCount: results.filter(r => r.skipped).length,
            totalExecutionTimeMs,
            results,
            startedAt,
            completedAt
        };

        logger.info('Batch execution completed', {
            sessionId,
            successCount: report.successCount,
            failureCount: report.failureCount,
            skippedCount: report.skippedCount,
            totalExecutionTimeMs
        });

        return report;
    }

    private async executeSingleCommand(
        cmd: BatchCommand,
        session: Session,
        limits: ResourceLimits,
        defaultTimeout?: number
    ): Promise<BatchResult> {
        try {
            const options: ExecuteOptions = {
                command: cmd.command,
                session,
                limits,
                timeout: cmd.timeout || defaultTimeout,
                environment: cmd.environment
            };

            if (cmd.workingDirectory) {
                session.updateWorkingDirectory(cmd.workingDirectory);
            }

            const result = await this.commandExecutor.execute(options);

            return {
                id: cmd.id,
                success: result.success,
                result
            };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            logger.error('Batch command execution failed', {
                commandId: cmd.id,
                error: errorMessage
            });

            return {
                id: cmd.id,
                success: false,
                error: errorMessage
            };
        }
    }

    private createBatches(commands: BatchCommand[], maxParallel: number): BatchCommand[][] {
        const batches: BatchCommand[][] = [];
        
        for (let i = 0; i < commands.length; i += maxParallel) {
            batches.push(commands.slice(i, i + maxParallel));
        }

        return batches;
    }

    async executePipeline(
        commands: BatchCommand[],
        _sessionId: string,
        session: Session,
        limits: ResourceLimits
    ): Promise<BatchResult> {
        const pipelineCommand = commands.map(c => c.command).join(' | ');
        
        try {
            const result = await this.commandExecutor.execute({
                command: pipelineCommand,
                session,
                limits
            });

            return {
                id: 'pipeline',
                success: result.success,
                result
            };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            
            return {
                id: 'pipeline',
                success: false,
                error: errorMessage
            };
        }
    }

    async executeSequence(
        commands: BatchCommand[],
        _sessionId: string,
        session: Session,
        limits: ResourceLimits
    ): Promise<BatchResult> {
        const sequenceCommand = commands.map(c => c.command).join(' && ');
        
        try {
            const result = await this.commandExecutor.execute({
                command: sequenceCommand,
                session,
                limits
            });

            return {
                id: 'sequence',
                success: result.success,
                result
            };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            
            return {
                id: 'sequence',
                success: false,
                error: errorMessage
            };
        }
    }

    createCommandChain(
        commands: Array<{ command: string; condition?: 'always' | 'success' | 'failure' }>
    ): string {
        const parts: string[] = [];

        for (let i = 0; i < commands.length; i++) {
            const cmd = commands[i];
            
            if (i === 0) {
                parts.push(cmd.command);
            } else {
                switch (cmd.condition) {
                    case 'success':
                        parts.push(`&& ${cmd.command}`);
                        break;
                    case 'failure':
                        parts.push(`|| ${cmd.command}`);
                        break;
                    case 'always':
                    default:
                        parts.push(`; ${cmd.command}`);
                        break;
                }
            }
        }

        return parts.join(' ');
    }
}
