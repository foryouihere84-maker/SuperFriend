/**
 * 应用配置文件
 * 集中管理应用级别的配置
 */

// 应用信息
export const APP_NAME = 'Super Friend'
export const APP_VERSION = '1.0.0'

// localStorage Key 配置
export const STORAGE_KEYS = {
  // 认证
  TOKEN: 'sf_token',                    // 认证令牌
  USER: 'sf_user',                      // 用户信息
  USER_PREFERENCES: 'sf_user_preferences', // 用户偏好设置

  // 主题
  THEME: 'sf_theme',                    // 主题设置 (light/dark)

  // 会话相关
  SESSION_FOLDERS: 'sf_session_folders', // 会话文件夹
  SESSION_META: 'sf_session_meta',       // 会话元数据

  // 待重试消息
  PENDING_MESSAGES: 'sf_pending_messages', // 待重试消息队列

  // 缓存
  CACHE_VERSION: 'sf_cache_version',    // 缓存版本
} as const

// 兼容旧 key 名称的映射（从 harmonynotes_ 到 sf_）
export const LEGACY_STORAGE_KEYS = {
  TOKEN: 'harmonynotes_token',
  PENDING_MESSAGES: 'harmonynotes_pending_messages',
} as const

// 存储前缀
export const STORAGE_PREFIX = 'sf_'

// 文件上传配置
export const UPLOAD_CONFIG = {
  // 图片上传
  IMAGE_MAX_SIZE_MB: 10,
  IMAGE_MAX_COUNT: 5,

  // 通用文件上传
  FILE_MAX_SIZE_MB: 15,
  FILE_MAX_COUNT: 5,

  // 聊天文件上传
  CHAT_FILE_MAX_SIZE_MB: 50,

  // 允许的文件类型
  ALLOWED_IMAGE_TYPES: ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'],
  ALLOWED_DOCUMENT_TYPES: ['pdf', 'docx', 'doc', 'pptx', 'ppt', 'xlsx', 'xls', 'txt', 'md'],
  ALLOWED_AUDIO_TYPES: ['mp3', 'wav', 'm4a', 'flac', 'aac', 'ogg'],
  ALLOWED_VIDEO_TYPES: ['mp4', 'avi', 'mov', 'mkv', 'webm'],
} as const

// 缓存配置
export const CACHE_CONFIG = {
  VERSION: '1.0.0',
  TTL: {
    SHORT: 1 * 60 * 1000,      // 1 分钟
    MEDIUM: 5 * 60 * 1000,     // 5 分钟
    LONG: 30 * 60 * 1000,      // 30 分钟
    HOUR: 60 * 60 * 1000,      // 1 小时
    DAY: 24 * 60 * 60 * 1000,  // 1 天
    WEEK: 7 * 24 * 60 * 60 * 1000, // 1 周
  },
  // 缓存键
  KEYS: {
    MCP_SERVERS: 'mcp_servers',
    MCP_STATS: 'mcp_stats',
    MCP_SELECTED_CONFIG: 'mcp_selected_config',
    SKILLS_LIST: 'skills_list',
    SKILLS_CATEGORIES: 'skills_categories',
    SKILLS_TAGS: 'skills_tags',
    SKILLS_STATS: 'skills_stats',
    SKILL_DETAIL: 'skill_detail_',
    MODEL_LIST: 'model_list',
    MODEL_DETAIL: 'model_detail_',
    CHAT_SESSIONS: 'chat_sessions',
    CHAT_HISTORY: 'chat_history_',
    CHAT_STATS: 'chat_stats',
    USER_PROFILE: 'user_profile',
    USER_PREFERENCES: 'user_preferences',
    NEWS_LIST: 'news_list',
    EMBEDDING_CONFIG: 'embedding_config',
    KNOWLEDGE_GRAPH: 'knowledge_graph_',
  },
  // 需要保留的键（版本更新时不清理）
  PRESERVE_KEYS: ['token', 'user', 'theme'],
} as const

// 重试配置
export const RETRY_CONFIG = {
  MAX_RETRIES: 3,
  INITIAL_DELAY: 1000,  // ms
  MAX_DELAY: 30000,     // ms
  AUTO_RETRY_ONLINE: true,
} as const

// API 响应相关
export const API_CONFIG = {
  // 业务错误消息
  ERROR_MESSAGES: {
    OPERATION_FAILED: '操作失败',
    REQUEST_FAILED: '请求失败',
    NETWORK_ERROR: '网络错误',
    TIMEOUT: '请求超时',
    SERVER_ERROR: '服务器错误',
    UNAUTHORIZED: '未授权，请重新登录',
  },
  // token 获取（兼容旧前缀）
  getToken: () => {
    return localStorage.getItem(STORAGE_KEYS.TOKEN) ||
           localStorage.getItem(LEGACY_STORAGE_KEYS.TOKEN)
  },
} as const

// 导出所有配置
export default {
  APP_NAME,
  APP_VERSION,
  STORAGE_KEYS,
  LEGACY_STORAGE_KEYS,
  STORAGE_PREFIX,
  UPLOAD_CONFIG,
  CACHE_CONFIG,
  RETRY_CONFIG,
  API_CONFIG,
}
