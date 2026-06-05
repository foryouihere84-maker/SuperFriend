import { ref, computed, onMounted, onUnmounted } from 'vue'

export interface NetworkStatusOptions {
  /**
   * 心跳检测间隔（毫秒），0 表示禁用
   * @default 0
   */
  heartbeatInterval?: number

  /**
   * 心跳检测 URL
   */
  heartbeatUrl?: string

  /**
   * 网络恢复时的回调
   */
  onOnline?: () => void

  /**
   * 网络断开时的回调
   */
  onOffline?: () => void
}

/**
 * 网络状态管理 composable
 *
 * 功能：
 * 1. 监听 online/offline 事件
 * 2. 提供网络状态（isOnline, wasOffline, offlineDuration）
 * 3. 支持网络恢复回调注册
 * 4. 可选心跳检测
 */
export function useNetworkStatus(options: NetworkStatusOptions = {}) {
  const {
    heartbeatInterval = 0,
    heartbeatUrl = '/api/health',
    onOnline,
    onOffline
  } = options

  // 网络状态
  const isOnline = ref(navigator.onLine)
  const wasOffline = ref(false)
  const lastOnlineTime = ref<Date | null>(navigator.onLine ? new Date() : null)
  const lastOfflineTime = ref<Date | null>(navigator.onLine ? null : new Date())

  // 心跳检测
  let heartbeatTimer: number | null = null
  const isHeartbeatRunning = ref(false)

  // 网络恢复回调队列
  const onlineCallbacks: Array<() => void> = []
  const offlineCallbacks: Array<() => void> = []

  /**
   * 离线时长（毫秒）
   */
  const offlineDuration = computed(() => {
    if (isOnline.value || !lastOfflineTime.value) return 0
    return Date.now() - lastOfflineTime.value.getTime()
  })

  /**
   * 离线时长描述
   */
  const offlineDurationText = computed(() => {
    const duration = offlineDuration.value
    if (duration === 0) return ''

    const seconds = Math.floor(duration / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)

    if (hours > 0) return `${hours}小时${minutes % 60}分钟`
    if (minutes > 0) return `${minutes}分钟`
    return `${seconds}秒`
  })

  /**
   * 注册网络恢复回调
   */
  const registerOnlineCallback = (callback: () => void) => {
    onlineCallbacks.push(callback)
    return () => {
      const index = onlineCallbacks.indexOf(callback)
      if (index > -1) onlineCallbacks.splice(index, 1)
    }
  }

  /**
   * 注册网络断开回调
   */
  const registerOfflineCallback = (callback: () => void) => {
    offlineCallbacks.push(callback)
    return () => {
      const index = offlineCallbacks.indexOf(callback)
      if (index > -1) offlineCallbacks.splice(index, 1)
    }
  }

  /**
   * 处理网络恢复事件
   */
  const handleOnline = () => {
    const previousState = isOnline.value
    isOnline.value = true
    lastOnlineTime.value = new Date()

    // 如果之前是离线状态，标记为曾经离线
    if (!previousState) {
      wasOffline.value = true
      console.log('[Network] 网络已恢复，离线时长:', offlineDurationText.value)
    }

    // 执行回调
    onOnline?.()
    onlineCallbacks.forEach(cb => {
      try {
        cb()
      } catch (e) {
        console.error('[Network] 网络恢复回调执行失败:', e)
      }
    })
  }

  /**
   * 处理网络断开事件
   */
  const handleOffline = () => {
    isOnline.value = false
    lastOfflineTime.value = new Date()
    console.log('[Network] 网络已断开')

    // 执行回调
    onOffline?.()
    offlineCallbacks.forEach(cb => {
      try {
        cb()
      } catch (e) {
        console.error('[Network] 网络断开回调执行失败:', e)
      }
    })
  }

  /**
   * 心跳检测
   */
  const runHeartbeat = async () => {
    if (!heartbeatInterval || !heartbeatUrl) return

    isHeartbeatRunning.value = true

    const check = async () => {
      if (!isOnline.value) return

      try {
        const controller = new AbortController()
        const timeoutId = setTimeout(() => controller.abort(), 5000)

        const response = await fetch(heartbeatUrl, {
          method: 'HEAD',
          signal: controller.signal,
          cache: 'no-cache'
        })

        clearTimeout(timeoutId)

        if (!response.ok && isOnline.value) {
          // 服务器不可达，但浏览器认为在线
          console.warn('[Network] 心跳检测失败：服务器不可达')
        }
      } catch (error) {
        if (isOnline.value) {
          console.warn('[Network] 心跳检测失败:', error)
        }
      }
    }

    await check()

    heartbeatTimer = window.setInterval(check, heartbeatInterval)
  }

  /**
   * 停止心跳检测
   */
  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    isHeartbeatRunning.value = false
  }

  /**
   * 重置状态
   */
  const reset = () => {
    wasOffline.value = false
    lastOnlineTime.value = navigator.onLine ? new Date() : null
    lastOfflineTime.value = navigator.onLine ? null : new Date()
  }

  // 生命周期
  onMounted(() => {
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)

    if (heartbeatInterval > 0) {
      runHeartbeat()
    }
  })

  onUnmounted(() => {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
    stopHeartbeat()
  })

  return {
    // 状态
    isOnline,
    wasOffline,
    lastOnlineTime,
    lastOfflineTime,
    offlineDuration,
    offlineDurationText,
    isHeartbeatRunning,

    // 方法
    registerOnlineCallback,
    registerOfflineCallback,
    reset,
    runHeartbeat,
    stopHeartbeat
  }
}

/**
 * 全局网络状态实例（单例模式）
 */
let globalNetworkStatus: ReturnType<typeof useNetworkStatus> | null = null

/**
 * 获取全局网络状态
 * 注意：必须在 Vue 组件中使用
 */
export function useGlobalNetworkStatus(options: NetworkStatusOptions = {}) {
  if (!globalNetworkStatus) {
    globalNetworkStatus = useNetworkStatus(options)
  }
  return globalNetworkStatus
}
