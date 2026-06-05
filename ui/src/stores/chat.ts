import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage, ChatStats } from '@/types/chat'
import { getHistory, clearHistory } from '@/api/ai'
import { cache, CacheKeys, CacheTTL, sessionCache } from '@/utils/cache'
import { fixHistoryImages } from '@/utils/fixHistoryImages'
import { debug } from '@/utils/debug'

/**
 * 待重试的请求数据
 */
export interface PendingRequestData {
  content: string
  model: string
  mode: string
  sessionId: string
  userId?: number
  images?: string[]
  documents?: string[]
  audios?: string[]
  videos?: string[]
  history?: Array<{ role: string; content: string }>
  timestamp: number
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const currentSessionId = ref('')
  const isLoading = ref(false)
  const stats = ref<ChatStats>({
    totalTokens: 0,
    requestCount: 0,
    avgTokens: 0,
    activeServers: 0
  })

  // 待重试的请求数据
  const pendingRequest = ref<PendingRequestData | null>(null)

  // 最后一次请求的数据（用于重试）
  const lastRequestData = ref<PendingRequestData | null>(null)

  const lastMessage = computed(() => messages.value[messages.value.length - 1])
  const messageCount = computed(() => messages.value.length)

  const addMessage = (message: ChatMessage) => {
    messages.value.push(message)
  }

  const updateLastMessage = (content: string) => {
    if (messages.value.length > 0) {
      messages.value[messages.value.length - 1].content = content
    }
  }

  const clearMessages = () => {
    messages.value = []
  }

  const setSessionId = (id: string) => {
    currentSessionId.value = id
    // 保存当前会话ID到会话缓存
    sessionCache.set('currentSessionId', id)
  }

  const setLoading = (loading: boolean) => {
    isLoading.value = loading
  }

  const loadHistory = async (sessionId: string, useCache = true) => {
    debug.log('[ChatStore] loadHistory 被调用, sessionId:', sessionId, 'useCache:', useCache)
    const cacheKey = `${CacheKeys.CHAT_HISTORY}${sessionId}`

    // 如果使用缓存，先尝试从缓存加载
    if (useCache) {
      const cachedHistory = cache.get<ChatMessage[]>(cacheKey)
      if (cachedHistory) {
        debug.log('[ChatStore] 使用缓存的历史记录')
        messages.value = cachedHistory
        // 从缓存加载后也需要检查修复图片
        await fixHistoryImages(messages.value, sessionId)
        return
      }
    }

    try {
      const history = await getHistory(sessionId)
      debug.log('[ChatStore] 从后端加载历史记录, 消息数:', history.length)
      messages.value = history.map((msg, index) => ({
        id: `${sessionId}-${index}`,
        role: msg.role,
        content: msg.content,
        thinking: '',
        toolCalls: [],
        done: true,
        loading: false,
        timestamp: Date.now(),
        taskPlan: (msg as ChatMessage).taskPlan || undefined
      }))
      // 修复历史消息中的损坏图片
      await fixHistoryImages(messages.value, sessionId)
      // 缓存历史记录（5分钟）
      cache.set(cacheKey, messages.value, { ttl: CacheTTL.MEDIUM })
    } catch (error) {
      console.error('Failed to load history:', error)
    }
  }

  const clearSessionHistory = async (sessionId: string) => {
    try {
      await clearHistory(sessionId)
      if (sessionId === currentSessionId.value) {
        clearMessages()
      }
      // 清除该会话的历史缓存
      cache.remove(`${CacheKeys.CHAT_HISTORY}${sessionId}`)
    } catch (error) {
      console.error('Failed to clear history:', error)
    }
  }

  /**
   * 初始化时恢复会话ID
   */
  const initCurrentSession = () => {
    const savedSessionId = sessionCache.get<string>('currentSessionId')
    if (savedSessionId) {
      currentSessionId.value = savedSessionId
    }
  }

  /**
   * 清除所有 Chat 相关缓存
   */
  const clearChatCache = () => {
    cache.clearByPrefix('chat_')
    sessionCache.clear()
  }

  /**
   * 保存待重试的请求
   */
  const savePendingRequest = (data: Omit<PendingRequestData, 'timestamp'>) => {
    pendingRequest.value = {
      ...data,
      timestamp: Date.now()
    }
    // 同时保存到 sessionStorage，页面刷新后可恢复
    sessionCache.set('pendingRequest', pendingRequest.value)
  }

  /**
   * 清除待重试的请求
   */
  const clearPendingRequest = () => {
    pendingRequest.value = null
    sessionCache.remove('pendingRequest')
  }

  /**
   * 恢复待重试的请求（从 sessionStorage）
   */
  const restorePendingRequest = () => {
    const stored = sessionCache.get<PendingRequestData>('pendingRequest')
    if (stored) {
      // 检查是否过期（超过 10 分钟）
      if (Date.now() - stored.timestamp < 10 * 60 * 1000) {
        pendingRequest.value = stored
        return true
      } else {
        sessionCache.remove('pendingRequest')
      }
    }
    return false
  }

  /**
   * 保存最后一次请求的数据（用于重试）
   */
  const saveLastRequest = (data: Omit<PendingRequestData, 'timestamp'>) => {
    lastRequestData.value = {
      ...data,
      timestamp: Date.now()
    }
  }

  /**
   * 清除最后一次请求的数据
   */
  const clearLastRequest = () => {
    lastRequestData.value = null
  }

  return {
    messages,
    currentSessionId,
    isLoading,
    stats,
    lastMessage,
    messageCount,
    pendingRequest,
    lastRequestData,
    addMessage,
    updateLastMessage,
    clearMessages,
    setSessionId,
    setLoading,
    loadHistory,
    clearSessionHistory,
    initCurrentSession,
    clearChatCache,
    savePendingRequest,
    clearPendingRequest,
    restorePendingRequest,
    saveLastRequest,
    clearLastRequest
  }
})
