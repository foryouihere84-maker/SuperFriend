import * as fs from 'fs/promises';
import * as path from 'path';
import { Session } from './Session.js';

export class SessionPersistence {
    private sessionDir: string;

    constructor(sessionDir: string) {
        this.sessionDir = sessionDir;
    }

    async saveSession(session: Session): Promise<void> {
        await this.ensureSessionDir();
        
        await this.saveEnvironment(session);
        await this.saveHistory(session);
        await this.saveWorkingDirectory(session);
    }

    async loadSession(_sessionId: string): Promise<{
        environment: Map<string, string>;
        history: string[];
        workingDirectory: string | null;
    } | null> {
        try {
            const envPath = path.join(this.sessionDir, '.env');
            const historyPath = path.join(this.sessionDir, '.history');
            const cwdPath = path.join(this.sessionDir, '.cwd');

            const environment = await this.loadEnvironment(envPath);
            const history = await this.loadHistory(historyPath);
            const workingDirectory = await this.loadWorkingDirectory(cwdPath);

            return { environment, history, workingDirectory };
        } catch {
            return null;
        }
    }

    private async ensureSessionDir(): Promise<void> {
        try {
            await fs.mkdir(this.sessionDir, { recursive: true });
        } catch {
            // ignore
        }
    }

    private async saveEnvironment(session: Session): Promise<void> {
        const envPath = path.join(this.sessionDir, '.env');
        const lines: string[] = [];
        
        for (const [key, value] of session.environment) {
            lines.push(`${key}=${value}`);
        }
        
        await fs.writeFile(envPath, lines.join('\n'), 'utf-8');
    }

    private async saveHistory(session: Session): Promise<void> {
        const historyPath = path.join(this.sessionDir, '.history');
        await fs.writeFile(historyPath, session.commandHistory.join('\n'), 'utf-8');
    }

    private async saveWorkingDirectory(session: Session): Promise<void> {
        const cwdPath = path.join(this.sessionDir, '.cwd');
        await fs.writeFile(cwdPath, session.workingDirectory, 'utf-8');
    }

    private async loadEnvironment(envPath: string): Promise<Map<string, string>> {
        const env = new Map<string, string>();
        
        try {
            const content = await fs.readFile(envPath, 'utf-8');
            const lines = content.split('\n');
            
            for (const line of lines) {
                const trimmed = line.trim();
                if (trimmed && !trimmed.startsWith('#')) {
                    const eqIndex = trimmed.indexOf('=');
                    if (eqIndex > 0) {
                        const key = trimmed.substring(0, eqIndex);
                        const value = trimmed.substring(eqIndex + 1);
                        env.set(key, value);
                    }
                }
            }
        } catch {
            // ignore
        }
        
        return env;
    }

    private async loadHistory(historyPath: string): Promise<string[]> {
        try {
            const content = await fs.readFile(historyPath, 'utf-8');
            return content.split('\n').filter(line => line.trim());
        } catch {
            return [];
        }
    }

    private async loadWorkingDirectory(cwdPath: string): Promise<string | null> {
        try {
            return await fs.readFile(cwdPath, 'utf-8');
        } catch {
            return null;
        }
    }

    async appendHistory(_sessionId: string, command: string): Promise<void> {
        const historyPath = path.join(this.sessionDir, '.history');
        const line = `${new Date().toISOString()} | ${command}\n`;
        
        try {
            await fs.appendFile(historyPath, line, 'utf-8');
        } catch {
            // ignore
        }
    }
}
