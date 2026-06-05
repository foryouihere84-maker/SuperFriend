<template>
  <div class="token-stats-page">
    <div class="token-stats-page__bg">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
    </div>

    <div class="token-stats-page__main">
      <div class="page-header">
        <button class="back-btn" @click="router.push('/settings')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
          <span>返回设置</span>
        </button>
        <div class="header-content">
          <h1>Token 统计</h1>
          <p>查看 API 调用成本和 Token 使用情况</p>
        </div>
        <div class="header-actions">
          <button class="refresh-btn" @click="refreshAll" :disabled="loading">
            <svg :class="{ 'animate-spin': loading }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6M1 20v-6h6M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
            <span>刷新</span>
          </button>
        </div>
      </div>

      <div class="stats-content">
        <div class="stats-overview">
          <div class="overview-card overview-card--primary">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10" />
                <path d="M12 6v6l4 2" />
              </svg>
            </div>
            <div class="card-data">
              <div class="card-value">{{ formatNumber(metrics?.totalApiCalls || 0) }}</div>
              <div class="card-label">总调用次数</div>
            </div>
          </div>
          
          <div class="overview-card overview-card--tokens">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2L2 7l10 5 10-5-10-5z" />
                <path d="M2 17l10 5 10-5" />
                <path d="M2 12l10 5 10-5" />
              </svg>
            </div>
            <div class="card-data">
              <div class="card-value">{{ formatTokens(metrics?.totalTokens || 0) }}</div>
              <div class="card-label">总 Token 数</div>
            </div>
          </div>
          
          <div class="overview-card overview-card--cost">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="1" x2="12" y2="23" />
                <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
              </svg>
            </div>
            <div class="card-data">
              <div class="card-value">${{ formatCost(metrics?.totalCost || 0) }}</div>
              <div class="card-label">总成本</div>
            </div>
          </div>
          
          <div class="overview-card overview-card--avg">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 20V10M12 20V4M6 20v-6" />
              </svg>
            </div>
            <div class="card-data">
              <div class="card-value">${{ formatCost(report?.averageCostPerCall || 0) }}</div>
              <div class="card-label">平均单次成本</div>
            </div>
          </div>
        </div>

        <div class="stats-grid">
          <div class="stats-panel stats-panel--models">
            <div class="panel-header">
              <h3>模型使用分布</h3>
            </div>
            <div class="models-list" v-if="modelList.length > 0">
              <div class="model-item" v-for="model in modelList" :key="model.modelId">
                <div class="model-info">
                  <span class="model-name">{{ model.modelId }}</span>
                  <span class="model-calls">{{ formatNumber(model.totalCalls) }} 次调用</span>
                </div>
                <div class="model-stats">
                  <div class="model-tokens">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M12 2L2 7l10 5 10-5-10-5z" />
                    </svg>
                    {{ formatTokens(model.totalInputTokens + model.totalOutputTokens) }}
                  </div>
                  <div class="model-cost">${{ formatCost(model.totalCost) }}</div>
                </div>
                <div class="model-bar">
                  <div class="model-bar-fill" :style="{ width: `${getModelPercentage(model)}%` }"></div>
                </div>
              </div>
            </div>
            <div class="panel-empty" v-else>
              <span>暂无模型使用数据</span>
            </div>
          </div>

          <div class="stats-panel stats-panel--trend">
            <div class="panel-header">
              <h3>每日趋势</h3>
              <div class="trend-tabs">
                <button 
                  v-for="range in dateRanges" 
                  :key="range.value"
                  class="trend-tab"
                  :class="{ 'trend-tab--active': selectedRange === range.value }"
                  @click="selectedRange = range.value"
                >
                  {{ range.label }}
                </button>
              </div>
            </div>
            <div class="trend-chart" v-if="filteredTrend.length > 0">
              <div class="chart-y-axis">
                <span>{{ formatCost(maxTrendCost) }}</span>
                <span>{{ formatCost(maxTrendCost / 2) }}</span>
                <span>$0</span>
              </div>
              <div class="chart-bars">
                <div class="chart-bar-item" v-for="day in filteredTrend" :key="day.date">
                  <div class="chart-bar-wrapper">
                    <div 
                      class="chart-bar" 
                      :style="{ height: `${maxTrendCost > 0 ? (day.cost / maxTrendCost * 100) : 0}%` }"
                    >
                      <div class="chart-bar-tooltip">
                        <div>{{ formatDate(day.date) }}</div>
                        <div>${{ formatCost(day.cost) }}</div>
                        <div>{{ formatNumber(day.apiCalls) }} 次调用</div>
                      </div>
                    </div>
                  </div>
                  <span class="chart-label">{{ formatDayLabel(day.date) }}</span>
                </div>
              </div>
            </div>
            <div class="panel-empty" v-else>
              <span>暂无趋势数据</span>
            </div>
          </div>

          <div class="stats-panel stats-panel--sessions">
            <div class="panel-header">
              <h3>最近会话</h3>
            </div>
            <div class="sessions-list" v-if="recentSessions.length > 0">
              <div class="session-item" v-for="session in recentSessions" :key="session.sessionId">
                <div class="session-info">
                  <span class="session-id">{{ session.sessionId.substring(0, 12) }}...</span>
                  <span class="session-model">{{ session.model }}</span>
                </div>
                <div class="session-stats">
                  <div class="session-tokens">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M12 2L2 7l10 5 10-5-10-5z" />
                    </svg>
                    {{ formatTokens(session.totalTokens) }}
                  </div>
                  <div class="session-cost">${{ formatCost(session.totalCost) }}</div>
                </div>
                <div class="session-meta">
                  <span>{{ session.apiCallCount }} 次调用</span>
                  <span>{{ session.toolCallCount }} 次工具</span>
                </div>
              </div>
            </div>
            <div class="panel-empty" v-else>
              <span>暂无会话数据</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTokenStats } from '@/composables/useTokenStats'

const router = useRouter()

const { loading, totalStats, modelStats, recentSessions, filteredDailyStats, loadStats } = useTokenStats()

const selectedRange = ref(7)

const dateRanges = [
  { label: '7天', value: 7 },
  { label: '14天', value: 14 },
  { label: '30天', value: 30 }
]

const metrics = computed(() => ({
  totalApiCalls: totalStats.value.totalApiCalls,
  totalTokens: totalStats.value.totalTokens,
  totalCost: totalStats.value.totalCost
}))

const report = computed(() => {
  const days = filteredDailyStats.value.slice(-selectedRange.value)
  return {
    averageCostPerCall: metrics.value.totalApiCalls > 0
      ? metrics.value.totalCost / metrics.value.totalApiCalls
      : 0,
    modelSummaries: modelStats.value.reduce((acc, m) => {
      acc[m.modelId] = {
        modelId: m.modelId,
        totalCalls: m.totalCalls,
        totalInputTokens: m.totalInputTokens,
        totalOutputTokens: m.totalOutputTokens,
        totalCost: m.totalCost,
        averageCostPerCall: m.totalCalls > 0 ? m.totalCost / m.totalCalls : 0
      }
      return acc
    }, {} as Record<string, any>),
    dailyTrend: {
      days: days.map(d => ({
        date: d.date,
        apiCalls: d.totalApiCalls,
        tokens: d.totalTokens,
        cost: d.totalCost
      }))
    }
  }
})

const filteredTrend = computed(() => report.value.dailyTrend.days)

const maxTrendCost = computed(() => {
  if (filteredTrend.value.length === 0) return 1
  return Math.max(...filteredTrend.value.map(d => d.cost), 0.01)
})

const modelList = computed(() => {
  return Object.values(report.value.modelSummaries)
    .sort((a: any, b: any) => b.totalCost - a.totalCost)
    .slice(0, 6)
})

const getModelPercentage = (model: any) => {
  if (!metrics.value.totalCost) return 0
  return (model.totalCost / metrics.value.totalCost) * 100
}

const formatNumber = (num: number) => {
  return num.toLocaleString()
}

const formatTokens = (num: number) => {
  if (num >= 1000000) return `${(num / 1000000).toFixed(2)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toString()
}

const formatCost = (cost: number) => {
  if (cost >= 1) return cost.toFixed(2)
  if (cost >= 0.01) return cost.toFixed(3)
  return cost.toFixed(6)
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const formatDayLabel = (dateStr: string) => {
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

const refreshAll = () => {
  loadStats(30)
}

onMounted(() => {
  loadStats(30)
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.token-stats-page {
  @include page-container;
}

.token-stats-page__bg {
  @include page-background;
}

.token-stats-page__main {
  @include page-main;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
  width: 100%;
  max-width: 1200px;

  .back-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 16px;
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 10px;
    color: var(--chat-text-secondary);
    font-size: 14px;
    cursor: pointer;
    transition: all 0.2s ease;

    svg {
      width: 18px;
      height: 18px;
    }

    &:hover {
      background: var(--chat-surface-glass-hover);
      color: var(--chat-text-primary);
      border-color: var(--chat-border-default);
    }
  }

  .header-content {
    flex: 1;

    h1 {
      font-size: 28px;
      font-weight: 700;
      color: var(--chat-text-primary);
      margin: 0 0 4px 0;
      letter-spacing: -0.5px;
    }

    p {
      font-size: 14px;
      color: var(--chat-text-tertiary);
      margin: 0;
    }
  }

  .header-actions {
    display: flex;
    gap: 10px;
  }

  .refresh-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 18px;
    background: var(--sf-accent);
    color: var(--sf-accent-text);
    border: none;
    border-radius: 10px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s ease;

    svg {
      width: 16px;
      height: 16px;
    }

    &:hover:not(:disabled) {
      opacity: 0.9;
      transform: translateY(-1px);
    }

    &:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }
  }
}

.stats-content {
  width: 100%;
  max-width: 1200px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-bottom: 40px;
}

.stats-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.overview-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 16px;
  transition: all 0.25s ease;

  &:hover {
    border-color: var(--chat-border-default);
    transform: translateY(-2px);
    box-shadow: var(--chat-shadow-md);
  }

  .card-icon {
    width: 48px;
    height: 48px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    flex-shrink: 0;

    svg {
      width: 24px;
      height: 24px;
    }
  }

  .card-data {
    flex: 1;
    min-width: 0;
  }

  .card-value {
    font-size: 24px;
    font-weight: 700;
    color: var(--chat-text-primary);
    line-height: 1.2;
    margin-bottom: 4px;
  }

  .card-label {
    font-size: 13px;
    color: var(--chat-text-muted);
  }

  &--primary {
    .card-icon {
      background: color-mix(in srgb, var(--chat-accent-purple) 15%, transparent);
      color: var(--chat-accent-purple);
    }
  }

  &--tokens {
    .card-icon {
      background: color-mix(in srgb, var(--chat-accent-cyan) 15%, transparent);
      color: var(--chat-accent-cyan);
    }
  }

  &--cost {
    .card-icon {
      background: color-mix(in srgb, var(--chat-accent-green) 15%, transparent);
      color: var(--chat-accent-green);
    }
  }

  &--avg {
    .card-icon {
      background: color-mix(in srgb, var(--chat-accent-orange) 15%, transparent);
      color: var(--chat-accent-orange);
    }
  }
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.stats-panel {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 16px;
  padding: 20px;
  transition: all 0.25s ease;

  &:hover {
    border-color: var(--chat-border-default);
  }

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;

    h3 {
      font-size: 16px;
      font-weight: 600;
      color: var(--chat-text-primary);
      margin: 0;
    }
  }

  .panel-badge {
    padding: 4px 10px;
    font-size: 12px;
    font-weight: 500;
    border-radius: 20px;
    background: color-mix(in srgb, var(--chat-accent-green) 15%, transparent);
    color: var(--chat-accent-green);

    &--warning {
      background: color-mix(in srgb, var(--chat-accent-red) 15%, transparent);
      color: var(--chat-accent-red);
    }
  }

  .panel-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 120px;
    color: var(--chat-text-muted);
    font-size: 14px;
  }
}

.budget-content {
  .budget-row {
    display: flex;
    flex-direction: column;
    gap: 16px;
    margin-bottom: 16px;
  }

  .budget-item {
    .budget-label {
      font-size: 13px;
      color: var(--chat-text-secondary);
      margin-bottom: 8px;
    }

    .budget-bar {
      height: 8px;
      background: var(--chat-surface-glass-active);
      border-radius: 4px;
      overflow: hidden;
      margin-bottom: 6px;

      .budget-fill {
        height: 100%;
        background: var(--chat-accent-green);
        border-radius: 4px;
        transition: width 0.3s ease;
      }
    }

    .budget-bar--month .budget-fill {
      background: var(--chat-accent-cyan);
    }

    .budget-values {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--chat-text-muted);
    }
  }

  .budget-summary {
    display: flex;
    justify-content: space-between;
    padding-top: 12px;
    border-top: 1px solid var(--chat-border-subtle);

    .summary-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .summary-label {
      font-size: 12px;
      color: var(--chat-text-muted);
    }

    .summary-value {
      font-size: 16px;
      font-weight: 600;
      color: var(--chat-text-primary);

      &.text-warning {
        color: var(--chat-accent-orange);
      }
    }
  }
}

.models-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.model-item {
  padding: 12px;
  background: var(--chat-surface-glass-active);
  border-radius: 10px;
  transition: all 0.2s ease;

  &:hover {
    background: var(--chat-surface-glass-hover);
  }

  .model-info {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }

  .model-name {
    font-size: 14px;
    font-weight: 500;
    color: var(--chat-text-primary);
  }

  .model-calls {
    font-size: 12px;
    color: var(--chat-text-muted);
  }

  .model-stats {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }

  .model-tokens {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: var(--chat-text-secondary);

    svg {
      width: 14px;
      height: 14px;
    }
  }

  .model-cost {
    font-size: 14px;
    font-weight: 600;
    color: var(--chat-accent-green);
  }

  .model-bar {
    height: 4px;
    background: var(--chat-surface-glass);
    border-radius: 2px;
    overflow: hidden;

    .model-bar-fill {
      height: 100%;
      background: var(--chat-accent-cyan);
      border-radius: 2px;
      transition: width 0.3s ease;
    }
  }
}

.trend-tabs {
  display: flex;
  gap: 4px;
}

.trend-tab {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--chat-text-muted);
  background: transparent;
  border: 1px solid var(--chat-border-subtle);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    color: var(--chat-text-secondary);
    border-color: var(--chat-border-default);
  }

  &--active {
    color: var(--sf-accent-text);
    background: var(--sf-accent);
    border-color: var(--sf-accent);
  }
}

.trend-chart {
  display: flex;
  gap: 12px;
  height: 200px;
  padding-top: 8px;

  .chart-y-axis {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    font-size: 11px;
    color: var(--chat-text-muted);
    padding: 4px 0;
    width: 50px;
    text-align: right;
  }

  .chart-bars {
    flex: 1;
    display: flex;
    align-items: flex-end;
    gap: 6px;
    padding-bottom: 24px;
    position: relative;
  }

  .chart-bar-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    height: 100%;

    .chart-bar-wrapper {
      flex: 1;
      width: 100%;
      display: flex;
      align-items: flex-end;
    }

    .chart-bar {
      width: 100%;
      min-height: 4px;
      background: linear-gradient(to top, var(--chat-accent-cyan), var(--chat-accent-purple));
      border-radius: 3px 3px 0 0;
      position: relative;
      cursor: pointer;
      transition: all 0.2s ease;

      &:hover {
        opacity: 0.8;

        .chart-bar-tooltip {
          opacity: 1;
          visibility: visible;
          transform: translateX(-50%) translateY(-4px);
        }
      }

      .chart-bar-tooltip {
        position: absolute;
        bottom: 100%;
        left: 50%;
        transform: translateX(-50%) translateY(0);
        background: var(--chat-bg-elevated);
        border: 1px solid var(--chat-border-default);
        border-radius: 8px;
        padding: 8px 12px;
        font-size: 11px;
        white-space: nowrap;
        opacity: 0;
        visibility: hidden;
        transition: all 0.2s ease;
        z-index: 10;
        box-shadow: var(--chat-shadow-lg);

        div {
          color: var(--chat-text-secondary);
          margin-bottom: 2px;

          &:first-child {
            color: var(--chat-text-primary);
            font-weight: 500;
          }
        }
      }
    }

    .chart-label {
      position: absolute;
      bottom: 0;
      font-size: 10px;
      color: var(--chat-text-muted);
      white-space: nowrap;
    }
  }
}

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.session-item {
  padding: 14px;
  background: var(--chat-surface-glass-active);
  border-radius: 10px;
  transition: all 0.2s ease;

  &:hover {
    background: var(--chat-surface-glass-hover);
  }

  .session-info {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }

  .session-id {
    font-size: 13px;
    font-family: var(--sf-font-mono);
    color: var(--chat-text-secondary);
  }

  .session-model {
    font-size: 12px;
    color: var(--chat-accent-cyan);
    background: color-mix(in srgb, var(--chat-accent-cyan) 10%, transparent);
    padding: 2px 8px;
    border-radius: 4px;
  }

  .session-stats {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
  }

  .session-tokens {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: var(--chat-text-secondary);

    svg {
      width: 14px;
      height: 14px;
    }
  }

  .session-cost {
    font-size: 15px;
    font-weight: 600;
    color: var(--chat-accent-green);
  }

  .session-meta {
    display: flex;
    gap: 12px;
    font-size: 11px;
    color: var(--chat-text-muted);
  }
}

.animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@media (max-width: 1024px) {
  .stats-overview {
    grid-template-columns: repeat(2, 1fr);
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .token-stats-page__main {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;

    .header-content h1 {
      font-size: 24px;
    }

    .header-actions {
      width: 100%;
      
      .refresh-btn {
        flex: 1;
        justify-content: center;
      }
    }
  }

  .stats-overview {
    grid-template-columns: 1fr;
  }

  .overview-card {
    padding: 16px;

    .card-value {
      font-size: 20px;
    }
  }

  .trend-chart {
    height: 160px;

    .chart-y-axis {
      width: 40px;
      font-size: 10px;
    }

    .chart-bars {
      gap: 4px;
    }
  }
}
</style>
