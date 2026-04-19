import { PlatformAdapter, PlatformConfig } from './PlatformAdapter.js';
import * as fs from 'fs';
import * as fsPromises from 'fs/promises';
import * as path from 'path';
import * as os from 'os';

export class WindowsAdapter implements PlatformAdapter {
    private sandboxRoot: string;

    constructor(sandboxRoot?: string) {
        this.sandboxRoot = sandboxRoot || process.env.SANDBOX_ROOT_DIR_WIN ||
            path.join(process.env.TEMP || os.tmpdir(), 'superfriend_sandbox');
    }

    getPlatformConfig(): PlatformConfig {
        // 优先使用Git Bash，支持完整的bash语法
        // 回退顺序: Git Bash -> PowerShell -> cmd.exe
        const gitBashPath = this.findGitBash();

        if (gitBashPath) {
            return {
                shell: gitBashPath,
                shellArgs: ['-c'],  // bash -c "command"
                sandboxDir: this.sandboxRoot,
                resourceLimitCmd: null,
                pathSeparator: '/',
                lineEnding: '\n'
            };
        }

        // 回退到PowerShell（比cmd.exe功能更强）
        return {
            shell: 'powershell.exe',
            shellArgs: ['-Command'],
            sandboxDir: this.sandboxRoot,
            resourceLimitCmd: null,
            pathSeparator: '\\',
            lineEnding: '\r\n'
        };
    }

    /**
     * 查找Git Bash路径
     */
    private findGitBash(): string | null {
        const possiblePaths = [
            // 常见Git安装路径
            'C:\\Program Files\\Git\\bin\\bash.exe',
            'C:\\Program Files (x86)\\Git\\bin\\bash.exe',
            // 用户目录安装
            path.join(os.homedir(), 'AppData', 'Local', 'Programs', 'Git', 'bin', 'bash.exe'),
            // scoop安装
            path.join(os.homedir(), 'scoop', 'apps', 'git', 'current', 'bin', 'bash.exe'),
            // 环境变量指定
            process.env.GIT_BASH_PATH || ''
        ];

        for (const p of possiblePaths) {
            if (p && fs.existsSync(p)) {
                return p;
            }
        }
        return null;
    }

    isWindows(): boolean {
        return true;
    }

    getEnvironmentSeparator(): string {
        return ';';
    }

    getDefaultPath(): string {
        // 包含常用开发工具路径
        const paths = [
            'C:\\Windows\\System32',
            'C:\\Windows',
            'C:\\Program Files\\dotnet',           // .NET SDK
            'C:\\Program Files\\nodejs',           // Node.js
            'C:\\Program Files\\Python313',        // Python
            'C:\\Program Files\\Python313\\Scripts',
            'C:\\Program Files\\Git\\bin',         // Git
            'C:\\Program Files\\Git\\cmd'
        ];
        return paths.join(';');
    }

    resolvePath(filePath: string): string {
        return path.resolve(filePath);
    }

    async createDirectory(dirPath: string): Promise<void> {
        await fsPromises.mkdir(dirPath, { recursive: true });
    }

    async removeDirectory(dirPath: string): Promise<void> {
        await fsPromises.rm(dirPath, { recursive: true, force: true });
    }

    async fileExists(filePath: string): Promise<boolean> {
        try {
            const stats = await fsPromises.stat(filePath);
            return stats.isFile();
        } catch {
            return false;
        }
    }

    async directoryExists(dirPath: string): Promise<boolean> {
        try {
            const stats = await fsPromises.stat(dirPath);
            return stats.isDirectory();
        } catch {
            return false;
        }
    }
}

export function createWindowsAdapter(sandboxRoot?: string): WindowsAdapter {
    return new WindowsAdapter(sandboxRoot);
}
