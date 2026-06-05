export interface ApiResponse<T = any> {
  success: boolean
  data: T
  message?: string
  code?: number
  error?: string
}

export interface ChatRequest {
  message: string
  sessionId: string
  model: string
  history?: Message[]
}

export interface ChatResponse {
  content: string | null
  done: boolean
  error?: string
  sessionId?: string
  model?: string
  tool_calls?: ToolCall[]
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

export interface Message {
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp?: number
}
