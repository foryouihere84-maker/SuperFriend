export interface SessionState {
    sessionId: string;
    name?: string;
    workingDirectory: string;
    environment: Map<string, string>;
    commandHistory: string[];
    createdAt: Date;
    lastActivityAt: Date;
    commandCount: number;
    totalExecutionTimeMs: number;
}

export class Session implements SessionState {
    sessionId: string;
    name?: string;
    workingDirectory: string;
    environment: Map<string, string>;
    commandHistory: string[];
    createdAt: Date;
    lastActivityAt: Date;
    commandCount: number;
    totalExecutionTimeMs: number;

    private maxHistorySize: number = 100;

    constructor(
        sessionId: string,
        workingDirectory: string,
        environment?: Map<string, string>,
        name?: string
    ) {
        this.sessionId = sessionId;
        this.workingDirectory = workingDirectory;
        this.environment = environment || new Map();
        this.commandHistory = [];
        this.createdAt = new Date();
        this.lastActivityAt = new Date();
        this.commandCount = 0;
        this.totalExecutionTimeMs = 0;
        this.name = name;
    }

    updateWorkingDirectory(newDir: string): void {
        this.workingDirectory = newDir;
        this.updateActivity();
    }

    setEnvironmentVariable(key: string, value: string): void {
        this.environment.set(key, value);
        this.updateActivity();
    }

    getEnvironmentVariable(key: string): string | undefined {
        return this.environment.get(key);
    }

    removeEnvironmentVariable(key: string): void {
        this.environment.delete(key);
        this.updateActivity();
    }

    addCommandToHistory(command: string): void {
        this.commandHistory.push(command);
        if (this.commandHistory.length > this.maxHistorySize) {
            this.commandHistory.shift();
        }
        this.commandCount++;
        this.updateActivity();
    }

    recordExecution(executionTimeMs: number): void {
        this.totalExecutionTimeMs += executionTimeMs;
        this.updateActivity();
    }

    private updateActivity(): void {
        this.lastActivityAt = new Date();
    }

    toJSON(): object {
        return {
            sessionId: this.sessionId,
            name: this.name,
            workingDirectory: this.workingDirectory,
            environment: Object.fromEntries(this.environment),
            commandHistory: this.commandHistory,
            createdAt: this.createdAt.toISOString(),
            lastActivityAt: this.lastActivityAt.toISOString(),
            commandCount: this.commandCount,
            totalExecutionTimeMs: this.totalExecutionTimeMs
        };
    }
}
