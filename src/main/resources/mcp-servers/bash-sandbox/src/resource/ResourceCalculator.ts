import { ResourceLimits, SystemResources, getSystemResources } from './ResourceLimits.js';

export interface ResourceCalculatorConfig {
    resourceReserveRatio: number;
    baseCpuSeconds: number;
    maxCpuSeconds: number;
    minCpuSeconds: number;
    baseMemoryRatio: number;
    maxMemoryBytes: number;
    minMemoryBytes: number;
    maxFileSizeKb: number;
    maxProcesses: number;
    maxOpenFiles: number;
    baseTimeoutMs: number;
    maxTimeoutMs: number;
    minTimeoutMs: number;
    maxOutputBytes: number;
}

const DEFAULT_CONFIG: ResourceCalculatorConfig = {
    resourceReserveRatio: 0.2,
    baseCpuSeconds: 30,
    maxCpuSeconds: 120,
    minCpuSeconds: 10,
    baseMemoryRatio: 0.25,
    maxMemoryBytes: 1024 * 1024 * 1024,
    minMemoryBytes: 64 * 1024 * 1024,
    maxFileSizeKb: 100 * 1024,
    maxProcesses: 64,
    maxOpenFiles: 256,
    baseTimeoutMs: 60000,
    maxTimeoutMs: 300000,
    minTimeoutMs: 5000,
    maxOutputBytes: 10 * 1024 * 1024
};

export class ResourceCalculator {
    private config: ResourceCalculatorConfig;
    private cachedResources: SystemResources | null = null;
    private cacheTime: number = 0;
    private cacheTtlMs: number = 5000;

    constructor(config: Partial<ResourceCalculatorConfig> = {}) {
        this.config = { ...DEFAULT_CONFIG, ...config };
    }

    calculate(): ResourceLimits {
        const resources = this.getFreshSystemResources();
        const limits: ResourceLimits = {
            maxCpuSeconds: 0,
            maxMemoryBytes: 0,
            maxMemoryKb: 0,
            maxFileSizeKb: 0,
            maxProcesses: 0,
            maxOpenFiles: 0,
            commandTimeoutMs: 0,
            maxOutputBytes: 0
        };

        const memoryRatio = this.calculateMemoryRatio(resources);
        const cpuRatio = this.calculateCpuRatio(resources);

        const maxCpuCores = Math.min(Math.floor(resources.cpuCount / 2), 4);
        limits.maxCpuSeconds = Math.floor(
            this.config.baseCpuSeconds * maxCpuCores * cpuRatio
        );
        limits.maxCpuSeconds = Math.max(
            limits.maxCpuSeconds, 
            this.config.minCpuSeconds
        );
        limits.maxCpuSeconds = Math.min(
            limits.maxCpuSeconds, 
            this.config.maxCpuSeconds
        );

        const baseMemoryLimit = Math.floor(
            resources.availableMemory * this.config.baseMemoryRatio
        );
        limits.maxMemoryBytes = Math.floor(
            Math.min(baseMemoryLimit, this.config.maxMemoryBytes) * memoryRatio
        );
        limits.maxMemoryBytes = Math.max(
            limits.maxMemoryBytes, 
            this.config.minMemoryBytes
        );
        limits.maxMemoryKb = Math.floor(limits.maxMemoryBytes / 1024);

        limits.maxFileSizeKb = this.config.maxFileSizeKb;
        limits.maxProcesses = this.config.maxProcesses;
        limits.maxOpenFiles = this.config.maxOpenFiles;

        limits.commandTimeoutMs = Math.min(
            this.config.baseTimeoutMs,
            limits.maxCpuSeconds * 1000
        );
        limits.commandTimeoutMs = Math.max(
            limits.commandTimeoutMs,
            this.config.minTimeoutMs
        );

        limits.maxOutputBytes = this.config.maxOutputBytes;

        return limits;
    }

    private getFreshSystemResources(): SystemResources {
        const now = Date.now();
        if (this.cachedResources && (now - this.cacheTime) < this.cacheTtlMs) {
            return this.cachedResources;
        }
        
        this.cachedResources = getSystemResources();
        this.cacheTime = now;
        return this.cachedResources;
    }

    private calculateMemoryRatio(resources: SystemResources): number {
        const ratio = resources.availableMemory / resources.totalMemory;
        return Math.max(0, (1 - this.config.resourceReserveRatio) * ratio);
    }

    private calculateCpuRatio(resources: SystemResources): number {
        if (resources.cpuLoadAverage.length === 0) {
            return 0.5;
        }

        const loadAverage = resources.cpuLoadAverage[0];
        if (loadAverage < 0) {
            return 0.5;
        }

        const loadPerCore = loadAverage / resources.cpuCount;

        if (loadPerCore < 0.5) {
            return 1.0;
        } else if (loadPerCore < 1.0) {
            return 0.75;
        } else if (loadPerCore < 2.0) {
            return 0.5;
        } else {
            return 0.25;
        }
    }

    updateConfig(newConfig: Partial<ResourceCalculatorConfig>): void {
        this.config = { ...this.config, ...newConfig };
    }

    getConfig(): ResourceCalculatorConfig {
        return { ...this.config };
    }
}
