# 前端硬编码修复计划

## 目标

1. **后端API地址**：提取到配置文件，实现多环境支持
2. **UI设计**：提取到主题配置文件，支持动态切换

---

## 当前硬编码问题汇总

### 1. 后端API地址硬编码

| 文件 | 行号 | 硬编码内容 | 类型 |
|------|------|------------|------|
| `ui/src/api/request.ts` | 6 | `baseURL: ... \|\| 'http://localhost:8080'` | API地址 |
| `ui/vite.config.ts` | 29 | `target: 'http://localhost:8080'` | API地址 |
| `ui/src/views/ModelConfig/index.vue` | 325 | `ollama: 'http://localhost:11434/api/chat'` | API地址 |
| `ui/src/views/DefaultLayout.vue` | 101 | `window.open('http://localhost:8088/search?q=...')` | API地址 |
| `ui/src/views/ApiTest/index.vue` | 186 | `const apiBaseUrl = ... \|\| 'http://localhost:8080'` | API地址 |
| `ui/src/api/ai.ts` | 4 | `const API_BASE = '/api/v16/ai'` | API路径 |
| `ui/src/api/auth.ts` | 4 | `const API_BASE = '/api/auth'` | API路径 |
| `ui/src/api/chat.ts` | 4 | `const API_BASE = '/api/v16/ai'` | API路径 |
| `ui/src/api/model.ts` | - | `/api/v1/model` | API路径 |
| `ui/src/api/skill.ts` | - | `/api/v1/skills` | API路径 |
| `ui/src/api/mcp.ts` | - | `/api/v1/mcp` | API路径 |
| `ui/src/api/knowledgeGraph.ts` | - | `/api/v1/knowledge-graph` | API路径 |

### 2. UI设计硬编码

| 文件 | 问题 |
|------|------|
| `ui/src/assets/styles/variables.scss` | 硬编码颜色、字体、间距 |
| `ui/src/styles/chat-theme.scss` | 大量硬编码颜色值（深色/亮色模式） |
| `ui/src/layouts/DefaultLayout.vue` | Element Plus 样式覆盖 (228,236,254,346行) |
| `ui/src/views/MCPManager/index.vue` | Element Plus 样式覆盖 (944-1072行) |
| `ui/src/views/UserProfile/index.vue` | Element Plus 样式覆盖 (1172-1287行) |
| `ui/src/views/KnowledgeGraph/index.vue` | 内联样式 (30, 914-963行) |
| `ui/src/views/SkillManager/index.vue` | 内联样式 (372,433,436,501,749,752行) |

### 3. 其他配置硬编码

| 文件 | 问题 |
|------|------|
| `ui/src/api/request.ts` | `timeout: 30000`, `harmonynotes_token` |
| `ui/src/utils/cache.ts` | `harmonynotes_` 存储前缀 |
| `ui/src/composables/useMessageRetry.ts` | `harmonynotes_pending_messages` |
| `ui/src/composables/useSessionFolders.ts` | `sf_session_folders` |
| `ui/src/api/file.ts` | `timeout: 60000/120000` |
| `ui/src/components/business/FileUploader/index.vue` | `maxSizeMB: 15` |
| `ui/src/components/business/ImageUploader/index.vue` | `maxSizeMB: 10` |
| `ui/src/views/ModelConfig/index.vue` | `PROVIDER_URLS` 对象 |

---

## 第一阶段：后端API地址配置化

### 任务1：创建统一的环境配置文件

**目标**：创建 `ui/src/config/api.ts` 集中管理所有API配置

```typescript
// ui/src/config/api.ts

// 后端服务地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

// 搜索服务地址
export const SEARCH_SERVICE_URL = import.meta.env.VITE_SEARCH_SERVICE_URL || 'http://localhost:8088'

// Ollama 服务地址
export const OLLAMA_URL = import.meta.env.VITE_OLLAMA_URL || 'http://localhost:11434'

// API 版本路径
export const API_PATHS = {
  AI: '/api/v16/ai',
  AUTH: '/api/auth',
  CHAT: '/api/v16/ai',
  MODEL: '/api/v1/model',
  SKILL: '/api/v1/skills',
  MCP: '/api/v1/mcp',
  KNOWLEDGE_GRAPH: '/api/v1/knowledge-graph',
  FILE: '/api/v1/file',
} as const

// Provider URLs
export const PROVIDER_URLS: Record<string, string> = {
  openai: 'https://api.openai.com/v1/chat/completions',
  deepseek: 'https://api.deepseek.com/chat/completions',
  dashscope: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
  ollama: `${OLLAMA_URL}/api/chat`,
  anthropic: 'https://api.anthropic.com/v1/messages',
  gemini: 'https://generativelanguage.googleapis.com/v1beta/models',
}

// 超时配置 (ms)
export const API_TIMEOUT = {
  DEFAULT: 30000,
  FILE_UPLOAD: 60000,
  FILE_DOWNLOAD: 120000,
}
```

### 任务2：修改 vite.config.ts 代理配置

**文件**：`ui/vite.config.ts`

修改：引用 `src/config/api.ts` 替代硬编码的 `http://localhost:8080`

### 任务3：修改 axios 请求实例

**文件**：`ui/src/api/request.ts`

修改：引用 `src/config/api.ts` 中的 `API_BASE_URL`

### 任务4：统一 API 模块路径

**文件**：
- `ui/src/api/ai.ts`
- `ui/src/api/auth.ts`
- `ui/src/api/chat.ts`
- `ui/src/api/model.ts`
- `ui/src/api/skill.ts`
- `ui/src/api/mcp.ts`
- `ui/src/api/knowledgeGraph.ts`

修改：从 `src/config/api.ts` 导入 `API_PATHS`

### 任务5：修复其他硬编码地址

- **ModelConfig/index.vue**：`ollama` URL 引用 `OLLAMA_URL`
- **DefaultLayout.vue**：`window.open()` 引用 `SEARCH_SERVICE_URL`
- **ApiTest/index.vue**：引用 `API_BASE_URL`

---

## 第二阶段：UI主题配置化

### 任务6：创建主题配置文件

**目标**：创建 `ui/src/config/theme.ts`

```typescript
// ui/src/config/theme.ts

export interface ThemeColors {
  primary: string
  secondary: string
  success: string
  danger: string
  warning: string
  info: string
  textPrimary: string
  textSecondary: string
}

export interface ChatThemeColors {
  bgDeep: string
  bgPrimary: string
  bgSecondary: string
  accentPurple: string
  accentCyan: string
  borderColor: string
  textPrimary: string
  textSecondary: string
  userBubble: string
  assistantBubble: string
}

export interface Spacing {
  xs: string
  sm: string
  md: string
  lg: string
  xl: string
}

export interface BorderRadius {
  sm: string
  md: string
  lg: string
}

export interface ThemeConfig {
  colors: ThemeColors
  chat: {
    light: ChatThemeColors
    dark: ChatThemeColors
  }
  spacing: Spacing
  borderRadius: BorderRadius
}

// 亮色主题颜色
export const lightColors: ThemeColors = {
  primary: '#667eea',
  secondary: '#764ba2',
  success: '#28a745',
  danger: '#dc3545',
  warning: '#ffc107',
  info: '#17a2b8',
  textPrimary: '#333333',
  textSecondary: '#666666',
}

// 深色主题颜色
export const darkColors: ThemeColors = {
  primary: '#8b5cf6',
  secondary: '#a855f7',
  success: '#22c55e',
  danger: '#ef4444',
  warning: '#eab308',
  info: '#06b6d4',
  textPrimary: '#ffffff',
  textSecondary: '#a1a1aa',
}

// 亮色 Chat 主题
export const lightChatTheme: ChatThemeColors = {
  bgDeep: '#f8f9fc',
  bgPrimary: '#ffffff',
  bgSecondary: '#f1f3f9',
  accentPurple: '#7c3aed',
  accentCyan: '#0891b2',
  borderColor: '#e5e7eb',
  textPrimary: '#1f2937',
  textSecondary: '#6b7280',
  userBubble: '#667eea',
  assistantBubble: '#f3f4f6',
}

// 深色 Chat 主题
export const darkChatTheme: ChatThemeColors = {
  bgDeep: '#06060a',
  bgPrimary: '#0c0c12',
  bgSecondary: '#111827',
  accentPurple: '#8b5cf6',
  accentCyan: '#06b6d4',
  borderColor: '#1f2937',
  textPrimary: '#f9fafb',
  textSecondary: '#9ca3af',
  userBubble: '#667eea',
  assistantBubble: '#1f2937',
}

export const spacing: Spacing = {
  xs: '4px',
  sm: '8px',
  md: '16px',
  lg: '24px',
  xl: '32px',
}

export const borderRadius: BorderRadius = {
  sm: '4px',
  md: '8px',
  lg: '12px',
}

export const theme: ThemeConfig = {
  colors: lightColors,
  chat: {
    light: lightChatTheme,
    dark: darkChatTheme,
  },
  spacing,
  borderRadius,
}
```

### 任务7：重构样式变量文件

**文件**：
- `ui/src/assets/styles/variables.scss` - 重构为引用 `theme.ts`
- `ui/src/styles/chat-theme.scss` - 重构为引用 `theme.ts`

### 任务8：移除内联样式

**文件**：
- `ui/src/views/KnowledgeGraph/index.vue`
- `ui/src/views/SkillManager/index.vue`

**修改**：提取到对应的 `.scss` 文件或使用 CSS 类

### 任务9：提取 Element Plus 样式覆盖

**文件**：
- `ui/src/layouts/DefaultLayout.vue`
- `ui/src/views/MCPManager/index.vue`
- `ui/src/views/UserProfile/index.vue`

**修改**：提取到统一的 `ui/src/styles/element-overrides.scss`

---

## 第三阶段：其他配置提取

### 任务10：创建应用配置文件

**目标**：创建 `ui/src/config/app.ts`

```typescript
// ui/src/config/app.ts

// 应用名称
export const APP_NAME = 'Super Friend'

// localStorage Key 配置
export const STORAGE_KEYS = {
  TOKEN: 'sf_token',              // 从 harmonynotes_token 改为 sf_token
  PENDING_MESSAGES: 'sf_pending_messages',
  SESSION_FOLDERS: 'sf_session_folders',
  THEME: 'sf_theme',
  USER_PREFERENCES: 'sf_user_preferences',
} as const

// 存储前缀（兼容性保留）
export const STORAGE_PREFIX = 'sf_'

// 文件上传配置
export const UPLOAD_CONFIG = {
  IMAGE_MAX_SIZE_MB: 10,
  FILE_MAX_SIZE_MB: 15,
  CHAT_FILE_MAX_SIZE_MB: 50,
} as const
```

### 任务11：统一 localStorage Key

**文件**：
- `ui/src/api/request.ts`
- `ui/src/utils/cache.ts`
- `ui/src/composables/useMessageRetry.ts`
- `ui/src/composables/useSessionFolders.ts`

**修改**：从 `src/config/app.ts` 导入 `STORAGE_KEYS`

### 任务12：超时和文件大小配置

**文件**：
- `ui/src/api/request.ts`
- `ui/src/api/file.ts`
- `ui/src/components/business/FileUploader/index.vue`
- `ui/src/components/business/ImageUploader/index.vue`
- `ui/src/views/Chat/index.vue`

**修改**：从 `src/config/app.ts` 和 `src/config/api.ts` 导入配置

### 任务13：Provider URLs 配置

**文件**：`ui/src/views/ModelConfig/index.vue`

**修改**：从 `src/config/api.ts` 导入 `PROVIDER_URLS`

---

## 第四阶段：文本国际化（可选）

### 任务14：创建 i18n 配置

**目标**：创建 `ui/src/locales/` 目录

```
ui/src/locales/
├── zh-CN.ts      # 中文文本
└── en-US.ts     # 英文文本
```

### 任务15：提取关键文本

**文件**：
- `ui/src/views/About/index.vue`
- `ui/src/views/Chat/index.vue`
- `ui/src/views/Login/index.vue`
- `ui/src/layouts/DefaultLayout.vue`
- `ui/src/api/request.ts`

---

## 文件结构最终目标

```
ui/src/
├── config/
│   ├── api.ts        # API地址和路径配置
│   ├── app.ts        # 应用配置（存储、文件大小等）
│   └── theme.ts      # 主题配置（颜色、间距等）
├── locales/          # 国际化文本
│   ├── zh-CN.ts
│   └── en-US.ts
├── styles/
│   ├── element-overrides.scss  # Element Plus 样式覆盖
│   └── chat-theme.scss         # Chat 主题（引用 theme.ts）
└── assets/
    └── styles/
        └── variables.scss      # 重构为引用 theme.ts
```

---

## 实施顺序

| 顺序 | 任务 | 优先级 | 风险 | 状态 |
|------|------|--------|------|------|
| 1 | 创建 `src/config/api.ts` | 高 | 低 | ✅ 已完成 |
| 2 | 修改 axios request.ts | 高 | 中 | ✅ 已完成 |
| 3 | 修改 vite.config.ts | 高 | 低 | ✅ 已完成 |
| 4 | 统一 API 模块路径 | 高 | 中 | ✅ 已完成 |
| 5 | 创建 `src/config/app.ts` | 高 | 低 | ✅ 已完成 |
| 6 | 创建 `src/config/theme.ts` | 中 | 低 | ✅ 已完成 |
| 7 | 重构 chat-theme.scss | 中 | 中 | ✅ 已完成 |
| 8 | 移除内联样式 | 中 | 低 | ✅ 已完成 |
| 9 | 提取 Element Plus 覆盖 | 中 | 低 | ✅ 已完成 |
| 10 | 统一 localStorage Key | 中 | 高 | ✅ 已完成 |
| 11 | Provider URLs 配置化 | 低 | 中 | ✅ 已完成 |
| 12 | i18n 文本提取 | 低 | 低 | ✅ 已完成 |

---

## 环境变量清单

在 `.env` 文件中配置：

```env
# API 服务地址
VITE_API_BASE_URL=http://localhost:8080

# 搜索服务地址
VITE_SEARCH_SERVICE_URL=http://localhost:8088

# Ollama 服务地址
VITE_OLLAMA_URL=http://localhost:11434
```
