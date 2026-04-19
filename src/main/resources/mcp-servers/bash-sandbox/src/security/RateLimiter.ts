export interface RateLimitConfig {
    maxRequestsPerMinute: number;
    maxRequestsPerHour: number;
    maxConcurrentRequests: number;
}

export interface RateLimitStatus {
    allowed: boolean;
    reason?: string;
    retryAfterMs?: number;
    currentMinuteCount: number;
    currentHourCount: number;
    currentConcurrent: number;
}

export class RateLimiter {
    private minuteWindow: Map<string, number[]> = new Map();
    private hourWindow: Map<string, number[]> = new Map();
    private concurrentRequests: Map<string, number> = new Map();
    private config: RateLimitConfig;

    constructor(config: Partial<RateLimitConfig> = {}) {
        this.config = {
            maxRequestsPerMinute: config.maxRequestsPerMinute || 60,
            maxRequestsPerHour: config.maxRequestsPerHour || 500,
            maxConcurrentRequests: config.maxConcurrentRequests || 5
        };

        setInterval(() => this.cleanup(), 60000);
    }

    checkLimit(sessionId: string): RateLimitStatus {
        const now = Date.now();
        const minuteAgo = now - 60000;
        const hourAgo = now - 3600000;

        const minuteRequests = this.minuteWindow.get(sessionId) || [];
        const hourRequests = this.hourWindow.get(sessionId) || [];
        const currentConcurrent = this.concurrentRequests.get(sessionId) || 0;

        const currentMinuteCount = minuteRequests.filter(t => t > minuteAgo).length;
        const currentHourCount = hourRequests.filter(t => t > hourAgo).length;

        if (currentConcurrent >= this.config.maxConcurrentRequests) {
            return {
                allowed: false,
                reason: 'Too many concurrent requests',
                currentMinuteCount,
                currentHourCount,
                currentConcurrent
            };
        }

        if (currentMinuteCount >= this.config.maxRequestsPerMinute) {
            const oldestInMinute = Math.min(...minuteRequests.filter(t => t > minuteAgo));
            const retryAfterMs = oldestInMinute + 60000 - now;

            return {
                allowed: false,
                reason: 'Rate limit exceeded: too many requests per minute',
                retryAfterMs: Math.max(0, retryAfterMs),
                currentMinuteCount,
                currentHourCount,
                currentConcurrent
            };
        }

        if (currentHourCount >= this.config.maxRequestsPerHour) {
            const oldestInHour = Math.min(...hourRequests.filter(t => t > hourAgo));
            const retryAfterMs = oldestInHour + 3600000 - now;

            return {
                allowed: false,
                reason: 'Rate limit exceeded: too many requests per hour',
                retryAfterMs: Math.max(0, retryAfterMs),
                currentMinuteCount,
                currentHourCount,
                currentConcurrent
            };
        }

        return {
            allowed: true,
            currentMinuteCount,
            currentHourCount,
            currentConcurrent
        };
    }

    recordRequest(sessionId: string): void {
        const now = Date.now();

        const minuteRequests = this.minuteWindow.get(sessionId) || [];
        minuteRequests.push(now);
        this.minuteWindow.set(sessionId, minuteRequests);

        const hourRequests = this.hourWindow.get(sessionId) || [];
        hourRequests.push(now);
        this.hourWindow.set(sessionId, hourRequests);
    }

    startRequest(sessionId: string): void {
        const current = this.concurrentRequests.get(sessionId) || 0;
        this.concurrentRequests.set(sessionId, current + 1);
    }

    endRequest(sessionId: string): void {
        const current = this.concurrentRequests.get(sessionId) || 0;
        if (current > 0) {
            this.concurrentRequests.set(sessionId, current - 1);
        }
    }

    private cleanup(): void {
        const now = Date.now();
        const minuteAgo = now - 60000;
        const hourAgo = now - 3600000;

        for (const [sessionId, requests] of this.minuteWindow) {
            const filtered = requests.filter(t => t > minuteAgo);
            if (filtered.length === 0) {
                this.minuteWindow.delete(sessionId);
            } else {
                this.minuteWindow.set(sessionId, filtered);
            }
        }

        for (const [sessionId, requests] of this.hourWindow) {
            const filtered = requests.filter(t => t > hourAgo);
            if (filtered.length === 0) {
                this.hourWindow.delete(sessionId);
            } else {
                this.hourWindow.set(sessionId, filtered);
            }
        }
    }

    getConfig(): RateLimitConfig {
        return { ...this.config };
    }

    setConfig(config: Partial<RateLimitConfig>): void {
        this.config = {
            ...this.config,
            ...config
        };
    }

    reset(sessionId?: string): void {
        if (sessionId) {
            this.minuteWindow.delete(sessionId);
            this.hourWindow.delete(sessionId);
            this.concurrentRequests.delete(sessionId);
        } else {
            this.minuteWindow.clear();
            this.hourWindow.clear();
            this.concurrentRequests.clear();
        }
    }
}
