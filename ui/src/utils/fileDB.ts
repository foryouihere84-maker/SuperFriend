/**
 * 文件存储服务 - 使用 IndexedDB + Blob 存储
 * 改进：
 * 1. 使用 Blob 存储替代 Base64，减少 33% 体积
 * 2. 添加 TTL 过期时间，支持自动清理
 * 3. 添加 lastAccessedAt 跟踪访问时间
 * 4. 添加存储配额管理
 * 5. 添加自动清理机制
 */

const DB_NAME = 'SuperFriendFiles'
const DB_VERSION = 2  // 升级版本号
const STORE_NAME = 'files'

// 默认配置
const DEFAULT_TTL_MS = 7 * 24 * 60 * 60 * 1000  // 7 天
const MAX_STORAGE_BYTES = 200 * 1024 * 1024     // 200 MB
const CLEANUP_INTERVAL_MS = 60 * 60 * 1000      // 1 小时

export type FileType =
  // Office文档
  | 'pdf' | 'docx' | 'xlsx' | 'pptx'
  // 文本文件
  | 'md' | 'txt'
  // 媒体文件
  | 'image' | 'audio' | 'video'
  // 其他类型
  | 'archive' | 'text' | 'other'

export interface StoredFile {
  id: string
  sessionId: string
  fileName: string
  fileType: FileType
  blob?: Blob              // Blob 存储（推荐）
  dataUrl?: string         // Base64 存储（兼容旧数据）
  size: number             // 原始文件大小（字节）
  createdAt: number
  lastAccessedAt: number   // 最后访问时间
  expiresAt: number        // 过期时间戳
  metadata?: {
    title?: string
    author?: string
    fileSize?: number
    generationTimeMs?: number
    mimeType?: string
  }
}

export interface StorageStats {
  count: number
  totalSize: number
  formattedSize: string
  usagePercent: number
  byType: Record<string, { count: number; size: number }>
  oldestFile?: { id: string; fileName: string; createdAt: number }
  expiredCount: number
}

class FileDatabase {
  private db: IDBDatabase | null = null
  private initPromise: Promise<void> | null = null
  private cleanupTimer: number | null = null

  async init(): Promise<void> {
    if (this.db) return
    if (this.initPromise) return this.initPromise

    this.initPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION)

      request.onerror = () => {
        console.error('[FileDB] 打开数据库失败:', request.error)
        reject(request.error)
      }

      request.onsuccess = () => {
        this.db = request.result
        console.log('[FileDB] 数据库连接成功')
        this.startAutoCleanup()
        resolve()
      }

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result
        const transaction = (event.target as IDBOpenDBRequest).transaction!

        if (!db.objectStoreNames.contains(STORE_NAME)) {
          const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' })
          store.createIndex('sessionId', 'sessionId', { unique: false })
          store.createIndex('fileType', 'fileType', { unique: false })
          store.createIndex('createdAt', 'createdAt', { unique: false })
          store.createIndex('expiresAt', 'expiresAt', { unique: false })
          console.log('[FileDB] 创建对象存储成功')
        } else {
          // 升级现有存储，添加新索引
          const store = transaction.objectStore(STORE_NAME)
          if (!store.indexNames.contains('expiresAt')) {
            store.createIndex('expiresAt', 'expiresAt', { unique: false })
            console.log('[FileDB] 添加 expiresAt 索引')
          }
          if (!store.indexNames.contains('lastAccessedAt')) {
            store.createIndex('lastAccessedAt', 'lastAccessedAt', { unique: false })
            console.log('[FileDB] 添加 lastAccessedAt 索引')
          }
        }
      }
    })

    return this.initPromise
  }

  /**
   * 保存文件（推荐使用 Blob）
   */
  async save(file: Omit<StoredFile, 'createdAt' | 'lastAccessedAt' | 'expiresAt' | 'size'> & {
    size?: number
    ttlMs?: number
  }): Promise<void> {
    await this.init()

    // 计算文件大小
    let size = file.size || 0
    if (file.blob) {
      size = file.blob.size
    } else if (file.dataUrl) {
      size = Math.floor(file.dataUrl.length * 0.75)  // Base64 解码后大小
    }

    // 检查存储配额
    const stats = await this.getStats()
    if (stats.totalSize + size > MAX_STORAGE_BYTES) {
      // 尝试清理过期文件
      await this.cleanupExpired()

      // 再次检查
      const newStats = await this.getStats()
      if (newStats.totalSize + size > MAX_STORAGE_BYTES) {
        throw new Error(`存储空间不足。当前已使用 ${newStats.formattedSize}，最大限制 ${formatBytes(MAX_STORAGE_BYTES)}`)
      }
    }

    const now = Date.now()
    const ttlMs = file.ttlMs || DEFAULT_TTL_MS

    const storedFile: StoredFile = {
      ...file,
      size,
      createdAt: now,
      lastAccessedAt: now,
      expiresAt: now + ttlMs
    }

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.put(storedFile)

      request.onsuccess = () => {
        console.log('[FileDB] 文件保存成功:', file.id, formatBytes(size))
        resolve()
      }

      request.onerror = () => {
        console.error('[FileDB] 保存失败:', request.error)
        reject(request.error)
      }
    })
  }

  /**
   * 获取文件（自动更新访问时间）
   */
  async get(id: string, updateAccessTime: boolean = true): Promise<StoredFile | null> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.get(id)

      request.onsuccess = async () => {
        const file = request.result || null

        if (file && updateAccessTime) {
          // 更新访问时间
          file.lastAccessedAt = Date.now()
          store.put(file)
        }

        resolve(file)
      }
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 获取文件的 Blob URL（用于预览/下载）
   */
  async getBlobUrl(id: string): Promise<string | null> {
    const file = await this.get(id)
    if (!file) return null

    if (file.blob) {
      return URL.createObjectURL(file.blob)
    } else if (file.dataUrl) {
      // 兼容旧数据：将 Base64 转换为 Blob
      const blob = await this.dataUrlToBlob(file.dataUrl)
      return URL.createObjectURL(blob)
    }
    return null
  }

  /**
   * 获取文件的 Data URL（兼容旧代码）
   */
  async getDataUrl(id: string): Promise<string | null> {
    const file = await this.get(id)
    if (!file) return null

    if (file.dataUrl) {
      return file.dataUrl
    } else if (file.blob) {
      // 将 Blob 转换为 Base64
      return await this.blobToDataUrl(file.blob)
    }
    return null
  }

  /**
   * 按会话获取文件
   */
  async getBySession(sessionId: string): Promise<StoredFile[]> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readonly')
      const store = transaction.objectStore(STORE_NAME)
      const index = store.index('sessionId')
      const request = index.getAll(sessionId)

      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 按类型获取文件
   */
  async getByType(fileType: string): Promise<StoredFile[]> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readonly')
      const store = transaction.objectStore(STORE_NAME)
      const index = store.index('fileType')
      const request = index.getAll(fileType)

      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 获取所有文件
   */
  async getAll(): Promise<StoredFile[]> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readonly')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.getAll()

      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 删除文件
   */
  async delete(id: string): Promise<void> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.delete(id)

      request.onsuccess = () => {
        console.log('[FileDB] 文件已删除:', id)
        resolve()
      }
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 删除会话的所有文件
   */
  async deleteBySession(sessionId: string): Promise<void> {
    const files = await this.getBySession(sessionId)

    if (files.length === 0) return

    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)

      let completed = 0
      let hasError = false

      files.forEach(file => {
        const request = store.delete(file.id)
        request.onsuccess = () => {
          completed++
          if (completed === files.length && !hasError) {
            console.log('[FileDB] 会话文件已删除:', sessionId, `(${files.length} 个文件)`)
            resolve()
          }
        }
        request.onerror = () => {
          hasError = true
          reject(request.error)
        }
      })
    })
  }

  /**
   * 清理过期文件
   */
  async cleanupExpired(): Promise<number> {
    await this.init()

    const now = Date.now()
    const files = await this.getAll()
    const expiredFiles = files.filter(f => f.expiresAt < now)

    if (expiredFiles.length === 0) {
      return 0
    }

    console.log(`[FileDB] 发现 ${expiredFiles.length} 个过期文件，正在清理...`)

    for (const file of expiredFiles) {
      await this.delete(file.id)
    }

    console.log(`[FileDB] 已清理 ${expiredFiles.length} 个过期文件`)
    return expiredFiles.length
  }

  /**
   * 清理长时间未访问的文件
   */
  async cleanupInactive(daysInactive: number = 30): Promise<number> {
    await this.init()

    const threshold = Date.now() - daysInactive * 24 * 60 * 60 * 1000
    const files = await this.getAll()
    const inactiveFiles = files.filter(f => f.lastAccessedAt < threshold)

    if (inactiveFiles.length === 0) {
      return 0
    }

    console.log(`[FileDB] 发现 ${inactiveFiles.length} 个 ${daysInactive} 天未访问的文件，正在清理...`)

    for (const file of inactiveFiles) {
      await this.delete(file.id)
    }

    return inactiveFiles.length
  }

  /**
   * 清空所有文件
   */
  async clear(): Promise<void> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.clear()

      request.onsuccess = () => {
        console.log('[FileDB] 所有文件已清除')
        resolve()
      }
      request.onerror = () => reject(request.error)
    })
  }

  /**
   * 获取存储统计信息
   */
  async getStats(): Promise<StorageStats> {
    const files = await this.getAll()
    const now = Date.now()

    let totalSize = 0
    const byType: Record<string, { count: number; size: number }> = {}
    let oldestFile: { id: string; fileName: string; createdAt: number } | undefined
    let expiredCount = 0

    files.forEach(file => {
      totalSize += file.size || 0

      // 按类型统计
      if (!byType[file.fileType]) {
        byType[file.fileType] = { count: 0, size: 0 }
      }
      byType[file.fileType].count++
      byType[file.fileType].size += file.size || 0

      // 最老文件
      if (!oldestFile || file.createdAt < oldestFile.createdAt) {
        oldestFile = { id: file.id, fileName: file.fileName, createdAt: file.createdAt }
      }

      // 过期文件
      if (file.expiresAt < now) {
        expiredCount++
      }
    })

    return {
      count: files.length,
      totalSize,
      formattedSize: formatBytes(totalSize),
      usagePercent: Math.round((totalSize / MAX_STORAGE_BYTES) * 100),
      byType,
      oldestFile,
      expiredCount
    }
  }

  /**
   * 下载文件
   */
  async download(id: string): Promise<void> {
    const file = await this.get(id)
    if (!file) {
      throw new Error('文件不存在')
    }

    let blob: Blob
    if (file.blob) {
      blob = file.blob
    } else if (file.dataUrl) {
      blob = await this.dataUrlToBlob(file.dataUrl)
    } else {
      throw new Error('文件数据不存在')
    }

    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  /**
   * 获取 Blob 对象
   */
  async getBlob(id: string): Promise<Blob | null> {
    const file = await this.get(id)
    if (!file) return null

    if (file.blob) {
      return file.blob
    } else if (file.dataUrl) {
      return await this.dataUrlToBlob(file.dataUrl)
    }
    return null
  }

  /**
   * 启动自动清理
   */
  private startAutoCleanup(): void {
    // 立即执行一次清理
    this.cleanupExpired().catch(err => {
      console.warn('[FileDB] 自动清理失败:', err)
    })

    // 定时清理
    this.cleanupTimer = window.setInterval(() => {
      this.cleanupExpired().catch(err => {
        console.warn('[FileDB] 自动清理失败:', err)
      })
    }, CLEANUP_INTERVAL_MS)

    console.log('[FileDB] 自动清理已启动，间隔:', CLEANUP_INTERVAL_MS / 1000 / 60, '分钟')
  }

  /**
   * 停止自动清理
   */
  stopAutoCleanup(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer)
      this.cleanupTimer = null
    }
  }

  /**
   * Base64 转 Blob
   */
  private dataUrlToBlob(dataUrl: string): Promise<Blob> {
    return new Promise((resolve) => {
      const arr = dataUrl.split(',')
      const mime = arr[0].match(/:(.*?);/)?.[1] || 'application/octet-stream'
      const bstr = atob(arr[1])
      let n = bstr.length
      const u8arr = new Uint8Array(n)
      while (n--) {
        u8arr[n] = bstr.charCodeAt(n)
      }
      resolve(new Blob([u8arr], { type: mime }))
    })
  }

  /**
   * Blob 转 Base64
   */
  private blobToDataUrl(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onloadend = () => resolve(reader.result as string)
      reader.onerror = reject
      reader.readAsDataURL(blob)
    })
  }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

export const fileDB = new FileDatabase()
