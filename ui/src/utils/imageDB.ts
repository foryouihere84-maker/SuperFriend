/**
 * 图片存储服务 - 使用 IndexedDB
 * 用于持久化存储 AI 生成的图片
 */

const DB_NAME = 'SuperFriendImages'
const DB_VERSION = 1
const STORE_NAME = 'images'

export interface StoredImage {
  id: string
  sessionId: string
  prompt: string
  dataUrl: string
  createdAt: number
  metadata?: {
    steps?: number
    cfgScale?: number
    sampler?: string
    generationTimeMs?: number
  }
}

class ImageDatabase {
  private db: IDBDatabase | null = null
  private initPromise: Promise<void> | null = null

  async init(): Promise<void> {
    if (this.db) return
    if (this.initPromise) return this.initPromise

    this.initPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION)

      request.onerror = () => {
        console.error('[ImageDB] 打开数据库失败:', request.error)
        reject(request.error)
      }

      request.onsuccess = () => {
        this.db = request.result
        console.log('[ImageDB] 数据库连接成功')
        resolve()
      }

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result

        if (!db.objectStoreNames.contains(STORE_NAME)) {
          const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' })
          store.createIndex('sessionId', 'sessionId', { unique: false })
          store.createIndex('createdAt', 'createdAt', { unique: false })
          console.log('[ImageDB] 创建对象存储成功')
        }
      }
    })

    return this.initPromise
  }

  async save(image: StoredImage): Promise<void> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.put(image)

      request.onsuccess = () => {
        console.log('[ImageDB] 图片保存成功:', image.id)
        resolve()
      }

      request.onerror = () => {
        console.error('[ImageDB] 保存失败:', request.error)
        reject(request.error)
      }
    })
  }

  async get(id: string): Promise<StoredImage | null> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readonly')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.get(id)

      request.onsuccess = () => resolve(request.result || null)
      request.onerror = () => reject(request.error)
    })
  }

  async getBySession(sessionId: string): Promise<StoredImage[]> {
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

  async getAll(): Promise<StoredImage[]> {
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
        console.log('[ImageDB] 图片已删除:', id)
        resolve()
      }
      request.onerror = () => reject(request.error)
    })
  }

  async deleteBySession(sessionId: string): Promise<void> {
    const images = await this.getBySession(sessionId)

    if (images.length === 0) return

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

      images.forEach(img => {
        const request = store.delete(img.id)
        request.onsuccess = () => {
          completed++
          if (completed === images.length && !hasError) {
            console.log('[ImageDB] 会话图片已删除:', sessionId)
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
        console.log('[ImageDB] 所有图片已清除')
        resolve()
      }
      request.onerror = () => reject(request.error)
    })
  }

  async getStats(): Promise<{ count: number; estimatedSize: string }> {
    const images = await this.getAll()
    let totalSize = 0

    images.forEach(img => {
      if (img.dataUrl) {
        totalSize += img.dataUrl.length * 0.75
      }
    })

    return {
      count: images.length,
      estimatedSize: formatBytes(totalSize)
    }
  }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

export const imageDB = new ImageDatabase()
