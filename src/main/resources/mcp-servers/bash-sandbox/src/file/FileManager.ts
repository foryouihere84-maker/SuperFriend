import * as fs from 'fs/promises';
import * as path from 'path';
import { PlatformAdapter } from '../platform/PlatformAdapter.js';
import { logger } from '../utils/index.js';

export interface FileInfo {
    name: string;
    path: string;
    absolutePath: string;
    size: number;
    isDirectory: boolean;
    modifiedAt: Date;
    createdAt?: Date;
}

export interface ExportResult {
    success: boolean;
    sourcePath: string;
    targetPath: string;
    bytesCopied: number;
    error?: string;
}

export interface FileManagerConfig {
    sharedDirectory?: string;
    exportDirectory?: string;
    maxFileSize?: number;
    allowedExtensions?: string[];
}

export class FileManager {
    private platformAdapter: PlatformAdapter;
    private sharedDirectory: string;
    private exportDirectory: string;
    private maxFileSize: number;
    private allowedExtensions: Set<string> | null;

    constructor(platformAdapter: PlatformAdapter, config: FileManagerConfig = {}) {
        this.platformAdapter = platformAdapter;
        
        const platformConfig = platformAdapter.getPlatformConfig();
        
        this.sharedDirectory = config.sharedDirectory || 
            process.env.SANDBOX_SHARED_DIR ||
            path.join(platformConfig.sandboxDir, 'shared');
        
        this.exportDirectory = config.exportDirectory ||
            process.env.SANDBOX_EXPORT_DIR ||
            path.join(platformConfig.sandboxDir, 'exports');
        
        this.maxFileSize = config.maxFileSize || 100 * 1024 * 1024;
        
        if (config.allowedExtensions && config.allowedExtensions.length > 0) {
            this.allowedExtensions = new Set(config.allowedExtensions.map(e => e.toLowerCase()));
        } else {
            this.allowedExtensions = null;
        }
    }

    async initialize(): Promise<void> {
        await this.platformAdapter.createDirectory(this.sharedDirectory);
        await this.platformAdapter.createDirectory(this.exportDirectory);
        logger.info('FileManager initialized', {
            sharedDirectory: this.sharedDirectory,
            exportDirectory: this.exportDirectory
        });
    }

    getSharedDirectory(): string {
        return this.sharedDirectory;
    }

    getExportDirectory(): string {
        return this.exportDirectory;
    }

    async listFiles(directory: string, recursive: boolean = false): Promise<FileInfo[]> {
        const absolutePath = this.resolveSecurePath(directory);
        
        if (!await this.platformAdapter.directoryExists(absolutePath)) {
            return [];
        }

        const files: FileInfo[] = [];
        await this.listDirectoryRecursive(absolutePath, files, recursive);
        return files;
    }

    private async listDirectoryRecursive(
        dirPath: string, 
        files: FileInfo[], 
        recursive: boolean,
        basePath?: string
    ): Promise<void> {
        const entries = await fs.readdir(dirPath, { withFileTypes: true });
        
        for (const entry of entries) {
            const fullPath = path.join(dirPath, entry.name);
            const relativePath = basePath ? path.join(basePath, entry.name) : entry.name;
            
            try {
                const stats = await fs.stat(fullPath);
                
                files.push({
                    name: entry.name,
                    path: relativePath,
                    absolutePath: fullPath,
                    size: stats.size,
                    isDirectory: entry.isDirectory(),
                    modifiedAt: stats.mtime,
                    createdAt: stats.birthtime
                });

                if (recursive && entry.isDirectory()) {
                    await this.listDirectoryRecursive(fullPath, files, recursive, relativePath);
                }
            } catch (error) {
                logger.warn(`Failed to stat file: ${fullPath}`, { error });
            }
        }
    }

    async getFileInfo(filePath: string): Promise<FileInfo | null> {
        const absolutePath = this.resolveSecurePath(filePath);
        
        if (!await this.platformAdapter.fileExists(absolutePath)) {
            return null;
        }

        try {
            const stats = await fs.stat(absolutePath);
            const name = path.basename(filePath);
            
            return {
                name,
                path: filePath,
                absolutePath,
                size: stats.size,
                isDirectory: stats.isDirectory(),
                modifiedAt: stats.mtime,
                createdAt: stats.birthtime
            };
        } catch (error) {
            logger.error(`Failed to get file info: ${absolutePath}`, { error });
            return null;
        }
    }

    async exportFile(
        sourcePath: string, 
        targetPath?: string,
        sessionId?: string
    ): Promise<ExportResult> {
        const absoluteSource = this.resolveSecurePath(sourcePath);
        
        if (!await this.platformAdapter.fileExists(absoluteSource)) {
            return {
                success: false,
                sourcePath,
                targetPath: targetPath || '',
                bytesCopied: 0,
                error: 'Source file does not exist'
            };
        }

        try {
            const stats = await fs.stat(absoluteSource);
            
            if (stats.size > this.maxFileSize) {
                return {
                    success: false,
                    sourcePath,
                    targetPath: targetPath || '',
                    bytesCopied: 0,
                    error: `File too large: ${stats.size} bytes (max: ${this.maxFileSize})`
                };
            }

            const fileName = path.basename(sourcePath);
            
            if (!this.isExtensionAllowed(fileName)) {
                return {
                    success: false,
                    sourcePath,
                    targetPath: targetPath || '',
                    bytesCopied: 0,
                    error: 'File extension not allowed'
                };
            }

            let targetDir: string;
            let finalTargetPath: string;
            
            if (targetPath) {
                finalTargetPath = this.resolveSecurePath(targetPath);
                targetDir = path.dirname(finalTargetPath);
            } else {
                const exportSubDir = sessionId 
                    ? path.join(this.exportDirectory, sessionId)
                    : this.exportDirectory;
                targetDir = exportSubDir;
                finalTargetPath = path.join(targetDir, fileName);
            }

            await this.platformAdapter.createDirectory(targetDir);

            await fs.copyFile(absoluteSource, finalTargetPath);

            logger.info('File exported', {
                source: absoluteSource,
                target: finalTargetPath,
                bytes: stats.size
            });

            return {
                success: true,
                sourcePath,
                targetPath: finalTargetPath,
                bytesCopied: stats.size
            };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            logger.error('Export failed', { sourcePath, error: errorMessage });
            
            return {
                success: false,
                sourcePath,
                targetPath: targetPath || '',
                bytesCopied: 0,
                error: errorMessage
            };
        }
    }

    async exportDirectoryContents(
        sourceDir: string,
        targetDir?: string,
        sessionId?: string
    ): Promise<ExportResult[]> {
        const absoluteSource = this.resolveSecurePath(sourceDir);
        
        if (!await this.platformAdapter.directoryExists(absoluteSource)) {
            return [{
                success: false,
                sourcePath: sourceDir,
                targetPath: targetDir || '',
                bytesCopied: 0,
                error: 'Source directory does not exist'
            }];
        }

        const results: ExportResult[] = [];
        const files = await this.listFiles(sourceDir, true);

        for (const file of files) {
            if (!file.isDirectory) {
                const relativePath = file.path;
                const targetPath = targetDir 
                    ? path.join(targetDir, relativePath)
                    : undefined;
                
                const result = await this.exportFile(
                    path.join(sourceDir, relativePath),
                    targetPath,
                    sessionId
                );
                results.push(result);
            }
        }

        return results;
    }

    async copyToShared(
        sourcePath: string, 
        sessionId: string,
        newName?: string
    ): Promise<ExportResult> {
        const fileName = newName || path.basename(sourcePath);
        const targetPath = path.join(this.sharedDirectory, sessionId, fileName);
        
        return this.exportFile(sourcePath, targetPath, sessionId);
    }

    async readTextFile(filePath: string, encoding: BufferEncoding = 'utf-8'): Promise<string> {
        const absolutePath = this.resolveSecurePath(filePath);
        
        if (!await this.platformAdapter.fileExists(absolutePath)) {
            throw new Error(`File not found: ${filePath}`);
        }

        const stats = await fs.stat(absolutePath);
        if (stats.size > this.maxFileSize) {
            throw new Error(`File too large: ${stats.size} bytes`);
        }

        return fs.readFile(absolutePath, encoding);
    }

    async writeTextFile(
        filePath: string, 
        content: string, 
        encoding: BufferEncoding = 'utf-8'
    ): Promise<{ success: boolean; path: string; bytes: number; error?: string }> {
        try {
            const absolutePath = this.resolveSecurePath(filePath);
            const dir = path.dirname(absolutePath);
            
            await this.platformAdapter.createDirectory(dir);
            await fs.writeFile(absolutePath, content, encoding);
            
            const bytes = Buffer.byteLength(content, encoding);
            
            logger.info('File written', { path: absolutePath, bytes });
            
            return {
                success: true,
                path: absolutePath,
                bytes
            };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            return {
                success: false,
                path: filePath,
                bytes: 0,
                error: errorMessage
            };
        }
    }

    async deleteFile(filePath: string): Promise<{ success: boolean; error?: string }> {
        try {
            const absolutePath = this.resolveSecurePath(filePath);
            
            if (!await this.platformAdapter.fileExists(absolutePath)) {
                return { success: false, error: 'File not found' };
            }

            await fs.unlink(absolutePath);
            logger.info('File deleted', { path: absolutePath });
            
            return { success: true };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            return { success: false, error: errorMessage };
        }
    }

    resolveSecurePath(filePath: string): string {
        let resolved: string;
        
        if (path.isAbsolute(filePath)) {
            resolved = path.normalize(filePath);
        } else {
            resolved = path.resolve(filePath);
        }

        return resolved;
    }

    private isExtensionAllowed(fileName: string): boolean {
        if (!this.allowedExtensions) {
            return true;
        }

        const ext = path.extname(fileName).toLowerCase();
        return this.allowedExtensions.has(ext) || this.allowedExtensions.has(ext.slice(1));
    }

    getDirectoryStats(directory: string): Promise<{
        fileCount: number;
        directoryCount: number;
        totalSize: number;
    }> {
        return this.listFiles(directory, true).then(files => ({
            fileCount: files.filter(f => !f.isDirectory).length,
            directoryCount: files.filter(f => f.isDirectory).length,
            totalSize: files.filter(f => !f.isDirectory).reduce((sum, f) => sum + f.size, 0)
        }));
    }
}
