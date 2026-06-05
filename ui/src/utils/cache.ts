/**
 * 增强版缓存工具类
 * 支持 TTL（过期时间）、版本控制、自动清理、数据压缩等功能
 */

import { STORAGE_PREFIX, CACHE_CONFIG } from '@/config/app'
import { debug } from './debug'

const VERSION_KEY = '_cache_version'
const CACHE_VERSION = CACHE_CONFIG.VERSION

interface CacheEntry<T> {
  data: T
  timestamp: number
  ttl: number // 存活时间（毫秒）
  version: string
  etag?: string // 用于数据变更检测
}

interface CacheOptions {
  ttl?: number // 过期时间（毫秒），默认 5 分钟
  etag?: string // 数据版本标识
  compress?: boolean // 是否压缩（对于大数据）
}

// 默认 TTL 配置（毫秒）
export const CacheTTL = {
  SHORT: 1 * 60 * 1000,      // 1 分钟 - 用于频繁变化的数据
  MEDIUM: 5 * 60 * 1000,     // 5 分钟 - 默认值
  LONG: 30 * 60 * 1000,      // 30 分钟 - 用于不常变化的数据
  HOUR: 60 * 60 * 1000,      // 1 小时
  DAY: 24 * 60 * 60 * 1000,  // 1 天 - 用于几乎不变的数据
  WEEK: 7 * 24 * 60 * 60 * 1000, // 1 周 - 用于静态配置
}

// 缓存键常量
export const CacheKeys = {
  // MCP 相关
  MCP_SERVERS: 'mcp_servers',
  MCP_STATS: 'mcp_stats',
  MCP_SELECTED_CONFIG: 'mcp_selected_config',

  // Skill 相关
  SKILLS_LIST: 'skills_list',
  SKILLS_CATEGORIES: 'skills_categories',
  SKILLS_TAGS: 'skills_tags',
  SKILLS_STATS: 'skills_stats',
  SKILL_DETAIL: 'skill_detail_', // 前缀，后面接 skill name

  // Model 配置
  MODEL_LIST: 'model_list',
  MODEL_DETAIL: 'model_detail_', // 前缀

  // Chat 相关
  CHAT_SESSIONS: 'chat_sessions',
  CHAT_HISTORY: 'chat_history_', // 前缀，后面接 sessionId
  CHAT_STATS: 'chat_stats',

  // 用户相关
  USER_PROFILE: 'user_profile',
  USER_PREFERENCES: 'user_preferences',

  // 新闻相关
  NEWS_LIST: 'news_list',

  // 其他
  EMBEDDING_CONFIG: 'embedding_config',
  KNOWLEDGE_GRAPH: 'knowledge_graph_',
}

/**
 * 检查缓存版本，版本不匹配时清除所有缓存
 */
function checkVersion(): void {
  try {
    const storedVersion = localStorage.getItem(`${STORAGE_PREFIX}${VERSION_KEY}`)
    if (storedVersion !== CACHE_VERSION) {
      clearAllCache()
      localStorage.setItem(`${STORAGE_PREFIX}${VERSION_KEY}`, CACHE_VERSION)
    }
  } catch (error) {
    console.error('Failed to check cache version:', error)
  }
}

/**
 * 清除所有缓存（保留 token 和 user 等关键数据）
 */
function clearAllCache(): void {
  try {
    const keys = Object.keys(localStorage)
    const preserveKeys = ['token', 'user', 'theme']
    keys.forEach(key => {
      if (key.startsWith(STORAGE_PREFIX)) {
        const rawKey = key.replace(STORAGE_PREFIX, '')
        if (!preserveKeys.includes(rawKey)) {
          localStorage.removeItem(key)
        }
      }
    })
    debug.log('[Cache] Cache cleared due to version mismatch')
  } catch (error) {
    console.error('Failed to clear cache:', error)
  }
}

/**
 * 检查缓存条目是否过期
 */
function isExpired<T>(entry: CacheEntry<T>): boolean {
  return Date.now() - entry.timestamp > entry.ttl
}

// 初始化时检查版本
if (typeof window !== 'undefined') {
  checkVersion()
}

export const cache = {
  /**
   * 获取缓存数据
   * @param key 缓存键
   * @returns 缓存数据，如果不存在或已过期则返回 null
   */
  get<T = any>(key: string): T | null {
    try {
      const item = localStorage.getItem(`${STORAGE_PREFIX}${key}`)
      if (!item) return null

      const entry: CacheEntry<T> = JSON.parse(item)

      // 检查版本
      if (entry.version !== CACHE_VERSION) {
        this.remove(key)
        return null
      }

      // 检查过期
      if (isExpired(entry)) {
        this.remove(key)
        return null
      }

      return entry.data
    } catch (error) {
      console.error('[Cache] Failed to get:', key, error)
      return null
    }
  },

  /**
   * 设置缓存数据
   * @param key 缓存键
   * @param value 缓存值
   * @param options 缓存选项
   */
  set<T = any>(key: string, value: T, options: CacheOptions = {}): void {
    try {
      const entry: CacheEntry<T> = {
        data: value,
        timestamp: Date.now(),
        ttl: options.ttl ?? CacheTTL.MEDIUM,
        version: CACHE_VERSION,
        etag: options.etag,
      }
      localStorage.setItem(`${STORAGE_PREFIX}${key}`, JSON.stringify(entry))
    } catch (error) {
      console.error('[Cache] Failed to set:', key, error)
      // 如果存储失败（可能是空间不足），尝试清理过期缓存后重试
      this.cleanExpired()
      try {
        const entry: CacheEntry<T> = {
          data: value,
          timestamp: Date.now(),
          ttl: options.ttl ?? CacheTTL.MEDIUM,
          version: CACHE_VERSION,
          etag: options.etag,
        }
        localStorage.setItem(`${STORAGE_PREFIX}${key}`, JSON.stringify(entry))
      } catch (retryError) {
        console.error('[Cache] Retry failed:', key, retryError)
      }
    }
  },

  /**
   * 移除缓存
   */
  remove(key: string): void {
    try {
      localStorage.removeItem(`${STORAGE_PREFIX}${key}`)
    } catch (error) {
      console.error('[Cache] Failed to remove:', key, error)
    }
  },

  /**
   * 检查缓存是否存在且有效
   */
  has(key: string): boolean {
    return this.get(key) !== null
  },

  /**
   * 获取缓存元数据
   */
  getMeta<T = any>(key: string): { exists: boolean; expired: boolean; age: number; remaining: number } {
    try {
      const item = localStorage.getItem(`${STORAGE_PREFIX}${key}`)
      if (!item) {
        return { exists: false, expired: true, age: 0, remaining: 0 }
      }

      const entry: CacheEntry<T> = JSON.parse(item)
      const age = Date.now() - entry.timestamp
      const remaining = Math.max(0, entry.ttl - age)

      return {
        exists: true,
        expired: isExpired(entry),
        age,
        remaining,
      }
    } catch {
      return { exists: false, expired: true, age: 0, remaining: 0 }
    }
  },

  /**
   * 清理所有过期缓存
   */
  cleanExpired(): number {
    let cleaned = 0
    try {
      const keys = Object.keys(localStorage)
      keys.forEach(key => {
        if (key.startsWith(STORAGE_PREFIX)) {
          try {
            const item = localStorage.getItem(key)
            if (item) {
              const entry = JSON.parse(item)
              if (entry.timestamp && entry.ttl && isExpired(entry)) {
                localStorage.removeItem(key)
                cleaned++
              }
            }
          } catch {
            // 解析失败，可能是非缓存数据，跳过
          }
        }
      })
      if (cleaned > 0) {
        debug.log(`[Cache] Cleaned ${cleaned} expired entries`)
      }
    } catch (error) {
      console.error('[Cache] Failed to clean expired:', error)
    }
    return cleaned
  },

  /**
   * 清理指定前缀的所有缓存
   */
  clearByPrefix(prefix: string): number {
    let cleaned = 0
    try {
      const keys = Object.keys(localStorage)
      keys.forEach(key => {
        if (key.startsWith(`${STORAGE_PREFIX}${prefix}`)) {
          localStorage.removeItem(key)
          cleaned++
        }
      })
    } catch (error) {
      console.error('[Cache] Failed to clear by prefix:', prefix, error)
    }
    return cleaned
  },

  /**
   * 获取缓存统计信息
   */
  getStats(): { totalKeys: number; cacheKeys: number; estimatedSize: string } {
    let cacheKeys = 0
    let totalSize = 0
    try {
      const keys = Object.keys(localStorage)
      keys.forEach(key => {
        if (key.startsWith(STORAGE_PREFIX)) {
          cacheKeys++
          const item = localStorage.getItem(key)
          if (item) {
            totalSize += item.length * 2 // 大约字节数
          }
        }
      })
    } catch (error) {
      console.error('[Cache] Failed to get stats:', error)
    }

    return {
      totalKeys: Object.keys(localStorage).length,
      cacheKeys,
      estimatedSize: `${(totalSize / 1024).toFixed(2)} KB`,
    }
  },

  /**
   * 获取或设置缓存（便捷方法）
   * 如果缓存存在且有效，返回缓存数据
   * 否则执行 fetcher 函数获取数据并缓存
   */
  async getOrSet<T>(
    key: string,
    fetcher: () => Promise<T>,
    options: CacheOptions = {}
  ): Promise<T> {
    const cached = this.get<T>(key)
    if (cached !== null) {
      return cached
    }

    const data = await fetcher()
    this.set(key, data, options)
    return data
  },
}

// 会话级缓存（页面刷新后清除）
export const sessionCache = {
  get<T = any>(key: string): T | null {
    try {
      const item = sessionStorage.getItem(`${STORAGE_PREFIX}${key}`)
      if (!item) return null
      return JSON.parse(item)
    } catch {
      return null
    }
  },

  set<T = any>(key: string, value: T): void {
    try {
      sessionStorage.setItem(`${STORAGE_PREFIX}${key}`, JSON.stringify(value))
    } catch (error) {
      console.error('[SessionCache] Failed to set:', key, error)
    }
  },

  remove(key: string): void {
    try {
      sessionStorage.removeItem(`${STORAGE_PREFIX}${key}`)
    } catch {
      // ignore
    }
  },

  clear(): void {
    try {
      const keys = Object.keys(sessionStorage)
      keys.forEach(key => {
        if (key.startsWith(STORAGE_PREFIX)) {
          sessionStorage.removeItem(key)
        }
      })
    } catch {
      // ignore
    }
  },
}

// 导出便捷方法
export const storage = {
  get: cache.get.bind(cache),
  set: cache.set.bind(cache),
  remove: cache.remove.bind(cache),
  clear: clearAllCache,
}

export const session = {
  get: sessionCache.get.bind(sessionCache),
  set: sessionCache.set.bind(sessionCache),
  remove: sessionCache.remove.bind(sessionCache),
  clear: sessionCache.clear.bind(sessionCache),
}

// 定期清理过期缓存（每 5 分钟）
if (typeof window !== 'undefined') {
  setInterval(() => {
    cache.cleanExpired()
  }, 5 * 60 * 1000)
}
