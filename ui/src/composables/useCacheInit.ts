/**
 * 缓存初始化 Hook
 * 在应用启动时预热常用数据缓存
 */

import { onMounted, onUnmounted } from 'vue'
import { useMCPStore } from '@/stores/mcp'
import { useSkillStore } from '@/stores/skill'
import { useModelStore } from '@/stores/model'
import { useUserStore } from '@/stores/user'
import { cache } from '@/utils/cache'
import { cacheHealthCheck, clearAllAppCache } from '@/utils/cacheManager'

export interface CacheInitOptions {
  /** 是否预热缓存 */
  warmup?: boolean
  /** 是否在挂载时检查缓存健康 */
  healthCheck?: boolean
  /** 是否自动清理过期缓存 */
  autoClean?: boolean
  /** 清理间隔（毫秒），默认 5 分钟 */
  cleanInterval?: number
}

/**
 * 初始化缓存系统
 */
export function useCacheInit(options: CacheInitOptions = {}) {
  const {
    warmup = true,
    healthCheck = true,
    autoClean = true,
    cleanInterval = 5 * 60 * 1000
  } = options

  let cleanTimer: ReturnType<typeof setInterval> | null = null

  /**
   * 预热缓存 - 加载常用数据
   */
  const warmupCache = async () => {
    // 只在用户登录时预热
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) {
      console.log('[CacheInit] User not logged in, skipping cache warmup')
      return
    }

    console.log('[CacheInit] Starting cache warmup...')

    // 并行加载各类数据
    const promises: Promise<void>[] = []

    // 预热 MCP 服务器列表
    const mcpStore = useMCPStore()
    promises.push(
      mcpStore.loadServers().then(() => {}).catch(() => {})
    )

    // 预热 Skills 列表
    const skillStore = useSkillStore()
    promises.push(
      skillStore.loadAllSkills().then(() => {}).catch(() => {})
    )

    // 预热 Model 配置
    const modelStore = useModelStore()
    const userId = userStore.user?.id ? Number(userStore.user.id) : undefined
    promises.push(
      modelStore.loadModels(userId).then(() => {}).catch(() => {})
    )

    await Promise.allSettled(promises)
    console.log('[CacheInit] Cache warmup completed')
  }

  /**
   * 执行健康检查
   */
  const runHealthCheck = () => {
    const result = cacheHealthCheck()
    if (!result.healthy) {
      console.warn('[CacheInit] Cache health check failed:', result.issues)
    } else {
      console.log('[CacheInit] Cache health check passed')
    }
    return result
  }

  /**
   * 自动清理过期缓存
   */
  const startAutoClean = () => {
    if (cleanTimer) {
      clearInterval(cleanTimer)
    }
    cleanTimer = setInterval(() => {
      const cleaned = cache.cleanExpired()
      if (cleaned > 0) {
        console.log(`[CacheInit] Auto-cleaned ${cleaned} expired cache entries`)
      }
    }, cleanInterval)
  }

  const stopAutoClean = () => {
    if (cleanTimer) {
      clearInterval(cleanTimer)
      cleanTimer = null
    }
  }

  onMounted(async () => {
    // 健康检查
    if (healthCheck) {
      const result = runHealthCheck()
      // 如果缓存有问题，尝试清理
      if (!result.healthy && result.issues.length > 0) {
        console.warn('[CacheInit] Clearing cache due to health issues')
        clearAllAppCache()
      }
    }

    // 预热缓存
    if (warmup) {
      await warmupCache()
    }

    // 启动自动清理
    if (autoClean) {
      startAutoClean()
    }
  })

  onUnmounted(() => {
    stopAutoClean()
  })

  return {
    warmupCache,
    runHealthCheck,
    startAutoClean,
    stopAutoClean
  }
}

/**
 * 清除所有缓存并重新加载
 */
export async function resetCache(): Promise<void> {
  clearAllAppCache()

  const userStore = useUserStore()
  if (userStore.isLoggedIn) {
    const mcpStore = useMCPStore()
    const skillStore = useSkillStore()
    const modelStore = useModelStore()

    // 重新加载所有数据
    await Promise.allSettled([
      mcpStore.loadServers(true),
      skillStore.loadAllSkills(true),
      modelStore.loadModels(userStore.user?.id ? Number(userStore.user.id) : undefined, true)
    ])
  }

  console.log('[CacheInit] Cache reset completed')
}
