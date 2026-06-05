// ============ 多模态消息类型 ============

/**
 * 媒体 URL 信息（通用）
 */
export interface MediaUrl {
  url: string
  detail?: 'auto' | 'low' | 'high'
  mimeType?: string
  format?: string
}

/**
 * 音频输入（OpenAI 格式）
 */
export interface InputAudio {
  data: string // base64 编码
  format: 'wav' | 'mp3' | 'pcm16' | 'g711_ulaw' | 'g711_alaw'
}

/**
 * 多模态消息内容项
 * 支持文本、图片、音频、视频
 */
export interface ChatMessageContent {
  type: 'text' | 'image_url' | 'input_audio' | 'audio_url' | 'video_url'
  text?: string
  imageUrl?: MediaUrl
  inputAudio?: InputAudio
  audioUrl?: MediaUrl
  videoUrl?: MediaUrl
  name?: string
  mimeType?: string
}

/**
 * 多模态请求中的历史消息
 */
export interface MultimodalChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string | ChatMessageContent[]
}

// ============ 基础消息类型 ============

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: number
  thinking: string
  toolCalls: ToolCallInfo[]
  done: boolean
  loading: boolean
  error?: string
  errorType?: string
  errorTitle?: string
  errorSuggestion?: string
  retryable?: boolean
  taskPlan?: TaskPlanInfo
  skillRecommendations?: SkillRecommendationInfo[]
  // 多模态支持
  images?: ChatMessageContent[]
  documents?: FileInfo[]
  audios?: FileInfo[]
  videos?: FileInfo[]
  // 多模态响应内容
  multimodalContent?: ResponseContent[]
}

/**
 * 文件信息
 */
export interface FileInfo {
  url: string
  name?: string
  mimeType?: string
}

/**
 * 响应内容（多模态）
 */
export interface ResponseContent {
  type: 'text' | 'image' | 'audio' | 'video'
  text?: string
  imageUrl?: MediaData
  audioUrl?: MediaData
  videoUrl?: MediaData
}

/**
 * 媒体数据
 */
export interface MediaData {
  url: string
  mimeType?: string
  description?: string
}

export interface SkillRecommendationInfo {
  skillName: string
  description: string
  category: string
  score: number
  confidence: string
  autoExecute: boolean
  suggestedParameters?: Record<string, any>
}

export interface ToolCallInfo {
  name: string
  status: 'running' | 'success' | 'error'
  result?: string
}

export interface TaskPlanInfo {
  planId: string
  summary: string
  totalSteps: number
  completedSteps: number
  status: 'pending' | 'executing' | 'completed' | 'failed'
  steps: TaskStepInfo[]
  adjustmentCount?: number
  adjustmentReason?: string
  planSummary?: string
}

export interface TaskStepInfo {
  stepNumber: number
  description: string
  toolName?: string
  status: 'pending' | 'executing' | 'completed' | 'failed' | 'skipped' | 'timeout'
  result?: string
  error?: string
  duration?: number
  iteration?: number
  maxIterations?: number
  elapsedMs?: number
  currentTool?: string
  currentServer?: string
}

export interface ToolCall {
  id: string
  type: string
  function: {
    name: string
    arguments: string
  }
}

export interface ToolResult {
  tool_call_id: string
  content: string
}

export interface ChatSession {
  id: string
  title: string
  createdAt: number
  updatedAt: number
  messageCount: number
}

export interface ChatStats {
  totalTokens: number
  requestCount: number
  avgTokens: number
  activeServers: number
}

// ============ AI 模型配置 ============

export interface AIModelConfigVO {
  configId: string
  name: string
  provider: string
  apiUrl: string
  apiKeyMasked: string
  modelId: string
  maxTokens: number
  temperature: number
  isDefault: boolean
  isEnabled: boolean
  sortOrder: number
  isSystem: boolean
  // 多模态能力
  supportedModalities: string
  inputModalities: string[]
  outputModalities: string
  outputModalitiesList: string[]
  capabilities: string
}

// ============ 请求类型 ============

/**
 * AI 对话请求
 */
export interface AIChatRequest {
  message?: string
  content?: ChatMessageContent[]
  images?: string[]
  documents?: string[]
  audios?: string[]
  videos?: string[]
  imageSize?: string
  sessionId: string
  model?: string
  processType?: string
  userId?: number
  relatedNotes?: string
  saveHistory?: boolean
  enableKnowledgeExtraction?: boolean
  history?: Array<{ role: string; content: string }>
  historyMessages?: MultimodalChatMessage[]
}

/**
 * AI 对话响应
 */
export interface AIChatResponse {
  content?: string
  reasoningContent?: string
  thought?: string
  multimodalContent?: ResponseContent[]
  sessionId: string
  model: string
  actualModel?: string
  done: boolean
  error?: string
  errorType?: string
  errorTitle?: string
  errorSuggestion?: string
  retryable?: boolean
  toolCalls?: any
  type?: string
  // 资源相关
  resourceId?: string
  fileName?: string
  fileType?: string
  fileSize?: number
  // Token 和成本追踪
  cost?: number
  inputTokens?: number
  outputTokens?: number
  totalCost?: number
  totalTokens?: number
  // 元数据
  metadata?: {
    title?: string
    author?: string
    generationTimeMs?: number
  }
  modelSwitch?: {
    originalModel: string
    actualModel: string
    reason: string
    requiredModalities: string[]
  }
}

// ============ 对话文件夹 ============

export interface SessionFolder {
  id: string
  name: string
  color?: string
  order: number
  createdAt: number
  updatedAt: number
}

export interface SessionMeta {
  folderId?: string
  isPinned?: boolean
}
