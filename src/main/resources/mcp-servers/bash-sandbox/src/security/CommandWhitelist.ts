import * as path from 'path';

// ==================== 文件操作命令 ====================
export const FILE_COMMANDS: Set<string> = new Set([
    // 基本文件操作
    '/bin/ls',
    '/bin/cat',
    '/bin/head',
    '/bin/tail',
    '/bin/mkdir',
    '/bin/rmdir',
    '/bin/touch',
    '/bin/cp',
    '/bin/mv',
    '/bin/rm',
    '/bin/find',
    '/bin/stat',
    '/bin/file',
    '/bin/tree',
    '/bin/chmod',
    '/bin/chown',
    '/bin/chgrp',
    '/bin/ln',
    '/bin/readlink',
    '/bin/realpath',
    '/bin/basename',
    '/bin/dirname',
    '/bin/mktemp',
    '/bin/install',
    '/bin/shred',
    '/bin/sync',

    // 压缩解压
    '/bin/tar',
    '/bin/gzip',
    '/bin/gunzip',
    '/bin/zcat',
    '/bin/bzip2',
    '/bin/bunzip2',
    '/bin/bzcat',
    '/bin/xz',
    '/bin/unxz',
    '/bin/xzcat',
    '/bin/zip',
    '/bin/unzip',
    '/usr/bin/zip',
    '/usr/bin/unzip',
    '/usr/bin/7z',
    '/usr/bin/7za',
    '/usr/bin/rar',
    '/usr/bin/unrar'
]);

// ==================== 文本处理命令 ====================
export const TEXT_COMMANDS: Set<string> = new Set([
    '/bin/grep',
    '/bin/egrep',
    '/bin/fgrep',
    '/bin/rg',           // ripgrep
    '/bin/sed',
    '/bin/awk',
    '/bin/gawk',
    '/bin/mawk',
    '/bin/cut',
    '/bin/sort',
    '/bin/uniq',
    '/bin/wc',
    '/bin/tr',
    '/bin/echo',
    '/bin/printf',
    '/bin/date',
    '/bin/tee',
    '/bin/fold',
    '/bin/fmt',
    '/bin/pr',
    '/bin/column',
    '/bin/expand',
    '/bin/unexpand',
    '/bin/iconv',
    '/bin/recode',
    '/usr/bin/jq',
    '/usr/bin/yq',
    '/usr/bin/tomlq',
    '/usr/bin/xq',
    '/usr/bin/mlr',      // Miller
    '/usr/bin/diff',
    '/usr/bin/diff3',
    '/usr/bin/sdiff',
    '/usr/bin/cmp',
    '/usr/bin/patch',
    '/usr/bin/paste',
    '/usr/bin/join',
    '/usr/bin/split',
    '/usr/bin/csplit',
    '/usr/bin/strings',
    '/usr/bin/hexdump',
    '/usr/bin/xxd',
    '/usr/bin/od',
    '/usr/bin/base64',
    '/usr/bin/base32'
]);

// ==================== 开发工具命令 ====================
export const DEV_COMMANDS: Set<string> = new Set([
    // Git 和版本控制
    '/usr/bin/git',
    '/usr/bin/git-lfs',
    '/usr/bin/svn',
    '/usr/bin/hg',
    '/usr/bin/bzr',

    // Java
    '/usr/bin/java',
    '/usr/bin/javac',
    '/usr/bin/jar',
    '/usr/bin/javadoc',
    '/usr/bin/javap',
    '/usr/bin/jps',
    '/usr/bin/jstat',
    '/usr/bin/jinfo',
    '/usr/bin/jmap',
    '/usr/bin/jhat',
    '/usr/bin/jstack',
    '/usr/bin/jcmd',
    '/usr/bin/jvisualvm',
    '/usr/bin/jconsole',
    '/usr/bin/keytool',
    '/usr/bin/jarsigner',

    // 构建工具
    '/usr/bin/mvn',
    '/usr/bin/mvnw',
    '/usr/bin/gradle',
    '/usr/bin/gradlew',
    '/usr/bin/ant',
    '/usr/bin/make',
    '/usr/bin/cmake',
    '/usr/bin/meson',
    '/usr/bin/ninja',
    '/usr/bin/bazel',
    '/usr/bin/buck',

    // Node.js
    '/usr/bin/node',
    '/usr/bin/npm',
    '/usr/bin/npx',
    '/usr/bin/yarn',
    '/usr/bin/pnpm',
    '/usr/bin/bun',
    '/usr/bin/deno',
    '/usr/bin/ts-node',
    '/usr/bin/tsc',
    '/usr/bin/esbuild',
    '/usr/bin/rollup',
    '/usr/bin/webpack',
    '/usr/bin/vite',
    '/usr/bin/prettier',
    '/usr/bin/eslint',
    '/usr/bin/jest',
    '/usr/bin/vitest',
    '/usr/bin/mocha',

    // Python
    '/usr/bin/python',
    '/usr/bin/python2',
    '/usr/bin/python3',
    '/usr/bin/python2.7',
    '/usr/bin/python3.6',
    '/usr/bin/python3.7',
    '/usr/bin/python3.8',
    '/usr/bin/python3.9',
    '/usr/bin/python3.10',
    '/usr/bin/python3.11',
    '/usr/bin/python3.12',
    '/usr/bin/pip',
    '/usr/bin/pip2',
    '/usr/bin/pip3',
    '/usr/bin/pipx',
    '/usr/bin/conda',
    '/usr/bin/mamba',
    '/usr/bin/virtualenv',
    '/usr/bin/venv',
    '/usr/bin/poetry',
    '/usr/bin/pdm',
    '/usr/bin/uv',
    '/usr/bin/pyinstaller',
    '/usr/bin/py.test',
    '/usr/bin/pytest',
    '/usr/bin/black',
    '/usr/bin/flake8',
    '/usr/bin/pylint',
    '/usr/bin/mypy',
    '/usr/bin/isort',
    '/usr/bin/autopep8',
    '/usr/bin/yapf',

    // Go
    '/usr/bin/go',
    '/usr/bin/gofmt',
    '/usr/bin/goimports',
    '/usr/bin/golint',
    '/usr/bin/govet',
    '/usr/bin/gotest',
    '/usr/bin/gobuild',
    '/usr/bin/gorun',
    '/usr/bin/gomod',

    // Rust
    '/usr/bin/rustc',
    '/usr/bin/rustup',
    '/usr/bin/cargo',
    '/usr/bin/cargo-clippy',
    '/usr/bin/rustfmt',
    '/usr/bin/rust-gdb',
    '/usr/bin/rust-lldb',

    // C/C++
    '/usr/bin/gcc',
    '/usr/bin/g++',
    '/usr/bin/cc',
    '/usr/bin/c++',
    '/usr/bin/clang',
    '/usr/bin/clang++',
    '/usr/bin/clang-format',
    '/usr/bin/clang-tidy',
    '/usr/bin/cpp',
    '/usr/bin/ld',
    '/usr/bin/ar',
    '/usr/bin/ranlib',
    '/usr/bin/nm',
    '/usr/bin/objdump',
    '/usr/bin/readelf',
    '/usr/bin/size',
    '/usr/bin/strip',
    '/usr/bin/gdb',
    '/usr/bin/lldb',
    '/usr/bin/valgrind',
    '/usr/bin/strace',
    '/usr/bin/ltrace',

    // Ruby
    '/usr/bin/ruby',
    '/usr/bin/gem',
    '/usr/bin/bundle',
    '/usr/bin/bundler',
    '/usr/bin/rake',
    '/usr/bin/rails',
    '/usr/bin/irb',
    '/usr/bin/pry',

    // PHP
    '/usr/bin/php',
    '/usr/bin/composer',
    '/usr/bin/phpunit',
    '/usr/bin/phpcs',
    '/usr/bin/phpcbf',
    '/usr/bin/phpstan',

    // Perl
    '/usr/bin/perl',
    '/usr/bin/cpan',
    '/usr/bin/cpanm',
    '/usr/bin/perlcritic',
    '/usr/bin/perltidy',

    // .NET
    '/usr/bin/dotnet',
    '/usr/bin/mono',

    // 其他语言
    '/usr/bin/lua',
    '/usr/bin/luarocks',
    '/usr/bin/rscript',
    '/usr/bin/R',
    '/usr/bin/julia',
    '/usr/bin/scala',
    '/usr/bin/sbt',
    '/usr/bin/kotlin',
    '/usr/bin/kotlinc',
    '/usr/bin/swift',
    '/usr/bin/swiftc',
    '/usr/bin/ocaml',
    '/usr/bin/haskell',
    '/usr/bin/ghc',
    '/usr/bin/stack',
    '/usr/bin/cabal',
    '/usr/bin/elixir',
    '/usr/bin/mix',
    '/usr/bin/erl',
    '/usr/bin/rebar3',
    '/usr/bin/clojure',
    '/usr/bin/lein',
    '/usr/bin/boot',

    // 容器和编排
    '/usr/bin/docker',
    '/usr/bin/docker-compose',
    '/usr/bin/podman',
    '/usr/bin/podman-compose',
    '/usr/bin/kubectl',
    '/usr/bin/helm',
    '/usr/bin/kustomize',
    '/usr/bin/skaffold',
    '/usr/bin/tilt',
    '/usr/bin/minikube',
    '/usr/bin/kind',
    '/usr/bin/k3s',
    '/usr/bin/k3d',
    '/usr/bin/terraform',
    '/usr/bin/packer',
    '/usr/bin/ansible',
    '/usr/bin/ansible-playbook',
    '/usr/bin/ansible-galaxy',
    '/usr/bin/vagrant',

    // 数据库客户端
    '/usr/bin/mysql',
    '/usr/bin/mysqldump',
    '/usr/bin/mysqladmin',
    '/usr/bin/psql',
    '/usr/bin/pg_dump',
    '/usr/bin/pg_restore',
    '/usr/bin/sqlite3',
    '/usr/bin/mongo',
    '/usr/bin/mongosh',
    '/usr/bin/mongodump',
    '/usr/bin/mongorestore',
    '/usr/bin/redis-cli',
    '/usr/bin/redis-benchmark',
    '/usr/bin/cassandra',
    '/usr/bin/cqlsh',
    '/usr/bin/cockroach',
    '/usr/bin/influx',
    '/usr/bin/influxd',
    '/usr/bin/neo4j',

    // Shell
    '/usr/bin/bash',
    '/usr/bin/sh',
    '/usr/bin/zsh',
    '/usr/bin/fish',
    '/usr/bin/dash',
    '/usr/bin/ksh',
    '/usr/bin/csh',
    '/usr/bin/tcsh',

    // PowerShell
    '/usr/bin/pwsh',
    '/usr/bin/powershell',

    // 其他开发工具
    '/usr/bin/openssl',
    '/usr/bin/ssh',
    '/usr/bin/scp',
    '/usr/bin/rsync',
    '/usr/bin/sftp',
    '/usr/bin/ssh-keygen',
    '/usr/bin/ssh-copy-id',
    '/usr/bin/expect',
    '/usr/bin/screen',
    '/usr/bin/tmux',
    '/usr/bin/byobu',
    '/usr/bin/watch',
    '/usr/bin/parallel',
    '/usr/bin/xargs',
    '/usr/bin/time',
    '/usr/bin/timeout',
    '/usr/bin/nice',
    '/usr/bin/ionice',
    '/usr/bin/nohup',
    '/usr/bin/setsid',
    '/usr/bin/env',
    '/usr/bin/printenv'
]);

// ==================== 网络命令 ====================
export const NETWORK_COMMANDS: Set<string> = new Set([
    '/usr/bin/curl',
    '/usr/bin/wget',
    '/usr/bin/aria2c',
    '/usr/bin/axel',
    '/usr/bin/ping',
    '/usr/bin/ping6',
    '/usr/bin/traceroute',
    '/usr/bin/traceroute6',
    '/usr/bin/tracepath',
    '/usr/bin/mtr',
    '/usr/bin/nc',
    '/usr/bin/ncat',
    '/usr/bin/netcat',
    '/usr/bin/netstat',
    '/usr/bin/ss',
    '/usr/bin/lsof',
    '/usr/bin/ifconfig',
    '/usr/bin/ip',
    '/usr/bin/route',
    '/usr/bin/arp',
    '/usr/bin/iwconfig',
    '/usr/bin/iwlist',
    '/usr/bin/dig',
    '/usr/bin/nslookup',
    '/usr/bin/host',
    '/usr/bin/whois',
    '/usr/bin/nmap',
    '/usr/bin/masscan',
    '/usr/bin/tcpdump',
    '/usr/bin/tshark',
    '/usr/bin/wireshark',
    '/usr/bin/ngrep',
    '/usr/bin/socat',
    '/usr/bin/proxychains',
    '/usr/bin/tor',
    '/usr/bin/torsocks'
]);

// ==================== 系统命令 ====================
export const SYSTEM_COMMANDS: Set<string> = new Set([
    '/bin/pwd',
    '/bin/whoami',
    '/bin/uname',
    '/bin/hostname',
    '/bin/domainname',
    '/bin/dnsdomainname',
    '/usr/bin/id',
    '/usr/bin/groups',
    '/usr/bin/users',
    '/usr/bin/w',
    '/usr/bin/who',
    '/usr/bin/last',
    '/usr/bin/lastlog',
    '/usr/bin/finger',
    '/bin/ps',
    '/usr/bin/pgrep',
    '/usr/bin/pkill',
    '/usr/bin/killall',
    '/usr/bin/top',
    '/usr/bin/htop',
    '/usr/bin/btop',
    '/usr/bin/atop',
    '/usr/bin/glances',
    '/usr/bin/du',
    '/usr/bin/df',
    '/usr/bin/free',
    '/usr/bin/vmstat',
    '/usr/bin/iostat',
    '/usr/bin/mpstat',
    '/usr/bin/sar',
    '/usr/bin/uptime',
    '/usr/bin/w',
    '/usr/bin/dmesg',
    '/usr/bin/journalctl',
    '/usr/bin/logger',
    '/usr/bin/sysctl',
    '/usr/bin/lsmod',
    '/usr/bin/modprobe',
    '/usr/bin/insmod',
    '/usr/bin/rmmod',
    '/usr/bin/lsusb',
    '/usr/bin/lspci',
    '/usr/bin/lshw',
    '/usr/bin/dmidecode',
    '/usr/bin/hdparm',
    '/usr/bin/smartctl',
    '/usr/bin/fdisk',
    '/usr/bin/parted',
    '/usr/bin/mkfs',
    '/usr/bin/mkfs.ext4',
    '/usr/bin/mkfs.xfs',
    '/usr/bin/mkfs.btrfs',
    '/usr/bin/mount',
    '/usr/bin/umount',
    '/usr/bin/blkid',
    '/usr/bin/lsblk',
    '/usr/bin/findmnt',
    '/usr/bin/systemctl',
    '/usr/bin/service',
    '/usr/bin/chkconfig',
    '/usr/bin/update-rc.d',
    '/usr/bin/crontab',
    '/usr/bin/at',
    '/usr/bin/batch',
    '/usr/bin/systemd-analyze',
    '/usr/bin/localectl',
    '/usr/bin/timedatectl',
    '/usr/bin/hostnamectl',
    '/usr/bin/loginctl',
    '/usr/bin/useradd',
    '/usr/bin/usermod',
    '/usr/bin/userdel',
    '/usr/bin/groupadd',
    '/usr/bin/groupmod',
    '/usr/bin/groupdel',
    '/usr/bin/passwd',
    '/usr/bin/chage',
    '/usr/bin/newusers',
    '/usr/bin/chpasswd'
]);

// ==================== Shell 命令 ====================
export const SHELL_COMMANDS: Set<string> = new Set([
    '/bin/bash',
    '/bin/sh'
]);

// Shell内置命令（不需要完整路径）
export const SHELL_BUILTINS: Set<string> = new Set([
    // 目录和导航
    'cd',
    'pushd',
    'popd',
    'dirs',
    'pwd',

    // 变量和环境
    'export',
    'unset',
    'readonly',
    'declare',
    'typeset',
    'local',
    'set',
    'shift',
    'getopts',

    // 函数和脚本
    'source',
    '.',
    'alias',
    'unalias',
    'type',
    'hash',
    'help',

    // 流程控制
    'if',
    'then',
    'else',
    'elif',
    'fi',
    'case',
    'esac',
    'for',
    'while',
    'until',
    'do',
    'done',
    'in',
    'select',
    'function',
    'return',
    'exit',
    'break',
    'continue',

    // 测试和条件
    'test',
    '[',
    '[[',
    ']]',

    // 算术
    'let',
    '((',
    '))',
    'expr',

    // 输入输出
    'echo',
    'printf',
    'read',
    'readarray',
    'mapfile',

    // 作业控制
    'jobs',
    'bg',
    'fg',
    'wait',
    'disown',
    'suspend',
    'logout',
    'kill',

    // 信号
    'trap',

    // 其他
    'eval',
    'exec',
    'shopt',
    'complete',
    'compgen',
    'compopt',
    'builtin',
    'command',
    'enable',
    'caller',
    'times',
    'ulimit',
    'umask',

    // 常用外部命令（作为内置命令处理，允许无路径执行）
    'ls',
    'll',
    'la',
    'cat',
    'less',
    'more',
    'head',
    'tail',
    'grep',
    'find',
    'awk',
    'sed',
    'sort',
    'uniq',
    'wc',
    'cut',
    'tr',
    'tee',
    'xargs',
    'which',
    'whereis',
    'what',
    'whois',
    'man',
    'info',
    'apropos',
    'whatis',
    'clear',
    'reset',
    'history',
    'script',
    'scriptreplay'
]);

// ==================== 合并所有 Linux 命令 ====================
export const ALL_ALLOWED_LINUX: Set<string> = new Set([
    ...FILE_COMMANDS,
    ...TEXT_COMMANDS,
    ...DEV_COMMANDS,
    ...NETWORK_COMMANDS,
    ...SYSTEM_COMMANDS,
    ...SHELL_COMMANDS
]);

export const LINUX_COMMAND_NAMES: Set<string> = new Set(
    Array.from(ALL_ALLOWED_LINUX).map(cmd => path.basename(cmd))
);

// ==================== Windows 命令 ====================
export const WINDOWS_COMMANDS: Set<string> = new Set([
    // 基本命令
    'dir',
    'cd',
    'md',
    'mkdir',
    'rd',
    'rmdir',
    'type',
    'copy',
    'xcopy',
    'robocopy',
    'move',
    'del',
    'erase',
    'ren',
    'rename',
    'find',
    'findstr',
    'sort',
    'more',
    'tree',
    'attrib',
    'icacls',
    'takeown',
    'cipher',
    'compact',
    'convert',
    'format',
    'label',
    'vol',
    'chkdsk',
    'sfc',
    'dism',

    // 系统
    'systeminfo',
    'hostname',
    'whoami',
    'ver',
    'time',
    'date',
    'echo',
    'set',
    'setx',
    'path',
    'prompt',
    'title',
    'color',
    'mode',
    'cls',
    'exit',
    'pause',
    'choice',
    'timeout',
    'waitfor',
    'shutdown',
    'restart',
    'logoff',

    // 进程
    'tasklist',
    'taskkill',
    'start',
    'wmic',
    'powershell',
    'pwsh',
    'schtasks',
    'at',
    'sc',
    'net',
    'netstat',
    'ipconfig',
    'ping',
    'tracert',
    'pathping',
    'nslookup',
    'route',
    'arp',
    'netsh',
    'getmac',
    'hostname',
    'finger',
    'telnet',
    'ftp',
    'tftp',

    // 开发工具
    'git',
    'git-lfs',
    'svn',
    'hg',
    'mvn',
    'mvnw',
    'gradle',
    'gradlew',
    'ant',
    'make',
    'cmake',
    'ninja',
    'node',
    'npm',
    'npx',
    'yarn',
    'pnpm',
    'bun',
    'deno',
    'ts-node',
    'tsc',
    'python',
    'python2',
    'python3',
    'py',
    'pip',
    'pip2',
    'pip3',
    'conda',
    'virtualenv',
    'poetry',
    'pytest',
    'black',
    'flake8',
    'pylint',
    'mypy',
    'java',
    'javac',
    'jar',
    'javadoc',
    'keytool',
    'jarsigner',
    'go',
    'cargo',
    'rustc',
    'rustup',
    'gcc',
    'g++',
    'clang',
    'clang++',
    'make',
    'cmake',
    'ruby',
    'gem',
    'bundle',
    'bundler',
    'rake',
    'rails',
    'php',
    'composer',
    'phpunit',
    'perl',
    'cpan',
    'dotnet',
    'nuget',
    'docker',
    'docker-compose',
    'kubectl',
    'helm',
    'minikube',
    'terraform',
    'ansible',
    'vagrant',
    'openssl',
    'ssh',
    'scp',
    'sftp',
    'rsync',
    'curl',
    'wget',
    'aria2c',
    '7z',
    '7za',
    'zip',
    'unzip',
    'tar',
    'gzip',
    'bzip2',
    'xz',

    // 数据库
    'mysql',
    'mysqldump',
    'psql',
    'pg_dump',
    'sqlite3',
    'mongo',
    'mongosh',
    'redis-cli',

    // Shell
    'bash',
    'sh',
    'zsh',
    'fish',
    'wsl',

    // 包管理器
    'choco',
    'winget',
    'scoop',
    'nuget',

    // 其他
    'where',
    'which',
    'sed',
    'awk',
    'grep',
    'rg',
    'jq',
    'yq',
    'less',
    'vim',
    'nvim',
    'nano',
    'notepad',
    'code',
    'code-insiders',
    'cursor'
]);

// ==================== 白名单检查函数 ====================
export function isCommandAllowed(command: string, isWindows: boolean): boolean {
    if (isWindows) {
        const cmd = command.toLowerCase().split(/\s+/)[0];
<<<<<<< HEAD
        // Windows: 如果命令在白名单中，允许
        if (WINDOWS_COMMANDS.has(cmd)) {
            return true;
        }
        // Windows: 允许所有 .exe, .bat, .cmd, .ps1 文件
        if (/\.(exe|bat|cmd|ps1)$/i.test(cmd)) {
            return true;
        }
        // Windows: 允许带路径的命令（如 C:\Program Files\...）
        if (/^[a-zA-Z]:[\\\/]/.test(cmd)) {
            return true;
        }
        return false;
    }

    const mainCommand = extractMainCommand(command);
    const commandName = path.basename(mainCommand);
=======
        return WINDOWS_COMMANDS.has(cmd);
    }

    const mainCommand = extractMainCommand(command);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

    // 检查完整路径
    if (ALL_ALLOWED_LINUX.has(mainCommand)) {
        return true;
    }

    // 检查命令名（basename）
<<<<<<< HEAD
=======
    const commandName = path.basename(mainCommand);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    if (LINUX_COMMAND_NAMES.has(commandName)) {
        return true;
    }

    // 检查shell内置命令
    if (SHELL_BUILTINS.has(commandName)) {
        return true;
    }

<<<<<<< HEAD
    // 【新增】Git Bash 环境特殊处理
    // Git Bash 常见路径前缀
    const gitBashPrefixes = [
        '/mingw64/bin/',
        '/mingw32/bin/',
        '/usr/bin/',
        '/bin/',
        '/opt/bin/'
    ];

    // 检查是否是 Git Bash 路径下的命令
    for (const prefix of gitBashPrefixes) {
        if (mainCommand.startsWith(prefix)) {
            const cmdName = mainCommand.slice(prefix.length);
            if (LINUX_COMMAND_NAMES.has(cmdName) || SHELL_BUILTINS.has(cmdName)) {
                return true;
            }
        }
    }

    // 【新增】允许常见的无路径命令（Git Bash 环境下）
    // 这些命令在 Git Bash 中通常可用但可能不在标准路径
    const commonGitBashCommands = new Set([
        'python', 'python3', 'pip', 'pip3', 'node', 'npm', 'npx', 'yarn', 'pnpm',
        'git', 'curl', 'wget', 'ssh', 'scp', 'rsync', 'tar', 'gzip', 'unzip',
        'grep', 'sed', 'awk', 'find', 'sort', 'uniq', 'cut', 'tr', 'head', 'tail',
        'cat', 'ls', 'cp', 'mv', 'rm', 'mkdir', 'rmdir', 'touch', 'chmod',
        'docker', 'docker-compose', 'kubectl', 'helm', 'terraform',
        'java', 'javac', 'mvn', 'gradle', 'go', 'cargo', 'rustc',
        'mysql', 'psql', 'redis-cli', 'mongo', 'mongosh',
        'jq', 'yq', 'rg', 'fd', 'bat', 'exa', 'delta',
        'code', 'cursor', 'vim', 'nvim', 'nano',
        'make', 'cmake', 'gcc', 'g++', 'clang', 'clang++'
    ]);

    if (commonGitBashCommands.has(commandName)) {
        return true;
    }

    // 允许带路径的本地命令（如 ./script.sh, /home/user/bin/custom）
    if (mainCommand.startsWith('./') || mainCommand.startsWith('/') || mainCommand.startsWith('../')) {
        return true;
    }

    // 允许环境变量形式的命令（如 $HOME/bin/script）
    if (mainCommand.startsWith('$')) {
        return true;
    }

=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    return false;
}

export function extractMainCommand(command: string): string {
    const firstPart = command.split(/\||;|&&|\|\|/)[0].trim();
    const parts = firstPart.split(/\s+/);
    return parts[0];
}
