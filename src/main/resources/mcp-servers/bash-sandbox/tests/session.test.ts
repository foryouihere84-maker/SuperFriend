import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { Session } from '../src/session/Session.js';
import { SessionManager } from '../src/session/SessionManager.js';
import { LinuxAdapter } from '../src/platform/LinuxAdapter.js';
import * as path from 'path';
import * as fs from 'fs/promises';
import * as os from 'os';

describe('Session', () => {
    let session: Session;

    beforeEach(() => {
        session = new Session(
            'test_session_001',
            '/tmp/test_workspace',
            new Map([['PATH', '/usr/bin']])
        );
    });

    describe('constructor', () => {
        it('should create a session with correct initial state', () => {
            expect(session.sessionId).toBe('test_session_001');
            expect(session.workingDirectory).toBe('/tmp/test_workspace');
            expect(session.commandCount).toBe(0);
            expect(session.totalExecutionTimeMs).toBe(0);
        });
    });

    describe('updateWorkingDirectory', () => {
        it('should update working directory', () => {
            session.updateWorkingDirectory('/tmp/new_dir');
            expect(session.workingDirectory).toBe('/tmp/new_dir');
        });
    });

    describe('environment variables', () => {
        it('should set and get environment variables', () => {
            session.setEnvironmentVariable('MY_VAR', 'my_value');
            expect(session.getEnvironmentVariable('MY_VAR')).toBe('my_value');
        });

        it('should remove environment variables', () => {
            session.setEnvironmentVariable('TEMP_VAR', 'temp');
            session.removeEnvironmentVariable('TEMP_VAR');
            expect(session.getEnvironmentVariable('TEMP_VAR')).toBeUndefined();
        });
    });

    describe('command history', () => {
        it('should add commands to history', () => {
            session.addCommandToHistory('ls -la');
            session.addCommandToHistory('pwd');
            
            expect(session.commandHistory).toHaveLength(2);
            expect(session.commandCount).toBe(2);
        });

        it('should limit history size', () => {
            for (let i = 0; i < 150; i++) {
                session.addCommandToHistory(`command_${i}`);
            }
            
            expect(session.commandHistory.length).toBeLessThanOrEqual(100);
        });
    });

    describe('recordExecution', () => {
        it('should record execution time', () => {
            session.recordExecution(100);
            session.recordExecution(200);
            
            expect(session.totalExecutionTimeMs).toBe(300);
        });
    });

    describe('toJSON', () => {
        it('should serialize session to JSON', () => {
            const json = session.toJSON() as Record<string, unknown>;
            
            expect(json.sessionId).toBe('test_session_001');
            expect(json.workingDirectory).toBe('/tmp/test_workspace');
            expect(json.environment).toBeDefined();
        });
    });
});

describe('SessionManager', () => {
    let manager: SessionManager;
    let testSandboxRoot: string;

    beforeEach(async () => {
        testSandboxRoot = path.join(os.tmpdir(), `sandbox_test_${Date.now()}`);
        const adapter = new LinuxAdapter(testSandboxRoot);
        manager = new SessionManager(adapter, 60000);
        await manager.initialize();
    });

    afterEach(async () => {
        manager.stopCleanupTimer();
        try {
            await fs.rm(testSandboxRoot, { recursive: true, force: true });
        } catch {
            // ignore cleanup errors
        }
    });

    describe('createSession', () => {
        it('should create a new session', async () => {
            const session = await manager.createSession();
            
            expect(session.sessionId).toMatch(/^sess_\d+_[a-f0-9]+$/);
            expect(session.workingDirectory).toBeDefined();
        });

        it('should create session with custom options', async () => {
            const session = await manager.createSession({
                name: 'test_session',
                environment: { CUSTOM_VAR: 'custom_value' }
            });
            
            expect(session.name).toBe('test_session');
            expect(session.getEnvironmentVariable('CUSTOM_VAR')).toBe('custom_value');
        });
    });

    describe('getSession', () => {
        it('should return existing session', async () => {
            const created = await manager.createSession();
            const retrieved = manager.getSession(created.sessionId);
            
            expect(retrieved).toBe(created);
        });

        it('should return undefined for non-existent session', () => {
            const session = manager.getSession('non_existent');
            expect(session).toBeUndefined();
        });
    });

    describe('closeSession', () => {
        it('should close and remove session', async () => {
            const session = await manager.createSession();
            const closed = await manager.closeSession(session.sessionId);
            
            expect(closed).toBe(true);
            expect(manager.getSession(session.sessionId)).toBeUndefined();
        });

        it('should return false for non-existent session', async () => {
            const closed = await manager.closeSession('non_existent');
            expect(closed).toBe(false);
        });
    });

    describe('listSessions', () => {
        it('should list all active sessions', async () => {
            await manager.createSession();
            await manager.createSession();
            
            const sessions = manager.listSessions();
            expect(sessions).toHaveLength(2);
        });
    });

    describe('getActiveSessionCount', () => {
        it('should return correct count', async () => {
            await manager.createSession();
            await manager.createSession();
            
            expect(manager.getActiveSessionCount()).toBe(2);
        });
    });
});
