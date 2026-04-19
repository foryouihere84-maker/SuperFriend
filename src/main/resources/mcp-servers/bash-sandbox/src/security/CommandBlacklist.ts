export enum BlockedCategory {
    FILESYSTEM_DESTRUCTION = 'FILESYSTEM_DESTRUCTION',
    PRIVILEGE_ESCALATION = 'PRIVILEGE_ESCALATION',
    NETWORK_ATTACK = 'NETWORK_ATTACK',
    SYSTEM_MODIFICATION = 'SYSTEM_MODIFICATION',
    RESOURCE_ABUSE = 'RESOURCE_ABUSE',
    INFORMATION_LEAK = 'INFORMATION_LEAK',
    PERSISTENCE = 'PERSISTENCE',
    OTHER = 'OTHER'
}

export enum Severity {
    HIGH = 'HIGH',
    MEDIUM = 'MEDIUM',
    LOW = 'LOW'
}

export interface BlockedPattern {
    pattern: RegExp;
    category: BlockedCategory;
    description: string;
    severity: Severity;
}

export const BLOCKED_PATTERNS: BlockedPattern[] = [
    {
        pattern: /.*\brm\s+(-[rf]+\s+)*(\/|\/\*|\/\.|~|\/home|\/etc|\/var|\/usr|\/root|\/boot).*/i,
        category: BlockedCategory.FILESYSTEM_DESTRUCTION,
        description: '递归删除系统关键目录',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\bdd\s+.*\bof=\/dev\/.*/i,
        category: BlockedCategory.FILESYSTEM_DESTRUCTION,
        description: 'dd 写入设备文件',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\bmkfs\b.*/i,
        category: BlockedCategory.FILESYSTEM_DESTRUCTION,
        description: '格式化文件系统',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\b(shred|wipe)\b.*/i,
        category: BlockedCategory.FILESYSTEM_DESTRUCTION,
        description: '安全删除/擦除文件',
        severity: Severity.MEDIUM
    },
    {
        pattern: /.*\bsudo\b.*/i,
        category: BlockedCategory.PRIVILEGE_ESCALATION,
        description: 'sudo 提权',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\bsu\b.*/i,
        category: BlockedCategory.PRIVILEGE_ESCALATION,
        description: 'su 切换用户',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\bchmod\s+[0-7]*777.*/i,
        category: BlockedCategory.PRIVILEGE_ESCALATION,
        description: '设置过于宽松的权限',
        severity: Severity.MEDIUM
    },
    {
        pattern: /.*\b(chown|chgrp)\s+.*/i,
        category: BlockedCategory.PRIVILEGE_ESCALATION,
        description: '修改文件所有者',
        severity: Severity.MEDIUM
    },
    {
        pattern: /.*\b(nc|netcat|ncat)\s+.*(-e|-c|--exec).*/i,
        category: BlockedCategory.NETWORK_ATTACK,
        description: 'netcat 反弹 shell',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\b(iptables|ufw|firewall-cmd)\b.*/i,
        category: BlockedCategory.NETWORK_ATTACK,
        description: '修改防火墙规则',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\b(nmap|masscan|zmap)\b.*/i,
        category: BlockedCategory.NETWORK_ATTACK,
        description: '网络扫描工具',
        severity: Severity.MEDIUM
    },
    {
        pattern: /.*\b(systemctl|service)\s+.*(start|stop|restart|enable|disable).*/i,
        category: BlockedCategory.SYSTEM_MODIFICATION,
        description: '管理系统服务',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\b(crontab|at)\b.*/i,
        category: BlockedCategory.PERSISTENCE,
        description: '修改定时任务',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\b(useradd|userdel|usermod|passwd)\b.*/i,
        category: BlockedCategory.SYSTEM_MODIFICATION,
        description: '用户管理命令',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\b:\(\)\s*\{\s*:\|:&\s*\}\s*;\s*:,.*/i,
        category: BlockedCategory.RESOURCE_ABUSE,
        description: 'Fork bomb',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\bnohup\s+.*&.*/i,
        category: BlockedCategory.RESOURCE_ABUSE,
        description: '后台持久进程',
        severity: Severity.MEDIUM
    },
    {
        pattern: /.*\b(cat|head|tail|less|more)\s+.*(passwd|shadow|sudoers|ssh).*/i,
        category: BlockedCategory.INFORMATION_LEAK,
        description: '读取敏感文件',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\bhistory\s+(-c|--clear).*/i,
        category: BlockedCategory.INFORMATION_LEAK,
        description: '清除命令历史',
        severity: Severity.MEDIUM
    },
    {
        pattern: /.*\b(>|>>)\s*\/dev\/(sda|hda|nvme|mmcblk).*/i,
        category: BlockedCategory.FILESYSTEM_DESTRUCTION,
        description: '重定向到磁盘设备',
        severity: Severity.HIGH
    },
    {
        pattern: /.*\b(wget|curl)\s+.*\|\s*(bash|sh|python|perl).*/i,
        category: BlockedCategory.OTHER,
        description: '下载并执行脚本',
        severity: Severity.HIGH
    }
];

export function checkBlocked(command: string): BlockedPattern | null {
    for (const bp of BLOCKED_PATTERNS) {
        if (bp.pattern.test(command)) {
            return bp;
        }
    }
    return null;
}
