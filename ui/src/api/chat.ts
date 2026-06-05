import { post, get, put, del } from './request'
import type { ChatRequest, ChatResponse, ApiResponse, Message } from '@/types/api'
import { API_PATHS } from '@/config/api'

const API_BASE = API_PATHS.CHAT

export interface ChatHistorySession {
  id: number
  sessionId: string
  userId: number
  title: string | null
  model: string | null
  mode: string
  messageCount: number
  createdTime: string
  updatedTime: string
}

export interface ChatHistoryMessage {
  id: number
  historyId: number
  sessionId: string
  role: string
  content: string
  createdTime: string
}

export interface ChatHistoryDetail {
  history: ChatHistorySession
  messages: ChatHistoryMessage[]
}

const readSSEStream = async (
  reader: ReadableStreamDefaultReader<Uint8Array>,
  onData: (data: unknown) => void
): Promise<void> => {
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n\r?\n/)
    buffer = parts.pop() || ''

    for (const part of parts) {
      const lines = part.split(/\r?\n/)
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data: ')) {
          const data = trimmed.slice(6)
          if (data === '[DONE]' || !data) continue

          try {
            const jsonData = JSON.parse(data)
            onData(jsonData)
          } catch (e) {
            console.error('Failed to parse SSE data:', e, 'raw:', data)
          }
        }
      }
    }
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    const parts = buffer.split(/\r?\n\r?\n/)
    for (const part of parts) {
      const lines = part.split(/\r?\n/)
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data: ')) {
          const data = trimmed.slice(6)
          if (data === '[DONE]' || !data) continue
          try {
            const jsonData = JSON.parse(data)
            onData(jsonData)
          } catch (e) {
            console.error('Failed to parse SSE data:', e, 'raw:', data)
          }
        }
      }
    }
  }
}

export const sendMessage = async (data: ChatRequest): Promise<ChatResponse> => {
  const response = await fetch(`${API_BASE}/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(data)
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const reader = response.body?.getReader()
  const messages: ChatResponse[] = []

  if (reader) {
    await readSSEStream(reader, (jsonData) => {
      messages.push(jsonData as ChatResponse)
    })
  }

  return messages[messages.length - 1] || { content: '', done: true }
}

export const getHistory = async (sessionId: string): Promise<Message[]> => {
  const response = await get<ApiResponse<Message[]>>(`${API_BASE}/history/${sessionId}`)
  return response.data
}

export const clearHistory = async (sessionId: string): Promise<void> => {
  return del(`${API_BASE}/history/${sessionId}`)
}

export const getChatSessions = async (mode?: string, userId?: number): Promise<ChatHistorySession[]> => {
  const params = new URLSearchParams()
  if (mode) params.set('mode', mode)
  if (userId) params.set('userId', String(userId))
  const query = params.toString() ? `?${params.toString()}` : ''
  const response = await get<ApiResponse<ChatHistorySession[]>>(`${API_BASE}/chat-sessions${query}`)
  return response.data
}

export const getChatSessionMessages = async (sessionId: string): Promise<ChatHistoryDetail> => {
  const response = await get<ApiResponse<ChatHistoryDetail>>(`${API_BASE}/chat-sessions/${sessionId}`)
  return response.data
}

export const deleteChatSession = async (sessionId: string): Promise<void> => {
  return del(`${API_BASE}/chat-sessions/${sessionId}`)
}

export const batchDeleteChatSessions = async (sessionIds: string[]): Promise<void> => {
  return del(`${API_BASE}/chat-sessions/batch`, { data: { sessionIds } })
}

export const renameChatSession = async (sessionId: string, title: string): Promise<void> => {
  return put(`${API_BASE}/chat-sessions/${sessionId}/title`, { title })
}

export const finalizeChatSession = async (
  sessionId: string,
  userId?: number,
  model?: string
): Promise<{ success: boolean; message: string }> => {
  const response = await post<{ success: boolean; message: string }>(
    `/api/v16/chat/finalize/${sessionId}`,
    { userId, model }
  )
  return response
}

export interface ExtractResult {
  status: 'SUCCESS' | 'EXTRACTING' | 'FAILED' | 'NO_MESSAGES'
  message: string
}

export const extractChatSession = async (
  sessionId: string,
  messages: Array<{ role: string; content: string }>,
  userId?: number,
  model?: string
): Promise<ExtractResult> => {
  const response = await post<ExtractResult>(`/api/v16/chat/extract/${sessionId}`, {
    messages,
    userId,
    model
  })
  return response
}

// ==================== 会话状态管理 ====================

export interface SessionStatus {
  sessionId: string
  state: 'IDLE' | 'EXECUTING' | 'PAUSED' | 'CANCELLED'
  isCancelled: boolean
  isExecuting: boolean
}

/**
 * 获取会话状态
 */
export const getSessionStatus = async (sessionId: string): Promise<SessionStatus> => {
  const response = await get<ApiResponse<SessionStatus>>(`/api/v16/chat/status/${sessionId}`)
  return response.data
}

/**
 * 取消会话
 */
export const cancelSession = async (sessionId: string): Promise<{ success: boolean; message: string }> => {
  const response = await post<ApiResponse<{ success: boolean; message: string }>>(`/api/v16/chat/cancel/${sessionId}`)
  return response.data
}
