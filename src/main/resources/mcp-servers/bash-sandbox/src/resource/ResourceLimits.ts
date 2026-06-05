import * as os from 'os';

export interface ResourceLimits {
    maxCpuSeconds: number;
    maxMemoryBytes: number;
    maxMemoryKb: number;
    maxFileSizeKb: number;
    maxProcesses: number;
    maxOpenFiles: number;
    commandTimeoutMs: number;
    maxOutputBytes: number;
}

export interface SystemResources {
    totalMemory: number;
    freeMemory: number;
    availableMemory: number;
    cpuCount: number;
    cpuLoadAverage: number[];
}

export function getSystemResources(): SystemResources {
    return {
        totalMemory: os.totalmem(),
        freeMemory: os.freemem(),
        availableMemory: os.freemem(),
        cpuCount: os.cpus().length,
        cpuLoadAverage: os.loadavg()
    };
}
