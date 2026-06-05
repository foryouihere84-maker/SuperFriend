import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LLMCallRecord } from '@/types/llmMonitor'
import { getSessionLLMCalls, getLLMCallDetail } from '@/api/llmMonitor'

export const useLLMMonitorStore = defineStore('llmMonitor', () => {
  const visible = ref(false)
  const records = ref<LLMCallRecord[]>([])
  const currentDetail = ref<LLMCallRecord | null>(null)
  const loading = ref(false)
  const currentSessionId = ref('')

  /**
   * 打开监控面板
   */
  const openPanel = async (sessionId: string) => {
    console.log('[LLMMonitor] openPanel called with sessionId:', sessionId)
    visible.value = true
    currentSessionId.value = sessionId
    loading.value = true

    try {
      console.log('[LLMMonitor] fetching records for sessionId:', sessionId)
      const result = await getSessionLLMCalls(sessionId)
      console.log('[LLMMonitor] received records:', result)
      records.value = result
    } catch (error) {
      console.error('[LLMMonitor] 获取 LLM 调用记录失败:', error)
      records.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 关闭监控面板
   */
  const closePanel = () => {
    visible.value = false
    currentDetail.value = null
  }

  /**
   * 刷新记录
   */
  const refreshRecords = async () => {
    if (!currentSessionId.value) return
    loading.value = true
    try {
      records.value = await getSessionLLMCalls(currentSessionId.value)
    } catch (error) {
      console.error('刷新 LLM 调用记录失败:', error)
    } finally {
      loading.value = false
    }
  }

  /**
   * 查看详情
   */
  const viewDetail = async (callId: string) => {
    loading.value = true
    try {
      currentDetail.value = await getLLMCallDetail(callId, currentSessionId.value)
    } catch (error) {
      console.error('获取详情失败:', error)
    } finally {
      loading.value = false
    }
  }

  /**
   * 清除详情
   */
  const clearDetail = () => {
    currentDetail.value = null
  }

  /**
   * 增加记录计数（用于实时更新徽章）
   */
  const incrementRecordCount = () => {
    // 刷新记录以获取最新数据
    if (currentSessionId.value && visible.value) {
      refreshRecords()
    }
  }

  return {
    visible,
    records,
    currentDetail,
    loading,
    currentSessionId,
    openPanel,
    closePanel,
    refreshRecords,
    viewDetail,
    clearDetail,
    incrementRecordCount
  }
})
