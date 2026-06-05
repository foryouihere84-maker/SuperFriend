import * as os from 'os';
import * as fs from 'fs/promises';
import * as path from 'path';

export interface ResourceUsage {
    timestamp: string;
    cpu: {
        usagePercent: number;
        loadAverage: number[];
    };
    memory: {
        totalBytes: number;
        usedBytes: number;
        freeBytes: number;
        usagePercent: number;
    };
    disk: {
        totalBytes: number;
        usedBytes: number;
        freeBytes: number;
        usagePercent: number;
    };
    process: {
        memoryBytes: number;
        cpuPercent: number;
    };
}

export interface SessionResourceUsage {
    sessionId: string;
    commandCount: number;
    totalCpuTimeMs: number;
    peakMemoryBytes: number;
    totalDiskReadBytes: number;
    totalDiskWriteBytes: number;
    lastUpdated: string;
}

export class ResourceMonitor {
    private sandboxDir: string;
    private sessionUsage: Map<string, SessionResourceUsage> = new Map();
    private previousCpuUsage: { user: number; system: number } | null = null;
    private previousCpuTime: number | null = null;

    constructor(sandboxDir: string) {
        this.sandboxDir = sandboxDir;
    }

    async getCurrentUsage(): Promise<ResourceUsage> {
        const cpuInfo = this.getCpuUsage();
        const memoryInfo = this.getMemoryUsage();
        const diskInfo = await this.getDiskUsage();
        const processInfo = this.getProcessUsage();

        return {
            timestamp: new Date().toISOString(),
            cpu: cpuInfo,
            memory: memoryInfo,
            disk: diskInfo,
            process: processInfo
        };
    }

    private getCpuUsage(): { usagePercent: number; loadAverage: number[] } {
        const loadAverage = os.loadavg();
        
        const cpus = os.cpus();
        let totalIdle = 0;
        let totalTick = 0;

        for (const cpu of cpus) {
            for (const type in cpu.times) {
                totalTick += (cpu.times as Record<string, number>)[type];
            }
            totalIdle += cpu.times.idle;
        }

        const totalUsage = totalTick - totalIdle;
        const usagePercent = totalTick > 0 ? (totalUsage / totalTick) * 100 : 0;

        return {
            usagePercent: Math.round(usagePercent * 100) / 100,
            loadAverage: loadAverage.map(l => Math.round(l * 100) / 100)
        };
    }

    private getMemoryUsage(): { totalBytes: number; usedBytes: number; freeBytes: number; usagePercent: number } {
        const totalBytes = os.totalmem();
        const freeBytes = os.freemem();
        const usedBytes = totalBytes - freeBytes;
        const usagePercent = totalBytes > 0 ? (usedBytes / totalBytes) * 100 : 0;

        return {
            totalBytes,
            usedBytes,
            freeBytes,
            usagePercent: Math.round(usagePercent * 100) / 100
        };
    }

    private async getDiskUsage(): Promise<{ totalBytes: number; usedBytes: number; freeBytes: number; usagePercent: number }> {
        try {
            const stats = await fs.statfs(this.sandboxDir);
            const totalBytes = stats.blocks * stats.bsize;
            const freeBytes = stats.bfree * stats.bsize;
            const usedBytes = totalBytes - freeBytes;
            const usagePercent = totalBytes > 0 ? (usedBytes / totalBytes) * 100 : 0;

            return {
                totalBytes,
                usedBytes,
                freeBytes,
                usagePercent: Math.round(usagePercent * 100) / 100
            };
        } catch {
            const memory = this.getMemoryUsage();
            return {
                totalBytes: memory.totalBytes,
                usedBytes: 0,
                freeBytes: memory.totalBytes,
                usagePercent: 0
            };
        }
    }

    private getProcessUsage(): { memoryBytes: number; cpuPercent: number } {
        const memoryUsage = process.memoryUsage();
        const memoryBytes = memoryUsage.heapUsed;

        const cpuUsage = process.cpuUsage();
        const currentTime = Date.now();
        
        let cpuPercent = 0;
        if (this.previousCpuUsage && this.previousCpuTime) {
            const userDiff = cpuUsage.user - this.previousCpuUsage.user;
            const systemDiff = cpuUsage.system - this.previousCpuUsage.system;
            const totalDiff = userDiff + systemDiff;
            const timeDiff = (currentTime - this.previousCpuTime) * 1000;
            
            cpuPercent = timeDiff > 0 ? (totalDiff / timeDiff) * 100 : 0;
        }

        this.previousCpuUsage = { user: cpuUsage.user, system: cpuUsage.system };
        this.previousCpuTime = currentTime;

        return {
            memoryBytes,
            cpuPercent: Math.round(cpuPercent * 100) / 100
        };
    }

    initSession(sessionId: string): void {
        this.sessionUsage.set(sessionId, {
            sessionId,
            commandCount: 0,
            totalCpuTimeMs: 0,
            peakMemoryBytes: 0,
            totalDiskReadBytes: 0,
            totalDiskWriteBytes: 0,
            lastUpdated: new Date().toISOString()
        });
    }

    recordCommandExecution(
        sessionId: string,
        executionTimeMs: number,
        memoryUsedBytes: number
    ): void {
        const usage = this.sessionUsage.get(sessionId);
        if (usage) {
            usage.commandCount++;
            usage.totalCpuTimeMs += executionTimeMs;
            usage.peakMemoryBytes = Math.max(usage.peakMemoryBytes, memoryUsedBytes);
            usage.lastUpdated = new Date().toISOString();
        }
    }

    recordDiskIO(sessionId: string, readBytes: number, writeBytes: number): void {
        const usage = this.sessionUsage.get(sessionId);
        if (usage) {
            usage.totalDiskReadBytes += readBytes;
            usage.totalDiskWriteBytes += writeBytes;
            usage.lastUpdated = new Date().toISOString();
        }
    }

    getSessionUsage(sessionId: string): SessionResourceUsage | undefined {
        return this.sessionUsage.get(sessionId);
    }

    getAllSessionUsage(): SessionResourceUsage[] {
        return Array.from(this.sessionUsage.values());
    }

    clearSessionUsage(sessionId: string): void {
        this.sessionUsage.delete(sessionId);
    }

    async getSessionDiskUsage(sessionId: string): Promise<{ totalBytes: number; fileCount: number }> {
        const sessionDir = path.join(this.sandboxDir, 'sessions', sessionId);
        
        try {
            const stats = await this.getDirectoryStats(sessionDir);
            return stats;
        } catch {
            return { totalBytes: 0, fileCount: 0 };
        }
    }

    private async getDirectoryStats(dirPath: string): Promise<{ totalBytes: number; fileCount: number }> {
        let totalBytes = 0;
        let fileCount = 0;

        try {
            const entries = await fs.readdir(dirPath, { withFileTypes: true });

            for (const entry of entries) {
                const fullPath = path.join(dirPath, entry.name);

                if (entry.isDirectory()) {
                    const subStats = await this.getDirectoryStats(fullPath);
                    totalBytes += subStats.totalBytes;
                    fileCount += subStats.fileCount;
                } else if (entry.isFile()) {
                    try {
                        const stats = await fs.stat(fullPath);
                        totalBytes += stats.size;
                        fileCount++;
                    } catch {
                        // Ignore files that can't be accessed
                    }
                }
            }
        } catch {
            // Ignore directories that can't be accessed
        }

        return { totalBytes, fileCount };
    }

    getSystemInfo(): {
        platform: string;
        arch: string;
        hostname: string;
        cpus: number;
        totalMemory: number;
        uptime: number;
    } {
        return {
            platform: os.platform(),
            arch: os.arch(),
            hostname: os.hostname(),
            cpus: os.cpus().length,
            totalMemory: os.totalmem(),
            uptime: os.uptime()
        };
    }
}
