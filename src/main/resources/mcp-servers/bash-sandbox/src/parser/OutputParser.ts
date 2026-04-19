export type OutputFormat = 
    | 'text' 
    | 'json' 
    | 'csv' 
    | 'table' 
    | 'list' 
    | 'keyvalue' 
    | 'xml'
    | 'yaml';

export interface ParseResult {
    format: OutputFormat;
    raw: string;
    parsed: unknown;
    structured: boolean;
    lineCount: number;
    error?: string;
}

export interface TableData {
    headers: string[];
    rows: Record<string, string>[];
}

export interface KeyValueData {
    [key: string]: string | number | boolean;
}

export class OutputParser {
    parse(output: string, format?: OutputFormat): ParseResult {
        const trimmedOutput = output.trim();
        
        if (!trimmedOutput) {
            return {
                format: 'text',
                raw: output,
                parsed: '',
                structured: false,
                lineCount: 0
            };
        }

        const detectedFormat = format || this.detectFormat(trimmedOutput);

        try {
            switch (detectedFormat) {
                case 'json':
                    return this.parseJson(trimmedOutput);
                case 'csv':
                    return this.parseCsv(trimmedOutput);
                case 'table':
                    return this.parseTable(trimmedOutput);
                case 'list':
                    return this.parseList(trimmedOutput);
                case 'keyvalue':
                    return this.parseKeyValue(trimmedOutput);
                case 'xml':
                    return this.parseXml(trimmedOutput);
                case 'yaml':
                    return this.parseYaml(trimmedOutput);
                default:
                    return this.parseText(trimmedOutput);
            }
        } catch (e) {
            return {
                format: detectedFormat,
                raw: output,
                parsed: trimmedOutput,
                structured: false,
                lineCount: trimmedOutput.split('\n').length,
                error: e instanceof Error ? e.message : 'Parse error'
            };
        }
    }

    detectFormat(output: string): OutputFormat {
        const trimmed = output.trim();
        
        if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
            try {
                JSON.parse(trimmed);
                return 'json';
            } catch {
                // Not valid JSON
            }
        }

        if (trimmed.startsWith('<?xml') || trimmed.startsWith('<')) {
            return 'xml';
        }

        if (/^[\w-]+:\s*.+/m.test(trimmed)) {
            const lines = trimmed.split('\n');
            let keyValueCount = 0;
            for (const line of lines) {
                if (/^[\w-]+:\s*.+/.test(line)) {
                    keyValueCount++;
                }
            }
            if (keyValueCount > lines.length * 0.5) {
                return 'keyvalue';
            }
        }

        if (trimmed.includes('\t') || /^\s*\S+\s+\S+/.test(trimmed)) {
            const lines = trimmed.split('\n').filter(l => l.trim());
            if (lines.length > 1) {
                const firstLineCols = this.countColumns(lines[0]);
                let consistentCols = 0;
                for (const line of lines) {
                    if (this.countColumns(line) === firstLineCols) {
                        consistentCols++;
                    }
                }
                if (consistentCols > lines.length * 0.8 && firstLineCols > 1) {
                    return 'table';
                }
            }
        }

        if (trimmed.includes(',') && !trimmed.includes('\t')) {
            const lines = trimmed.split('\n').filter(l => l.trim());
            if (lines.length > 0) {
                const firstLineCommas = (lines[0].match(/,/g) || []).length;
                if (firstLineCommas > 0) {
                    let consistentCommas = 0;
                    for (const line of lines) {
                        if ((line.match(/,/g) || []).length === firstLineCommas) {
                            consistentCommas++;
                        }
                    }
                    if (consistentCommas > lines.length * 0.8) {
                        return 'csv';
                    }
                }
            }
        }

        const lines = trimmed.split('\n').filter(l => l.trim());
        if (lines.length > 1) {
            const listPatterns = [
                /^\s*[-*•]\s+/,      // Bullet lists
                /^\s*\d+[.)]\s+/,    // Numbered lists
                /^\s*dr?[wx-]+\s+/,  // ls -l output
            ];
            
            let listMatchCount = 0;
            for (const line of lines) {
                for (const pattern of listPatterns) {
                    if (pattern.test(line)) {
                        listMatchCount++;
                        break;
                    }
                }
            }
            
            if (listMatchCount > lines.length * 0.7) {
                return 'list';
            }
        }

        return 'text';
    }

    private countColumns(line: string): number {
        const parts = line.split(/\s{2,}|\t/).filter(p => p.trim());
        return parts.length;
    }

    private parseText(output: string): ParseResult {
        const lines = output.split('\n');
        
        return {
            format: 'text',
            raw: output,
            parsed: {
                lines: lines,
                wordCount: output.split(/\s+/).filter(w => w).length,
                charCount: output.length
            },
            structured: false,
            lineCount: lines.length
        };
    }

    private parseJson(output: string): ParseResult {
        const parsed = JSON.parse(output);
        
        return {
            format: 'json',
            raw: output,
            parsed: parsed,
            structured: true,
            lineCount: output.split('\n').length
        };
    }

    private parseCsv(output: string): ParseResult {
        const lines = output.split('\n').filter(l => l.trim());
        
        if (lines.length === 0) {
            return {
                format: 'csv',
                raw: output,
                parsed: [],
                structured: true,
                lineCount: 0
            };
        }

        const parseCsvLine = (line: string): string[] => {
            const result: string[] = [];
            let current = '';
            let inQuotes = false;
            
            for (let i = 0; i < line.length; i++) {
                const char = line[i];
                
                if (char === '"') {
                    if (inQuotes && line[i + 1] === '"') {
                        current += '"';
                        i++;
                    } else {
                        inQuotes = !inQuotes;
                    }
                } else if (char === ',' && !inQuotes) {
                    result.push(current.trim());
                    current = '';
                } else {
                    current += char;
                }
            }
            result.push(current.trim());
            
            return result;
        };

        const headers = parseCsvLine(lines[0]);
        const rows: Record<string, string>[] = [];

        for (let i = 1; i < lines.length; i++) {
            const values = parseCsvLine(lines[i]);
            const row: Record<string, string> = {};
            
            for (let j = 0; j < headers.length; j++) {
                row[headers[j]] = values[j] || '';
            }
            rows.push(row);
        }

        return {
            format: 'csv',
            raw: output,
            parsed: { headers, rows },
            structured: true,
            lineCount: lines.length
        };
    }

    private parseTable(output: string): ParseResult {
        const lines = output.split('\n').filter(l => l.trim());
        
        if (lines.length === 0) {
            return {
                format: 'table',
                raw: output,
                parsed: { headers: [], rows: [] },
                structured: true,
                lineCount: 0
            };
        }

        const parseRow = (line: string): string[] => {
            return line.split(/\s{2,}|\t/).filter(p => p.trim());
        };

        const headers = parseRow(lines[0]);
        const rows: Record<string, string>[] = [];

        for (let i = 1; i < lines.length; i++) {
            if (/^[-=]+$/.test(lines[i].trim())) continue;
            
            const values = parseRow(lines[i]);
            const row: Record<string, string> = {};
            
            for (let j = 0; j < headers.length; j++) {
                row[headers[j]] = values[j] || '';
            }
            rows.push(row);
        }

        return {
            format: 'table',
            raw: output,
            parsed: { headers, rows },
            structured: true,
            lineCount: lines.length
        };
    }

    private parseList(output: string): ParseResult {
        const lines = output.split('\n').filter(l => l.trim());
        const items: Array<{ type: string; content: string; index?: number }> = [];

        for (const line of lines) {
            const bulletMatch = line.match(/^\s*[-*•]\s+(.+)/);
            if (bulletMatch) {
                items.push({ type: 'bullet', content: bulletMatch[1] });
                continue;
            }

            const numberMatch = line.match(/^\s*(\d+)[.)]\s+(.+)/);
            if (numberMatch) {
                items.push({ 
                    type: 'numbered', 
                    content: numberMatch[2], 
                    index: parseInt(numberMatch[1]) 
                });
                continue;
            }

            const fileMatch = line.match(/^([d-][rwx-]{9})\s+\d+\s+\S+\s+\S+\s+\d+\s+\S+\s+\d+\s+\S+\s+(.+)/);
            if (fileMatch) {
                items.push({ 
                    type: 'file', 
                    content: fileMatch[2],
                    index: fileMatch[1].startsWith('d') ? 1 : 0
                });
                continue;
            }

            items.push({ type: 'plain', content: line.trim() });
        }

        return {
            format: 'list',
            raw: output,
            parsed: items,
            structured: true,
            lineCount: lines.length
        };
    }

    private parseKeyValue(output: string): ParseResult {
        const lines = output.split('\n').filter(l => l.trim());
        const result: KeyValueData = {};

        for (const line of lines) {
            const match = line.match(/^([\w.-]+)\s*[:=]\s*(.*)$/);
            if (match) {
                const key = match[1];
                let value: string | number | boolean = match[2].trim();
                
                if (value === 'true') value = true;
                else if (value === 'false') value = false;
                else if (/^-?\d+$/.test(value)) value = parseInt(value);
                else if (/^-?\d+\.\d+$/.test(value)) value = parseFloat(value);
                
                result[key] = value;
            }
        }

        return {
            format: 'keyvalue',
            raw: output,
            parsed: result,
            structured: true,
            lineCount: lines.length
        };
    }

    private parseXml(output: string): ParseResult {
        const simpleParse = (xml: string): unknown => {
            const result: Record<string, unknown> = {};
            
            const tagRegex = /<(\w+)[^>]*>(.*?)<\/\1>/gs;
            let match;
            
            while ((match = tagRegex.exec(xml)) !== null) {
                const tagName = match[1];
                const content = match[2];
                
                if (/<\w+/.test(content)) {
                    result[tagName] = simpleParse(content);
                } else {
                    if (result[tagName] !== undefined) {
                        if (!Array.isArray(result[tagName])) {
                            result[tagName] = [result[tagName]];
                        }
                        (result[tagName] as unknown[]).push(content.trim());
                    } else {
                        result[tagName] = content.trim();
                    }
                }
            }
            
            return result;
        };

        const parsed = simpleParse(output);

        return {
            format: 'xml',
            raw: output,
            parsed: parsed,
            structured: true,
            lineCount: output.split('\n').length
        };
    }

    private parseYaml(output: string): ParseResult {
        const result: Record<string, unknown> = {};
        const lines = output.split('\n');
        
        const stack: Array<{ indent: number; obj: Record<string, unknown> }> = [
            { indent: -1, obj: result }
        ];

        for (const line of lines) {
            if (!line.trim() || line.trim().startsWith('#')) continue;
            
            const indent = line.search(/\S/);
            const content = line.trim();
            
            while (stack.length > 1 && indent <= stack[stack.length - 1].indent) {
                stack.pop();
            }
            
            const current = stack[stack.length - 1].obj;
            
            const keyMatch = content.match(/^(\S+)\s*:\s*(.*)$/);
            if (keyMatch) {
                const key = keyMatch[1];
                const value = keyMatch[2].trim();
                
                if (value) {
                    let parsedValue: string | number | boolean = value;
                    if (value === 'true') parsedValue = true;
                    else if (value === 'false') parsedValue = false;
                    else if (/^-?\d+$/.test(value)) parsedValue = parseInt(value);
                    else if (/^-?\d+\.\d+$/.test(value)) parsedValue = parseFloat(value);
                    else if ((value.startsWith('"') && value.endsWith('"')) || 
                             (value.startsWith("'") && value.endsWith("'"))) {
                        parsedValue = value.slice(1, -1);
                    }
                    current[key] = parsedValue;
                } else {
                    current[key] = {};
                    stack.push({ indent, obj: current[key] as Record<string, unknown> });
                }
            }
        }

        return {
            format: 'yaml',
            raw: output,
            parsed: result,
            structured: true,
            lineCount: lines.length
        };
    }

    extractFileInfo(output: string): Array<{
        name: string;
        type: 'file' | 'directory' | 'link' | 'unknown';
        size?: string;
        permissions?: string;
        modified?: string;
    }> {
        const files: Array<{
            name: string;
            type: 'file' | 'directory' | 'link' | 'unknown';
            size?: string;
            permissions?: string;
            modified?: string;
        }> = [];

        const lines = output.split('\n').filter(l => l.trim());
        
        for (const line of lines) {
            const lsMatch = line.match(/^([dld-][rwx-]{9})\s+\d+\s+\S+\s+\S+\s+(\d+)\s+(\S+\s+\d+\s+[\d:]+)\s+(.+)/);
            if (lsMatch) {
                const perms = lsMatch[1];
                const size = lsMatch[2];
                const modified = lsMatch[3];
                const name = lsMatch[4];
                
                let type: 'file' | 'directory' | 'link' | 'unknown' = 'file';
                if (perms.startsWith('d')) type = 'directory';
                else if (perms.startsWith('l')) type = 'link';
                
                files.push({ name, type, size, permissions: perms, modified });
                continue;
            }

            const simpleMatch = line.match(/^(.+)\s+(\d+)\s*$/);
            if (simpleMatch) {
                files.push({
                    name: simpleMatch[1].trim(),
                    type: 'unknown',
                    size: simpleMatch[2]
                });
            }
        }

        return files;
    }

    extractGitInfo(output: string): {
        branch?: string;
        status?: Array<{ status: string; file: string }>;
        commits?: Array<{ hash: string; message: string }>;
    } {
        const result: ReturnType<OutputParser['extractGitInfo']> = {};

        const branchMatch = output.match(/On branch (\S+)/);
        if (branchMatch) {
            result.branch = branchMatch[1];
        }

        const detachedMatch = output.match(/HEAD detached at (\S+)/);
        if (detachedMatch) {
            result.branch = `HEAD:${detachedMatch[1]}`;
        }

        const statusLines = output.match(/^[MADRCU\?\!]{1,2}\s+.+$/gm);
        if (statusLines) {
            result.status = statusLines.map(line => {
                const match = line.match(/^([MADRCU\?\!]{1,2})\s+(.+)$/);
                return {
                    status: match ? match[1] : '??',
                    file: match ? match[2].trim() : line
                };
            });
        }

        const commitLines = output.match(/^([a-f0-9]{7,})\s+(.+)$/gm);
        if (commitLines) {
            result.commits = commitLines.map(line => {
                const match = line.match(/^([a-f0-9]{7,})\s+(.+)$/);
                return {
                    hash: match ? match[1] : '',
                    message: match ? match[2].trim() : line
                };
            });
        }

        return result;
    }

    extractProcessInfo(output: string): Array<{
        user?: string;
        pid?: string;
        cpu?: string;
        mem?: string;
        command?: string;
    }> {
        const processes: Array<{
            user?: string;
            pid?: string;
            cpu?: string;
            mem?: string;
            command?: string;
        }> = [];

        const lines = output.split('\n').filter(l => l.trim());
        
        for (const line of lines) {
            const match = line.match(/^(\S+)\s+(\d+)\s+(\S+)\s+(\S+)\s+(.+)$/);
            if (match) {
                processes.push({
                    user: match[1],
                    pid: match[2],
                    cpu: match[3],
                    mem: match[4],
                    command: match[5].trim()
                });
            }
        }

        return processes;
    }
}

export const outputParser = new OutputParser();
