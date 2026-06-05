/**
 * 缓存管理工具
 * 提供缓存状态监控、清理和管理功能
 */

import { cache, CacheKeys, CacheTTL, sessionCache } from './cache'
import { STORAGE_PREFIX, LEGACY_STORAGE_KEYS } from '@/config/app'
import { debug } from './debug'

export interface CacheStatus {
  key: string
  exists: boolean
  expired: boolean
  age: number
  remaining: number
  size: number
}

/**
 * 获取所有缓存项的状态
 */
export function getCacheStatusList(): CacheStatus[] {
  const statuses: CacheStatus[] = []
  const allKeys = Object.values(CacheKeys)

  allKeys.forEach(key => {
    const meta = cache.getMeta(key)
    try {
      // 优先使用新前缀，兼容旧前缀
      const item = localStorage.getItem(`${STORAGE_PREFIX}${key}`) ||
                   localStorage.getItem(`${LEGACY_STORAGE_KEYS.TOKEN.split('_')[0]}_${key}`)
      statuses.push({
        key,
        ...meta,
        size: item ? item.length * 2 : 0
      })
    } catch {
      statuses.push({
        key,
        ...meta,
        size: 0
      })
    }
  })

  return statuses
}

/**
 * 清理所有应用缓存（保留用户登录信息）
 */
export function clearAllAppCache(): void {
  // 清理 localStorage 缓存
  const preserveKeys = ['token', 'user', 'theme', '_cache_version']
  const keys = Object.keys(localStorage)

  keys.forEach(key => {
    // 兼容新旧前缀
    if (key.startsWith(STORAGE_PREFIX) || key.startsWith('harmonynotes_')) {
      const prefix = key.startsWith(STORAGE_PREFIX) ? STORAGE_PREFIX : 'harmonynotes_'
      const rawKey = key.replace(prefix, '')
      if (!preserveKeys.includes(rawKey)) {
        localStorage.removeItem(key)
      }
    }
  })

  // 清理会话缓存
  sessionCache.clear()

  debug.log('[CacheManager] All app cache cleared')
}

/**
 * 按类别清理缓存
 */
export function clearCacheByCategory(category: 'mcp' | 'skill' | 'model' | 'chat' | 'all'): number {
  if (category === 'all') {
    clearAllAppCache()
    return -1
  }

  const prefixMap: Record<string, string> = {
    mcp: 'mcp_',
    skill: 'skills_',
    model: 'model_',
    chat: 'chat_'
  }

  return cache.clearByPrefix(prefixMap[category])
}

/**
 * 预热缓存 - 在应用启动时预加载常用数据
 */
export async function warmupCache(
  loaders: Array<{ key: string; loader: () => Promise<any>; ttl?: number }>
): Promise<void> {
  const promises = loaders.map(async ({ key, loader, ttl }) => {
    const cached = cache.get(key)
    if (!cached) {
      try {
        const data = await loader()
        cache.set(key, data, { ttl: ttl ?? CacheTTL.MEDIUM })
        debug.log(`[CacheManager] Warmed up cache: ${key}`)
      } catch (error) {
        console.error(`[CacheManager] Failed to warm up cache: ${key}`, error)
      }
    }
  })

  await Promise.allSettled(promises)
}

/**
 * 缓存健康检查
 */
export function cacheHealthCheck(): {
  healthy: boolean
  issues: string[]
  stats: ReturnType<typeof cache.getStats>
} {
  const issues: string[] = []
  const stats = cache.getStats()

  // 检查缓存是否可用
  try {
    const testKey = '__cache_test__'
    cache.set(testKey, { test: true }, { ttl: 1000 })
    const retrieved = cache.get(testKey)
    cache.remove(testKey)

    if (!retrieved || !(retrieved as { test?: boolean }).test) {
      issues.push('Cache read/write test failed')
    }
  } catch (error) {
    issues.push(`Cache test error: ${error}`)
  }

  // 检查存储空间
  if (stats.estimatedSize.includes('KB')) {
    const sizeKB = parseFloat(stats.estimatedSize)
    if (sizeKB > 5000) { // 超过 5MB
      issues.push(`Cache size is large: ${stats.estimatedSize}`)
    }
  }

  return {
    healthy: issues.length === 0,
    issues,
    stats
  }
}

/**
 * 导出缓存数据（用于调试）
 */
export function exportCacheData(): Record<string, any> {
  const data: Record<string, any> = {}
  const keys = Object.keys(localStorage)

  keys.forEach(key => {
    // 兼容新旧前缀
    const isNewPrefix = key.startsWith(STORAGE_PREFIX)
    const isLegacyPrefix = key.startsWith('harmonynotes_')
    if (isNewPrefix || isLegacyPrefix) {
      try {
        const prefix = isNewPrefix ? STORAGE_PREFIX : 'harmonynotes_'
        const rawKey = key.replace(prefix, '')
        const item = localStorage.getItem(key)
        if (item) {
          data[rawKey] = JSON.parse(item)
        }
      } catch {
        // ignore parse errors
      }
    }
  })

  return data
}

/**
 * 缓存使用建议
 */
export function getCacheRecommendations(): string[] {
  const recommendations: string[] = []
  const stats = cache.getStats()

  // 检查缓存命中率（基于缓存项数量）
  if (stats.cacheKeys < 3) {
    recommendations.push('缓存使用率较低，建议启用更多数据缓存以提高性能')
  }

  // 检查存储大小
  const sizeStr = stats.estimatedSize
  if (sizeStr.includes('KB')) {
    const sizeKB = parseFloat(sizeStr)
    if (sizeKB > 3000) {
      recommendations.push('缓存数据较大，建议定期清理过期数据')
    }
  }

  // 检查关键缓存
  const criticalCaches = [
    CacheKeys.MCP_SERVERS,
    CacheKeys.SKILLS_LIST,
    CacheKeys.MODEL_LIST
  ]

  criticalCaches.forEach(key => {
    if (!cache.has(key)) {
      recommendations.push(`关键缓存 ${key} 不存在，建议预加载`)
    }
  })

  return recommendations
}
