#!/usr/bin/env node

import { BashSandboxServer } from './server/BashSandboxServer.js';

async function main() {
    const server = new BashSandboxServer();
    await server.start();
}

main().catch((error) => {
    console.error('Fatal error:', error);
    process.exit(1);
});
