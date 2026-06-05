<template>
  <el-drawer
    v-model="visible"
    title="LLM 调用监控"
    direction="rtl"
    size="520px"
    :before-close="handleClose"
    class="llm-monitor-drawer"
  >
    <div class="monitor-panel">
      <!-- 刷新按钮 -->
      <div class="panel-header">
        <button class="refresh-btn" @click="refreshRecords" :disabled="loading">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            :class="{ 'spin': loading }"
          >
            <path d="M1 4v6h6" />
            <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" />
          </svg>
          刷新
        </button>
        <span class="session-info" v-if="currentSessionId">
          会话: {{ currentSessionId.substring(0, 8) }}...
        </span>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading && records.length === 0" class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>

      <!-- 无记录状态 -->
      <div v-if="!loading && records.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10" />
          <path d="M12 6v6l4 2" />
        </svg>
        <span>暂无 LLM 调用记录</span>
        <p class="empty-hint">发送消息后，点击刷新查看记录</p>
      </div>

      <!-- 记录列表 -->
      <div v-if="records.length > 0" class="records-list">
        <div
          v-for="record in records"
          :key="record.callId"
          class="record-item"
          :class="{ 'record-item--error': !record.success }"
          @click="viewDetail(record.callId)"
        >
          <!-- 时间和模型 -->
          <div class="record-header">
            <span class="record-time">{{ formatTime(record.requestTime) }}</span>
            <span class="record-model">{{ record.model }}</span>
            <span :class="['record-status', record.success ? 'status--success' : 'status--error']">
              {{ record.success ? '成功' : '失败' }}
            </span>
          </div>

          <!-- 调用信息 -->
          <div class="record-info">
            <div class="info-item">
              <span class="info-label">消息:</span>
              <span class="info-value">{{ record.messages?.length || 0 }}</span>
            </div>
            <div class="info-item" v-if="record.tools?.length">
              <span class="info-label">工具:</span>
              <span class="info-value">{{ record.tools.length }}</span>
            </div>
            <div class="info-item" v-if="record.durationMs">
              <span class="info-label">耗时:</span>
              <span class="info-value">{{ formatDuration(record.durationMs) }}</span>
            </div>
          </div>

          <!-- Token 使用 -->
          <div class="record-tokens" v-if="record.totalTokens">
            <div class="token-bar">
              <div
                class="token-input"
                :style="{ width: tokenInputWidth(record) }"
                :title="`输入: ${record.promptTokens || 0}`"
              >
                {{ record.promptTokens || 0 }}
              </div>
              <div
                class="token-output"
                :style="{ width: tokenOutputWidth(record) }"
                :title="`输出: ${record.completionTokens || 0}`"
              >
                {{ record.completionTokens || 0 }}
              </div>
            </div>
            <div class="token-total">
              总计: {{ record.totalTokens }} tokens
            </div>
          </div>

          <!-- 错误信息 -->
          <div class="record-error" v-if="!record.success && record.errorMessage">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <span>{{ truncateError(record.errorMessage) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情子面板 -->
    <el-drawer
      v-model="detailVisible"
      title="调用详情"
      direction="rtl"
      size="650px"
      append-to-body
      class="llm-monitor-detail-drawer"
    >
      <div class="detail-panel" v-if="currentDetail">
        <!-- 请求详情 -->
        <div class="detail-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 2L11 13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
            <span>请求内容</span>
            <span class="section-badge">{{ currentDetail.messages?.length || 0 }} 条消息</span>
          </div>
          <div class="messages-list" v-if="currentDetail.messages?.length">
            <div v-for="(msg, idx) in currentDetail.messages" :key="idx" class="message-item">
              <div class="message-role" :class="`role--${msg.role}`">{{ msg.role }}</div>
              <div class="message-content">
                <pre>{{ formatMessageContent(msg.content) }}</pre>
              </div>
            </div>
          </div>
        </div>

        <!-- 工具定义 -->
        <div class="detail-section" v-if="currentDetail.tools?.length">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-3.77 3.77a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-3.77 3.77a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0" />
            </svg>
            <span>工具定义</span>
            <span class="section-badge">{{ currentDetail.tools.length }} 个</span>
          </div>
          <div class="tools-list">
            <div v-for="(tool, idx) in currentDetail.tools" :key="idx" class="tool-item">
              <span class="tool-name">{{ tool.function?.name }}</span>
              <span class="tool-desc">{{ tool.function?.description }}</span>
            </div>
          </div>
        </div>

        <!-- 响应详情 -->
        <div class="detail-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 11 12 14 22 4" />
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
            </svg>
            <span>响应内容</span>
            <span class="section-badge" v-if="currentDetail.totalTokens">
              {{ currentDetail.totalTokens }} tokens
            </span>
          </div>
          <div class="response-content" v-if="currentDetail.responseContent">
            <pre>{{ currentDetail.responseContent }}</pre>
          </div>
          <div class="response-empty" v-else>
            无响应内容
          </div>
        </div>

        <!-- 推理内容 -->
        <div class="detail-section" v-if="currentDetail.reasoningContent">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
              <line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
            <span>推理过程</span>
          </div>
          <div class="reasoning-content">
            <pre>{{ currentDetail.reasoningContent }}</pre>
          </div>
        </div>

        <!-- 元数据 -->
        <div class="detail-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3" />
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
            </svg>
            <span>调用元数据</span>
          </div>
          <div class="metadata-grid">
            <div class="meta-item">
              <span class="meta-key">API 地址:</span>
              <span class="meta-value">{{ currentDetail.apiUrl || '-' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-key">请求时间:</span>
              <span class="meta-value">{{ formatTime(currentDetail.requestTime) }}</span>
            </div>
            <div class="meta-item" v-if="currentDetail.responseTime">
              <span class="meta-key">响应时间:</span>
              <span class="meta-value">{{ formatTime(currentDetail.responseTime) }}</span>
            </div>
            <div class="meta-item" v-if="currentDetail.durationMs">
              <span class="meta-key">耗时:</span>
              <span class="meta-value">{{ formatDuration(currentDetail.durationMs) }}</span>
            </div>
            <div class="meta-item" v-if="currentDetail.temperature">
              <span class="meta-key">温度:</span>
              <span class="meta-value">{{ currentDetail.temperature }}</span>
            </div>
            <div class="meta-item" v-if="currentDetail.maxTokens">
              <span class="meta-key">最大Token:</span>
              <span class="meta-value">{{ currentDetail.maxTokens }}</span>
            </div>
            <div class="meta-item" v-if="currentDetail.finishReason">
              <span class="meta-key">完成原因:</span>
              <span class="meta-value">{{ currentDetail.finishReason }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useLLMMonitorStore } from '@/stores/llmMonitor'
import type { LLMCallRecord } from '@/types/llmMonitor'

const llmMonitorStore = useLLMMonitorStore()

const visible = computed({
  get: () => llmMonitorStore.visible,
  set: (v) => {
    if (!v) llmMonitorStore.closePanel()
  }
})

const detailVisible = computed({
  get: () => llmMonitorStore.currentDetail !== null,
  set: (v) => {
    if (!v) llmMonitorStore.clearDetail()
  }
})

// 使用 storeToRefs 保持响应式
const { records, loading, currentSessionId, currentDetail } = storeToRefs(llmMonitorStore)
const { refreshRecords, viewDetail } = llmMonitorStore

const handleClose = (done: () => void) => {
  llmMonitorStore.closePanel()
  done()
}

const formatTime = (time: string) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const formatDuration = (ms: number) => {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}min`
}

const tokenInputWidth = (record: LLMCallRecord) => {
  if (!record.totalTokens) return '0%'
  const width = ((record.promptTokens || 0) / record.totalTokens) * 100
  return `${Math.max(width, 10)}%`
}

const tokenOutputWidth = (record: LLMCallRecord) => {
  if (!record.totalTokens) return '0%'
  const width = ((record.completionTokens || 0) / record.totalTokens) * 100
  return `${Math.max(width, 10)}%`
}

const formatMessageContent = (content: any) => {
  if (content === null || content === undefined) return ''
  if (typeof content === 'string') return content
  return JSON.stringify(content, null, 2)
}

const truncateError = (error: string) => {
  if (error.length <= 100) return error
  return error.substring(0, 100) + '...'
}
</script>

<style scoped lang="scss">
.monitor-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--sf-border-light, #f0f0f0);
  margin-bottom: 16px;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--sf-accent, #1a1a1a);
  color: white;
  border: none;
  border-radius: var(--sf-radius-md, 8px);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;

  svg {
    width: 16px;
    height: 16px;
  }

  &:hover:not(:disabled) {
    opacity: 0.9;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .spin {
    animation: spin 1s linear infinite;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.session-info {
  color: var(--sf-text-secondary, #666);
  font-size: 12px;
  font-family: monospace;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: var(--sf-text-secondary, #666);
  gap: 12px;

  svg {
    width: 48px;
    height: 48px;
    opacity: 0.5;
  }
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--sf-border-default, #e5e5e5);
  border-top-color: var(--sf-accent, #1a1a1a);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.empty-hint {
  font-size: 12px;
  opacity: 0.7;
  margin: 0;
}

.records-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-item {
  padding: 16px;
  background: var(--sf-surface, #fff);
  border: 1px solid var(--sf-border-light, #f0f0f0);
  border-radius: var(--sf-radius-md, 8px);
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: var(--sf-accent, #1a1a1a);
    box-shadow: var(--sf-shadow-sm, 0 1px 2px rgba(0, 0, 0, 0.04));
  }

  &--error {
    border-color: var(--sf-error, #ef4444);
    background: rgba(239, 68, 68, 0.05);
  }
}

.record-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.record-time {
  font-size: 12px;
  color: var(--sf-text-secondary, #666);
  font-family: monospace;
}

.record-model {
  font-size: 13px;
  font-weight: 500;
  color: var(--sf-text-primary, #1a1a1a);
}

.record-status {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: var(--sf-radius-full, 9999px);
  font-size: 11px;
  font-weight: 500;

  &.status--success {
    background: rgba(16, 185, 129, 0.1);
    color: var(--sf-success, #10b981);
  }

  &.status--error {
    background: rgba(239, 68, 68, 0.1);
    color: var(--sf-error, #ef4444);
  }
}

.record-info {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.info-label {
  color: var(--sf-text-secondary, #666);
}

.info-value {
  color: var(--sf-text-primary, #1a1a1a);
  font-weight: 500;
}

.record-tokens {
  margin-top: 8px;
}

.token-bar {
  display: flex;
  height: 20px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--sf-bg-gray-100, #f5f5f5);
}

.token-input {
  background: var(--sf-accent, #1a1a1a);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 500;
  min-width: 30px;
}

.token-output {
  background: var(--sf-success, #10b981);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 500;
  min-width: 30px;
}

.token-total {
  font-size: 11px;
  color: var(--sf-text-secondary, #666);
  margin-top: 4px;
}

.record-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 12px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: var(--sf-radius-sm, 4px);
  color: var(--sf-error, #ef4444);
  font-size: 12px;

  svg {
    width: 16px;
    height: 16px;
    flex-shrink: 0;
    margin-top: 2px;
  }
}

// 详情面板样式
.detail-panel {
  height: 100%;
  overflow-y: auto;
}

.detail-section {
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--sf-border-light, #f0f0f0);

  svg {
    width: 18px;
    height: 18px;
    color: var(--sf-accent, #1a1a1a);
  }

  span:first-of-type {
    font-weight: 600;
    color: var(--sf-text-primary, #1a1a1a);
  }
}

.section-badge {
  margin-left: auto;
  padding: 2px 8px;
  background: var(--sf-bg-gray-100, #f5f5f5);
  border-radius: var(--sf-radius-full, 9999px);
  font-size: 11px;
  color: var(--sf-text-secondary, #666);
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-item {
  border: 1px solid var(--sf-border-light, #f0f0f0);
  border-radius: var(--sf-radius-md, 8px);
  overflow: hidden;
}

.message-role {
  padding: 6px 12px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;

  &.role--system {
    background: rgba(99, 102, 241, 0.1);
    color: #6366f1;
  }

  &.role--user {
    background: rgba(16, 185, 129, 0.1);
    color: #10b981;
  }

  &.role--assistant {
    background: rgba(245, 158, 11, 0.1);
    color: #f59e0b;
  }
}

.message-content {
  padding: 12px;

  pre {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 13px;
    line-height: 1.5;
    font-family: inherit;
  }
}

.tools-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-item {
  padding: 8px 12px;
  background: var(--sf-bg-gray-100, #f5f5f5);
  border-radius: var(--sf-radius-sm, 4px);
}

.tool-name {
  display: block;
  font-weight: 500;
  font-size: 13px;
  color: var(--sf-text-primary, #1a1a1a);
}

.tool-desc {
  display: block;
  font-size: 12px;
  color: var(--sf-text-secondary, #666);
  margin-top: 2px;
}

.response-content,
.reasoning-content {
  padding: 16px;
  background: var(--sf-bg-gray-100, #f5f5f5);
  border-radius: var(--sf-radius-md, 8px);
  max-height: 400px;
  overflow: auto;

  pre {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 13px;
    line-height: 1.6;
    font-family: inherit;
  }
}

.response-empty {
  padding: 16px;
  text-align: center;
  color: var(--sf-text-secondary, #666);
  font-size: 13px;
}

.metadata-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-key {
  font-size: 11px;
  color: var(--sf-text-secondary, #666);
}

.meta-value {
  font-size: 13px;
  color: var(--sf-text-primary, #1a1a1a);
  font-family: monospace;
}
</style>
