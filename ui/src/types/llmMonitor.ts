/**
 * LLM 调用记录
 */
export interface LLMCallRecord {
  callId: string
  sessionId: string
  model: string
  apiUrl: string

  // 请求信息
  requestTime: string
  messages?: Array<{ role: string; content: any }>
  tools?: Array<{
    type: string
    function: {
      name: string
      description: string
      parameters?: any
    }
  }>
  temperature?: number
  maxTokens?: number

  // 响应信息
  responseTime?: string
  durationMs?: number
  finishReason?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  responseContent?: string
  reasoningContent?: string

  // 状态
  success: boolean
  errorMessage?: string
}

/**
 * LLM 监控面板状态
 */
export interface LLMMonitorState {
  visible: boolean
  records: LLMCallRecord[]
  currentDetail: LLMCallRecord | null
  loading: boolean
  currentSessionId: string
}
