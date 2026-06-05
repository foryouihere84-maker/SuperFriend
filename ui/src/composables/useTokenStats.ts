/**
 * Token 统计 composable
 * 提供本地 Token 使用统计数据的访问和更新
 */

import { ref, computed } from 'vue'
import { tokenStatsDB, type CostEvent, type DailyStats, type ModelStats } from '@/utils/tokenStatsDB'

export function useTokenStats() {
  const loading = ref(false)
  const events = ref<CostEvent[]>([])
  const dailyStats = ref<DailyStats[]>([])
  const modelStats = ref<ModelStats[]>([])

  const totalStats = computed(() => {
    return events.value.reduce(
      (acc, event) => ({
        totalApiCalls: acc.totalApiCalls + (event.apiCallCount || 1),
        totalTokens: acc.totalTokens + event.totalTokens,
        totalCost: acc.totalCost + event.cost
      }),
      { totalApiCalls: 0, totalTokens: 0, totalCost: 0 }
    )
  })

  const recentSessions = computed(() => {
    const sessionMap = new Map<string, CostEvent[]>()

    for (const event of events.value) {
      const sessionId = event.sessionId
      if (!sessionMap.has(sessionId)) {
        sessionMap.set(sessionId, [])
      }
      sessionMap.get(sessionId)!.push(event)
    }

    return Array.from(sessionMap.entries())
      .map(([sessionId, sessionEvents]) => {
        const sorted = sessionEvents.sort((a, b) => a.timestamp - b.timestamp)
        const latest = sorted[sorted.length - 1]
        const useCumulative = latest?.sessionTotalCost != null
        return {
          sessionId,
          model: sorted[0]?.modelId || 'unknown',
          totalTokens: useCumulative ? (latest.sessionTotalTokens || 0) : sorted.reduce((sum, e) => sum + e.totalTokens, 0),
          totalCost: useCumulative ? (latest.sessionTotalCost || 0) : sorted.reduce((sum, e) => sum + e.cost, 0),
          apiCallCount: sorted.reduce((sum, e) => sum + (e.apiCallCount || 1), 0),
          toolCallCount: sorted.reduce((sum, e) => sum + (e.toolCallCount || 0), 0)
        }
      })
      .sort((a, b) => b.totalCost - a.totalCost)
      .slice(0, 10)
  })

  const filteredDailyStats = computed(() => {
    return dailyStats.value.slice(-30)
  })

  const loadStats = async (days: number = 30) => {
    loading.value = true
    try {
      const [allEvents, daily, models] = await Promise.all([
        tokenStatsDB.getAllEvents(),
        tokenStatsDB.getDailyStats(days),
        tokenStatsDB.getModelStats()
      ])

      events.value = allEvents.sort((a, b) => b.timestamp - a.timestamp)
      dailyStats.value = daily
      modelStats.value = models
    } catch (error) {
      console.error('[useTokenStats] 加载统计数据失败:', error)
    } finally {
      loading.value = false
    }
  }

  const recordEvent = async (event: {
    sessionId: string
    modelId: string
    inputTokens: number
    outputTokens: number
    cost: number
    apiCallCount?: number
    toolCallCount?: number
    sessionTotalCost?: number
    sessionTotalTokens?: number
  }) => {
    const fullEvent: Omit<CostEvent, 'id'> = {
      timestamp: Date.now(),
      sessionId: event.sessionId,
      modelId: event.modelId,
      inputTokens: event.inputTokens,
      outputTokens: event.outputTokens,
      totalTokens: event.inputTokens + event.outputTokens,
      cost: event.cost,
      apiCallCount: event.apiCallCount || 1,
      toolCallCount: event.toolCallCount || 0,
      sessionTotalCost: event.sessionTotalCost,
      sessionTotalTokens: event.sessionTotalTokens
    }

    await tokenStatsDB.addEvent(fullEvent)

    events.value = [fullEvent as CostEvent, ...events.value]

    const date = new Date().toISOString().split('T')[0]
    const existingDay = dailyStats.value.find(d => d.date === date)

    if (existingDay) {
      existingDay.totalApiCalls += fullEvent.apiCallCount || 1
      existingDay.totalInputTokens += fullEvent.inputTokens
      existingDay.totalOutputTokens += fullEvent.outputTokens
      existingDay.totalTokens += fullEvent.totalTokens
      existingDay.totalCost += fullEvent.cost
      existingDay.modelCosts[event.modelId] = (existingDay.modelCosts[event.modelId] || 0) + fullEvent.cost
    } else {
      dailyStats.value.push({
        date,
        totalApiCalls: fullEvent.apiCallCount || 1,
        totalInputTokens: fullEvent.inputTokens,
        totalOutputTokens: fullEvent.outputTokens,
        totalTokens: fullEvent.totalTokens,
        totalCost: fullEvent.cost,
        modelCosts: { [event.modelId]: fullEvent.cost }
      })
    }

    const existingModel = modelStats.value.find(m => m.modelId === event.modelId)
    if (existingModel) {
      existingModel.totalCalls += fullEvent.apiCallCount || 1
      existingModel.totalInputTokens += fullEvent.inputTokens
      existingModel.totalOutputTokens += fullEvent.outputTokens
      existingModel.totalTokens += fullEvent.totalTokens
      existingModel.totalCost += fullEvent.cost
    } else {
      modelStats.value.push({
        modelId: event.modelId,
        totalCalls: fullEvent.apiCallCount || 1,
        totalInputTokens: fullEvent.inputTokens,
        totalOutputTokens: fullEvent.outputTokens,
        totalTokens: fullEvent.totalTokens,
        totalCost: fullEvent.cost
      })
    }
  }

  const clearStats = async () => {
    await tokenStatsDB.clear()
    events.value = []
    dailyStats.value = []
    modelStats.value = []
  }

  return {
    loading,
    events,
    dailyStats,
    modelStats,
    totalStats,
    recentSessions,
    filteredDailyStats,
    loadStats,
    recordEvent,
    clearStats
  }
}
