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
        const paths: string[] = [];
        const homeDir = os.homedir();

        // ==================== 系统路径 ====================
        paths.push('C:\\Windows\\System32');
        paths.push('C:\\Windows');
        paths.push('C:\\Windows\\System32\\WindowsPowerShell\\v1.0');

        // ==================== Git Bash ====================
        const gitPaths = [
            'C:\\Program Files\\Git\\bin',
            'C:\\Program Files\\Git\\usr\\bin',      // Unix 工具
            'C:\\Program Files\\Git\\mingw64\\bin',  // MinGW 工具
            'C:\\Program Files (x86)\\Git\\bin',
            path.join(homeDir, 'AppData', 'Local', 'Programs', 'Git', 'bin'),
            path.join(homeDir, 'scoop', 'apps', 'git', 'current', 'bin'),
            path.join(homeDir, 'scoop', 'apps', 'git', 'current', 'usr', 'bin'),
            path.join(homeDir, 'scoop', 'apps', 'git', 'current', 'mingw64', 'bin')
        ];
        for (const p of gitPaths) {
            if (fs.existsSync(p)) {
                paths.push(p);
            }
        }

        // ==================== Node.js ====================
        const nodePaths = [
            'C:\\Program Files\\nodejs',
            path.join(homeDir, 'AppData', 'Roaming', 'npm'),
            path.join(homeDir, 'scoop', 'apps', 'nodejs', 'current'),
            path.join(homeDir, '.nvm', 'versions', 'node')  // nvm-windows
        ];
        for (const p of nodePaths) {
            if (fs.existsSync(p)) {
                paths.push(p);
            }
        }

        // ==================== Python ====================
        // 动态检测 Python 版本
        const pythonBasePaths = [
            'C:\\Python313', 'C:\\Python312', 'C:\\Python311', 'C:\\Python310', 'C:\\Python39', 'C:\\Python38',
            'C:\\Program Files\\Python313', 'C:\\Program Files\\Python312',
            'C:\\Program Files\\Python311', 'C:\\Program Files\\Python310',
            path.join(homeDir, 'AppData', 'Local', 'Programs', 'Python', 'Python313'),
            path.join(homeDir, 'AppData', 'Local', 'Programs', 'Python', 'Python312'),
            path.join(homeDir, 'AppData', 'Local', 'Programs', 'Python', 'Python311'),
            path.join(homeDir, 'AppData', 'Local', 'Programs', 'Python', 'Python310'),
            path.join(homeDir, 'scoop', 'apps', 'python', 'current'),
            path.join(homeDir, '.pyenv', 'pyenv-win', 'versions')  // pyenv-win
        ];

        for (const basePath of pythonBasePaths) {
            if (fs.existsSync(basePath)) {
                paths.push(basePath);
                const scriptsPath = path.join(basePath, 'Scripts');
                if (fs.existsSync(scriptsPath)) {
                    paths.push(scriptsPath);
                }
            }
        }

        // ==================== Java ====================
        const javaPaths = [
            'C:\\Program Files\\Java',
            'C:\\Program Files\\Eclipse Adoptium',
            'C:\\Program Files\\Microsoft',
            path.join(homeDir, '.jdks'),
            path.join(homeDir, 'scoop', 'apps', 'openjdk', 'current', 'bin')
        ];
        for (const p of javaPaths) {
            if (fs.existsSync(p)) {
                // 检查是否是 JDK 目录
                try {
                    const entries = fs.readdirSync(p);
                    for (const entry of entries) {
                        const binPath = path.join(p, entry, 'bin');
                        if (fs.existsSync(binPath)) {
                            paths.push(binPath);
                        }
                    }
                } catch {
                    // 如果是 bin 目录本身
                    if (p.endsWith('bin') && fs.existsSync(p)) {
                        paths.push(p);
                    }
                }
            }
        }

        // ==================== Go ====================
        const goPaths = [
            'C:\\Program Files\\Go\\bin',
            'C:\\Go\\bin',
            path.join(homeDir, 'scoop', 'apps', 'go', 'current', 'bin'),
            path.join(homeDir, 'go', 'bin')  // GOPATH/bin
        ];
        for (const p of goPaths) {
            if (fs.existsSync(p)) {
                paths.push(p);
            }
        }

        // ==================== Rust ====================
        const rustPaths = [
            path.join(homeDir, '.cargo', 'bin'),
            path.join(homeDir, 'scoop', 'apps', 'rustup', 'current', '.cargo', 'bin')
        ];
        for (const p of rustPaths) {
            if (fs.existsSync(p)) {
                paths.push(p);
            }
        }

        // ==================== .NET ====================
        const dotnetPaths = [
            'C:\\Program Files\\dotnet',
            path.join(homeDir, '.dotnet', 'tools')
        ];
        for (const p of dotnetPaths) {
            if (fs.existsSync(p)) {
                paths.push(p);
            }
        }

        // ==================== Ruby ====================
        const rubyPaths = [
            'C:\\Ruby32-x64\\bin',
            'C:\\Ruby31-x64\\bin',
            'C:\\Ruby30-x64\\bin',
            path.join(homeDir, 'scoop', 'apps', 'ruby', 'current', 'bin')
        ];
        for (const p of rubyPaths) {
            if (fs.existsSync(p)) {
                paths.push(p);
            }
        }

        // ==================== 容器工具 ====================
        const containerPaths = [
            'C:\\Program Files\\Docker\\Docker\\resources\\bin',
            'C:\\Program Files\\Docker\\Docker\\resources',
            path.join(homeDir, 'AppData', 'Local', 'Docker', 'wsl'),
            path.join(homeDir, '.docker', 'bin')
        ];
        for (const p of containerPaths) {
            if (fs.existsSync(p)) {
                paths.push(p);
            }
        }

        // ==================== 编辑器 ====================
        const editorPaths = [
            'C:\\Program Files\\Microsoft VS Code\\bin',
            path.join(homeDir, 'AppData', 'Local', 'Programs', 'Microsoft VS Code', 'bin'),
            path.join(homeDir, 'AppData', 'Local', 'Programs', 'cursor', 'resources', 'app', 'bin'),
            'C:\\Program Files\\Neovim\\bin',
            path.join(homeDir, 'AppData', 'Local', 'nvim', 'bin')
        ];
        for (const p of editorPaths) {
            if (fs.existsSync(p)) {
                paths.push(p);
            }
        }

        // ==================== 用户路径 ====================
        const userPaths = [
            path.join(homeDir, '.local', 'bin'),
            path.join(homeDir, 'bin')
        ];
        for (const p of userPaths) {
            if (fs.existsSync(p)) {
                paths.push(p);
            }
        }

        // 去重
        const uniquePaths = [...new Set(paths)];

        return uniquePaths.join(';');
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
