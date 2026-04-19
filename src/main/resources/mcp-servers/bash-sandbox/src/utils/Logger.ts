export enum LogLevel {
    DEBUG = 'DEBUG',
    INFO = 'INFO',
    WARN = 'WARN',
    ERROR = 'ERROR'
}

export interface LogEntry {
    timestamp: string;
    level: LogLevel;
    component: string;
    message: string;
    data?: Record<string, unknown>;
}

class LoggerImpl {
    private component: string;
    private minLevel: LogLevel;

    constructor(component: string, minLevel: LogLevel = LogLevel.INFO) {
        this.component = component;
        this.minLevel = minLevel;
    }

    private shouldLog(level: LogLevel): boolean {
        const levels = [LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR];
        return levels.indexOf(level) >= levels.indexOf(this.minLevel);
    }

    private formatEntry(level: LogLevel, message: string, data?: Record<string, unknown>): LogEntry {
        return {
            timestamp: new Date().toISOString(),
            level,
            component: this.component,
            message,
            data
        };
    }

    private output(entry: LogEntry): void {
        const logLine = `[${entry.timestamp}] [${entry.level}] [${entry.component}] ${entry.message}`;
        
        switch (entry.level) {
            case LogLevel.ERROR:
                console.error(logLine, entry.data || '');
                break;
            case LogLevel.WARN:
                console.warn(logLine, entry.data || '');
                break;
            default:
                console.log(logLine, entry.data || '');
        }
    }

    debug(message: string, data?: Record<string, unknown>): void {
        if (this.shouldLog(LogLevel.DEBUG)) {
            this.output(this.formatEntry(LogLevel.DEBUG, message, data));
        }
    }

    info(message: string, data?: Record<string, unknown>): void {
        if (this.shouldLog(LogLevel.INFO)) {
            this.output(this.formatEntry(LogLevel.INFO, message, data));
        }
    }

    warn(message: string, data?: Record<string, unknown>): void {
        if (this.shouldLog(LogLevel.WARN)) {
            this.output(this.formatEntry(LogLevel.WARN, message, data));
        }
    }

    error(message: string, data?: Record<string, unknown>): void {
        if (this.shouldLog(LogLevel.ERROR)) {
            this.output(this.formatEntry(LogLevel.ERROR, message, data));
        }
    }

    child(subComponent: string): LoggerImpl {
        return new LoggerImpl(`${this.component}:${subComponent}`, this.minLevel);
    }
}

export function createLogger(component: string, minLevel?: LogLevel): LoggerImpl {
    return new LoggerImpl(component, minLevel);
}

export const logger = createLogger('bash_sandbox');
