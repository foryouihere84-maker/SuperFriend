/**
 * Token 统计本地存储 - 使用 IndexedDB
 * 用于持久化存储用户的 Token 使用统计数据
 */

const DB_NAME = 'SuperFriendTokenStats'
const DB_VERSION = 1
const STORE_NAME = 'tokenStats'

export interface CostEvent {
  id?: number
  timestamp: number
  sessionId: string
  modelId: string
  inputTokens: number
  outputTokens: number
  totalTokens: number
  cost: number
  apiCallCount: number
  toolCallCount: number
  sessionTotalCost?: number
  sessionTotalTokens?: number
}

export interface DailyStats {
  date: string
  totalApiCalls: number
  totalInputTokens: number
  totalOutputTokens: number
  totalTokens: number
  totalCost: number
  modelCosts: Record<string, number>
}

export interface ModelStats {
  modelId: string
  totalCalls: number
  totalInputTokens: number
  totalOutputTokens: number
  totalTokens: number
  totalCost: number
}

class TokenStatsDatabase {
  private db: IDBDatabase | null = null
  private initPromise: Promise<void> | null = null

  async init(): Promise<void> {
    if (this.db) return
    if (this.initPromise) return this.initPromise

    this.initPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION)

      request.onerror = () => {
        console.error('[TokenStatsDB] 打开数据库失败:', request.error)
        reject(request.error)
      }

      request.onsuccess = () => {
        this.db = request.result
        console.log('[TokenStatsDB] 数据库连接成功')
        resolve()
      }

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result

        if (!db.objectStoreNames.contains(STORE_NAME)) {
          const store = db.createObjectStore(STORE_NAME, { keyPath: 'id', autoIncrement: true })
          store.createIndex('timestamp', 'timestamp', { unique: false })
          store.createIndex('sessionId', 'sessionId', { unique: false })
          store.createIndex('modelId', 'modelId', { unique: false })
          store.createIndex('date', 'date', { unique: false })
          console.log('[TokenStatsDB] 创建对象存储成功')
        }
      }
    })

    return this.initPromise
  }

  async addEvent(event: Omit<CostEvent, 'id'>): Promise<void> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.add(event)

      request.onsuccess = () => {
        console.log('[TokenStatsDB] 添加事件成功:', event)
        resolve()
      }

      request.onerror = () => {
        console.error('[TokenStatsDB] 添加事件失败:', request.error)
        reject(request.error)
      }
    })
  }

  async getEventsByDateRange(startDate: string, endDate: string): Promise<CostEvent[]> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readonly')
      const store = transaction.objectStore(STORE_NAME)
      const index = store.index('timestamp')

      const startTimestamp = new Date(startDate).getTime()
      const endTimestamp = new Date(endDate + 'T23:59:59').getTime()

      const range = IDBKeyRange.bound(startTimestamp, endTimestamp)
      const request = index.getAll(range)

      request.onsuccess = () => {
        resolve(request.result || [])
      }

      request.onerror = () => {
        console.error('[TokenStatsDB] 获取事件失败:', request.error)
        reject(request.error)
      }
    })
  }

  async getAllEvents(): Promise<CostEvent[]> {
    await this.init()

    return new Promise((resolve, reject) => {
      if (!this.db) {
        reject(new Error('数据库未初始化'))
        return
      }

      const transaction = this.db.transaction([STORE_NAME], 'readonly')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.getAll()

      request.onsuccess = () => {
        resolve(request.result || [])
      }

      request.onerror = () => {
        console.error('[TokenStatsDB] 获取所有事件失败:', request.error)
        reject(request.error)
      }
    })
  }

  async getDailyStats(days: number = 30): Promise<DailyStats[]> {
    const events = await this.getAllEvents()
    const now = new Date()
    const startDate = new Date(now.getTime() - days * 24 * 60 * 60 * 1000)

    const dailyMap = new Map<string, DailyStats>()

    for (const event of events) {
      if (event.timestamp < startDate.getTime()) continue

      const date = new Date(event.timestamp).toISOString().split('T')[0]

      if (!dailyMap.has(date)) {
        dailyMap.set(date, {
          date,
          totalApiCalls: 0,
          totalInputTokens: 0,
          totalOutputTokens: 0,
          totalTokens: 0,
          totalCost: 0,
          modelCosts: {}
        })
      }

      const stats = dailyMap.get(date)!
      stats.totalApiCalls += event.apiCallCount || 1
      stats.totalInputTokens += event.inputTokens
      stats.totalOutputTokens += event.outputTokens
      stats.totalTokens += event.totalTokens
      stats.totalCost += event.cost
      stats.modelCosts[event.modelId] = (stats.modelCosts[event.modelId] || 0) + event.cost
    }

    return Array.from(dailyMap.values()).sort((a, b) => a.date.localeCompare(b.date))
  }

  async getModelStats(): Promise<ModelStats[]> {
    const events = await this.getAllEvents()
    const modelMap = new Map<string, ModelStats>()

    for (const event of events) {
      if (!modelMap.has(event.modelId)) {
        modelMap.set(event.modelId, {
          modelId: event.modelId,
          totalCalls: 0,
          totalInputTokens: 0,
          totalOutputTokens: 0,
          totalTokens: 0,
          totalCost: 0
        })
      }

      const stats = modelMap.get(event.modelId)!
      stats.totalCalls += event.apiCallCount || 1
      stats.totalInputTokens += event.inputTokens
      stats.totalOutputTokens += event.outputTokens
      stats.totalTokens += event.totalTokens
      stats.totalCost += event.cost
    }

    return Array.from(modelMap.values()).sort((a, b) => b.totalCost - a.totalCost)
  }

  async getTotalStats(): Promise<{
    totalApiCalls: number
    totalTokens: number
    totalCost: number
  }> {
    const events = await this.getAllEvents()

    return events.reduce(
      (acc, event) => ({
        totalApiCalls: acc.totalApiCalls + (event.apiCallCount || 1),
        totalTokens: acc.totalTokens + event.totalTokens,
        totalCost: acc.totalCost + event.cost
      }),
      { totalApiCalls: 0, totalTokens: 0, totalCost: 0 }
    )
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
        console.log('[TokenStatsDB] 清空数据成功')
        resolve()
      }

      request.onerror = () => {
        console.error('[TokenStatsDB] 清空数据失败:', request.error)
        reject(request.error)
      }
    })
  }
}

export const tokenStatsDB = new TokenStatsDatabase()
