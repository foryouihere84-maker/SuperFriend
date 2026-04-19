import { CommandTemplates, CommandTemplate, commandTemplates } from '../templates/index.js';

export interface CommandSuggestion {
    templateId: string;
    name: string;
    description: string;
    command: string;
    category: string;
    relevance: number;
    params?: Record<string, string | number | boolean>;
}

export interface SuggestionContext {
    currentDirectory?: string;
    recentCommands?: string[];
    projectType?: 'node' | 'java' | 'python' | 'unknown';
    platform?: 'linux' | 'windows';
    intent?: string;
}

export class CommandSuggester {
    private templates: CommandTemplates;
    private projectIndicators: Map<string, string[]> = new Map([
        ['node', ['package.json', 'node_modules', 'yarn.lock', 'pnpm-lock.yaml']],
        ['java', ['pom.xml', 'build.gradle', 'gradlew', 'mvnw']],
        ['python', ['requirements.txt', 'setup.py', 'pyproject.toml', 'venv', '.venv']]
    ]);

    private intentKeywords: Map<string, string[]> = new Map([
        ['list_files', ['list', 'show', 'display', 'files', 'directory', 'ls', 'dir']],
        ['find_files', ['find', 'search', 'locate', 'where', 'file named']],
        ['grep_content', ['search', 'find', 'grep', 'content', 'text', 'in files']],
        ['read_file', ['read', 'view', 'cat', 'show', 'display', 'content', 'file']],
        ['git_status', ['git', 'status', 'changes', 'modified', 'staged']],
        ['git_log', ['git', 'history', 'commits', 'log', 'recent']],
        ['git_diff', ['git', 'diff', 'difference', 'changes', 'compare']],
        ['npm_install', ['npm', 'install', 'dependencies', 'packages', 'node']],
        ['npm_run', ['npm', 'run', 'script', 'build', 'test', 'start']],
        ['mvn_compile', ['maven', 'compile', 'build', 'java']],
        ['mvn_test', ['maven', 'test', 'junit', 'java']],
        ['disk_usage', ['disk', 'usage', 'size', 'space', 'du']]
    ]);

    constructor(templates: CommandTemplates = commandTemplates) {
        this.templates = templates;
    }

    suggest(context: SuggestionContext): CommandSuggestion[] {
        const suggestions: CommandSuggestion[] = [];

        const relevantTemplates = this.getRelevantTemplates(context);
        
        for (const template of relevantTemplates) {
            const relevance = this.calculateRelevance(template, context);
            
            if (relevance > 0) {
                suggestions.push({
                    templateId: template.id,
                    name: template.name,
                    description: template.description,
                    command: template.command,
                    category: template.category,
                    relevance
                });
            }
        }

        suggestions.sort((a, b) => b.relevance - a.relevance);

        return suggestions.slice(0, 10);
    }

    suggestByIntent(intent: string, platform: 'linux' | 'windows' = 'linux'): CommandSuggestion[] {
        const suggestions: CommandSuggestion[] = [];
        const intentLower = intent.toLowerCase();

        for (const [templateId, keywords] of this.intentKeywords) {
            const matchCount = keywords.filter(kw => intentLower.includes(kw)).length;
            
            if (matchCount > 0) {
                const template = this.templates.getTemplate(templateId);
                if (template && (template.platform === 'all' || template.platform === platform)) {
                    suggestions.push({
                        templateId: template.id,
                        name: template.name,
                        description: template.description,
                        command: template.command,
                        category: template.category,
                        relevance: matchCount / keywords.length
                    });
                }
            }
        }

        suggestions.sort((a, b) => b.relevance - a.relevance);

        return suggestions.slice(0, 5);
    }

    suggestByProjectType(projectType: string, platform: 'linux' | 'windows' = 'linux'): CommandSuggestion[] {
        const suggestions: CommandSuggestion[] = [];

        const templates = this.templates.listTemplates(undefined, platform);

        const projectTemplates: Record<string, string[]> = {
            'node': ['npm_install', 'npm_run', 'npm_list', 'list_files', 'grep_content'],
            'java': ['mvn_compile', 'mvn_test', 'mvn_package', 'list_files', 'grep_content'],
            'python': ['pip_install', 'pip_list', 'list_files', 'grep_content'],
            'unknown': ['list_files', 'find_files', 'grep_content', 'current_dir', 'disk_usage']
        };

        const relevantIds = projectTemplates[projectType] || projectTemplates['unknown'];

        for (const template of templates) {
            const index = relevantIds.indexOf(template.id);
            if (index !== -1) {
                suggestions.push({
                    templateId: template.id,
                    name: template.name,
                    description: template.description,
                    command: template.command,
                    category: template.category,
                    relevance: 1 - (index / relevantIds.length)
                });
            }
        }

        return suggestions;
    }

    suggestNextCommand(
        lastCommand: string, 
        lastExitCode: number,
        _platform: 'linux' | 'windows' = 'linux'
    ): CommandSuggestion[] {
        const suggestions: CommandSuggestion[] = [];

        if (lastExitCode !== 0) {
            if (lastCommand.includes('npm')) {
                const template = this.templates.getTemplate('npm_install');
                if (template) {
                    suggestions.push({
                        templateId: template.id,
                        name: template.name,
                        description: 'Try installing dependencies first',
                        command: template.command,
                        category: template.category,
                        relevance: 0.9
                    });
                }
            }

            if (lastCommand.includes('git')) {
                const template = this.templates.getTemplate('git_status');
                if (template) {
                    suggestions.push({
                        templateId: template.id,
                        name: template.name,
                        description: 'Check git status for issues',
                        command: template.command,
                        category: template.category,
                        relevance: 0.9
                    });
                }
            }
        }

        if (lastCommand.includes('git status')) {
            const diffTemplate = this.templates.getTemplate('git_diff');
            if (diffTemplate) {
                suggestions.push({
                    templateId: diffTemplate.id,
                    name: diffTemplate.name,
                    description: diffTemplate.description,
                    command: diffTemplate.command,
                    category: diffTemplate.category,
                    relevance: 0.8
                });
            }
        }

        if (lastCommand.includes('ls') || lastCommand.includes('dir')) {
            const readTemplate = this.templates.getTemplate('read_file');
            if (readTemplate) {
                suggestions.push({
                    templateId: readTemplate.id,
                    name: readTemplate.name,
                    description: readTemplate.description,
                    command: readTemplate.command,
                    category: readTemplate.category,
                    relevance: 0.7
                });
            }
        }

        if (lastCommand.includes('find')) {
            const readTemplate = this.templates.getTemplate('read_file');
            if (readTemplate) {
                suggestions.push({
                    templateId: readTemplate.id,
                    name: readTemplate.name,
                    description: 'Read a found file',
                    command: readTemplate.command,
                    category: readTemplate.category,
                    relevance: 0.7
                });
            }
        }

        return suggestions.slice(0, 5);
    }

    detectProjectType(files: string[]): string {
        for (const [projectType, indicators] of this.projectIndicators) {
            for (const file of files) {
                if (indicators.some(indicator => file.includes(indicator))) {
                    return projectType;
                }
            }
        }
        return 'unknown';
    }

    private getRelevantTemplates(context: SuggestionContext): CommandTemplate[] {
        const platform = context.platform || 'linux';
        let templates = this.templates.listTemplates(undefined, platform);

        if (context.projectType) {
            const projectTemplateIds = this.getProjectTemplateIds(context.projectType);
            templates = templates.filter(t => projectTemplateIds.includes(t.id) || t.category === '文件操作');
        }

        return templates;
    }

    private getProjectTemplateIds(projectType: string): string[] {
        const projectTemplates: Record<string, string[]> = {
            'node': ['npm_install', 'npm_run', 'npm_list'],
            'java': ['mvn_compile', 'mvn_test', 'mvn_package'],
            'python': ['pip_install', 'pip_list']
        };

        return projectTemplates[projectType] || [];
    }

    private calculateRelevance(template: CommandTemplate, context: SuggestionContext): number {
        let relevance = 0.5;

        if (context.intent) {
            const intentLower = context.intent.toLowerCase();
            const keywords = this.intentKeywords.get(template.id) || [];
            const matchCount = keywords.filter(kw => intentLower.includes(kw)).length;
            relevance += matchCount * 0.2;
        }

        if (context.recentCommands) {
            for (const recentCmd of context.recentCommands) {
                if (this.areRelated(template.id, recentCmd)) {
                    relevance += 0.1;
                }
            }
        }

        if (context.projectType) {
            const projectTemplateIds = this.getProjectTemplateIds(context.projectType);
            if (projectTemplateIds.includes(template.id)) {
                relevance += 0.3;
            }
        }

        return Math.min(relevance, 1.0);
    }

    private areRelated(templateId: string, command: string): boolean {
        const relations: Record<string, string[]> = {
            'git_status': ['git', 'status'],
            'git_log': ['git', 'log'],
            'git_diff': ['git', 'diff'],
            'npm_install': ['npm', 'install'],
            'npm_run': ['npm', 'run'],
            'mvn_compile': ['mvn', 'compile'],
            'list_files': ['ls', 'dir', 'list']
        };

        const keywords = relations[templateId] || [];
        return keywords.some(kw => command.includes(kw));
    }
}

export const commandSuggester = new CommandSuggester();
