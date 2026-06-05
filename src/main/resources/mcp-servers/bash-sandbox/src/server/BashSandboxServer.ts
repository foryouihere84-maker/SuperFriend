import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
    CallToolRequestSchema,
    ListToolsRequestSchema,
    ErrorCode,
    McpError
} from '@modelcontextprotocol/sdk/types.js';
import * as path from 'path';

import { createPlatformAdapter, PlatformAdapter } from '../platform/index.js';
import { SessionManager } from '../session/index.js';
import { SecurityFilter, RateLimiter } from '../security/index.js';
import { ResourceCalculator, ResourceMonitor } from '../resource/index.js';
import { CommandExecutor } from '../executor/index.js';
import { AuditLogger } from '../audit/index.js';
import { FileManager } from '../file/index.js';
import { logger } from '../utils/index.js';
import { commandTemplates, CommandTemplates } from '../templates/index.js';
import { outputParser, OutputParser } from '../parser/index.js';
import { BatchExecutor } from '../batch/index.js';
import { commandSuggester, CommandSuggester } from '../suggest/index.js';

export const ExitCodes = {
    SUCCESS: 0,
    BLOCKED: -1,
    TIMEOUT: -2,
    SYSTEM_ERROR: -3,
    SESSION_INVALID: -4,
    RESOURCE_INSUFFICIENT: -5,
    RATE_LIMITED: -6
} as const;

interface BashSandboxConfig {
    sandboxRoot?: string;
    sessionTimeoutMs?: number;
    maxCommandLength?: number;
    rateLimit?: {
        maxRequestsPerMinute?: number;
        maxRequestsPerHour?: number;
        maxConcurrentRequests?: number;
    };
}

export class BashSandboxServer {
    private server: Server;
    private platformAdapter: PlatformAdapter;
    private sessionManager: SessionManager;
    private securityFilter: SecurityFilter;
    private resourceCalculator: ResourceCalculator;
    private resourceMonitor: ResourceMonitor;
    private commandExecutor: CommandExecutor;
    private auditLogger: AuditLogger;
    private rateLimiter: RateLimiter;
    private fileManager: FileManager;
    private templates: CommandTemplates;
    private parser: OutputParser;
    private batchExecutor: BatchExecutor;
    private suggester: CommandSuggester;

    constructor(config: BashSandboxConfig = {}) {
        
        this.server = new Server(
            {
                name: 'bash-sandbox',
                version: '1.0.0'
            },
            {
                capabilities: {
                    tools: {}
                }
            }
        );

        this.platformAdapter = createPlatformAdapter(config.sandboxRoot);
        this.sessionManager = new SessionManager(
            this.platformAdapter,
            config.sessionTimeoutMs
        );
        // 检测是否使用bash shell（Git Bash或其他bash）
        const platformConfig = this.platformAdapter.getPlatformConfig();
        const useBashShell = platformConfig.shell.includes('bash');
        this.securityFilter = new SecurityFilter(
            this.platformAdapter.isWindows(),
            config.maxCommandLength || 8192,
            useBashShell  // 当使用bash时，使用Linux命令检测逻辑
        );
        this.resourceCalculator = new ResourceCalculator();
        this.resourceMonitor = new ResourceMonitor(
            this.platformAdapter.getPlatformConfig().sandboxDir
        );
        this.commandExecutor = new CommandExecutor(this.platformAdapter);
        this.auditLogger = new AuditLogger(
            this.platformAdapter.getPlatformConfig().sandboxDir
        );
        this.rateLimiter = new RateLimiter(config.rateLimit);
        this.fileManager = new FileManager(this.platformAdapter);
        this.templates = commandTemplates;
        this.parser = outputParser;
        this.batchExecutor = new BatchExecutor(this.commandExecutor);
        this.suggester = commandSuggester;

        this.setupHandlers();
    }

    private setupHandlers(): void {
        this.server.setRequestHandler(
            ListToolsRequestSchema,
            async () => this.handleListTools()
        );

        this.server.setRequestHandler(
            CallToolRequestSchema,
            async (request) => this.handleCallTool(request)
        );
    }

    private async handleListTools() {
        return {
            tools: [
                {
                    name: 'execute',
<<<<<<< HEAD
                    description: '在沙箱中执行 bash 命令。命令在隔离环境中运行，受资源限制和安全策略约束。\n\n' +
                        '## 使用说明\n' +
                        '- 首次执行会自动创建会话，返回 sessionId 和 workingDirectory\n' +
                        '- 后续执行可传入 sessionId 复用会话，保持环境变量和工作目录状态\n' +
                        '- 生成的文件（图片、PDF、文档等）会自动检测并发送给用户\n\n' +
                        '## 自动注入的环境变量\n' +
                        '- `$SKILL_DIR`: 技能目录路径（如适用）\n' +
                        '- `$SKILL_OUTPUT_DIR`: 建议的输出目录，生成的文件应放在此目录\n' +
                        '- `$SKILL_SESSION_ID`: 会话ID\n' +
                        '- `$SKILL_PARAM_*`: 用户传入的参数\n\n' +
                        '## 最佳实践\n' +
                        '```\n' +
                        '# 1. 首次执行获取会话信息\n' +
                        'execute(command="pwd")  # 返回 sessionId 和 workingDirectory\n\n' +
                        '# 2. 后续命令复用会话\n' +
                        'execute(sessionId="sess_xxx", command="python script.py")\n\n' +
                        '# 3. 输出文件使用环境变量\n' +
                        'execute(command="python -c \\"import os; open(os.environ[\'SKILL_OUTPUT_DIR\']+\'/output.txt\', \'w\')\\"")\n' +
                        '```',
=======
                    description: '在沙箱中执行 bash 命令。命令在隔离环境中运行，受资源限制和安全策略约束。' +
                        '执行结果包含 sessionId 和 workingDirectory，可用于后续命令。' +
                        '生成的文件（图片、PDF、文档等）会自动发送给用户，无需手动导出。',
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    inputSchema: {
                        type: 'object',
                        properties: {
                            command: {
                                type: 'string',
<<<<<<< HEAD
                                description: '要执行的命令。支持管道、重定向等 shell 特性。命令长度限制：最大 8192 字符。\n' +
                                    'Windows 环境：优先使用 Git Bash（支持完整 bash 语法），如无则使用 PowerShell。'
=======
                                description: '要执行的命令。支持管道、重定向等 shell 特性。命令长度限制：最大 8192 字符。'
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                            },
                            workingDirectory: {
                                type: 'string',
                                description: '工作目录。可选，默认使用会话的当前工作目录。可指定技能目录等外部路径。'
                            },
                            timeout: {
                                type: 'integer',
                                description: '超时时间（毫秒）。可选，默认使用系统配置的超时时间。',
                                minimum: 1000,
                                maximum: 300000
                            },
                            environment: {
                                type: 'object',
                                description: '额外的环境变量。可选，会与会话的环境变量合并。',
                                additionalProperties: {
                                    type: 'string'
                                }
                            },
                            sessionId: {
                                type: 'string',
<<<<<<< HEAD
                                description: '会话 ID。可选，如果不提供则创建新会话。\n' +
                                    '建议复用会话以保持状态（环境变量、工作目录等）。'
=======
                                description: '会话 ID。可选，如果不提供则创建新会话。建议复用会话以保持状态。'
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                            }
                        },
                        required: ['command']
                    }
                },
                {
                    name: 'create_session',
<<<<<<< HEAD
                    description: '创建一个新的沙箱会话，返回会话 ID 和工作目录。\n\n' +
                        '## 使用场景\n' +
                        '- 需要预先创建会话以确定工作目录\n' +
                        '- 需要在特定目录（如技能目录）中执行多个命令\n' +
                        '- 需要设置特定的环境变量\n\n' +
                        '## 建议\n' +
                        '强烈建议指定 workingDirectory，以便在特定目录中执行命令。\n' +
=======
                    description: '创建一个新的沙箱会话，返回会话 ID 和工作目录。' +
                        '建议指定 workingDirectory 以便在特定目录（如技能目录）中执行命令。' +
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                        '会话保持状态，后续命令可以访问之前命令创建的文件。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            workingDirectory: {
                                type: 'string',
                                description: '初始工作目录。强烈建议指定，如技能目录路径。不指定则使用会话专属临时目录。'
                            },
                            environment: {
                                type: 'object',
                                description: '初始环境变量。可选。',
                                additionalProperties: {
                                    type: 'string'
                                }
                            },
                            name: {
                                type: 'string',
                                description: '会话名称。可选，用于日志和调试。'
                            }
                        },
                        required: []
                    }
                },
                {
                    name: 'close_session',
                    description: '关闭指定会话，释放资源。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            sessionId: {
                                type: 'string',
                                description: '要关闭的会话 ID'
                            },
                            cleanup: {
                                type: 'boolean',
                                description: '是否清理会话目录。默认 true。',
                                default: true
                            }
                        },
                        required: ['sessionId']
                    }
                },
                {
                    name: 'get_session_info',
<<<<<<< HEAD
                    description: '获取指定会话的详细信息，包括工作目录、环境变量等。',
=======
                    description: '获取指定会话的详细信息。',
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    inputSchema: {
                        type: 'object',
                        properties: {
                            sessionId: {
                                type: 'string',
                                description: '会话 ID'
                            }
                        },
                        required: ['sessionId']
                    }
                },
                {
                    name: 'list_sessions',
                    description: '列出当前所有活跃的会话。',
                    inputSchema: {
                        type: 'object',
                        properties: {},
                        required: []
                    }
                },
                {
                    name: 'get_command_history',
                    description: '获取指定会话的命令执行历史。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            sessionId: {
                                type: 'string',
                                description: '会话 ID'
                            },
                            limit: {
                                type: 'integer',
                                description: '返回的最大记录数。默认 50。',
                                default: 50
                            }
                        },
                        required: ['sessionId']
                    }
                },
                {
                    name: 'get_statistics',
                    description: '获取沙箱执行的统计信息。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            sessionId: {
                                type: 'string',
                                description: '会话 ID。可选，不提供则返回全局统计。'
                            }
                        },
                        required: []
                    }
                },
                {
                    name: 'get_audit_logs',
                    description: '查询审计日志。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            sessionId: {
                                type: 'string',
                                description: '会话 ID 过滤。可选。'
                            },
                            event: {
                                type: 'string',
                                description: '事件类型过滤。可选。'
                            },
                            startTime: {
                                type: 'string',
                                description: '开始时间（ISO 格式）。可选。'
                            },
                            endTime: {
                                type: 'string',
                                description: '结束时间（ISO 格式）。可选。'
                            },
                            limit: {
                                type: 'integer',
                                description: '返回的最大记录数。默认 100。',
                                default: 100
                            }
                        },
                        required: []
                    }
                },
                {
                    name: 'get_resource_usage',
                    description: '获取系统资源使用情况。',
                    inputSchema: {
                        type: 'object',
                        properties: {},
                        required: []
                    }
                },
                {
                    name: 'get_session_resource_usage',
                    description: '获取指定会话的资源使用情况。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            sessionId: {
                                type: 'string',
                                description: '会话 ID'
                            }
                        },
                        required: ['sessionId']
                    }
                },
                {
                    name: 'get_system_info',
                    description: '获取系统信息。',
                    inputSchema: {
                        type: 'object',
                        properties: {},
                        required: []
                    }
                },
                {
                    name: 'list_templates',
                    description: '列出可用的命令模板。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            category: {
                                type: 'string',
                                description: '模板类别过滤。可选。'
                            }
                        },
                        required: []
                    }
                },
                {
                    name: 'get_template',
                    description: '获取指定命令模板的详细信息。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            templateId: {
                                type: 'string',
                                description: '模板 ID'
                            }
                        },
                        required: ['templateId']
                    }
                },
                {
                    name: 'render_template',
                    description: '渲染命令模板，生成可执行的命令。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            templateId: {
                                type: 'string',
                                description: '模板 ID'
                            },
                            params: {
                                type: 'object',
                                description: '模板参数',
                                additionalProperties: {
                                    oneOf: [
                                        { type: 'string' },
                                        { type: 'number' },
                                        { type: 'boolean' }
                                    ]
                                }
                            }
                        },
                        required: ['templateId']
                    }
                },
                {
                    name: 'parse_output',
                    description: '解析命令输出的结构化数据。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            output: {
                                type: 'string',
                                description: '要解析的命令输出'
                            },
                            format: {
                                type: 'string',
                                description: '输出格式。可选。可选值：json, csv, table, list, keyvalue, xml, yaml'
                            }
                        },
                        required: ['output']
                    }
                },
                {
                    name: 'execute_batch',
                    description: '批量执行多个命令。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            sessionId: {
                                type: 'string',
                                description: '会话 ID'
                            },
                            commands: {
                                type: 'array',
                                description: '要执行的命令列表',
                                items: {
                                    type: 'object',
                                    properties: {
                                        id: { type: 'string' },
                                        command: { type: 'string' },
                                        workingDirectory: { type: 'string' },
                                        timeout: { type: 'integer' }
                                    },
                                    required: ['id', 'command']
                                }
                            },
                            parallel: {
                                type: 'boolean',
                                description: '是否并行执行。默认 false。',
                                default: false
                            },
                            stopOnFirstError: {
                                type: 'boolean',
                                description: '遇到错误是否停止。默认 false。',
                                default: false
                            }
                        },
                        required: ['sessionId', 'commands']
                    }
                },
                {
                    name: 'suggest_commands',
                    description: '根据上下文建议命令。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            intent: {
                                type: 'string',
                                description: '用户意图描述'
                            },
                            projectType: {
                                type: 'string',
                                description: '项目类型。可选值：node, java, python, unknown'
                            },
                            lastCommand: {
                                type: 'string',
                                description: '最后执行的命令'
                            },
                            lastExitCode: {
                                type: 'integer',
                                description: '最后命令的退出码'
                            }
                        },
                        required: []
                    }
                },
                {
                    name: 'list_files',
                    description: '列出沙箱中的文件。可以列出会话工作目录或共享目录中的文件。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            path: {
                                type: 'string',
                                description: '要列出的目录路径。可选，默认为会话工作目录。'
                            },
                            sessionId: {
                                type: 'string',
                                description: '会话 ID。可选，用于获取会话工作目录。'
                            },
                            recursive: {
                                type: 'boolean',
                                description: '是否递归列出子目录。默认 false。',
                                default: false
                            }
                        },
                        required: []
                    }
                },
                {
                    name: 'export_file',
                    description: '将沙箱中的文件导出到共享目录。导出的文件可以被其他工具访问。' +
                        '导出后文件路径会包含在返回结果中。默认导出到共享目录，可被 Filesystem MCP 等工具访问。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            sourcePath: {
                                type: 'string',
                                description: '源文件路径（沙箱内路径，可以是相对路径或绝对路径）'
                            },
                            targetPath: {
                                type: 'string',
                                description: '目标路径。可选，默认导出到共享目录并保持原文件名。'
                            },
                            sessionId: {
                                type: 'string',
                                description: '会话 ID。可选，用于组织导出文件到会话专属目录。'
                            }
                        },
                        required: ['sourcePath']
                    }
                },
                {
                    name: 'get_shared_directory',
                    description: '获取共享目录的路径。共享目录可以被 Filesystem MCP 等其他工具访问。',
                    inputSchema: {
                        type: 'object',
                        properties: {},
                        required: []
                    }
                },
                {
                    name: 'read_file',
                    description: '读取沙箱中的文件内容。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            path: {
                                type: 'string',
                                description: '文件路径'
                            }
                        },
                        required: ['path']
                    }
                },
                {
                    name: 'write_file',
                    description: '在沙箱中创建或写入文件。',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            path: {
                                type: 'string',
                                description: '文件路径'
                            },
                            content: {
                                type: 'string',
                                description: '文件内容'
                            },
                            sessionId: {
                                type: 'string',
                                description: '会话 ID。可选，用于在会话目录中创建文件。'
                            }
                        },
                        required: ['path', 'content']
                    }
                }
            ]
        };
    }

    private async handleCallTool(request: { params: { name: string; arguments?: Record<string, unknown> } }) {
        const { name, arguments: args = {} } = request.params;

        try {
            switch (name) {
                case 'execute':
                    return await this.handleExecute(args);
                case 'create_session':
                    return await this.handleCreateSession(args);
                case 'close_session':
                    return await this.handleCloseSession(args);
                case 'get_session_info':
                    return await this.handleGetSessionInfo(args);
                case 'list_sessions':
                    return await this.handleListSessions();
                case 'get_command_history':
                    return await this.handleGetCommandHistory(args);
                case 'get_statistics':
                    return await this.handleGetStatistics(args);
                case 'get_audit_logs':
                    return await this.handleGetAuditLogs(args);
                case 'get_resource_usage':
                    return await this.handleGetResourceUsage();
                case 'get_session_resource_usage':
                    return await this.handleGetSessionResourceUsage(args);
                case 'get_system_info':
                    return await this.handleGetSystemInfo();
                case 'list_templates':
                    return await this.handleListTemplates(args);
                case 'get_template':
                    return await this.handleGetTemplate(args);
                case 'render_template':
                    return await this.handleRenderTemplate(args);
                case 'parse_output':
                    return await this.handleParseOutput(args);
                case 'execute_batch':
                    return await this.handleExecuteBatch(args);
                case 'suggest_commands':
                    return await this.handleSuggestCommands(args);
                case 'list_files':
                    return await this.handleListFiles(args);
                case 'export_file':
                    return await this.handleExportFile(args);
                case 'get_shared_directory':
                    return await this.handleGetSharedDirectory();
                case 'read_file':
                    return await this.handleReadFile(args);
                case 'write_file':
                    return await this.handleWriteFile(args);
                default:
                    throw new McpError(ErrorCode.MethodNotFound, `Unknown tool: ${name}`);
            }
        } catch (error) {
            if (error instanceof McpError) {
                throw error;
            }
            const errorMessage = error instanceof Error ? error.message : String(error);
            logger.error(`Tool execution failed: ${name}`, { error: errorMessage });
            throw new McpError(ErrorCode.InternalError, errorMessage);
        }
    }

    private async handleExecute(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const command = args.command as string;
        const sessionId = args.sessionId as string | undefined;
        const workingDirectory = args.workingDirectory as string | undefined;
        const timeout = args.timeout as number | undefined;
        const environment = args.environment as Record<string, string> | undefined;

        if (!command) {
            throw new McpError(ErrorCode.InvalidParams, 'command is required');
        }

        const validationResult = this.securityFilter.validate(command);
        if (!validationResult.allowed) {
            logger.warn('Command blocked', { 
                command, 
                reason: validationResult.reason,
                severity: validationResult.severity
            });
            
            this.auditLogger.logCommandExecuted(
                sessionId || '',
                command,
                workingDirectory || '',
                ExitCodes.BLOCKED,
                0,
                true,
                validationResult.reason
            );
            
            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: false,
                        stdout: '',
                        stderr: '',
                        exitCode: ExitCodes.BLOCKED,
                        executionTimeMs: 0,
                        sessionId: sessionId || '',
                        workingDirectory: workingDirectory || '',
                        blocked: true,
                        blockedReason: validationResult.reason
                    }, null, 2)
                }]
            };
        }

        const filteredEnv = environment 
            ? this.securityFilter.validateEnvironment(environment)
            : undefined;

        let session;
        try {
            session = await this.sessionManager.getOrCreate(sessionId);
        } catch (error) {
            const errorMsg = error instanceof Error ? error.message : String(error);
            logger.error('Failed to get/create session', { error: errorMsg });
            
            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: false,
                        stdout: '',
                        stderr: errorMsg,
                        exitCode: ExitCodes.SYSTEM_ERROR,
                        executionTimeMs: 0,
                        sessionId: sessionId || '',
                        workingDirectory: workingDirectory || '',
                        blocked: false,
                        error: errorMsg
                    }, null, 2)
                }]
            };
        }

        const rateLimitStatus = this.rateLimiter.checkLimit(session.sessionId);
        if (!rateLimitStatus.allowed) {
            logger.warn('Rate limit exceeded', { 
                sessionId: session.sessionId,
                reason: rateLimitStatus.reason
            });

            this.auditLogger.logError(session.sessionId, `Rate limit: ${rateLimitStatus.reason}`);

            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: false,
                        stdout: '',
                        stderr: rateLimitStatus.reason,
                        exitCode: ExitCodes.RATE_LIMITED,
                        executionTimeMs: 0,
                        sessionId: session.sessionId,
                        workingDirectory: session.workingDirectory,
                        blocked: false,
                        rateLimited: true,
                        retryAfterMs: rateLimitStatus.retryAfterMs
                    }, null, 2)
                }]
            };
        }

        this.rateLimiter.recordRequest(session.sessionId);
        this.rateLimiter.startRequest(session.sessionId);

        try {
            const limits = this.resourceCalculator.calculate();
            
            if (limits.maxMemoryBytes < 64 * 1024 * 1024) {
                this.auditLogger.logError(session.sessionId, 'Insufficient system resources');
                
                return {
                    content: [{
                        type: 'text',
                        text: JSON.stringify({
                            success: false,
                            stdout: '',
                            stderr: 'Insufficient system resources to execute command',
                            exitCode: ExitCodes.RESOURCE_INSUFFICIENT,
                            executionTimeMs: 0,
                            sessionId: session.sessionId,
                            workingDirectory: session.workingDirectory,
                            blocked: false,
                            error: 'Insufficient system resources'
                        }, null, 2)
                    }]
                };
            }

            if (workingDirectory) {
                session.updateWorkingDirectory(workingDirectory);
            }

            logger.info('Executing command', {
                command,
                sessionId: session.sessionId,
                workingDirectory: session.workingDirectory
            });

            const result = await this.commandExecutor.execute({
                command,
                session,
                limits,
                timeout,
                environment: filteredEnv
            });

            this.auditLogger.logCommandExecuted(
                session.sessionId,
                command,
                result.workingDirectory,
                result.exitCode,
                result.executionTimeMs,
                result.blocked,
                result.blockedReason
            );

            this.resourceMonitor.recordCommandExecution(
                session.sessionId,
                result.executionTimeMs,
                process.memoryUsage().heapUsed
            );

            await this.sessionManager.persistSession(session.sessionId);

            // 构建更清晰的返回信息
            let resultText = '';
            if (result.success) {
                resultText = `✅ 命令执行成功\n`;
            } else if (result.blocked) {
                resultText = `🚫 命令被阻止\n`;
            } else {
                resultText = `❌ 命令执行失败\n`;
            }

            resultText += `\n**会话信息**:\n`;
            resultText += `- sessionId: \`${session.sessionId}\` (后续命令可复用此会话)\n`;
            resultText += `- workingDirectory: \`${result.workingDirectory}\`\n`;
            resultText += `- 执行时间: ${result.executionTimeMs}ms\n`;

            if (result.blocked) {
                resultText += `\n**阻止原因**: ${result.blockedReason}\n`;
            }

            if (result.stdout) {
                resultText += `\n**输出**:\n\`\`\`\n${result.stdout}\n\`\`\`\n`;
            }

            if (result.stderr) {
                resultText += `\n**错误输出**:\n\`\`\`\n${result.stderr}\n\`\`\`\n`;
            }

            if (!result.success && !result.blocked) {
                resultText += `\n**退出码**: ${result.exitCode}\n`;
            }

            resultText += `\n**提示**: 如需保存输出文件，使用 \`export_file(sourcePath="文件路径")\` 导出到共享目录。`;

            return {
                content: [{
                    type: 'text',
                    text: resultText
                }]
            };
        } finally {
            this.rateLimiter.endRequest(session.sessionId);
        }
    }

    private async handleCreateSession(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const session = await this.sessionManager.createSession({
            workingDirectory: args.workingDirectory as string | undefined,
            environment: args.environment as Record<string, string> | undefined,
            name: args.name as string | undefined
        });

        logger.info('Session created', { sessionId: session.sessionId });

        this.auditLogger.logSessionCreated(session.sessionId, session.workingDirectory);
        this.resourceMonitor.initSession(session.sessionId);

        return {
            content: [{
                type: 'text',
                text: `✅ 会话创建成功

**会话信息**:
- sessionId: \`${session.sessionId}\`
- workingDirectory: \`${session.workingDirectory}\`

**使用方式**:
\`\`\`
bash-sandbox__execute(sessionId="${session.sessionId}", command="your command")
\`\`\`

**提示**: 复用 sessionId 可以保持命令间的状态（如环境变量、工作目录变更等）。`
            }]
        };
    }

    private async handleCloseSession(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const sessionId = args.sessionId as string;
        const cleanup = args.cleanup !== false;

        if (!sessionId) {
            throw new McpError(ErrorCode.InvalidParams, 'sessionId is required');
        }

        const session = this.sessionManager.getSession(sessionId);
        if (!session) {
            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: false,
                        exitCode: ExitCodes.SESSION_INVALID,
                        message: 'Session not found or expired'
                    }, null, 2)
                }]
            };
        }

        const success = await this.sessionManager.closeSession(sessionId, cleanup);

        logger.info('Session closed', { sessionId, success });
        
        this.auditLogger.logSessionClosed(sessionId);
        this.rateLimiter.reset(sessionId);
        this.resourceMonitor.clearSessionUsage(sessionId);

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success,
                    message: success ? 'Session closed successfully' : 'Session not found'
                }, null, 2)
            }]
        };
    }

    private async handleGetSessionInfo(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const sessionId = args.sessionId as string;

        if (!sessionId) {
            throw new McpError(ErrorCode.InvalidParams, 'sessionId is required');
        }

        const session = this.sessionManager.getSession(sessionId);

        if (!session) {
            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: false,
                        exitCode: ExitCodes.SESSION_INVALID,
                        message: 'Session not found or expired'
                    }, null, 2)
                }]
            };
        }

        const stats = this.auditLogger.getSessionStats(sessionId);

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    session: {
                        ...session.toJSON(),
                        stats: stats ? {
                            commandCount: stats.commandCount,
                            totalExecutionTimeMs: stats.totalExecutionTimeMs,
                            successCount: stats.successCount,
                            failureCount: stats.failureCount,
                            blockedCount: stats.blockedCount
                        } : null
                    }
                }, null, 2)
            }]
        };
    }

    private async handleListSessions(): Promise<{ content: Array<{ type: string; text: string }> }> {
        const sessions = this.sessionManager.listSessions();

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    sessions: sessions.map(s => ({
                        sessionId: s.sessionId,
                        name: s.name,
                        workingDirectory: s.workingDirectory,
                        createdAt: s.createdAt.toISOString(),
                        lastActivityAt: s.lastActivityAt.toISOString()
                    })),
                    totalCount: sessions.length
                }, null, 2)
            }]
        };
    }

    private async handleGetCommandHistory(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const sessionId = args.sessionId as string;
        const limit = (args.limit as number) || 50;

        if (!sessionId) {
            throw new McpError(ErrorCode.InvalidParams, 'sessionId is required');
        }

        const history = await this.auditLogger.getCommandHistory(sessionId, limit);

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    sessionId,
                    history: history.map(h => ({
                        timestamp: h.timestamp,
                        command: h.command,
                        exitCode: h.exitCode,
                        executionTimeMs: h.executionTimeMs,
                        blocked: h.blocked,
                        blockedReason: h.blockedReason
                    }))
                }, null, 2)
            }]
        };
    }

    private async handleGetStatistics(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const sessionId = args.sessionId as string | undefined;

        const stats = await this.auditLogger.getStatistics(sessionId);

        const bySessionObj: Record<string, unknown> = {};
        if (!sessionId && stats.bySession) {
            for (const [id, s] of stats.bySession) {
                bySessionObj[id] = {
                    commandCount: s.commandCount,
                    totalExecutionTimeMs: s.totalExecutionTimeMs,
                    successCount: s.successCount,
                    failureCount: s.failureCount,
                    blockedCount: s.blockedCount
                };
            }
        }

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    statistics: {
                        totalExecutions: stats.totalExecutions,
                        successfulExecutions: stats.successfulExecutions,
                        failedExecutions: stats.failedExecutions,
                        blockedExecutions: stats.blockedExecutions,
                        totalExecutionTimeMs: stats.totalExecutionTimeMs,
                        avgExecutionTimeMs: stats.avgExecutionTimeMs,
                        bySession: sessionId ? undefined : bySessionObj
                    }
                }, null, 2)
            }]
        };
    }

    private async handleGetAuditLogs(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const limit = (args.limit as number) || 100;

        const logs = await this.auditLogger.query({
            sessionId: args.sessionId as string | undefined,
            event: args.event as string | undefined,
            startTime: args.startTime as string | undefined,
            endTime: args.endTime as string | undefined,
            limit
        });

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    logs,
                    count: logs.length
                }, null, 2)
            }]
        };
    }

    private async handleGetResourceUsage(): Promise<{ content: Array<{ type: string; text: string }> }> {
        const usage = await this.resourceMonitor.getCurrentUsage();

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    resourceUsage: usage
                }, null, 2)
            }]
        };
    }

    private async handleGetSessionResourceUsage(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const sessionId = args.sessionId as string;

        if (!sessionId) {
            throw new McpError(ErrorCode.InvalidParams, 'sessionId is required');
        }

        const sessionUsage = this.resourceMonitor.getSessionUsage(sessionId);
        const diskUsage = await this.resourceMonitor.getSessionDiskUsage(sessionId);

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    sessionId,
                    resourceUsage: sessionUsage || null,
                    diskUsage
                }, null, 2)
            }]
        };
    }

    private async handleGetSystemInfo(): Promise<{ content: Array<{ type: string; text: string }> }> {
        const systemInfo = this.resourceMonitor.getSystemInfo();

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    systemInfo
                }, null, 2)
            }]
        };
    }

    private async handleListTemplates(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const category = args.category as string | undefined;
        const platform = this.platformAdapter.isWindows() ? 'windows' : 'linux';

        const templates = this.templates.listTemplates(category, platform);
        const categories = this.templates.getCategories();

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    templates: templates.map(t => ({
                        id: t.id,
                        name: t.name,
                        description: t.description,
                        category: t.category,
                        command: t.command
                    })),
                    categories,
                    totalCount: templates.length
                }, null, 2)
            }]
        };
    }

    private async handleGetTemplate(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const templateId = args.templateId as string;

        if (!templateId) {
            throw new McpError(ErrorCode.InvalidParams, 'templateId is required');
        }

        const template = this.templates.getTemplate(templateId);

        if (!template) {
            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: false,
                        message: `Template not found: ${templateId}`
                    }, null, 2)
                }]
            };
        }

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    template: {
                        id: template.id,
                        name: template.name,
                        description: template.description,
                        category: template.category,
                        command: template.command,
                        parameters: template.parameters,
                        examples: template.examples,
                        platform: template.platform
                    }
                }, null, 2)
            }]
        };
    }

    private async handleRenderTemplate(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const templateId = args.templateId as string;
        const params = (args.params as Record<string, string | number | boolean>) || {};

        if (!templateId) {
            throw new McpError(ErrorCode.InvalidParams, 'templateId is required');
        }

        try {
            const platform = this.platformAdapter.isWindows() ? 'windows' : 'linux';
            const rendered = this.templates.renderTemplate(templateId, params, platform);

            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: true,
                        templateId,
                        command: rendered.command,
                        description: rendered.description,
                        warnings: rendered.warnings
                    }, null, 2)
                }]
            };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: false,
                        templateId,
                        error: errorMessage
                    }, null, 2)
                }]
            };
        }
    }

    private async handleParseOutput(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const output = args.output as string;
        const format = args.format as string | undefined;

        if (!output) {
            throw new McpError(ErrorCode.InvalidParams, 'output is required');
        }

        const result = this.parser.parse(output, format as any);

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    parseResult: {
                        format: result.format,
                        structured: result.structured,
                        lineCount: result.lineCount,
                        parsed: result.parsed,
                        error: result.error
                    }
                }, null, 2)
            }]
        };
    }

    private async handleExecuteBatch(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const sessionId = args.sessionId as string;
        const commands = args.commands as Array<{
            id: string;
            command: string;
            workingDirectory?: string;
            timeout?: number;
        }>;
        const parallel = (args.parallel as boolean) || false;
        const stopOnFirstError = (args.stopOnFirstError as boolean) || false;

        if (!sessionId) {
            throw new McpError(ErrorCode.InvalidParams, 'sessionId is required');
        }

        if (!commands || !Array.isArray(commands) || commands.length === 0) {
            throw new McpError(ErrorCode.InvalidParams, 'commands array is required and must not be empty');
        }

        const session = this.sessionManager.getSession(sessionId);
        if (!session) {
            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: false,
                        exitCode: ExitCodes.SESSION_INVALID,
                        message: 'Session not found or expired'
                    }, null, 2)
                }]
            };
        }

        const limits = this.resourceCalculator.calculate();

        const report = await this.batchExecutor.executeBatch({
            commands,
            sessionId,
            session,
            limits,
            parallel,
            stopOnFirstError
        });

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    report
                }, null, 2)
            }]
        };
    }

    private async handleSuggestCommands(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const intent = args.intent as string | undefined;
        const projectType = args.projectType as string | undefined;
        const lastCommand = args.lastCommand as string | undefined;
        const lastExitCode = args.lastExitCode as number | undefined;

        const platform = this.platformAdapter.isWindows() ? 'windows' : 'linux';
        let suggestions: any[] = [];

        if (lastCommand !== undefined && lastExitCode !== undefined) {
            suggestions = this.suggester.suggestNextCommand(lastCommand, lastExitCode, platform);
        } else if (intent) {
            suggestions = this.suggester.suggestByIntent(intent, platform);
        } else if (projectType) {
            suggestions = this.suggester.suggestByProjectType(projectType, platform);
        } else {
            suggestions = this.suggester.suggest({
                platform,
                projectType: projectType as any,
                intent
            });
        }

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    suggestions,
                    count: suggestions.length
                }, null, 2)
            }]
        };
    }

    private async handleListFiles(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        let directory = args.path as string | undefined;
        const sessionId = args.sessionId as string | undefined;
        const recursive = (args.recursive as boolean) || false;

        if (!directory && sessionId) {
            const session = this.sessionManager.getSession(sessionId);
            if (session) {
                directory = session.workingDirectory;
            }
        }

        if (!directory) {
            directory = this.fileManager.getSharedDirectory();
        }

        try {
            const files = await this.fileManager.listFiles(directory, recursive);
            
            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: true,
                        directory,
                        files: files.map(f => ({
                            name: f.name,
                            path: f.path,
                            size: f.size,
                            isDirectory: f.isDirectory,
                            modifiedAt: f.modifiedAt.toISOString()
                        })),
                        totalCount: files.length,
                        fileCount: files.filter(f => !f.isDirectory).length,
                        directoryCount: files.filter(f => f.isDirectory).length
                    }, null, 2)
                }]
            };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: false,
                        directory,
                        error: errorMessage
                    }, null, 2)
                }]
            };
        }
    }

    private async handleExportFile(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const sourcePath = args.sourcePath as string;
        const targetPath = args.targetPath as string | undefined;
        const sessionId = args.sessionId as string | undefined;

        if (!sourcePath) {
            throw new McpError(ErrorCode.InvalidParams, 'sourcePath is required');
        }

        const result = await this.fileManager.exportFile(sourcePath, targetPath, sessionId);

        if (result.success) {
            return {
                content: [{
                    type: 'text',
                    text: `✅ 文件导出成功

**源文件**: \`${result.sourcePath}\`
**目标位置**: \`${result.targetPath}\`
**文件大小**: ${result.bytesCopied} bytes

**提示**: 文件已导出到共享目录，可被其他工具（如 filesystem MCP）访问。`
                }]
            };
        } else {
            return {
                content: [{
                    type: 'text',
                    text: `❌ 文件导出失败

**源文件**: \`${result.sourcePath}\`
**错误**: ${result.error}

**可能原因**:
- 源文件不存在
- 没有读取权限
- 目标目录没有写入权限`
                }]
            };
        }
    }

    private async handleGetSharedDirectory(): Promise<{ content: Array<{ type: string; text: string }> }> {
        const sharedDir = this.fileManager.getSharedDirectory();
        const exportDir = this.fileManager.getExportDirectory();

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: true,
                    sharedDirectory: sharedDir,
                    exportDirectory: exportDir,
                    message: '共享目录中的文件可以被 Filesystem MCP 等其他工具访问。使用 export_file 工具将沙箱中的文件导出到共享目录。'
                }, null, 2)
            }]
        };
    }

    private async handleReadFile(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        const filePath = args.path as string;

        if (!filePath) {
            throw new McpError(ErrorCode.InvalidParams, 'path is required');
        }

        try {
            const content = await this.fileManager.readTextFile(filePath);
            const info = await this.fileManager.getFileInfo(filePath);

            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: true,
                        path: filePath,
                        absolutePath: info?.absolutePath,
                        content,
                        size: info?.size,
                        modifiedAt: info?.modifiedAt?.toISOString()
                    }, null, 2)
                }]
            };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            return {
                content: [{
                    type: 'text',
                    text: JSON.stringify({
                        success: false,
                        path: filePath,
                        error: errorMessage
                    }, null, 2)
                }]
            };
        }
    }

    private async handleWriteFile(args: Record<string, unknown>): Promise<{ content: Array<{ type: string; text: string }> }> {
        let filePath = args.path as string;
        const content = args.content as string;
        const sessionId = args.sessionId as string | undefined;

        if (!filePath) {
            throw new McpError(ErrorCode.InvalidParams, 'path is required');
        }

        if (content === undefined || content === null) {
            throw new McpError(ErrorCode.InvalidParams, 'content is required');
        }

        if (!path.isAbsolute(filePath) && sessionId) {
            const session = this.sessionManager.getSession(sessionId);
            if (session) {
                filePath = path.join(session.workingDirectory, filePath);
            }
        }

        const result = await this.fileManager.writeTextFile(filePath, content);

        return {
            content: [{
                type: 'text',
                text: JSON.stringify({
                    success: result.success,
                    path: result.path,
                    bytes: result.bytes,
                    error: result.error
                }, null, 2)
            }]
        };
    }

    async start(): Promise<void> {
        await this.sessionManager.initialize();
        await this.auditLogger.initialize();
        await this.fileManager.initialize();
        
        const transport = new StdioServerTransport();
        await this.server.connect(transport);
        
        logger.info('Bash Sandbox MCP Server started', {
            platform: this.platformAdapter.isWindows() ? 'Windows' : 'Linux',
            sandboxRoot: this.platformAdapter.getPlatformConfig().sandboxDir,
            sharedDirectory: this.fileManager.getSharedDirectory(),
            rateLimit: this.rateLimiter.getConfig()
        });
    }

    async stop(): Promise<void> {
        this.sessionManager.stopCleanupTimer();
        logger.info('Bash Sandbox MCP Server stopped');
    }
}
