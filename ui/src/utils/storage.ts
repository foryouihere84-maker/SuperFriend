/**
 * @deprecated 请使用 './cache' 模块中的 cache 和 session
 * 此文件仅为向后兼容保留
 */

import { cache, sessionCache } from './cache'
import { STORAGE_PREFIX } from '@/config/app'

// 重新导出以保持向后兼容
export const storage = {
  get: cache.get.bind(cache),
  set: cache.set.bind(cache),
  remove: cache.remove.bind(cache),
  clear: () => {
    const keys = Object.keys(localStorage)
    keys.forEach(key => {
      // 兼容新旧前缀
      if (key.startsWith(STORAGE_PREFIX) || key.startsWith('harmonynotes_')) {
        localStorage.removeItem(key)
      }
    })
  }
}

export const session = {
  get: sessionCache.get.bind(sessionCache),
  set: sessionCache.set.bind(sessionCache),
  remove: sessionCache.remove.bind(sessionCache),
  clear: sessionCache.clear.bind(sessionCache)
}
