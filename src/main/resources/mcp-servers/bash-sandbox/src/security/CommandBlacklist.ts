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

/**
 * 黑名单配置
 *
 * 设计原则：
 * 1. 只阻止真正危险的操作（如删除系统目录、格式化磁盘）
 * 2. 允许开发环境中常用的命令（sudo, systemctl, curl|bash 等）
 * 3. 通过白名单控制大部分命令访问
 */
export const BLOCKED_PATTERNS: BlockedPattern[] = [
    // ==================== 文件系统破坏（保留） ====================
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
        pattern: /.*\b(>|>>)\s*\/dev\/(sda|hda|nvme|mmcblk).*/i,
        category: BlockedCategory.FILESYSTEM_DESTRUCTION,
        description: '重定向到磁盘设备',
        severity: Severity.HIGH
    },

    // ==================== 资源滥用（保留） ====================
    {
        pattern: /.*\b:\(\)\s*\{\s*:\|:&\s*\}\s*;\s*:,.*/i,
        category: BlockedCategory.RESOURCE_ABUSE,
        description: 'Fork bomb',
        severity: Severity.HIGH
    },

    // ==================== 网络攻击（放宽） ====================
    {
        pattern: /.*\b(nc|netcat|ncat)\s+.*(-e|-c|--exec).*/i,
        category: BlockedCategory.NETWORK_ATTACK,
        description: 'netcat 反弹 shell',
        severity: Severity.HIGH
    },

    // ==================== 信息泄露（放宽） ====================
    {
        pattern: /.*\b(cat|head|tail|less|more)\s+.*\/etc\/(shadow|gshadow).*/i,
        category: BlockedCategory.INFORMATION_LEAK,
        description: '读取密码哈希文件',
        severity: Severity.HIGH
    }

    // ==================== 以下命令已从黑名单移除 ====================
    // sudo, su - 允许提权（开发环境需要）
    // chmod 777 - 允许修改权限
    // chown, chgrp - 允许修改所有者
    // iptables, ufw - 允许修改防火墙
    // nmap, masscan - 允许网络扫描
    // systemctl, service - 允许管理服务
    // crontab, at - 允许定时任务
    // useradd, userdel, passwd - 允许用户管理
    // nohup & - 允许后台进程
    // wget|curl | bash - 允许下载执行脚本
    // shred, wipe - 允许安全删除
    // history -c - 允许清除历史
];

export function checkBlocked(command: string): BlockedPattern | null {
    for (const bp of BLOCKED_PATTERNS) {
        if (bp.pattern.test(command)) {
            return bp;
        }
    }
    return null;
}
