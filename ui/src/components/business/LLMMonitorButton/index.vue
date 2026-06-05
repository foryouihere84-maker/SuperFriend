<template>
  <button
    class="llm-monitor-btn"
    @click="handleOpen"
    :disabled="!sessionId"
    :title="title"
  >
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </svg>
    <span class="btn-label">监控</span>
    <span v-if="recordCount && recordCount > 0" class="btn-badge">{{ recordCount }}</span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useLLMMonitorStore } from '@/stores/llmMonitor'

const props = defineProps<{
  sessionId: string
  recordCount?: number
}>()

const llmMonitorStore = useLLMMonitorStore()

const title = computed(() => {
  if (!props.sessionId) return '无会话 ID'
  return `查看 LLM 调用详情${props.recordCount ? ` (${props.recordCount} 条)` : ''}`
})

const handleOpen = () => {
  llmMonitorStore.openPanel(props.sessionId)
}
</script>

<style scoped lang="scss">
.llm-monitor-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--sf-bg-gray-100, #f5f5f5);
  border: 1px solid var(--sf-border-light, #f0f0f0);
  border-radius: var(--sf-radius-md, 8px);
  color: var(--sf-text-secondary, #666);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;

  svg {
    width: 16px;
    height: 16px;
    flex-shrink: 0;
  }

  .btn-label {
    white-space: nowrap;
  }

  .btn-badge {
    padding: 2px 6px;
    background: var(--sf-accent, #1a1a1a);
    color: white;
    border-radius: var(--sf-radius-full, 9999px);
    font-size: 11px;
    font-weight: 600;
    min-width: 18px;
    text-align: center;
  }

  &:hover:not(:disabled) {
    background: var(--sf-bg-gray-200, #e5e5e5);
    border-color: var(--sf-accent, #1a1a1a);
    color: var(--sf-accent, #1a1a1a);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}
</style>
