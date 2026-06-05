import { Session } from './Session.js';
import { PlatformAdapter } from '../platform/PlatformAdapter.js';
import { SessionPersistence } from './SessionPersistence.js';
import { v4 as uuidv4 } from 'uuid';
import * as path from 'path';

export interface CreateSessionOptions {
    workingDirectory?: string;
    environment?: Record<string, string>;
    name?: string;
}

export class SessionManager {
    private sessions: Map<string, Session> = new Map();
    private platformAdapter: PlatformAdapter;
    private sandboxRoot: string;
    private sessionTimeoutMs: number;
    private cleanupInterval: NodeJS.Timeout | null = null;
    private persistence: Map<string, SessionPersistence> = new Map();

    constructor(platformAdapter: PlatformAdapter, sessionTimeoutMs: number = 30 * 60 * 1000) {
        this.platformAdapter = platformAdapter;
        this.sandboxRoot = platformAdapter.getPlatformConfig().sandboxDir;
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    async initialize(): Promise<void> {
        await this.platformAdapter.createDirectory(this.sandboxRoot);
        await this.platformAdapter.createDirectory(path.join(this.sandboxRoot, 'sessions'));
        this.startCleanupTimer();
    }

    async createSession(options: CreateSessionOptions = {}): Promise<Session> {
        const sessionId = this.generateSessionId();
        const sessionDir = await this.createSessionDirectory(sessionId);
        
        const workingDirectory = options.workingDirectory || 
            path.join(sessionDir, 'workspace');
        
        await this.platformAdapter.createDirectory(workingDirectory);
        
        const environment = new Map<string, string>();
        
        this.setDefaultEnvironment(environment, sessionId, sessionDir);
        
        if (options.environment) {
            for (const [key, value] of Object.entries(options.environment)) {
                environment.set(key, value);
            }
        }
        
        const session = new Session(
            sessionId,
            workingDirectory,
            environment,
            options.name
        );
        
        this.sessions.set(sessionId, session);
        
        const persistence = new SessionPersistence(sessionDir);
        this.persistence.set(sessionId, persistence);
        await persistence.saveSession(session);
        
        return session;
    }

    getSession(sessionId: string): Session | undefined {
        return this.sessions.get(sessionId);
    }

    async getOrCreate(sessionId?: string): Promise<Session> {
        if (sessionId) {
            const existing = this.sessions.get(sessionId);
            if (existing) {
                return existing;
            }
        }
        
        return this.createSession();
    }

    async closeSession(sessionId: string, cleanup: boolean = true): Promise<boolean> {
        const session = this.sessions.get(sessionId);
        if (!session) {
            return false;
        }
        
        this.sessions.delete(sessionId);
        this.persistence.delete(sessionId);
        
        if (cleanup) {
            const sessionDir = path.join(
                this.sandboxRoot, 
                'sessions', 
                sessionId
            );
            try {
                await this.platformAdapter.removeDirectory(sessionDir);
            } catch (error) {
                console.error(`Failed to cleanup session directory: ${error}`);
            }
        }
        
        return true;
    }

    async persistSession(sessionId: string): Promise<void> {
        const session = this.sessions.get(sessionId);
        const persistence = this.persistence.get(sessionId);
        
        if (session && persistence) {
            await persistence.saveSession(session);
        }
    }

    listSessions(): Session[] {
        return Array.from(this.sessions.values());
    }

    getActiveSessionCount(): number {
        return this.sessions.size;
    }

    private generateSessionId(): string {
        const timestamp = Date.now();
        const random = uuidv4().split('-')[0];
        return `sess_${timestamp}_${random}`;
    }

    private async createSessionDirectory(sessionId: string): Promise<string> {
        const sessionDir = path.join(this.sandboxRoot, 'sessions', sessionId);
        await this.platformAdapter.createDirectory(sessionDir);
        
        const tmpDir = path.join(sessionDir, 'tmp');
        await this.platformAdapter.createDirectory(tmpDir);
        
        const workspaceDir = path.join(sessionDir, 'workspace');
        await this.platformAdapter.createDirectory(workspaceDir);
        
        return sessionDir;
    }

    private setDefaultEnvironment(
        env: Map<string, string>,
        sessionId: string,
        sessionDir: string
    ): void {
        // 【修复】追加而非覆盖系统 PATH
        const systemPath = process.env.PATH || '';
        const defaultPath = this.platformAdapter.getDefaultPath();
        const separator = this.platformAdapter.getEnvironmentSeparator();

        // 将默认路径放在前面，确保优先级，但保留系统路径
        const combinedPath = defaultPath + separator + systemPath;
        env.set('PATH', combinedPath);

        env.set('HOME', sessionDir);
        env.set('TEMP', path.join(sessionDir, 'tmp'));
        env.set('TMP', path.join(sessionDir, 'tmp'));
        env.set('SANDBOX_SESSION_ID', sessionId);

        // 【新增】继承用户环境变量
        // 保留常用的用户配置
        const userEnvKeys = [
            'USERPROFILE', 'APPDATA', 'LOCALAPPDATA',  // Windows
            'HOME', 'USER', 'LOGNAME',  // Linux/Mac
            'NODE_PATH', 'PYTHONPATH', 'GOPATH', 'CARGO_HOME',  // 开发工具
            'JAVA_HOME', 'M2_HOME', 'GRADLE_HOME',  // Java
            'EDITOR', 'VISUAL', 'PAGER',  // 编辑器
            'LANG', 'LC_ALL', 'LC_CTYPE',  // 语言设置
            'TERM', 'COLORTERM',  // 终端
            'HTTP_PROXY', 'HTTPS_PROXY', 'NO_PROXY',  // 代理
            'NUGET_PACKAGES', 'NPM_CONFIG_CACHE'  // 包管理器
        ];

        for (const key of userEnvKeys) {
            if (process.env[key] && !env.has(key)) {
                env.set(key, process.env[key]!);
            }
        }

        if (!this.platformAdapter.isWindows()) {
            if (!env.has('LANG')) {
                env.set('LANG', 'C.UTF-8');
            }
            if (!env.has('LC_ALL')) {
                env.set('LC_ALL', 'C.UTF-8');
            }
        }
    }

    private startCleanupTimer(): void {
        this.cleanupInterval = setInterval(() => {
            this.cleanupExpiredSessions();
        }, 5 * 60 * 1000);
    }

    private async cleanupExpiredSessions(): Promise<void> {
        const now = Date.now();
        const expiredSessions: string[] = [];
        
        for (const [sessionId, session] of this.sessions) {
            const inactiveTime = now - session.lastActivityAt.getTime();
            if (inactiveTime > this.sessionTimeoutMs) {
                expiredSessions.push(sessionId);
            }
        }
        
        for (const sessionId of expiredSessions) {
            console.log(`Cleaning up expired session: ${sessionId}`);
            await this.closeSession(sessionId, true);
        }
    }

    stopCleanupTimer(): void {
        if (this.cleanupInterval) {
            clearInterval(this.cleanupInterval);
            this.cleanupInterval = null;
        }
    }
}
