import { get } from './request'
import type { LLMCallRecord } from '@/types/llmMonitor'

const API_BASE = '/api/v16/llm-monitor'

/**
 * 获取会话的 LLM 调用记录列表
 */
export const getSessionLLMCalls = async (sessionId: string): Promise<LLMCallRecord[]> => {
  console.log('[LLMMonitor API] getSessionLLMCalls called with sessionId:', sessionId)
  const url = `${API_BASE}/sessions/${sessionId}/calls`
  console.log('[LLMMonitor API] requesting URL:', url)
  try {
    const response = await get<{ success: boolean; data: LLMCallRecord[]; total: number }>(url)
    console.log('[LLMMonitor API] response:', response)
    return response.data || []
  } catch (error) {
    console.error('[LLMMonitor API] error:', error)
    return []
  }
}

/**
 * 获取单条调用记录详情
 */
export const getLLMCallDetail = async (callId: string, sessionId: string): Promise<LLMCallRecord> => {
  const response = await get<{ success: boolean; data: LLMCallRecord }>(
    `${API_BASE}/calls/${callId}?sessionId=${sessionId}`
  )
  return response.data
}

/**
 * 清除会话的监控记录
 */
export const clearSessionLLMCalls = async (sessionId: string): Promise<void> => {
  // 使用 fetch 直接调用 DELETE 接口
  const baseUrl = (window as any).__API_BASE__ || ''
  await fetch(`${baseUrl}${API_BASE}/sessions/${sessionId}/calls`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json'
    }
  })
}

/**
 * 获取监控统计信息
 */
export const getLLMMonitorStats = async (): Promise<{
  totalSessions: number
  totalCalls: number
  data: Record<string, number>
}> => {
  const response = await get<{
    success: boolean
    totalSessions: number
    totalCalls: number
    data: Record<string, number>
  }>(`${API_BASE}/stats`)
  return response
}
