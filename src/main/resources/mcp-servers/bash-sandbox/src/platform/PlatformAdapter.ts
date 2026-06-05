export interface PlatformConfig {
    shell: string;
    shellArgs: string[];
    sandboxDir: string;
    resourceLimitCmd: string | null;
    pathSeparator: string;
    lineEnding: string;
}

export interface PlatformAdapter {
    getPlatformConfig(): PlatformConfig;
    isWindows(): boolean;
    getEnvironmentSeparator(): string;
    getDefaultPath(): string;
    resolvePath(path: string): string;
    createDirectory(path: string): Promise<void>;
    removeDirectory(path: string): Promise<void>;
    fileExists(path: string): Promise<boolean>;
    directoryExists(path: string): Promise<boolean>;
}
