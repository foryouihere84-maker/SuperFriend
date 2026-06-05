import { spawn } from 'child_process';
import { ResourceLimits } from '../resource/ResourceLimits.js';
import { Session } from '../session/Session.js';
import { PlatformAdapter } from '../platform/PlatformAdapter.js';
import type { ChildProcess, SpawnOptions } from 'child_process';

export interface ExecuteResult {
    success: boolean;
    stdout: string;
    stderr: string;
    exitCode: number;
    executionTimeMs: number;
    sessionId: string;
    workingDirectory: string;
    blocked: boolean;
    blockedReason?: string;
    timedOut?: boolean;
    error?: string;
}

export interface ExecuteOptions {
    command: string;
    session: Session;
    limits: ResourceLimits;
    timeout?: number;
    environment?: Record<string, string>;
}

export class CommandExecutor {
    private platformAdapter: PlatformAdapter;

    constructor(platformAdapter: PlatformAdapter) {
        this.platformAdapter = platformAdapter;
    }

    async execute(options: ExecuteOptions): Promise<ExecuteResult> {
        const { command, session, limits, timeout, environment } = options;
        const startTime = Date.now();
        const effectiveTimeout = timeout || limits.commandTimeoutMs;
        
        const workDir = session.workingDirectory;
        const env = this.buildEnvironment(session, environment);
        
        const platformConfig = this.platformAdapter.getPlatformConfig();
        const fullCommand = this.buildCommand(command, limits, platformConfig);

        return new Promise<ExecuteResult>((resolve) => {
            let stdout = '';
            let stderr = '';
            let timedOut = false;

            const spawnOptions: SpawnOptions = {
                cwd: workDir,
                env: env
            };

            const proc: ChildProcess = spawn(
                platformConfig.shell,
                [...platformConfig.shellArgs, fullCommand],
                spawnOptions
            );

            const timeoutId = setTimeout(() => {
                timedOut = true;
                proc.kill('SIGKILL');
            }, effectiveTimeout);

            proc.stdout?.on('data', (data: Buffer) => {
                const chunk = data.toString();
                if (stdout.length + chunk.length <= limits.maxOutputBytes) {
                    stdout += chunk;
                }
            });

            proc.stderr?.on('data', (data: Buffer) => {
                const chunk = data.toString();
                if (stderr.length + chunk.length <= limits.maxOutputBytes) {
                    stderr += chunk;
                }
            });

            proc.on('close', (code: number | null) => {
                clearTimeout(timeoutId);
                const executionTimeMs = Date.now() - startTime;
                
                session.addCommandToHistory(command);
                session.recordExecution(executionTimeMs);

                resolve({
                    success: code === 0 && !timedOut,
                    stdout: stdout,
                    stderr: stderr,
                    exitCode: timedOut ? -2 : (code ?? 0),
                    executionTimeMs,
                    sessionId: session.sessionId,
                    workingDirectory: workDir,
                    blocked: false,
                    timedOut
                });
            });

            proc.on('error', (err: Error) => {
                clearTimeout(timeoutId);
                const executionTimeMs = Date.now() - startTime;
                
                resolve({
                    success: false,
                    stdout: stdout,
                    stderr: err.message,
                    exitCode: -3,
                    executionTimeMs,
                    sessionId: session.sessionId,
                    workingDirectory: workDir,
                    blocked: false,
                    error: err.message
                });
            });
        });
    }

    private buildCommand(
        command: string, 
        limits: ResourceLimits, 
        platformConfig: { shell: string; shellArgs: string[]; resourceLimitCmd: string | null }
    ): string {
        if (platformConfig.resourceLimitCmd) {
            const limitCmd = platformConfig.resourceLimitCmd
                .replace('{cpuSeconds}', limits.maxCpuSeconds.toString())
                .replace('{memoryKb}', limits.maxMemoryKb.toString())
                .replace('{fileKb}', limits.maxFileSizeKb.toString());
            return limitCmd + command;
        }
        return command;
    }

    private buildEnvironment(
        session: Session, 
        extraEnv?: Record<string, string>
    ): NodeJS.ProcessEnv {
        const env: NodeJS.ProcessEnv = { ...process.env };
        
        for (const [key, value] of session.environment) {
            env[key] = value;
        }
        
        if (extraEnv) {
            for (const [key, value] of Object.entries(extraEnv)) {
                env[key] = value;
            }
        }
        
        return env;
    }
}
