# Linux 部署指南

## 1. 目录结构

```
/opt/superfriend/
├── app.jar                              # 后端 JAR 包
├── config/
│   ├── application-linux.yml            # Spring Boot 配置
│   └── mcp-servers-config.json          # MCP 服务器配置
├── skills/
│   └── system/                          # 系统技能目录
│       ├── algorithmic-art/
│       ├── brainstorming/
│       ├── canvas-design/
│       ├── doc-coauthoring/
│       ├── executing-plans/
│       ├── minimax-docx/
│       ├── minimax-pdf/
│       ├── minimax-xlsx/
│       ├── pptx-generator/
│       ├── skill-creator/
│       └── brand-guidelines/
├── frontend/                            # 前端静态文件
│   ├── index.html
│   └── assets/
├── uploads/
│   ├── temp/
│   └── skill_outputs/
├── logs/
├── traces/
├── mcp-servers/
│   └── bash-sandbox/
│       └── dist/
│           └── index.js
└── mcp-local/                           # MCP npm/python 缓存
```

## 2. 前置条件

### 2.1 Java 8

```bash
# CentOS / OpenCloudOS
sudo yum install java-1.8.0-openjdk java-1.8.0-openjdk-devel

# Ubuntu / Debian
sudo apt install openjdk-8-jdk

# 验证
java -version
```

### 2.2 MySQL

```bash
# CentOS
sudo yum install mysql-server
sudo systemctl start mysqld
sudo systemctl enable mysqld

# Ubuntu
sudo apt install mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql

# 创建数据库
mysql -u root -p
CREATE DATABASE superfriend DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

导入表结构（按顺序执行）:

```bash
mysql -u root -p superfriend < /opt/superfriend/sql/superfriend.sql
mysql -u root -p superfriend < /opt/superfriend/sql/chat_history.sql
mysql -u root -p superfriend < /opt/superfriend/sql/ai_process_history.sql
mysql -u root -p superfriend < /opt/superfriend/sql/ai_model_config.sql
mysql -u root -p superfriend < /opt/superfriend/sql/ai_model_config_multimodal.sql
mysql -u root -p superfriend < /opt/superfriend/sql/knowledge_graph.sql
mysql -u root -p superfriend < /opt/superfriend/sql/knowledge_graph_fix.sql
mysql -u root -p superfriend < /opt/superfriend/sql/knowledge_node_add_fields.sql
mysql -u root -p superfriend < /opt/superfriend/sql/migration_dual_layer_graph.sql
mysql -u root -p superfriend < /opt/superfriend/sql/skill_selection.sql
mysql -u root -p superfriend < /opt/superfriend/sql/user_skill_selection.sql
mysql -u root -p superfriend < /opt/superfriend/sql/skill_package_support.sql
mysql -u root -p superfriend < /opt/superfriend/sql/task_plan.sql
mysql -u root -p superfriend < /opt/superfriend/sql/permission.sql
mysql -u root -p superfriend < /opt/superfriend/sql/chat_compression.sql
mysql -u root -p superfriend < /opt/superfriend/sql/chat_message_mediumtext.sql
mysql -u root -p superfriend < /opt/superfriend/sql/memory_palace.sql
```

### 2.3 Node.js（MCP 服务器和 bash-sandbox 需要）

```bash
# 使用 NodeSource 安装 Node.js 18
curl -fsSL https://rpm.nodesource.com/setup_18.x | sudo bash -  # CentOS
sudo yum install nodejs

# 或 Ubuntu
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install nodejs

# 验证
node -v
npm -v
```

### 2.4 Python 3（MCP Python 服务器和部分技能需要）

MCP 中的 Python 服务器（fetch、time 等）通过 `uvx` 运行，`uvx` 依赖 Python 3 环境。

```bash
# CentOS
sudo yum install python3 python3-pip

# Ubuntu
sudo apt install python3 python3-pip

# 验证
python3 --version
```

### 2.5 uv/uvx（MCP fetch 和 time 服务器需要）

> 注意: uv/uvx 会自动使用系统 Python 3，确保先安装 Python 3。

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
source $HOME/.local/bin/env
uv --version

# 验证 uvx 能正常调用 MCP Python 服务器
uvx mcp-server-fetch --help
```

## 3. 打包（本地执行）

### 3.1 后端

```bash
./mvnw.cmd clean package -DskipTests
```

产物: `target/superfriend-0.0.1-SNAPSHOT.jar`

### 3.2 前端

```bash
cd ui
npm install
npm run build
```

产物: `ui/dist/`

## 4. 部署

### 4.1 创建服务器目录

```bash
ssh root@120.53.237.2 "mkdir -p /opt/superfriend/{config,skills/system,frontend,uploads/{temp,skill_outputs},logs,traces,mcp-servers,mcp-local,sql}"
```

### 4.2 上传文件

```bash
# 后端 JAR
scp target/superfriend-*.jar root@120.53.237.2:/opt/superfriend/app.jar

# 配置文件
scp src/main/resources/application-linux.yml root@120.53.237.2:/opt/superfriend/config/
scp src/main/resources/mcpserverconfig/mcp-servers-config.json root@120.53.237.2:/opt/superfriend/config/

# 技能目录
scp -r src/main/resources/skills/system/* root@120.53.237.2:/opt/superfriend/skills/system/

# MCP bash-sandbox
scp -r src/main/resources/mcp-servers/bash-sandbox root@120.53.237.2:/opt/superfriend/mcp-servers/

# 前端
scp -r ui/dist/* root@120.53.237.2:/opt/superfriend/frontend/

# SQL 初始化脚本
scp src/main/resources/sql/*.sql root@120.53.237.2:/opt/superfriend/sql/
```

### 4.3 修改服务器配置

SSH 登录服务器后,根据实际情况修改配置:

```bash
ssh root@120.53.237.2
vi /opt/superfriend/config/application-linux.yml
```

需要确认的关键配置:

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `spring.datasource.url` | 数据库连接 | `localhost:3306/superfriend` |
| `spring.datasource.username` | 数据库用户名 | `root` |
| `spring.datasource.password` | 数据库密码 | `123456` |
| `server.port` | 后端端口 | `8080` |
| `mcp.config-path` | MCP 配置文件路径 | `/opt/superfriend/config/mcp-servers-config.json` |
| `skills.system.path` | 技能目录 | `/opt/superfriend/skills/system` |

MCP 配置中 bash-sandbox 路径需要修改:

```bash
vi /opt/superfriend/config/mcp-servers-config.json
```

将 bash-sandbox 的 args 改为服务器路径:

```json
"args": ["/opt/superfriend/mcp-servers/bash-sandbox/dist/index.js"]
```

## 5. Nginx 配置（推荐）

使用 Nginx 反向代理,前后端同域,无需处理跨域问题。

### 5.1 安装 Nginx

```bash
# CentOS
sudo yum install nginx
sudo systemctl start nginx
sudo systemctl enable nginx

# Ubuntu
sudo apt install nginx
sudo systemctl start nginx
sudo systemctl enable nginx
```

### 5.2 配置

创建 `/etc/nginx/conf.d/superfriend.conf`:

```nginx
server {
    listen 80;
    server_name 120.53.237.2;

    # 前端静态文件
    location / {
        root /opt/superfriend/frontend;
        try_files $uri $uri/ /index.html;
        index index.html;
    }

    # API 请求转发到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Swagger API 文档
    location /v3/api-docs {
        proxy_pass http://127.0.0.1:8080;
    }

    location /swagger-ui/ {
        proxy_pass http://127.0.0.1:8080;
    }

    # Gzip 压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml;
    gzip_min_length 1024;
}
```

### 5.3 应用配置

```bash
# 检查配置语法
sudo nginx -t

# 重新加载
sudo nginx -s reload
```

### 5.4 防火墙放行

```bash
# CentOS
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --reload

# 云服务器需在安全组中放行 80 端口
```

## 6. 启动与停止

### 6.1 前台启动（调试用）

```bash
cd /opt/superfriend
java -jar app.jar --spring.config.location=file:./config/application-linux.yml
```

### 6.2 后台启动

```bash
cd /opt/superfriend
nohup java -jar app.jar \
  --spring.config.location=file:./config/application-linux.yml \
  > logs/startup.log 2>&1 &

# 查看进程
ps -ef | grep app.jar

# 查看启动日志
tail -f logs/startup.log
```

### 6.3 停止服务

```bash
# 查找进程
ps -ef | grep app.jar

# 停止
kill <PID>
```

### 6.4 Systemd 服务（生产环境推荐）

创建 `/etc/systemd/system/superfriend.service`:

```ini
[Unit]
Description=SuperFriend AI Agent Platform
After=network.target mysqld.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/superfriend
Environment="JAVA_HOME=/usr/lib/jvm/java-1.8.0"
Environment="BASH_SANDBOX_PATH=/opt/superfriend/mcp-servers/bash-sandbox/dist/index.js"
Environment="PATH=/usr/local/bin:/usr/bin:/root/.local/bin:/root/.dotnet:/root/.dotnet/tools"
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/superfriend/app.jar --spring.config.location=file:/opt/superfriend/config/application-linux.yml
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

管理命令:

```bash
sudo systemctl daemon-reload
sudo systemctl enable superfriend
sudo systemctl start superfriend
sudo systemctl status superfriend
sudo systemctl stop superfriend
sudo systemctl restart superfriend

# 查看日志
sudo journalctl -u superfriend -f
```

## 7. 环境变量参考

所有配置均可通过环境变量覆盖,优先级: 环境变量 > application-linux.yml > 默认值。

```bash
# 应用路径
APP_HOME=/opt/superfriend
SKILLS_SYSTEM_PATH=/opt/superfriend/skills/system
SKILL_OUTPUT_DIR=/opt/superfriend/uploads/skill_outputs
TEMP_DIR=/opt/superfriend/uploads/temp
TRACE_DIR=/opt/superfriend/traces

# 数据库
DB_HOST=localhost
DB_PORT=3306
DB_NAME=superfriend
DB_USERNAME=root
DB_PASSWORD=your_password

# 服务端口
SERVER_PORT=8080

# MCP
MCP_CONFIG_PATH=/opt/superfriend/config/mcp-servers-config.json
MCP_LOCAL_STORE_PATH=/opt/superfriend/mcp-local
BASH_SANDBOX_PATH=/opt/superfriend/mcp-servers/bash-sandbox/dist/index.js

# npm/PyPI 镜像（可选,加速 MCP 服务器安装）
NPM_REGISTRY=https://registry.npmmirror.com
PYPI_INDEX=https://pypi.tuna.tsinghua.edu.cn/simple

# JWT
JWT_SECRET=your-jwt-secret-key-at-least-256-bits-long

# 可观测性（可选）
LANGFUSE_ENABLED=false
LANGFUSE_PUBLIC_KEY=
LANGFUSE_SECRET_KEY=
ARIZE_ENABLED=false
ARIZE_API_KEY=
ARIZE_SPACE_ID=

# 对象存储（可选）
ALIYUN_OSS_ENABLED=false
TENCENT_COS_ENABLED=false

# 智谱 AI（可选）
ZHIPU_ENABLED=false
ZHIPU_API_KEY=
```

## 8. 配置差异对照

| 配置项 | 开发环境默认值 | Linux 生产环境 |
|--------|---------------|---------------|
| `app.file.app-home-dir` | `.` | `/opt/superfriend` |
| `app.file.skills-dir` | `skills/system` | `/opt/superfriend/skills/system` |
| `app.file.skill-output-dir` | `uploads/skill_outputs` | `/opt/superfriend/uploads/skill_outputs` |
| `app.file.temp-dir` | `uploads/temp` | `/opt/superfriend/uploads/temp` |
| `app.file.trace-dir` | `traces` | `/opt/superfriend/traces` |
| `bash-sandbox.root-dir` | `${java.io.tmpdir}/superfriend_sandbox` | `/tmp/superfriend_sandbox` |
| `logging.file.name` | (仅控制台) | `/opt/superfriend/logs/superfriend.log` |
| `mcp.host.enable-idle-check` | `true` | `false` |
| `spring.jpa.hibernate.ddl-auto` | `update` | `none` |
| `spring.jpa.show-sql` | `true` | `false` |

## 9. 验证部署

```bash
# 检查后端健康状态
curl http://127.0.0.1:8080/api/health

# 检查前端是否可访问
curl http://127.0.0.1/

# 检查 Nginx 代理
curl http://120.53.237.2/api/health

# 检查 Swagger 文档
curl http://120.53.237.2/v3/api-docs

# 检查技能目录
ls -la /opt/superfriend/skills/system/

# 检查日志
tail -f /opt/superfriend/logs/superfriend.log
```

## 10. 更新部署

```bash
# 1. 本地重新打包
./mvnw.cmd clean package -DskipTests
cd ui && npm run build

# 2. 上传
scp target/superfriend-*.jar root@120.53.237.2:/opt/superfriend/app.jar
scp -r ui/dist/* root@120.53.237.2:/opt/superfriend/frontend/

# 3. 重启服务
ssh root@120.53.237.2 "systemctl restart superfriend"
# 或
ssh root@120.53.237.2 "kill \$(pgrep -f app.jar); sleep 2; cd /opt/superfriend && nohup java -jar app.jar --spring.config.location=file:./config/application-linux.yml > logs/startup.log 2>&1 &"
```

## 11. 故障排查

### YAML 解析错误

```
ParserException: expected '<document start>', but found '<block mapping start>'
```

检查 `application-linux.yml` 文件开头是否有多余字符,确保以 `#` 或 `spring:` 开头。

### CORS 跨域错误

```
No 'Access-Control-Allow-Origin' header is present
```

使用 Nginx 反向代理,前后端同域同端口,彻底避免跨域问题。不要直接通过 `http://IP:8080` 访问 API。

### 502 Bad Gateway

Nginx 无法连接后端,检查:

```bash
# 后端是否运行
curl http://127.0.0.1:8080/api/health

# 查看后端日志
tail -100 /opt/superfriend/logs/superfriend.log
```

### MCP 服务器启动失败

```bash
# 检查 Node.js 是否安装
node -v

# 检查 uvx 是否安装
uvx --version

# 检查 bash-sandbox 路径
ls -la /opt/superfriend/mcp-servers/bash-sandbox/dist/index.js

# 手动测试 bash-sandbox
node /opt/superfriend/mcp-servers/bash-sandbox/dist/index.js
```

### 技能未找到

```bash
# 检查技能目录
ls -la /opt/superfriend/skills/system/

# 检查权限
chmod -R 755 /opt/superfriend/skills/
```

### 数据库连接失败

```bash
# 测试数据库连接
mysql -u root -p -h localhost superfriend -e "SELECT 1"

# 检查配置中的数据库地址、用户名、密码
grep -A 5 'datasource' /opt/superfriend/config/application-linux.yml
```

### 内存不足（4GB 服务器）

调整 JVM 参数:

```bash
# 限制堆内存
java -Xms256m -Xmx768m -jar app.jar --spring.config.location=file:./config/application-linux.yml

# 或修改 systemd service 中的 -Xmx 值
```

### 端口被占用

```bash
# 查看端口占用
ss -tlnp | grep 8080

# 释放端口
kill $(lsof -t -i:8080)
```
