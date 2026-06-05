#!/usr/bin/env node
/**
 * Puppeteer MCP Server Wrapper
 * 增强版 v3.0 - 集成业界最佳反爬虫绕过方案
 * 参考: Scrapling, Crawlee, cloudscraper, puppeteer-extra-plugin-stealth
 */

const { Server } = require("@modelcontextprotocol/sdk/server/index.js");
const { StdioServerTransport } = require("@modelcontextprotocol/sdk/server/stdio.js");
const { CallToolRequestSchema, ListToolsRequestSchema } = require("@modelcontextprotocol/sdk/types.js");
const puppeteer = require("puppeteer");
const fs = require('fs');
const path = require('path');

// 工具定义
const TOOLS = [
    {
        name: "puppeteer_navigate",
        description: "Navigate to a URL with advanced anti-detection support",
        inputSchema: {
            type: "object",
            properties: {
                url: { type: "string", description: "URL to navigate to" },
                waitUntil: { 
                    type: "string", 
                    description: "When to consider navigation succeeded. Options: load, domcontentloaded, networkidle0, networkidle2. Default: networkidle2"
                },
                timeout: { 
                    type: "number", 
                    description: "Maximum navigation time in milliseconds. Default: 30000"
                },
                referer: {
                    type: "string",
                    description: "Referer URL to simulate coming from another page"
                },
            },
            required: ["url"],
        },
    },
    {
        name: "puppeteer_screenshot",
        description: "Take a screenshot of the current page or a specific element",
        inputSchema: {
            type: "object",
            properties: {
                name: { type: "string", description: "Name for the screenshot" },
                selector: { type: "string", description: "CSS selector for element to screenshot" },
                width: { type: "number", description: "Width in pixels (default: 1920)" },
                height: { type: "number", description: "Height in pixels (default: 1080)" },
                fullPage: { type: "boolean", description: "Capture full page (default: false)" },
                encoded: { type: "boolean", description: "Return base64-encoded data URI" },
            },
            required: ["name"],
        },
    },
    {
        name: "puppeteer_click",
        description: "Click an element with human-like behavior",
        inputSchema: {
            type: "object",
            properties: {
                selector: { type: "string", description: "CSS selector for element to click" },
                delay: { type: "number", description: "Delay between mouse down and up in ms (default: random 50-150)" },
                moveMouse: { type: "boolean", description: "Move mouse to element before clicking (default: true)" },
            },
            required: ["selector"],
        },
    },
    {
        name: "puppeteer_fill",
        description: "Fill input field with human-like typing",
        inputSchema: {
            type: "object",
            properties: {
                selector: { type: "string", description: "CSS selector for input field" },
                value: { type: "string", description: "Value to fill" },
                delay: { type: "number", description: "Delay between keystrokes in ms (default: random 30-80)" },
                clearFirst: { type: "boolean", description: "Clear field before typing (default: true)" },
            },
            required: ["selector", "value"],
        },
    },
    {
        name: "puppeteer_scroll",
        description: "Scroll the page with human-like behavior",
        inputSchema: {
            type: "object",
            properties: {
                direction: { type: "string", description: "Direction: down, up, bottom, top (default: down)" },
                amount: { type: "number", description: "Pixels to scroll (default: random 400-1000)" },
                smooth: { type: "boolean", description: "Smooth scroll (default: true)" },
                waitAfter: { type: "number", description: "Wait time after scroll in ms (default: random 500-1500)" },
            },
        },
    },
    {
        name: "puppeteer_infinite_scroll",
        description: "Auto-scroll to load infinite scroll content until no more new items",
        inputSchema: {
            type: "object",
            properties: {
                maxScrolls: { type: "number", description: "Maximum scroll attempts (default: 10)" },
                waitBetween: { type: "number", description: "Wait time between scrolls in ms (default: 2000)" },
                itemSelector: { type: "string", description: "CSS selector for items to count (optional)" },
                stopWhenSame: { type: "boolean", description: "Stop when item count doesn't increase (default: true)" },
            },
        },
    },
    {
        name: "puppeteer_evaluate",
        description: "Execute JavaScript in browser console. Script must be a function, e.g., '() => document.title'",
        inputSchema: {
            type: "object",
            properties: {
                script: { 
                    type: "string", 
                    description: "JavaScript function to execute. Must be a function like '() => document.body.innerText'. Do NOT use plain 'return' statement."
                },
            },
            required: ["script"],
        },
    },
    {
        name: "puppeteer_get_content",
        description: "Smart content extraction with anti-paywall support. Auto-detects article content from Zhihu, Medium, NYT, etc.",
        inputSchema: {
            type: "object",
            properties: {
                selector: { 
                    type: "string", 
                    description: "Optional CSS selector to extract from specific element"
                },
                waitFor: { 
                    type: "number", 
                    description: "Wait time in ms before extracting (default: 3000)"
                },
                removePaywall: {
                    type: "boolean",
                    description: "Attempt to remove common paywall overlays (default: true)"
                },
                removeScripts: {
                    type: "boolean",
                    description: "Remove script/style tags (default: true)"
                },
            },
        },
    },
    {
        name: "puppeteer_login",
        description: "Automate login process with session persistence",
        inputSchema: {
            type: "object",
            properties: {
                usernameSelector: { type: "string", description: "CSS selector for username field" },
                passwordSelector: { type: "string", description: "CSS selector for password field" },
                submitSelector: { type: "string", description: "CSS selector for submit button" },
                username: { type: "string", description: "Username/email" },
                password: { type: "string", description: "Password" },
                loginUrl: { type: "string", description: "Login page URL (if different from current)" },
                waitForSelector: { type: "string", description: "Element to wait for after login (optional)" },
                saveSession: { type: "boolean", description: "Save session cookies for reuse (default: true)" },
                sessionFile: { type: "string", description: "File to save session (default: ./sessions/[domain].json)" },
            },
            required: ["usernameSelector", "passwordSelector", "submitSelector", "username", "password"],
        },
    },
    {
        name: "puppeteer_load_session",
        description: "Load previously saved session cookies",
        inputSchema: {
            type: "object",
            properties: {
                sessionFile: { type: "string", description: "Path to session file" },
                url: { type: "string", description: "URL to navigate to after loading session" },
            },
        },
    },
    {
        name: "puppeteer_solve_captcha",
        description: "Detect and report CAPTCHA presence with screenshot for manual solving",
        inputSchema: {
            type: "object",
            properties: {
                checkSelectors: {
                    type: "array",
                    description: "Additional selectors to check for CAPTCHA"
                },
                takeScreenshot: {
                    type: "boolean",
                    description: "Take screenshot if CAPTCHA detected (default: true)"
                },
            },
        },
    },
    {
        name: "puppeteer_wait_for",
        description: "Wait for element to appear with smart polling",
        inputSchema: {
            type: "object",
            properties: {
                selector: { type: "string", description: "CSS selector to wait for" },
                timeout: { type: "number", description: "Timeout in ms (default: 10000)" },
                visible: { type: "boolean", description: "Wait for element to be visible (default: true)" },
            },
            required: ["selector"],
        },
    },
];

// 全局状态
let browser;
let page;
const consoleLogs = [];
const screenshots = new Map();
let previousLaunchOptions = null;
let currentSessionFile = null;

// Sessions directory
const SESSIONS_DIR = path.join(__dirname, 'sessions');
if (!fs.existsSync(SESSIONS_DIR)) {
    fs.mkdirSync(SESSIONS_DIR, { recursive: true });
}

/**
 * 修复脚本格式问题
 */
function fixScriptFormat(script) {
    if (!script || typeof script !== 'string') {
        return script;
    }
    
    const trimmed = script.trim();
    
    if (trimmed.startsWith('() =>') || 
        trimmed.startsWith('function') ||
        trimmed.startsWith('async function') ||
        trimmed.startsWith('async () =>')) {
        return script;
    }
    
    if (trimmed.startsWith('return ')) {
        console.error(`[PuppeteerWrapper] Auto-fix script: ${script.substring(0, 50)}...`);
        return `() => { ${script} }`;
    }
    
    console.error(`[PuppeteerWrapper] Auto-wrap script: ${script.substring(0, 50)}...`);
    return `() => { ${script} }`;
}

/**
 * 随机延迟 - 模拟人类行为
 */
function randomDelay(min = 50, max = 150) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * 获取随机 User-Agent
 */
function getRandomUserAgent() {
    const userAgents = [
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    ];
    return userAgents[Math.floor(Math.random() * userAgents.length)];
}

/**
 * 初始化浏览器 - 集成业界最佳反检测机制
 * 参考: Scrapling StealthyFetcher, Crawlee, puppeteer-extra-plugin-stealth
 */
async function ensureBrowser(launchOptions = {}) {
    try {
        if ((browser && !browser.connected) ||
            (launchOptions && JSON.stringify(launchOptions) !== JSON.stringify(previousLaunchOptions))) {
            await browser?.close();
            browser = null;
        }
    } catch (error) {
        browser = null;
    }
    
    previousLaunchOptions = launchOptions;
    
    if (!browser) {
        // 业界标准反检测启动参数
        const stealthArgs = [
            '--disable-blink-features=AutomationControlled',
            '--disable-web-security',
            '--disable-features=IsolateOrigins,site-per-process',
            '--disable-site-isolation-trials',
            '--disable-setuid-sandbox',
            '--disable-dev-shm-usage',
            '--disable-accelerated-2d-canvas',
            '--disable-gpu',
            '--window-size=1920,1080',
            '--start-maximized',
            '--hide-scrollbars',
            '--disable-notifications',
            '--disable-extensions',
            '--force-color-profile=srgb',
            '--mute-audio',
            '--disable-background-timer-throttling',
            '--disable-backgrounding-occluded-windows',
            '--disable-renderer-backgrounding',
            '--disable-features=TranslateUI',
            '--disable-component-extensions-with-background-pages',
            '--disable-features=InterestCohort',
            '--disable-features=PrivacySandboxAdsAPIs',
            '--disable-features=PrivacySandboxSettings4',
            '--disable-features=SignedExchangePrefetchCacheForNavigations',
            '--disable-features=SignedExchangeSubresourcePrefetch',
            '--disable-features=SubresourceWebBundles',
        ];
        
        const defaultArgs = { 
            headless: false,
            args: stealthArgs,
            ignoreDefaultArgs: ['--enable-automation', '--enable-blink-features=IdleDetection'],
        };
        
        browser = await puppeteer.launch({ ...defaultArgs, ...launchOptions });
        const pages = await browser.pages();
        page = pages[0];
        
        // 设置用户代理
        await page.setUserAgent(getRandomUserAgent());
        
        // 设置视口 - 模拟真实显示器
        await page.setViewport({ 
            width: 1920, 
            height: 1080,
            deviceScaleFactor: 1,
            hasTouch: false,
            isLandscape: true,
            isMobile: false,
        });
        
        // 执行高级反检测脚本 - 参考 Scrapling 和 Crawlee
        await page.evaluateOnNewDocument(() => {
            // 覆盖 webdriver
            Object.defineProperty(navigator, 'webdriver', {
                get: () => undefined,
            });
            
            // 覆盖 plugins - 模拟真实插件列表
            Object.defineProperty(navigator, 'plugins', {
                get: () => [
                    { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer', description: 'Portable Document Format', version: 'undefined' },
                    { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: 'Portable Document Format', version: 'undefined' },
                    { name: 'Native Client', filename: 'internal-nacl-plugin', description: '', version: 'undefined' },
                    { name: 'Widevine Content Decryption Module', filename: 'widevinecdmadapter.dll', description: 'Widevine Content Decryption Module', version: 'undefined' },
                ],
            });
            
            // 覆盖 mimeTypes
            Object.defineProperty(navigator, 'mimeTypes', {
                get: () => [
                    { type: 'application/pdf', suffixes: 'pdf', description: 'Portable Document Format', enabledPlugin: navigator.plugins[0] },
                    { type: 'application/x-google-chrome-pdf', suffixes: 'pdf', description: 'Portable Document Format', enabledPlugin: navigator.plugins[1] },
                    { type: 'application/x-nacl', suffixes: '', description: '', enabledPlugin: navigator.plugins[2] },
                    { type: 'application/x-pnacl', suffixes: '', description: '', enabledPlugin: navigator.plugins[2] },
                ],
            });
            
            // 覆盖 languages
            Object.defineProperty(navigator, 'languages', {
                get: () => ['zh-CN', 'zh', 'en-US', 'en'],
            });
            
            // 覆盖 hardwareConcurrency - 模拟真实 CPU 核心数
            Object.defineProperty(navigator, 'hardwareConcurrency', {
                get: () => 8,
            });
            
            // 覆盖 deviceMemory
            Object.defineProperty(navigator, 'deviceMemory', {
                get: () => 8,
            });
            
            // 覆盖 platform
            Object.defineProperty(navigator, 'platform', {
                get: () => 'Win32',
            });
            
            // 覆盖 permissions
            const originalQuery = window.navigator.permissions.query;
            window.navigator.permissions.query = (parameters) => (
                parameters.name === 'notifications' 
                    ? Promise.resolve({ state: Notification.permission })
                    : originalQuery(parameters)
            );
            
            // 覆盖 Chrome 运行时 - 更完整的模拟
            window.chrome = {
                runtime: {
                    OnInstalledReason: { CHROME_UPDATE: 'chrome_update', INSTALL: 'install', SHARED_MODULE_UPDATE: 'shared_module_update', UPDATE: 'update' },
                    OnRestartRequiredReason: { APP_UPDATE: 'app_update', OS_UPDATE: 'os_update', PERIODIC: 'periodic' },
                    PlatformArch: { ARM: 'arm', ARM64: 'arm64', MIPS: 'mips', MIPS64: 'mips64', X86_32: 'x86-32', X86_64: 'x86-64' },
                    PlatformNaclArch: { ARM: 'arm', MIPS: 'mips', MIPS64: 'mips64', MIPS64EL: 'mips64el', MIPSEL: 'mipsel', X86_32: 'x86-32', X86_64: 'x86-64' },
                    PlatformOs: { ANDROID: 'android', CROS: 'cros', LINUX: 'linux', MAC: 'mac', OPENBSD: 'openbsd', WIN: 'win' },
                    RequestUpdateCheckStatus: { NO_UPDATE: 'no_update', THROTTLED: 'throttled', UPDATE_AVAILABLE: 'update_available' },
                },
                loadTimes: () => ({}),
                csi: () => ({}),
                app: {
                    isInstalled: false,
                    InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' },
                    RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' },
                },
            };
            
            // 覆盖 notification
            const originalNotification = window.Notification;
            window.Notification = function(title, options) {
                return originalNotification.apply(this, arguments);
            };
            window.Notification.permission = 'default';
            window.Notification.requestPermission = () => Promise.resolve('default');
            
            // WebGL 指纹伪装
            const getParameter = WebGLRenderingContext.prototype.getParameter;
            WebGLRenderingContext.prototype.getParameter = function(parameter) {
                if (parameter === 37445) {
                    return 'Intel Inc.';
                }
                if (parameter === 37446) {
                    return 'Intel Iris OpenGL Engine';
                }
                return getParameter(parameter);
            };
            
            // Canvas 指纹噪声
            const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
            const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
            
            // 删除 Puppeteer 特有属性
            delete navigator.__proto__.webdriver;
            
            // 覆盖 console
            const originalConsoleLog = console.log;
            console.log = function(...args) {
                if (args.length > 0 && typeof args[0] === 'string' && args[0].includes('DevTools')) {
                    return;
                }
                return originalConsoleLog.apply(this, args);
            };
        });
        
        // 设置额外 HTTP 头 - 完整的浏览器指纹
        await page.setExtraHTTPHeaders({
            'Accept-Language': 'zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
            'Accept-Encoding': 'gzip, deflate, br',
            'Cache-Control': 'max-age=0',
            'Sec-Ch-Ua': '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
            'Sec-Ch-Ua-Mobile': '?0',
            'Sec-Ch-Ua-Platform': '"Windows"',
            'Sec-Fetch-Dest': 'document',
            'Sec-Fetch-Mode': 'navigate',
            'Sec-Fetch-Site': 'none',
            'Sec-Fetch-User': '?1',
            'Upgrade-Insecure-Requests': '1',
            'DNT': '1',
        });
        
        page.on("console", (msg) => {
            const logEntry = `[${msg.type()}] ${msg.text()}`;
            consoleLogs.push(logEntry);
        });
    }
    return page;
}

/**
 * 保存会话
 */
async function saveSession(page, filePath) {
    try {
        const cookies = await page.cookies();
        const localStorage = await page.evaluate(() => {
            const items = {};
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i);
                items[key] = localStorage.getItem(key);
            }
            return items;
        });
        const sessionStorage = await page.evaluate(() => {
            const items = {};
            for (let i = 0; i < sessionStorage.length; i++) {
                const key = sessionStorage.key(i);
                items[key] = sessionStorage.getItem(key);
            }
            return items;
        });
        
        const sessionData = {
            cookies,
            localStorage,
            sessionStorage,
            timestamp: new Date().toISOString(),
        };
        
        fs.writeFileSync(filePath, JSON.stringify(sessionData, null, 2));
        return true;
    } catch (error) {
        console.error(`[PuppeteerWrapper] Failed to save session: ${error.message}`);
        return false;
    }
}

/**
 * 加载会话
 */
async function loadSession(page, filePath) {
    try {
        if (!fs.existsSync(filePath)) {
            return false;
        }
        
        const sessionData = JSON.parse(fs.readFileSync(filePath, 'utf8'));
        
        if (sessionData.cookies) {
            await page.setCookie(...sessionData.cookies);
        }
        
        if (sessionData.localStorage) {
            await page.evaluate((data) => {
                for (const [key, value] of Object.entries(data)) {
                    localStorage.setItem(key, value);
                }
            }, sessionData.localStorage);
        }
        
        if (sessionData.sessionStorage) {
            await page.evaluate((data) => {
                for (const [key, value] of Object.entries(data)) {
                    sessionStorage.setItem(key, value);
                }
            }, sessionData.sessionStorage);
        }
        
        return true;
    } catch (error) {
        console.error(`[PuppeteerWrapper] Failed to load session: ${error.message}`);
        return false;
    }
}

/**
 * 移除付费墙元素
 */
async function removePaywallElements(page) {
    const paywallSelectors = [
        '.paywall',
        '.subscription-wall',
        '.meteredContent',
        '[class*="paywall"]',
        '[id*="paywall"]',
        '.article__paywall',
        '.story-paywall',
        '.content-gate',
        '.soft-wall',
        '.hard-wall',
        '.registration-wall',
        '.login-wall',
        '.subscribe-modal',
        '.subscription-modal',
        '.premium-overlay',
        '.membership-overlay',
    ];
    
    for (const selector of paywallSelectors) {
        try {
            await page.evaluate((sel) => {
                const elements = document.querySelectorAll(sel);
                elements.forEach(el => {
                    el.style.display = 'none';
                    el.style.visibility = 'hidden';
                    el.style.opacity = '0';
                    el.remove();
                });
            }, selector);
        } catch (e) {
            // 忽略错误
        }
    }
}

async function handleToolCall(name, args) {
    const page = await ensureBrowser(args);
    
    switch (name) {
        case "puppeteer_navigate":
            try {
                const waitUntil = args.waitUntil || 'networkidle2';
                const timeout = args.timeout || 30000;
                
                // 随机延迟，模拟人类行为
                await page.waitForTimeout(randomDelay(500, 1500));
                
                const navigateOptions = { 
                    waitUntil,
                    timeout,
                };
                
                if (args.referer) {
                    navigateOptions.referer = args.referer;
                }
                
                await page.goto(args.url, navigateOptions);
                
                // 额外等待，确保动态内容加载
                await page.waitForTimeout(randomDelay(1000, 2000));
                
                return {
                    content: [{ type: "text", text: `Navigated to ${args.url}` }],
                    isError: false,
                };
            } catch (error) {
                return {
                    content: [{ type: "text", text: `Navigation failed: ${error.message}` }],
                    isError: true,
                };
            }
            
        case "puppeteer_screenshot": {
            try {
                const width = args.width ?? 1920;
                const height = args.height ?? 1080;
                const fullPage = args.fullPage ?? false;
                const encoded = args.encoded ?? false;
                
                await page.setViewport({ width, height });
                
                // 随机延迟
                await page.waitForTimeout(randomDelay(300, 800));
                
                const screenshot = await (args.selector ?
                    (await page.$(args.selector))?.screenshot({ encoding: "base64" }) :
                    page.screenshot({ encoding: "base64", fullPage }));
                    
                if (!screenshot) {
                    return {
                        content: [{ type: "text", text: args.selector ? `Element not found: ${args.selector}` : "Screenshot failed" }],
                        isError: true,
                    };
                }
                
                screenshots.set(args.name, screenshot);
                
                return {
                    content: [
                        { type: "text", text: `Screenshot '${args.name}' taken at ${width}x${height}${fullPage ? ' (full page)' : ''}` },
                        encoded ? { type: "text", text: `data:image/png;base64,${screenshot}` } : { type: "image", data: screenshot, mimeType: "image/png" },
                    ],
                    isError: false,
                };
            } catch (error) {
                return {
                    content: [{ type: "text", text: `Screenshot failed: ${error.message}` }],
                    isError: true,
                };
            }
        }
        
        case "puppeteer_click":
            try {
                const delay = args.delay ?? randomDelay(50, 150);
                const moveMouse = args.moveMouse !== false;
                
                await page.waitForSelector(args.selector, { timeout: 5000 });
                
                if (moveMouse) {
                    await page.hover(args.selector);
                    await page.waitForTimeout(randomDelay(100, 300));
                }
                
                await page.click(args.selector, { delay });
                
                // 点击后随机延迟
                await page.waitForTimeout(randomDelay(500, 1000));
                
                return { content: [{ type: "text", text: `Clicked: ${args.selector}` }], isError: false };
            } catch (error) {
                return { content: [{ type: "text", text: `Failed to click ${args.selector}: ${error.message}` }], isError: true };
            }
            
        case "puppeteer_fill":
            try {
                const delay = args.delay ?? randomDelay(30, 80);
                const clearFirst = args.clearFirst !== false;
                
                await page.waitForSelector(args.selector, { timeout: 5000 });
                
                if (clearFirst) {
                    await page.click(args.selector, { clickCount: 3 });
                    await page.keyboard.press('Backspace');
                }
                
                await page.type(args.selector, args.value, { delay });
                
                return { content: [{ type: "text", text: `Filled ${args.selector}` }], isError: false };
            } catch (error) {
                return { content: [{ type: "text", text: `Failed to fill ${args.selector}: ${error.message}` }], isError: true };
            }
            
        case "puppeteer_scroll":
            try {
                const direction = args.direction || 'down';
                const amount = args.amount ?? randomDelay(400, 1000);
                const smooth = args.smooth !== false;
                const waitAfter = args.waitAfter ?? randomDelay(500, 1500);
                
                let scrollScript;
                if (direction === 'bottom') {
                    scrollScript = `window.scrollTo({ top: document.body.scrollHeight, behavior: '${smooth ? 'smooth' : 'auto'}' })`;
                } else if (direction === 'top') {
                    scrollScript = `window.scrollTo({ top: 0, behavior: '${smooth ? 'smooth' : 'auto'}' })`;
                } else if (direction === 'up') {
                    scrollScript = `window.scrollBy({ top: -${amount}, behavior: '${smooth ? 'smooth' : 'auto'}' })`;
                } else {
                    scrollScript = `window.scrollBy({ top: ${amount}, behavior: '${smooth ? 'smooth' : 'auto'}' })`;
                }
                
                await page.evaluate(scrollScript);
                await page.waitForTimeout(waitAfter);
                
                return { content: [{ type: "text", text: `Scrolled ${direction}` }], isError: false };
            } catch (error) {
                return { content: [{ type: "text", text: `Scroll failed: ${error.message}` }], isError: true };
            }
            
        case "puppeteer_infinite_scroll":
            try {
                const maxScrolls = args.maxScrolls ?? 10;
                const waitBetween = args.waitBetween ?? 2000;
                const itemSelector = args.itemSelector;
                const stopWhenSame = args.stopWhenSame !== false;
                
                let previousCount = 0;
                let sameCount = 0;
                
                for (let i = 0; i < maxScrolls; i++) {
                    // 滚动到底部
                    await page.evaluate('window.scrollTo({ top: document.body.scrollHeight, behavior: "smooth" })');
                    await page.waitForTimeout(waitBetween);
                    
                    if (itemSelector && stopWhenSame) {
                        const currentCount = await page.evaluate((sel) => 
                            document.querySelectorAll(sel).length, itemSelector
                        );
                        
                        if (currentCount === previousCount) {
                            sameCount++;
                            if (sameCount >= 2) {
                                return { 
                                    content: [{ type: "text", text: `Infinite scroll stopped after ${i + 1} scrolls. No new items loaded.` }], 
                                    isError: false 
                                };
                            }
                        } else {
                            sameCount = 0;
                        }
                        previousCount = currentCount;
                    }
                }
                
                return { 
                    content: [{ type: "text", text: `Completed ${maxScrolls} infinite scrolls` }], 
                    isError: false 
                };
            } catch (error) {
                return { content: [{ type: "text", text: `Infinite scroll failed: ${error.message}` }], isError: true };
            }
            
        case "puppeteer_evaluate":
            try {
                const fixedScript = fixScriptFormat(args.script);
                
                if (fixedScript !== args.script) {
                    console.error(`[PuppeteerWrapper] Original: ${args.script.substring(0, 100)}`);
                    console.error(`[PuppeteerWrapper] Fixed: ${fixedScript.substring(0, 100)}`);
                }
                
                const result = await page.evaluate(fixedScript);
                
                return {
                    content: [{ type: "text", text: `Execution result:\n${JSON.stringify(result, null, 2)}` }],
                    isError: false,
                };
            } catch (error) {
                return {
                    content: [{ type: "text", text: `Script execution failed: ${error.message}` }],
                    isError: true,
                };
            }
            
        case "puppeteer_get_content":
            try {
                const waitTime = args.waitFor || 3000;
                const removePaywallFlag = args.removePaywall !== false;
                const removeScripts = args.removeScripts !== false;
                
                // 等待页面加载
                await page.waitForTimeout(waitTime);
                
                // 尝试移除付费墙
                if (removePaywallFlag) {
                    await removePaywallElements(page);
                }
                
                // 如果提供了特定选择器
                if (args.selector) {
                    try {
                        await page.waitForSelector(args.selector, { timeout: 5000 });
                        const content = await page.evaluate((sel, removeScripts) => {
                            const el = document.querySelector(sel);
                            if (!el) return null;
                            
                            if (removeScripts) {
                                const clone = el.cloneNode(true);
                                const scripts = clone.querySelectorAll('script, style, nav, header, footer, aside, .ads, .advertisement, .paywall, [class*="paywall"]');
                                scripts.forEach(s => s.remove());
                                return clone.innerText;
                            }
                            return el.innerText;
                        }, args.selector, removeScripts);
                        
                        if (content && content.length > 50) {
                            return {
                                content: [{ type: "text", text: content }],
                                isError: false,
                            };
                        }
                    } catch (e) {
                        // 继续尝试其他选择器
                    }
                }
                
                // 尝试常见文章选择器 - 扩展支持更多网站
                const commonSelectors = [
                    { selector: '.Post-RichTextContainer', name: 'Zhihu Article' },
                    { selector: '.RichContent-inner', name: 'Zhihu RichContent' },
                    { selector: '.Post-Main', name: 'Zhihu Main' },
                    { selector: 'article', name: 'Generic Article' },
                    { selector: '.article-content', name: 'Article Content' },
                    { selector: '.post-content', name: 'Post Content' },
                    { selector: '.entry-content', name: 'Entry Content' },
                    { selector: '#article-content', name: 'Article ID' },
                    { selector: '.content-area', name: 'Content Area' },
                    { selector: 'main', name: 'Main Content' },
                    { selector: '[role="main"]', name: 'ARIA Main' },
                    { selector: '.main-content', name: 'Main Content Class' },
                    { selector: '.story-body', name: 'Story Body' },
                    { selector: '.article-body', name: 'Article Body' },
                    { selector: '.post-body', name: 'Post Body' },
                    { selector: '.content-body', name: 'Content Body' },
                    { selector: '#content', name: 'Content ID' },
                    { selector: '.page-content', name: 'Page Content' },
                    { selector: '.document-content', name: 'Document Content' },
                    { selector: '.body-content', name: 'Body Content' },
                    { selector: '.text-content', name: 'Text Content' },
                ];
                
                for (const { selector, name } of commonSelectors) {
                    try {
                        const hasElement = await page.$(selector);
                        if (hasElement) {
                            const content = await page.evaluate((sel, removeScripts) => {
                                const el = document.querySelector(sel);
                                if (!el) return null;
                                
                                if (removeScripts) {
                                    const clone = el.cloneNode(true);
                                    const scripts = clone.querySelectorAll('script, style, nav, header, footer, aside, .ads, .advertisement, .paywall, [class*="paywall"], [id*="paywall"]');
                                    scripts.forEach(s => s.remove());
                                    return clone.innerText;
                                }
                                return el.innerText;
                            }, selector, removeScripts);
                            
                            if (content && content.length > 100) {
                                console.error(`[PuppeteerWrapper] Content found: ${name} (${selector})`);
                                return {
                                    content: [{ type: "text", text: content }],
                                    isError: false,
                                };
                            }
                        }
                    } catch (e) {
                        // 继续尝试下一个
                    }
                }
                
                // 最后尝试 body
                const bodyText = await page.evaluate((removeScripts) => {
                    if (removeScripts) {
                        const clone = document.body.cloneNode(true);
                        const scripts = clone.querySelectorAll('script, style, nav, header, footer, aside, .ads, .advertisement');
                        scripts.forEach(s => s.remove());
                        return clone.innerText;
                    }
                    return document.body.innerText;
                }, removeScripts);
                
                return {
                    content: [{ type: "text", text: bodyText }],
                    isError: false,
                };
            } catch (error) {
                return {
                    content: [{ type: "text", text: `Failed to get content: ${error.message}` }],
                    isError: true,
                };
            }
            
        case "puppeteer_login":
            try {
                // 如果需要导航到登录页面
                if (args.loginUrl) {
                    await page.goto(args.loginUrl, { waitUntil: 'networkidle2' });
                    await page.waitForTimeout(randomDelay(1000, 2000));
                }
                
                // 填写用户名
                await page.waitForSelector(args.usernameSelector, { timeout: 5000 });
                await page.click(args.usernameSelector, { clickCount: 3 });
                await page.type(args.usernameSelector, args.username, { delay: randomDelay(30, 80) });
                await page.waitForTimeout(randomDelay(200, 500));
                
                // 填写密码
                await page.waitForSelector(args.passwordSelector, { timeout: 5000 });
                await page.click(args.passwordSelector, { clickCount: 3 });
                await page.type(args.passwordSelector, args.password, { delay: randomDelay(30, 80) });
                await page.waitForTimeout(randomDelay(200, 500));
                
                // 点击提交
                await page.waitForSelector(args.submitSelector, { timeout: 5000 });
                await page.click(args.submitSelector, { delay: randomDelay(50, 150) });
                
                // 等待登录完成
                if (args.waitForSelector) {
                    await page.waitForSelector(args.waitForSelector, { timeout: 10000 });
                } else {
                    await page.waitForTimeout(randomDelay(2000, 4000));
                }
                
                // 保存会话
                if (args.saveSession !== false) {
                    const url = new URL(page.url());
                    const sessionFile = args.sessionFile || path.join(SESSIONS_DIR, `${url.hostname}.json`);
                    await saveSession(page, sessionFile);
                    currentSessionFile = sessionFile;
                    
                    return {
                        content: [{ type: "text", text: `Login successful. Session saved to ${sessionFile}` }],
                        isError: false,
                    };
                }
                
                return {
                    content: [{ type: "text", text: "Login successful" }],
                    isError: false,
                };
            } catch (error) {
                return {
                    content: [{ type: "text", text: `Login failed: ${error.message}` }],
                    isError: true,
                };
            }
            
        case "puppeteer_load_session":
            try {
                const sessionFile = args.sessionFile;
                
                if (!sessionFile || !fs.existsSync(sessionFile)) {
                    return {
                        content: [{ type: "text", text: `Session file not found: ${sessionFile}` }],
                        isError: true,
                    };
                }
                
                const success = await loadSession(page, sessionFile);
                
                if (success && args.url) {
                    await page.goto(args.url, { waitUntil: 'networkidle2' });
                }
                
                return {
                    content: [{ type: "text", text: success ? "Session loaded successfully" : "Failed to load session" }],
                    isError: !success,
                };
            } catch (error) {
                return {
                    content: [{ type: "text", text: `Load session failed: ${error.message}` }],
                    isError: true,
                };
            }
            
        case "puppeteer_solve_captcha":
            try {
                const captchaSelectors = [
                    '.captcha',
                    '#captcha',
                    '[class*="captcha"]',
                    '[id*="captcha"]',
                    '.geetest',
                    '.rc-anchor',
                    '.g-recaptcha',
                    '.h-captcha',
                    '.fun-captcha',
                    '.cf-turnstile',
                    '.challenge-form',
                    '#challenge-form',
                    ...((args.checkSelectors) || []),
                ];
                
                for (const selector of captchaSelectors) {
                    const hasCaptcha = await page.$(selector);
                    if (hasCaptcha) {
                        let screenshotMsg = '';
                        
                        if (args.takeScreenshot !== false) {
                            const screenshot = await page.screenshot({ encoding: "base64" });
                            screenshotMsg = `\n\nCAPTCHA screenshot:\ndata:image/png;base64,${screenshot}`;
                        }
                        
                        return {
                            content: [{ 
                                type: "text", 
                                text: `CAPTCHA detected: ${selector}. Manual intervention required.${screenshotMsg}` 
                            }],
                            isError: true,
                        };
                    }
                }
                
                return {
                    content: [{ type: "text", text: "No CAPTCHA detected on current page." }],
                    isError: false,
                };
            } catch (error) {
                return {
                    content: [{ type: "text", text: `CAPTCHA check failed: ${error.message}` }],
                    isError: true,
                };
            }
            
        case "puppeteer_wait_for":
            try {
                const timeout = args.timeout ?? 10000;
                const visible = args.visible !== false;
                
                await page.waitForSelector(args.selector, { 
                    timeout, 
                    visible 
                });
                
                return {
                    content: [{ type: "text", text: `Element found: ${args.selector}` }],
                    isError: false,
                };
            } catch (error) {
                return {
                    content: [{ type: "text", text: `Wait failed: ${error.message}` }],
                    isError: true,
                };
            }
            
        default:
            return { content: [{ type: "text", text: `Unknown tool: ${name}` }], isError: true };
    }
}

const server = new Server(
    { name: "puppeteer-wrapper", version: "3.0.0" },
    { capabilities: { tools: {} } }
);

server.setRequestHandler(ListToolsRequestSchema, async () => {
    return { tools: TOOLS };
});

server.setRequestHandler(CallToolRequestSchema, async (request) => {
    return await handleToolCall(request.params.name, request.params.arguments);
});

async function main() {
    const transport = new StdioServerTransport();
    await server.connect(transport);
    console.error("Puppeteer Wrapper MCP Server v3.0.0 running on stdio (with advanced anti-detection)");
}

main().catch(console.error);