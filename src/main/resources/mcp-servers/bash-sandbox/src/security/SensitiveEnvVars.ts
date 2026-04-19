export const SENSITIVE_ENV_VARS: Set<string> = new Set([
    'PASSWORD',
    'PASSWD',
    'SECRET',
    'TOKEN',
    'API_KEY',
    'APIKEY',
    'AWS_ACCESS_KEY_ID',
    'AWS_SECRET_ACCESS_KEY',
    'AWS_SESSION_TOKEN',
    'PRIVATE_KEY',
    'PRIVATEKEY',
    'CREDENTIALS',
    'AUTH'
]);

export function isSensitiveEnvVar(name: string): boolean {
    const upperName = name.toUpperCase();
    for (const sensitive of SENSITIVE_ENV_VARS) {
        if (upperName.includes(sensitive)) {
            return true;
        }
    }
    return false;
}

export function filterSensitiveEnvVars(env: Record<string, string>): Record<string, string> {
    const filtered: Record<string, string> = {};
    for (const [key, value] of Object.entries(env)) {
        if (!isSensitiveEnvVar(key)) {
            filtered[key] = value;
        }
    }
    return filtered;
}
