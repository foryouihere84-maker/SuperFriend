export interface CommandTemplate {
    id: string;
    name: string;
    description: string;
    category: string;
    command: string;
    parameters: TemplateParameter[];
    examples: TemplateExample[];
    platform?: 'linux' | 'windows' | 'all';
}

export interface TemplateParameter {
    name: string;
    type: 'string' | 'number' | 'boolean' | 'path' | 'enum';
    description: string;
    required: boolean;
    default?: string | number | boolean;
    enumValues?: string[];
    validation?: {
        pattern?: string;
        minLength?: number;
        maxLength?: number;
        min?: number;
        max?: number;
    };
}

export interface TemplateExample {
    description: string;
    params: Record<string, string | number | boolean>;
}

export interface RenderedTemplate {
    command: string;
    description: string;
    warnings?: string[];
}

export class CommandTemplates {
    private templates: Map<string, CommandTemplate> = new Map();
    private categories: Map<string, string[]> = new Map();

    constructor() {
        this.initializeDefaultTemplates();
    }

    private initializeDefaultTemplates(): void {
        this.addTemplate({
            id: 'list_files',
            name: '列出文件',
            description: '列出指定目录下的文件和子目录',
            category: '文件操作',
            command: 'ls -la {{path}}',
            parameters: [
                {
                    name: 'path',
                    type: 'path',
                    description: '要列出的目录路径',
                    required: false,
                    default: '.'
                }
            ],
            examples: [
                { description: '列出当前目录', params: { path: '.' } },
                { description: '列出临时目录', params: { path: '/tmp' } }
            ],
            platform: 'linux'
        });

        this.addTemplate({
            id: 'list_files_windows',
            name: '列出文件 (Windows)',
            description: '列出指定目录下的文件和子目录',
            category: '文件操作',
            command: 'dir {{path}}',
            parameters: [
                {
                    name: 'path',
                    type: 'path',
                    description: '要列出的目录路径',
                    required: false,
                    default: '.'
                }
            ],
            examples: [
                { description: '列出当前目录', params: { path: '.' } }
            ],
            platform: 'windows'
        });

        this.addTemplate({
            id: 'find_files',
            name: '查找文件',
            description: '在指定目录下查找匹配名称的文件',
            category: '文件操作',
            command: 'find {{searchPath}} -name "{{pattern}}" -type f',
            parameters: [
                {
                    name: 'searchPath',
                    type: 'path',
                    description: '搜索起始目录',
                    required: false,
                    default: '.'
                },
                {
                    name: 'pattern',
                    type: 'string',
                    description: '文件名匹配模式（支持通配符）',
                    required: true,
                    validation: {
                        minLength: 1,
                        maxLength: 255
                    }
                }
            ],
            examples: [
                { description: '查找所有 .txt 文件', params: { searchPath: '.', pattern: '*.txt' } },
                { description: '查找所有 Java 文件', params: { searchPath: './src', pattern: '*.java' } }
            ],
            platform: 'linux'
        });

        this.addTemplate({
            id: 'grep_content',
            name: '搜索文件内容',
            description: '在文件中搜索匹配的文本内容',
            category: '文件操作',
            command: 'grep -r "{{pattern}}" {{path}}',
            parameters: [
                {
                    name: 'pattern',
                    type: 'string',
                    description: '搜索模式',
                    required: true,
                    validation: {
                        minLength: 1,
                        maxLength: 500
                    }
                },
                {
                    name: 'path',
                    type: 'path',
                    description: '搜索路径',
                    required: false,
                    default: '.'
                }
            ],
            examples: [
                { description: '搜索包含 TODO 的文件', params: { pattern: 'TODO', path: '.' } },
                { description: '搜索特定函数调用', params: { pattern: 'functionName', path: './src' } }
            ],
            platform: 'linux'
        });

        this.addTemplate({
            id: 'read_file',
            name: '读取文件',
            description: '读取文件内容',
            category: '文件操作',
            command: 'cat "{{filePath}}"',
            parameters: [
                {
                    name: 'filePath',
                    type: 'path',
                    description: '文件路径',
                    required: true,
                    validation: {
                        minLength: 1
                    }
                }
            ],
            examples: [
                { description: '读取配置文件', params: { filePath: './config.json' } }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'read_file_head',
            name: '读取文件开头',
            description: '读取文件的前 N 行',
            category: '文件操作',
            command: 'head -n {{lines}} "{{filePath}}"',
            parameters: [
                {
                    name: 'filePath',
                    type: 'path',
                    description: '文件路径',
                    required: true
                },
                {
                    name: 'lines',
                    type: 'number',
                    description: '读取的行数',
                    required: false,
                    default: 10,
                    validation: {
                        min: 1,
                        max: 1000
                    }
                }
            ],
            examples: [
                { description: '读取前 20 行', params: { filePath: './README.md', lines: 20 } }
            ],
            platform: 'linux'
        });

        this.addTemplate({
            id: 'read_file_tail',
            name: '读取文件末尾',
            description: '读取文件的最后 N 行',
            category: '文件操作',
            command: 'tail -n {{lines}} "{{filePath}}"',
            parameters: [
                {
                    name: 'filePath',
                    type: 'path',
                    description: '文件路径',
                    required: true
                },
                {
                    name: 'lines',
                    type: 'number',
                    description: '读取的行数',
                    required: false,
                    default: 10,
                    validation: {
                        min: 1,
                        max: 1000
                    }
                }
            ],
            examples: [
                { description: '读取最后 50 行日志', params: { filePath: './logs/app.log', lines: 50 } }
            ],
            platform: 'linux'
        });

        this.addTemplate({
            id: 'count_lines',
            name: '统计文件行数',
            description: '统计文件的行数、字数、字节数',
            category: '文件操作',
            command: 'wc -lwc "{{filePath}}"',
            parameters: [
                {
                    name: 'filePath',
                    type: 'path',
                    description: '文件路径',
                    required: true
                }
            ],
            examples: [
                { description: '统计代码文件', params: { filePath: './src/main.js' } }
            ],
            platform: 'linux'
        });

        this.addTemplate({
            id: 'disk_usage',
            name: '磁盘使用情况',
            description: '查看目录或文件的磁盘使用情况',
            category: '系统信息',
            command: 'du -sh {{path}}',
            parameters: [
                {
                    name: 'path',
                    type: 'path',
                    description: '目录或文件路径',
                    required: false,
                    default: '.'
                }
            ],
            examples: [
                { description: '查看当前目录大小', params: { path: '.' } }
            ],
            platform: 'linux'
        });

        this.addTemplate({
            id: 'git_status',
            name: 'Git 状态',
            description: '查看 Git 仓库状态',
            category: 'Git 操作',
            command: 'git status',
            parameters: [],
            examples: [
                { description: '查看仓库状态', params: {} }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'git_log',
            name: 'Git 日志',
            description: '查看 Git 提交日志',
            category: 'Git 操作',
            command: 'git log --oneline -n {{count}}',
            parameters: [
                {
                    name: 'count',
                    type: 'number',
                    description: '显示的提交数量',
                    required: false,
                    default: 10,
                    validation: {
                        min: 1,
                        max: 100
                    }
                }
            ],
            examples: [
                { description: '查看最近 5 条提交', params: { count: 5 } }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'git_diff',
            name: 'Git 差异',
            description: '查看 Git 工作区差异',
            category: 'Git 操作',
            command: 'git diff {{file}}',
            parameters: [
                {
                    name: 'file',
                    type: 'path',
                    description: '文件路径（可选，不指定则显示所有差异）',
                    required: false,
                    default: ''
                }
            ],
            examples: [
                { description: '查看所有差异', params: { file: '' } },
                { description: '查看特定文件差异', params: { file: 'src/main.js' } }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'git_branch',
            name: 'Git 分支',
            description: '查看 Git 分支列表',
            category: 'Git 操作',
            command: 'git branch -a',
            parameters: [],
            examples: [
                { description: '列出所有分支', params: {} }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'npm_install',
            name: 'NPM 安装',
            description: '安装 npm 依赖',
            category: '包管理',
            command: 'npm install',
            parameters: [],
            examples: [
                { description: '安装项目依赖', params: {} }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'npm_run',
            name: 'NPM 运行脚本',
            description: '运行 npm 脚本',
            category: '包管理',
            command: 'npm run {{script}}',
            parameters: [
                {
                    name: 'script',
                    type: 'string',
                    description: '脚本名称',
                    required: true,
                    validation: {
                        minLength: 1,
                        maxLength: 100
                    }
                }
            ],
            examples: [
                { description: '运行构建脚本', params: { script: 'build' } },
                { description: '运行测试脚本', params: { script: 'test' } }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'npm_list',
            name: 'NPM 包列表',
            description: '列出已安装的 npm 包',
            category: '包管理',
            command: 'npm list --depth={{depth}}',
            parameters: [
                {
                    name: 'depth',
                    type: 'number',
                    description: '依赖深度',
                    required: false,
                    default: 0,
                    validation: {
                        min: 0,
                        max: 10
                    }
                }
            ],
            examples: [
                { description: '列出顶层依赖', params: { depth: 0 } }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'mvn_compile',
            name: 'Maven 编译',
            description: '使用 Maven 编译项目',
            category: '包管理',
            command: 'mvn compile',
            parameters: [],
            examples: [
                { description: '编译项目', params: {} }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'mvn_test',
            name: 'Maven 测试',
            description: '使用 Maven 运行测试',
            category: '包管理',
            command: 'mvn test',
            parameters: [],
            examples: [
                { description: '运行测试', params: {} }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'mvn_package',
            name: 'Maven 打包',
            description: '使用 Maven 打包项目',
            category: '包管理',
            command: 'mvn package -DskipTests={{skipTests}}',
            parameters: [
                {
                    name: 'skipTests',
                    type: 'boolean',
                    description: '是否跳过测试',
                    required: false,
                    default: false
                }
            ],
            examples: [
                { description: '打包并运行测试', params: { skipTests: false } },
                { description: '打包跳过测试', params: { skipTests: true } }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'pip_install',
            name: 'Pip 安装',
            description: '使用 pip 安装 Python 包',
            category: '包管理',
            command: 'pip install {{package}}',
            parameters: [
                {
                    name: 'package',
                    type: 'string',
                    description: '包名称',
                    required: true,
                    validation: {
                        minLength: 1,
                        maxLength: 200
                    }
                }
            ],
            examples: [
                { description: '安装 requests 库', params: { package: 'requests' } }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'pip_list',
            name: 'Pip 包列表',
            description: '列出已安装的 Python 包',
            category: '包管理',
            command: 'pip list',
            parameters: [],
            examples: [
                { description: '列出所有包', params: {} }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'env_vars',
            name: '环境变量',
            description: '显示当前环境变量',
            category: '系统信息',
            command: 'env | grep -i "{{filter}}"',
            parameters: [
                {
                    name: 'filter',
                    type: 'string',
                    description: '过滤关键词（可选）',
                    required: false,
                    default: ''
                }
            ],
            examples: [
                { description: '显示所有环境变量', params: { filter: '' } },
                { description: '过滤 PATH 相关', params: { filter: 'PATH' } }
            ],
            platform: 'linux'
        });

        this.addTemplate({
            id: 'current_dir',
            name: '当前目录',
            description: '显示当前工作目录',
            category: '系统信息',
            command: 'pwd',
            parameters: [],
            examples: [
                { description: '显示当前目录', params: {} }
            ],
            platform: 'all'
        });

        this.addTemplate({
            id: 'date_time',
            name: '日期时间',
            description: '显示当前日期和时间',
            category: '系统信息',
            command: 'date',
            parameters: [],
            examples: [
                { description: '显示当前时间', params: {} }
            ],
            platform: 'linux'
        });

        this.addTemplate({
            id: 'process_list',
            name: '进程列表',
            description: '显示进程列表',
            category: '系统信息',
            command: 'ps aux | head -n {{count}}',
            parameters: [
                {
                    name: 'count',
                    type: 'number',
                    description: '显示的进程数量',
                    required: false,
                    default: 20,
                    validation: {
                        min: 1,
                        max: 100
                    }
                }
            ],
            examples: [
                { description: '显示前 20 个进程', params: { count: 20 } }
            ],
            platform: 'linux'
        });
    }

    addTemplate(template: CommandTemplate): void {
        this.templates.set(template.id, template);
        
        const categoryTemplates = this.categories.get(template.category) || [];
        if (!categoryTemplates.includes(template.id)) {
            categoryTemplates.push(template.id);
            this.categories.set(template.category, categoryTemplates);
        }
    }

    getTemplate(id: string): CommandTemplate | undefined {
        return this.templates.get(id);
    }

    listTemplates(category?: string, platform?: 'linux' | 'windows'): CommandTemplate[] {
        let result = Array.from(this.templates.values());

        if (category) {
            result = result.filter(t => t.category === category);
        }

        if (platform) {
            result = result.filter(t => 
                t.platform === 'all' || t.platform === platform
            );
        }

        return result;
    }

    getCategories(): string[] {
        return Array.from(this.categories.keys());
    }

    renderTemplate(
        templateId: string, 
        params: Record<string, string | number | boolean>,
        platform: 'linux' | 'windows' = 'linux'
    ): RenderedTemplate {
        const template = this.templates.get(templateId);
        
        if (!template) {
            throw new Error(`Template not found: ${templateId}`);
        }

        if (template.platform !== 'all' && template.platform !== platform) {
            throw new Error(`Template ${templateId} is not available on ${platform}`);
        }

        const warnings: string[] = [];
        const processedParams: Record<string, string | number | boolean> = {};

        for (const param of template.parameters) {
            let value: string | number | boolean | undefined = params[param.name];

            if (value === undefined || value === null) {
                if (param.required) {
                    throw new Error(`Required parameter missing: ${param.name}`);
                }
                value = param.default as string | number | boolean | undefined;
            }

            if (value !== undefined && param.validation) {
                if (param.type === 'string' || param.type === 'path') {
                    const strValue = String(value);
                    if (param.validation.minLength && strValue.length < param.validation.minLength) {
                        throw new Error(`Parameter ${param.name} is too short (min: ${param.validation.minLength})`);
                    }
                    if (param.validation.maxLength && strValue.length > param.validation.maxLength) {
                        warnings.push(`Parameter ${param.name} was truncated to ${param.validation.maxLength} characters`);
                        value = strValue.substring(0, param.validation.maxLength);
                    }
                    if (param.validation.pattern) {
                        const regex = new RegExp(param.validation.pattern);
                        if (!regex.test(strValue)) {
                            throw new Error(`Parameter ${param.name} does not match required pattern`);
                        }
                    }
                }

                if (param.type === 'number') {
                    const numValue = Number(value);
                    if (param.validation.min !== undefined && numValue < param.validation.min) {
                        warnings.push(`Parameter ${param.name} was adjusted to minimum: ${param.validation.min}`);
                        value = param.validation.min;
                    }
                    if (param.validation.max !== undefined && numValue > param.validation.max) {
                        warnings.push(`Parameter ${param.name} was adjusted to maximum: ${param.validation.max}`);
                        value = param.validation.max;
                    }
                }

                if (param.type === 'enum' && param.enumValues) {
                    if (!param.enumValues.includes(String(value))) {
                        throw new Error(`Parameter ${param.name} must be one of: ${param.enumValues.join(', ')}`);
                    }
                }
            }

            processedParams[param.name] = value as string | number | boolean;
        }

        let command = template.command;
        for (const [key, value] of Object.entries(processedParams)) {
            const placeholder = `{{${key}}}`;
            command = command.replace(new RegExp(placeholder, 'g'), String(value));
        }

        return {
            command: command.trim(),
            description: template.description,
            warnings: warnings.length > 0 ? warnings : undefined
        };
    }

    searchTemplates(query: string): CommandTemplate[] {
        const lowerQuery = query.toLowerCase();
        
        return Array.from(this.templates.values()).filter(t => 
            t.name.toLowerCase().includes(lowerQuery) ||
            t.description.toLowerCase().includes(lowerQuery) ||
            t.command.toLowerCase().includes(lowerQuery) ||
            t.id.toLowerCase().includes(lowerQuery)
        );
    }
}

export const commandTemplates = new CommandTemplates();
