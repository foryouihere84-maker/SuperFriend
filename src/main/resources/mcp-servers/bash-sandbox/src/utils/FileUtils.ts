import * as fs from 'fs/promises';
import * as path from 'path';

export async function ensureDirectory(dirPath: string): Promise<void> {
    try {
        await fs.mkdir(dirPath, { recursive: true });
    } catch (error) {
        throw new Error(`Failed to create directory ${dirPath}: ${error}`);
    }
}

export async function removeDirectory(dirPath: string): Promise<void> {
    try {
        await fs.rm(dirPath, { recursive: true, force: true });
    } catch (error) {
        throw new Error(`Failed to remove directory ${dirPath}: ${error}`);
    }
}

export async function fileExists(filePath: string): Promise<boolean> {
    try {
        const stats = await fs.stat(filePath);
        return stats.isFile();
    } catch {
        return false;
    }
}

export async function directoryExists(dirPath: string): Promise<boolean> {
    try {
        const stats = await fs.stat(dirPath);
        return stats.isDirectory();
    } catch {
        return false;
    }
}

export async function readFile(filePath: string): Promise<string> {
    return fs.readFile(filePath, 'utf-8');
}

export async function writeFile(filePath: string, content: string): Promise<void> {
    await fs.writeFile(filePath, content, 'utf-8');
}

export async function appendFile(filePath: string, content: string): Promise<void> {
    await fs.appendFile(filePath, content, 'utf-8');
}

export function resolvePath(basePath: string, relativePath: string): string {
    return path.resolve(basePath, relativePath);
}

export function joinPath(...paths: string[]): string {
    return path.join(...paths);
}

export function normalizePath(filePath: string): string {
    return path.normalize(filePath);
}

export function getFileName(filePath: string): string {
    return path.basename(filePath);
}

export function getDirectoryName(filePath: string): string {
    return path.dirname(filePath);
}

export function getFileExtension(filePath: string): string {
    return path.extname(filePath);
}
