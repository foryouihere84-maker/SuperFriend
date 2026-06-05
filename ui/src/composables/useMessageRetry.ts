import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useNetworkStatus } from './useNetworkStatus'
import { STORAGE_KEYS } from '@/config/app'
import { debug } from '@/utils/debug'

const STORAGE_KEY = STORAGE_KEYS.PENDING_MESSAGES

export type RetryStatus = 'pending' | 'retrying' | 'success' | 'failed' | 'cancelled'

export interface PendingMessage {
  id: string
  timestamp: number
  retryCount: number
  maxRetries: number
  status: RetryStatus
  lastError?: string
  requestData: {
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
  }
}

export interface MessageRetryOptions {
  /**
   * 最大重试次数
   * @default 3
   */
  maxRetries?: number

  /**
   * 初始重试延迟（毫秒）
   * @default 1000
   */
  initialDelay?: number

  /**
   * 最大重试延迟（毫秒）
   * @default 30000
   */
  maxDelay?: number

  /**
   * 是否在网络恢复时自动重试
   * @default true
   */
  autoRetryOnOnline?: boolean

  /**
   * 重试成功回调
   */
  onRetrySuccess?: (message: PendingMessage) => void

  /**
   * 重试失败回调
   */
  onRetryFailed?: (message: PendingMessage, error: Error) => void

  /**
   * 发送消息函数
   */
  sendFunction?: (message: PendingMessage) => Promise<void>
}

/**
 * 消息重试队列 composable
 *
 * 功能：
 * 1. 保存失败的消息请求
 * 2. 支持自动/手动重试
 * 3. 指数退避重试策略
 * 4. 与网络状态联动
 */
export function useMessageRetry(options: MessageRetryOptions = {}) {
  const {
    maxRetries = 3,
    initialDelay = 1000,
    maxDelay = 30000,
    autoRetryOnOnline = true,
    onRetrySuccess,
    onRetryFailed,
    sendFunction
  } = options

  // 网络状态
  const networkStatus = useNetworkStatus()

  // 待重试消息队列
  const pendingMessages = ref<PendingMessage[]>([])

  // 是否正在重试
  const isRetrying = ref(false)

  // 重试定时器
  const retryTimers = new Map<string, number>()

  /**
   * 待重试消息数量
   */
  const pendingCount = computed(() => {
    return pendingMessages.value.filter(m => m.status === 'pending' || m.status === 'retrying').length
  })

  /**
   * 是否有待重试消息
   */
  const hasPending = computed(() => pendingCount.value > 0)

  /**
   * 计算重试延迟（指数退避）
   */
  const calculateDelay = (retryCount: number): number => {
    const delay = initialDelay * Math.pow(2, retryCount)
    return Math.min(delay, maxDelay)
  }

  /**
   * 从 sessionStorage 加载待重试消息
   */
  const loadFromStorage = () => {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY)
      if (stored) {
        const messages = JSON.parse(stored) as PendingMessage[]
        // 只加载未完成的消息
        pendingMessages.value = messages.filter(m =>
          m.status === 'pending' || m.status === 'retrying'
        )
        console.log('[MessageRetry] 从存储加载了', pendingMessages.value.length, '条待重试消息')
      }
    } catch (e) {
      console.error('[MessageRetry] 加载存储失败:', e)
    }
  }

  /**
   * 保存到 sessionStorage
   */
  const saveToStorage = () => {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(pendingMessages.value))
    } catch (e) {
      console.error('[MessageRetry] 保存存储失败:', e)
    }
  }

  /**
   * 添加待重试消息
   */
  const addPendingMessage = (data: PendingMessage['requestData']): PendingMessage => {
    const message: PendingMessage = {
      id: `pending-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      timestamp: Date.now(),
      retryCount: 0,
      maxRetries,
      status: 'pending',
      requestData: data
    }

    pendingMessages.value.push(message)
    saveToStorage()

    console.log('[MessageRetry] 添加待重试消息:', message.id)
    return message
  }

  /**
   * 更新消息状态
   */
  const updateMessageStatus = (id: string, status: RetryStatus, error?: string) => {
    const message = pendingMessages.value.find(m => m.id === id)
    if (message) {
      message.status = status
      if (error) message.lastError = error
      saveToStorage()
    }
  }

  /**
   * 移除消息
   */
  const removeMessage = (id: string) => {
    const index = pendingMessages.value.findIndex(m => m.id === id)
    if (index > -1) {
      pendingMessages.value.splice(index, 1)
      saveToStorage()
    }

    // 清除定时器
    const timer = retryTimers.get(id)
    if (timer) {
      clearTimeout(timer)
      retryTimers.delete(id)
    }
  }

  /**
   * 清除所有消息
   */
  const clearAll = () => {
    pendingMessages.value = []
    retryTimers.forEach(timer => clearTimeout(timer))
    retryTimers.clear()
    saveToStorage()
  }

  /**
   * 执行重试
   */
  const executeRetry = async (message: PendingMessage): Promise<boolean> => {
    if (!sendFunction) {
      console.error('[MessageRetry] 未配置发送函数')
      return false
    }

    if (!networkStatus.isOnline.value) {
      console.log('[MessageRetry] 网络离线，跳过重试')
      return false
    }

    updateMessageStatus(message.id, 'retrying')
    isRetrying.value = true

    try {
      await sendFunction(message)
      updateMessageStatus(message.id, 'success')
      onRetrySuccess?.(message)

      // 成功后移除
      setTimeout(() => removeMessage(message.id), 1000)

      debug.log('[MessageRetry] 重试成功:', message.id)
      return true
    } catch (error: unknown) {
      const newRetryCount = message.retryCount + 1
      const errorMsg = (error as Error)?.message || '未知错误'

      if (newRetryCount >= message.maxRetries) {
        updateMessageStatus(message.id, 'failed', errorMsg)
        onRetryFailed?.(message, error as Error)
        debug.error('[MessageRetry] 重试次数用尽:', message.id, errorMsg)
      } else {
        // 更新重试次数
        const msg = pendingMessages.value.find(m => m.id === message.id)
        if (msg) {
          msg.retryCount = newRetryCount
          msg.lastError = errorMsg
          saveToStorage()
        }

        // 安排下次重试
        const delay = calculateDelay(newRetryCount)
        console.log(`[MessageRetry] 安排第 ${newRetryCount + 1} 次重试，延迟 ${delay}ms`)

        const timer = window.setTimeout(() => {
          retryTimers.delete(message.id)
          const currentMsg = pendingMessages.value.find(m => m.id === message.id)
          if (currentMsg && currentMsg.status !== 'cancelled') {
            executeRetry(currentMsg)
          }
        }, delay)

        retryTimers.set(message.id, timer)
      }

      return false
    } finally {
      isRetrying.value = false
    }
  }

  /**
   * 手动重试
   */
  const retry = async (id: string): Promise<boolean> => {
    const message = pendingMessages.value.find(m => m.id === id)
    if (!message) {
      console.error('[MessageRetry] 消息不存在:', id)
      return false
    }

    // 重置重试次数
    message.retryCount = 0
    message.status = 'pending'
    saveToStorage()

    return executeRetry(message)
  }

  /**
   * 取消重试
   */
  const cancel = (id: string) => {
    updateMessageStatus(id, 'cancelled')

    // 清除定时器
    const timer = retryTimers.get(id)
    if (timer) {
      clearTimeout(timer)
      retryTimers.delete(id)
    }

    // 移除消息
    removeMessage(id)
  }

  /**
   * 重试所有待处理消息
   */
  const retryAll = async () => {
    if (!networkStatus.isOnline.value) {
      console.log('[MessageRetry] 网络离线，无法重试')
      return
    }

    const pendingList = pendingMessages.value.filter(m => m.status === 'pending' || m.status === 'failed')

    for (const message of pendingList) {
      await executeRetry(message)
    }
  }

  // 网络恢复时自动重试
  const unregisterOnlineCallback = networkStatus.registerOnlineCallback(() => {
    if (autoRetryOnOnline && hasPending.value) {
      console.log('[MessageRetry] 网络恢复，开始自动重试')
      // 延迟一小段时间确保网络稳定
      setTimeout(() => retryAll(), 500)
    }
  })

  // 生命周期
  onMounted(() => {
    loadFromStorage()
  })

  onUnmounted(() => {
    unregisterOnlineCallback()
    retryTimers.forEach(timer => clearTimeout(timer))
    retryTimers.clear()
  })

  return {
    // 状态
    pendingMessages,
    pendingCount,
    hasPending,
    isRetrying,

    // 方法
    addPendingMessage,
    removeMessage,
    clearAll,
    retry,
    retryAll,
    cancel,
    updateMessageStatus,

    // 工具方法
    calculateDelay
  }
}
