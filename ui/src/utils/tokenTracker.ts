/**
 * Token 追踪器 - 全局单例
 * 用于在任何地方记录 Token 使用情况
 */

import { tokenStatsDB } from './tokenStatsDB'
import { debug } from './debug'

// 扩展 Window 接口（仅开发环境）
declare global {
  interface Window {
    recordTokenUsage?: typeof recordTokenUsage
  }
}

let isInitialized = false

export interface TokenUsageEvent {
  sessionId: string
  modelId: string
  inputTokens: number
  outputTokens: number
  cost: number
  apiCallCount?: number
  toolCallCount?: number
  sessionTotalCost?: number
  sessionTotalTokens?: number
}

async function ensureInitialized(): Promise<void> {
  if (!isInitialized) {
    try {
      await tokenStatsDB.init()
      isInitialized = true
    } catch (error) {
      debug.error('[TokenTracker] 初始化失败:', error)
      throw error
    }
  }
}

export async function recordTokenUsage(event: TokenUsageEvent): Promise<void> {
  try {
    await ensureInitialized()
    await tokenStatsDB.addEvent({
      timestamp: Date.now(),
      sessionId: event.sessionId,
      modelId: event.modelId,
      inputTokens: event.inputTokens,
      outputTokens: event.outputTokens,
      totalTokens: event.inputTokens + event.outputTokens,
      cost: event.cost,
      apiCallCount: event.apiCallCount || 1,
      toolCallCount: event.toolCallCount || 0,
      sessionTotalCost: event.sessionTotalCost,
      sessionTotalTokens: event.sessionTotalTokens
    })
    debug.log('[TokenTracker] 记录 Token 使用:', event)
  } catch (error) {
    debug.error('[TokenTracker] 记录失败:', error)
  }
}

export async function initTokenTracker(): Promise<void> {
  await ensureInitialized()
  debug.log('[TokenTracker] 初始化完成')
}

// 提供全局访问入口（在开发环境下挂载到 window）
if (import.meta.env.DEV) {
  window.recordTokenUsage = recordTokenUsage
}
