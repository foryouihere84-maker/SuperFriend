import { PlatformAdapter, PlatformConfig } from './PlatformAdapter.js';
import * as fs from 'fs/promises';
import * as path from 'path';

export class LinuxAdapter implements PlatformAdapter {
    private sandboxRoot: string;

    constructor(sandboxRoot?: string) {
        // 优先使用传入的 sandboxRoot，然后尝试 SANDBOX_ROOT_DIR 环境变量
        this.sandboxRoot = sandboxRoot || process.env.SANDBOX_ROOT_DIR || '/tmp/superfriend_sandbox';
    }

    getPlatformConfig(): PlatformConfig {
        return {
            shell: '/bin/bash',
            shellArgs: ['-c'],
            sandboxDir: this.sandboxRoot,
            resourceLimitCmd: 'ulimit -t {cpuSeconds} -v {memoryKb} -f {fileKb} -u 64 -n 256 && ',
            pathSeparator: '/',
            lineEnding: '\n'
        };
    }

    isWindows(): boolean {
        return false;
    }

    getEnvironmentSeparator(): string {
        return ':';
    }

    getDefaultPath(): string {
        return '/usr/bin:/bin';
    }

    resolvePath(filePath: string): string {
        return path.resolve(filePath);
    }

    async createDirectory(dirPath: string): Promise<void> {
        await fs.mkdir(dirPath, { recursive: true });
    }

    async removeDirectory(dirPath: string): Promise<void> {
        await fs.rm(dirPath, { recursive: true, force: true });
    }

    async fileExists(filePath: string): Promise<boolean> {
        try {
            const stats = await fs.stat(filePath);
            return stats.isFile();
        } catch {
            return false;
        }
    }

    async directoryExists(dirPath: string): Promise<boolean> {
        try {
            const stats = await fs.stat(dirPath);
            return stats.isDirectory();
        } catch {
            return false;
        }
    }
}

export function createLinuxAdapter(sandboxRoot?: string): LinuxAdapter {
    return new LinuxAdapter(sandboxRoot);
}
