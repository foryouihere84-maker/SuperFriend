import { PlatformAdapter } from './PlatformAdapter.js';
import { LinuxAdapter } from './LinuxAdapter.js';
import { WindowsAdapter } from './WindowsAdapter.js';

export function createPlatformAdapter(sandboxRoot?: string): PlatformAdapter {
    const platform = process.platform;
    
    if (platform === 'win32') {
        return new WindowsAdapter(sandboxRoot);
    } else {
        return new LinuxAdapter(sandboxRoot);
    }
}

export { PlatformAdapter } from './PlatformAdapter.js';
export { LinuxAdapter } from './LinuxAdapter.js';
export { WindowsAdapter } from './WindowsAdapter.js';
