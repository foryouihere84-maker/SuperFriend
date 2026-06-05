export interface CacheEntry<T> {
    value: T;
    createdAt: number;
    expiresAt: number;
    hits: number;
}

export interface CacheOptions {
    ttlMs?: number;
    maxSize?: number;
}

export class CacheManager<T> {
    private cache: Map<string, CacheEntry<T>> = new Map();
    private defaultTtlMs: number;
    private maxSize: number;
    private hits: number = 0;
    private misses: number = 0;

    constructor(options: CacheOptions = {}) {
        this.defaultTtlMs = options.ttlMs || 60000;
        this.maxSize = options.maxSize || 1000;
    }

    get(key: string): T | undefined {
        const entry = this.cache.get(key);
        
        if (!entry) {
            this.misses++;
            return undefined;
        }

        if (Date.now() > entry.expiresAt) {
            this.cache.delete(key);
            this.misses++;
            return undefined;
        }

        entry.hits++;
        this.hits++;
        return entry.value;
    }

    set(key: string, value: T, ttlMs?: number): void {
        if (this.cache.size >= this.maxSize && !this.cache.has(key)) {
            this.evictOldest();
        }

        const now = Date.now();
        const ttl = ttlMs ?? this.defaultTtlMs;

        this.cache.set(key, {
            value,
            createdAt: now,
            expiresAt: now + ttl,
            hits: 0
        });
    }

    has(key: string): boolean {
        const entry = this.cache.get(key);
        if (!entry) return false;
        
        if (Date.now() > entry.expiresAt) {
            this.cache.delete(key);
            return false;
        }
        
        return true;
    }

    delete(key: string): boolean {
        return this.cache.delete(key);
    }

    clear(): void {
        this.cache.clear();
        this.hits = 0;
        this.misses = 0;
    }

    private evictOldest(): void {
        let oldestKey: string | null = null;
        let oldestTime = Infinity;

        for (const [key, entry] of this.cache) {
            if (entry.createdAt < oldestTime) {
                oldestTime = entry.createdAt;
                oldestKey = key;
            }
        }

        if (oldestKey) {
            this.cache.delete(oldestKey);
        }
    }

    cleanup(): number {
        const now = Date.now();
        let cleaned = 0;

        for (const [key, entry] of this.cache) {
            if (now > entry.expiresAt) {
                this.cache.delete(key);
                cleaned++;
            }
        }

        return cleaned;
    }

    getStats(): {
        size: number;
        maxSize: number;
        hits: number;
        misses: number;
        hitRate: number;
    } {
        const total = this.hits + this.misses;
        return {
            size: this.cache.size,
            maxSize: this.maxSize,
            hits: this.hits,
            misses: this.misses,
            hitRate: total > 0 ? this.hits / total : 0
        };
    }

    getOrSet(key: string, factory: () => T | Promise<T>, ttlMs?: number): T | Promise<T> {
        const cached = this.get(key);
        if (cached !== undefined) {
            return cached;
        }

        const value = factory();
        
        if (value instanceof Promise) {
            return value.then(v => {
                this.set(key, v, ttlMs);
                return v;
            });
        }

        this.set(key, value, ttlMs);
        return value;
    }
}

export class PerformanceMonitor {
    private metrics: Map<string, {
        count: number;
        totalTimeMs: number;
        minTimeMs: number;
        maxTimeMs: number;
        lastTimeMs: number;
    }> = new Map();

    private startTime: number;

    constructor() {
        this.startTime = Date.now();
    }

    startTimer(): () => number {
        const start = Date.now();
        return () => {
            const elapsed = Date.now() - start;
            return elapsed;
        };
    }

    recordMetric(name: string, timeMs: number): void {
        const metric = this.metrics.get(name);
        
        if (metric) {
            metric.count++;
            metric.totalTimeMs += timeMs;
            metric.minTimeMs = Math.min(metric.minTimeMs, timeMs);
            metric.maxTimeMs = Math.max(metric.maxTimeMs, timeMs);
            metric.lastTimeMs = timeMs;
        } else {
            this.metrics.set(name, {
                count: 1,
                totalTimeMs: timeMs,
                minTimeMs: timeMs,
                maxTimeMs: timeMs,
                lastTimeMs: timeMs
            });
        }
    }

    measure<T>(name: string, fn: () => T | Promise<T>): T | Promise<T> {
        const stopTimer = this.startTimer();

        const result = fn();

        if (result instanceof Promise) {
            return result
                .then(v => {
                    this.recordMetric(name, stopTimer());
                    return v;
                })
                .catch(e => {
                    this.recordMetric(`${name}_error`, stopTimer());
                    throw e;
                });
        }

        this.recordMetric(name, stopTimer());
        return result;
    }

    async measureAsync<T>(name: string, fn: () => Promise<T>): Promise<T> {
        const stopTimer = this.startTimer();
        try {
            const result = await fn();
            this.recordMetric(name, stopTimer());
            return result;
        } catch (e) {
            this.recordMetric(`${name}_error`, stopTimer());
            throw e;
        }
    }

    getMetric(name: string): {
        count: number;
        avgTimeMs: number;
        minTimeMs: number;
        maxTimeMs: number;
        lastTimeMs: number;
    } | undefined {
        const metric = this.metrics.get(name);
        if (!metric) return undefined;

        return {
            count: metric.count,
            avgTimeMs: metric.totalTimeMs / metric.count,
            minTimeMs: metric.minTimeMs,
            maxTimeMs: metric.maxTimeMs,
            lastTimeMs: metric.lastTimeMs
        };
    }

    getAllMetrics(): Record<string, {
        count: number;
        avgTimeMs: number;
        minTimeMs: number;
        maxTimeMs: number;
        lastTimeMs: number;
    }> {
        const result: Record<string, {
            count: number;
            avgTimeMs: number;
            minTimeMs: number;
            maxTimeMs: number;
            lastTimeMs: number;
        }> = {};

        for (const [name, metric] of this.metrics) {
            result[name] = {
                count: metric.count,
                avgTimeMs: metric.totalTimeMs / metric.count,
                minTimeMs: metric.minTimeMs,
                maxTimeMs: metric.maxTimeMs,
                lastTimeMs: metric.lastTimeMs
            };
        }

        return result;
    }

    getUptime(): number {
        return Date.now() - this.startTime;
    }

    reset(): void {
        this.metrics.clear();
        this.startTime = Date.now();
    }
}

export const globalPerformanceMonitor = new PerformanceMonitor();
