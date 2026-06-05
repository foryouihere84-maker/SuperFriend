# SuperFriend

**智能 AI Agent 平台 — 自定义 Skills + MCP 服务 + 沙箱执行 + 文档生成/解析**

SuperFriend 是一个基于 Spring Boot 的全栈 AI Agent 平台，支持自定义技能（Skills）、MCP 协议服务、安全沙箱执行，以及 Word/PPT/Excel/PDF 等文档的生成与解析。通过多模态对话、知识图谱、记忆宫殿等能力，为用户提供强大的智能助手体验。

---

## 核心特性

### 自定义 Skills 系统

SuperFriend 实现了完整的 [Agent Skills 协议](https://agentskills.io/specification)，兼容 Anthropic Skills、Pi Skills、OpenClaw Skills 等主流技能规范。

- **技能发现与加载**：启动时自动扫描 `skills/system/` 和 `skills/users/{user_id}/` 目录，解析 `SKILL.md` 元数据
- **渐进式加载**：三级加载策略 — 元数据（始终在上下文）→ SKILL.md 正文（触发时加载）→ 捆绑资源（按需加载）
- **技能执行器**：支持 `script`（脚本执行）和 `command`（命令模板）两种执行模式，兼容 Python / Node / Bash / .NET 运行时
- **技能激活分析**：基于关键词、上下文、描述的多维度权重评分，智能匹配用户意图
- **Hook 机制**：支持技能执行前后的钩子拦截（`SkillHook`），可扩展预处理和后处理逻辑
- **工作流编排**：`SkillWorkflow` 支持多步骤技能编排，`WorkflowExecutor` 负责顺序执行
- **用户自定义技能**：用户可通过 `skill-creator` 技能创建、测试、迭代自己的技能

### MCP 服务集成

SuperFriend 内置 MCP（Model Context Protocol）Host，统一管理和调度外部工具服务。

- **MCP Host 服务**：`McpHostService` 负责服务发现、生命周期管理、工具调用路由
- **内置 MCP 服务**：
  - `bash-sandbox` — 安全沙箱命令执行（详见下方）
  - `filesystem` — 文件系统操作（读写、搜索、目录管理）
  - `sequential-thinking` — 顺序思考/思维链推理
  - `fetch` — HTTP 网络请求/网页抓取
  - `time` — 时间查询与时区转换
  - `shell-mcp-server` — 多 Shell 命令执行（Bash/PowerShell/CMD）
  - `git` — Git 版本控制操作
- **动态配置**：通过 `mcp-servers-config.json` 配置 MCP 服务，支持环境变量替换、超时控制、启用/禁用
- **自动进程管理**：`McpServiceLauncher` 负责 MCP 服务进程的启动、监控和自动重启
- **工具智能选择**：`SemanticToolSelectionService` 根据语义匹配最优工具

### 安全沙箱执行

沙箱系统为 AI Agent 提供安全的命令执行环境，防止危险操作。

- **双重命令过滤**：
  - **白名单**：仅允许文件操作、文本处理、开发工具、网络工具、系统查询等安全命令
  - **黑名单**：阻止文件系统破坏（`rm -rf /`）、权限提升（`sudo`）、网络攻击（`nmap`）等危险命令
- **资源限制**：动态计算 CPU 时间（10-120s）、内存（64MB-1GB）、文件大小（100MB）、进程数（64）等上限
- **会话隔离**：每个会话独立的工作目录和环境变量，支持创建/关闭/查询会话
- **审计追踪**：`AuditLogger` 记录所有命令执行，完整的可观测性
- **跨平台**：支持 Linux（ulimit 隔离）和 Windows（进程超时控制）
- **速率限制**：`RateLimiter` 防止命令洪泛
- **敏感环境变量保护**：`SensitiveEnvVars` 自动过滤敏感信息

### 文档生成与解析

SuperFriend 提供完整的 Office 文档和 PDF 生成/解析能力，通过内置 Skills 实现。

#### Word 文档（DOCX）

- **创建**：基于 OpenXML SDK (.NET)，支持报告、信函、备忘录、学术论文等类型
- **编辑**：文本替换、占位符填充、模板套用
- **验证**：XSD 规则验证文档结构完整性
- **分析**：文档结构分析、差异对比、文本块合并
- **CJK 支持**：中文排版、大学论文模板、CJK 字体处理

#### PowerPoint 演示文稿（PPTX）

- **创建**：基于 PptxGenJS，支持 16:9 宽屏、主题配色、多种幻灯片类型
- **编辑**：XML 解包 → 编辑 → 重新打包的工作流
- **读取**：通过 markitdown 提取演示文稿文本内容
- **设计系统**：内置主题键（primary/secondary/accent/light/bg）、中英文字体支持

#### Excel 电子表格（XLSX）

- **读取/分析**：`xlsx_reader.py` 解析数据、公式、格式
- **创建**：基于模板打包生成
- **编辑**：Unpack → 编辑 XML → Pack 工作流，保证公式完整性
- **公式验证**：`formula_check.py` 检查公式正确性
- **金融标准**：硬编码输入蓝色、公式结果黑色、跨表引用绿色的颜色规范

#### PDF 文档

- **创建**：支持 report、proposal、resume、portfolio、academic 等 15+ 文档类型
- **表单填充**：检查表单字段 → 填写数据 → 输出
- **重新排版**：将 Markdown 等源文件重新排版为精美 PDF

#### 文件解析

`FileParseService` 统一协调多种文件解析器：

| 文件类型 | 解析器 | 技术方案 |
|---------|--------|---------|
| 图片 | `ImageParser` | 腾讯云 OCR 文字识别 |
| PDF | `PdfParser` | Apache PDFBox |
| Office (DOCX/XLSX/PPTX) | `OfficeParser` | Apache POI |
| 音频 | `AudioParser` | 腾讯云 ASR 语音识别 |
| 视频 | `VideoParser` | 视频帧提取 + 分析 |
| 文本 | `TextFileParser` | 编码检测 + 文本读取 |

---

## 其他功能

### AI 对话系统

- **多模型支持**：兼容 OpenAI API 格式，支持 GPT-4、GPT-4o 等多种模型，用户可自定义模型配置
- **流式响应**：SSE（Server-Sent Events）实时推送对话内容
- **多模式聊天**：
  - `Lite` — 轻量模式，快速响应
  - `Medium` — 中等模式，平衡速度与质量
  - `Complex` — 复杂模式，深度推理 + 工具调用
- **意图识别**：`LLMIntentClassifier` 自动分类用户意图，路由到合适的处理策略
- **上下文压缩**：`ContextCompressionService` 智能压缩历史消息，优化 Token 使用
- **上下文摘要**：`ContextSummarizerService` 对长对话生成摘要

### Agent 编排

- **任务规划**：`TaskPlannerService` 将复杂任务分解为子任务，支持依赖管理和并行执行
- **增强执行器**：`EnhancedPlanExecutor` 按计划执行子任务，支持检查点和恢复
- **错误恢复**：`ErrorRecoveryManager` + `ErrorDiagnoser` 自动诊断错误并选择恢复策略
- **重试策略**：`RetryStrategy` 支持指数退避等重试模式
- **审批机制**：`ApprovalService` 对敏感操作进行人工审批
- **权限管理**：`PermissionService` 基于策略的细粒度权限控制

### 知识图谱

- **节点管理**：创建、更新、删除知识节点，支持语义搜索
- **关系管理**：节点间的关联关系，构建知识网络
- **上下文生成**：根据对话自动提取和关联知识，生成上下文注入 LLM
- **节点丰富**：`NodeEnrichmentService` 自动补充节点元数据

### 记忆宫殿

- **记忆创建**：从对话中智能提取关键信息形成记忆
- **触发分析**：`MemoryTriggerAnalyzer` 分析何时应激活相关记忆
- **记忆检索**：`MemoryRetrievalService` 语义检索相关记忆
- **生命周期管理**：`MemoryLifecycleService` 管理记忆的创建、衰减和归档
- **记忆连接**：记忆之间的关联关系，形成记忆网络

### 多模态支持

- **图片生成**：`GenericImageGenerator` 支持多种图片生成模型
- **视频生成**：`GenericVideoGenerator` 视频内容生成
- **音频生成**：`GenericAudioGenerator` / `ZhipuAudioGenerator` 音频内容生成
- **文件上传**：支持多文件上传，自动解析内容注入对话上下文

### 费用追踪

- **模型定价**：内置主流模型定价表，支持自定义定价
- **实时统计**：按会话、用户、日期维度统计 Token 用量和费用
- **SSE 推送**：费用变化实时推送到前端

### LLM 监控

- **调用记录**：`LLMCallMonitorService` 记录每次 LLM 调用的详细信息
- **监控面板**：前端实时展示 LLM 调用状态、延迟、Token 消耗

### 系统内置 Skills

| 技能 | 说明 |
|------|------|
| `minimax-docx` | Word 文档创建、编辑、排版（OpenXML SDK） |
| `minimax-xlsx` | Excel 电子表格创建、编辑、分析 |
| `minimax-pdf` | PDF 文档生成、表单填充、重新排版 |
| `pptx-generator` | PowerPoint 演示文稿生成与编辑 |
| `skill-creator` | 创建、测试、迭代自定义技能 |
| `brainstorming` | 协作式头脑风暴与设计 |
| `doc-coauthoring` | 文档协作撰写工作流 |
| `executing-plans` | 计划执行与任务管理 |
| `canvas-design` | 视觉艺术与海报设计 |
| `algorithmic-art` | p5.js 生成艺术创作 |
| `brand-guidelines` | 品牌色彩与排版规范应用 |

### 用户认证与安全

- **JWT 认证**：基于 JWT Token 的用户认证体系
- **BCrypt 加密**：密码使用 BCrypt 安全哈希
- **CORS 配置**：跨域请求安全控制

---

## 技术栈

### 后端

- **框架**：Spring Boot 2.7.18 + Java 8
- **数据库**：MySQL + MyBatis + Spring Data JDBC
- **文档解析**：Apache POI 5.2.5 + Apache PDFBox 2.0.30
- **云服务**：阿里云 OSS、腾讯云 COS/OCR/ASR
- **API 文档**：Springdoc OpenAPI (Swagger)
- **监控**：Spring Boot Actuator + Micrometer

### 前端

- **框架**：Vue 3 + TypeScript + Vite
- **UI**：Element Plus
- **状态管理**：Pinia
- **国际化**：vue-i18n（中/英）
- **特色组件**：流式文本渲染、LLM 监控面板、技能管理器、MCP 服务管理器、知识图谱可视化

### MCP 服务

- **bash-sandbox**：TypeScript + Node.js
- **shell-mcp-server**：Python
- **official-servers**：Python（fetch / git / time）

---

## 项目结构

```
SuperFriend/
├── src/main/
│   ├── java/com/superfriend/superfriend/
│   │   ├── agent/                    # Agent 核心
│   │   │   ├── config/               # 动态配置管理
│   │   │   ├── context/              # 上下文压缩策略
│   │   │   ├── error/                # 错误诊断与恢复
│   │   │   ├── executor/             # 工具执行器
│   │   │   ├── orchestration/        # Agent 事件编排
│   │   │   ├── planner/              # 任务规划与分解
│   │   │   ├── reasoning/            # 推理链
│   │   │   ├── retry/                # 重试策略
│   │   │   ├── skill/                # 技能系统核心
│   │   │   └── tool/                 # 工具注册与选择
│   │   ├── config/                   # Spring 配置
│   │   ├── controller/               # REST 控制器
│   │   ├── dto/                      # 数据传输对象
│   │   ├── entity/                   # 数据库实体
│   │   ├── generator/                # 多模态内容生成
│   │   ├── parser/                   # 文件解析器
│   │   ├── service/                  # 业务服务层
│   │   └── strategy/                 # 聊天模式策略
│   └── resources/
│       ├── mcp-servers/              # MCP 服务实现
│       │   ├── bash-sandbox/         # 沙箱服务 (TypeScript)
│       │   ├── shell-mcp-server/     # Shell 服务 (Python)
│       │   └── official-servers/     # 官方 MCP 服务
│       ├── mcpserverconfig/          # MCP 配置文件
│       ├── python_scripts/           # Python 工具脚本
│       ├── skills/system/            # 系统内置技能
│       └── application.yml           # 应用配置
├── ui/                               # Vue 3 前端
│   └── src/
│       ├── api/                      # API 接口
│       ├── components/               # 组件
│       ├── composables/              # 组合式函数
│       ├── stores/                   # Pinia 状态
│       ├── views/                    # 页面视图
│       └── utils/                    # 工具函数
└── docs/                             # 项目文档
```

---

## 快速开始

### 环境要求

- Java 8+
- Node.js 18+
- Python 3.8+
- MySQL 8.0+
- .NET 8.0 SDK（DOCX 技能需要）

### 后端启动

```bash
# 配置数据库
# 修改 src/main/resources/application.yml 中的数据库连接信息

# 启动应用
./mvnw spring-boot:run
```

### 前端启动

```bash
cd ui
npm install
npm run dev
```

### MCP 服务构建

```bash
# 构建 bash-sandbox
cd src/main/resources/mcp-servers/bash-sandbox
npm install && npm run build
```

---

## License

MIT
