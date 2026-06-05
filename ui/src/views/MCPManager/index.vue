<template>
  <div class="mcp-manager-page">
    <div class="mcp-manager-page__bg">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
      <div class="bg-noise"></div>
    </div>

    <div class="mcp-manager-page__main">
      <div class="mcp-manager-page__header">
        <div class="header-content">
          <h1>MCP 服务器管理</h1>
          <p>手动配置和选择您需要使用的 MCP 服务器</p>
        </div>
        <div class="header-stats">
          <div class="header-stat">
            <span class="header-stat__value">{{ runningServersCount }}</span>
            <span class="header-stat__label">运行中</span>
          </div>
          <div class="header-stat">
            <span class="header-stat__value">{{ totalToolsCount }}</span>
            <span class="header-stat__label">工具</span>
          </div>
        </div>
      </div>

      <div class="mcp-manager-page__stats">
        <div class="stat-card">
          <div class="stat-card__icon stat-card__icon--primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
              <line x1="8" y1="21" x2="16" y2="21" />
              <line x1="12" y1="17" x2="12" y2="21" />
            </svg>
          </div>
          <div class="stat-card__content">
            <div class="stat-card__value">{{ mcpStore.servers.length }}</div>
            <div class="stat-card__label">总服务器</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-card__icon stat-card__icon--success">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
          </div>
          <div class="stat-card__content">
            <div class="stat-card__value">{{ selectedCount }}</div>
            <div class="stat-card__label">已选中</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-card__icon stat-card__icon--warning">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
          </div>
          <div class="stat-card__content">
            <div class="stat-card__value">{{ runningServersCount }}</div>
            <div class="stat-card__label">运行中</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-card__icon stat-card__icon--info">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
            </svg>
          </div>
          <div class="stat-card__content">
            <div class="stat-card__value">{{ totalToolsCount }}</div>
            <div class="stat-card__label">可用工具</div>
          </div>
        </div>
      </div>

      <div class="mcp-manager-page__toolbar">
        <div class="search-box">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索服务器名称或描述..."
          />
        </div>
        <div class="toolbar-actions">
          <button class="btn btn--primary" @click="handleStartSelected" :disabled="startLoading">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="5 3 19 12 5 21 5 3" />
            </svg>
            {{ startLoading ? '启动中...' : '启动选中' }}
          </button>
          <button class="btn btn--secondary" @click="handleSelectAll">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 11 12 14 22 4" />
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
            </svg>
            全选
          </button>
          <button class="btn btn--secondary" @click="handleDeselectAll">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
            </svg>
            取消全选
          </button>
          <button class="btn btn--ghost" @click="handleReload">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6M1 20v-6h6" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
            刷新
          </button>
        </div>
      </div>

      <div class="error-alert" v-if="errorMessage">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
        <span>{{ errorMessage }}</span>
      </div>

      <div class="mcp-manager-page__content">
        <div v-if="mcpStore.isLoading" class="loading-container">
          <div class="loading-spinner"></div>
          <p>正在加载服务器列表...</p>
        </div>

        <div v-else class="server-grid">
          <div
            v-for="server in filteredServers"
            :key="server.name"
            class="server-card"
            :class="{ 'server-card--selected': mcpStore.selectedServerNames.has(server.name) }"
          >
            <div class="server-card__header">
              <div class="server-card__status" :class="{ 'server-card__status--running': server.running }"></div>
              <div class="server-card__name">{{ server.name }}</div>
              <label class="server-card__checkbox">
                <input
                  type="checkbox"
                  :checked="mcpStore.selectedServerNames.has(server.name)"
                  @change="mcpStore.selectedServerNames.has(server.name) ? handleDeselectServer(server.name) : handleSelectServer(server.name)"
                />
                <span class="checkbox-mark"></span>
              </label>
            </div>

            <div class="server-card__desc" v-if="server.description">{{ server.description }}</div>

            <div class="server-card__meta">
              <div class="server-card__tools" v-if="server.toolCount">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
                </svg>
                {{ server.toolCount }} 工具
              </div>
              <div class="server-card__type">stdio</div>
            </div>

            <div class="server-card__actions">
              <button
                class="server-card__btn"
                :class="server.running ? 'server-card__btn--stop' : 'server-card__btn--start'"
                @click="handleToggleServer(server.name)"
              >
                {{ server.running ? '停止' : '启动' }}
              </button>
            </div>
          </div>
        </div>

        <div v-if="filteredServers.length === 0 && !mcpStore.isLoading" class="empty-state">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-state__icon">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
            <line x1="8" y1="21" x2="16" y2="21" />
            <line x1="12" y1="17" x2="12" y2="21" />
          </svg>
          <div class="empty-state__text">没有找到匹配的服务器</div>
        </div>
      </div>

      <div v-if="selectedCount > 0" class="action-bar">
        <div class="selected-count">
          已选择 <strong>{{ selectedCount }}</strong> 个服务器
        </div>
        <div class="action-buttons">
          <button class="btn btn--success" @click="handleSaveSelection">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
              <polyline points="17 21 17 13 7 13 7 21" />
              <polyline points="7 3 7 8 15 8" />
            </svg>
            保存选择
          </button>
          <button class="btn btn--primary" @click="handleViewConfig">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="16" y1="13" x2="8" y2="13" />
              <line x1="16" y1="17" x2="8" y2="17" />
              <polyline points="10 9 9 9 8 9" />
            </svg>
            查看配置
          </button>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="configDialogVisible"
      title="精简配置"
      :width="isMobile ? '95%' : '60%'"
      class="config-dialog"
    >
      <el-input
        v-model="configContent"
        type="textarea"
        :rows="20"
        readonly
      />
      <template #footer>
        <el-button @click="configDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleCopyConfig">复制配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useMCPStore } from '@/stores/mcp'
import { startSelectedServers } from '@/api/mcp'

const mcpStore = useMCPStore()
const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)

const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
const searchKeyword = ref('')
const errorMessage = ref('')
const configDialogVisible = ref(false)
const configContent = ref('')
const startLoading = ref(false)

const filteredServers = computed(() => {
  if (!searchKeyword.value) {
    return mcpStore.servers
  }

  const keyword = searchKeyword.value.toLowerCase()
  return mcpStore.servers.filter(
    server =>
      server.name.toLowerCase().includes(keyword) ||
      (server.description && server.description.toLowerCase().includes(keyword))
  )
})

const selectedCount = computed(() => mcpStore.selectedServerNames.size)

const runningServersCount = computed(() =>
  mcpStore.servers.filter(server => server.running).length
)

const totalToolsCount = computed(() =>
  mcpStore.servers.reduce((total, server) => total + (server.toolCount || 0), 0)
)

const handleSelectAll = () => {
  mcpStore.selectAll()
  ElMessage.success('已全选所有服务器')
}

const handleDeselectAll = () => {
  mcpStore.deselectAll()
  ElMessage.success('已取消全选')
}

const handleReload = async () => {
  try {
    errorMessage.value = ''
    await mcpStore.loadServers()
    ElMessage.success('刷新成功')
  } catch (error: any) {
    errorMessage.value = error.message || '刷新失败'
  }
}

const handleStartSelected = async () => {
  try {
    startLoading.value = true
    errorMessage.value = ''
    const response = await startSelectedServers()
    const result = (response as any).data || response
    ElMessage.success(result || '启动完成')
    await mcpStore.loadServers()
  } catch (error: any) {
    errorMessage.value = error.message || '启动失败'
    ElMessage.error('启动失败: ' + (error.message || '未知错误'))
  } finally {
    startLoading.value = false
  }
}

const handleToggleServer = async (serverName: string) => {
  try {
    errorMessage.value = ''
    await mcpStore.toggleServer(serverName)
  } catch (error: any) {
    errorMessage.value = error.message || '操作失败'
  }
}

const handleSelectServer = async (serverName: string) => {
  try {
    errorMessage.value = ''
    await mcpStore.selectServerByName(serverName)
    ElMessage.success(`已选择服务器：${serverName}`)
  } catch (error: any) {
    errorMessage.value = error.message || '选择失败'
  }
}

const handleDeselectServer = async (serverName: string) => {
  try {
    errorMessage.value = ''
    await mcpStore.deselectServerByName(serverName)
    ElMessage.success(`已取消选择服务器：${serverName}`)
  } catch (error: any) {
    errorMessage.value = error.message || '取消选择失败'
  }
}

const handleSaveSelection = async () => {
  try {
    errorMessage.value = ''
    const result = await mcpStore.saveSelection()
    const configPath = (result as any).configFilePath || '未知路径'
    ElMessage.success(`保存成功！精简配置已保存到：${configPath}`)
    await mcpStore.loadServers()
  } catch (error: any) {
    errorMessage.value = error.message || '保存失败'
  }
}

const handleViewConfig = async () => {
  try {
    errorMessage.value = ''
    const config = await mcpStore.loadSelectedConfig()
    configContent.value = JSON.stringify(config, null, 2)
    configDialogVisible.value = true
  } catch (error: any) {
    errorMessage.value = error.message || '加载配置失败'
  }
}

const handleCopyConfig = () => {
  navigator.clipboard.writeText(configContent.value)
  ElMessage.success('配置已复制到剪贴板')
}

onMounted(() => {
  mcpStore.loadServers().catch((error: any) => {
    errorMessage.value = error.message || '加载服务器列表失败'
  })
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.mcp-manager-page {
  @include page-container;
}

.mcp-manager-page__bg {
  @include page-background;
}

.mcp-manager-page__main {
  @include page-main;
  overflow-y: auto;
  padding: 24px;
  padding-bottom: 100px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.mcp-manager-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  max-width: 1200px;
  margin-bottom: 20px;

  .header-content {
    h1 {
      font-size: 24px;
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

  .header-stats {
    display: flex;
    gap: 20px;
  }

  .header-stat {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 12px 20px;
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 12px;

    &__value {
      font-size: 24px;
      font-weight: 700;
      color: var(--chat-text-primary);
    }

    &__label {
      font-size: 11px;
      color: var(--chat-text-muted);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
  }
}

.mcp-manager-page__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  width: 100%;
  max-width: 1200px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 14px;
  transition: all 0.25s ease;

  &:hover {
    background: var(--chat-surface-glass-hover);
    border-color: var(--chat-border-default);
    transform: translateY(-2px);
  }

  &__icon {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;

    svg {
      width: 22px;
      height: 22px;
    }

    &--primary {
      background: rgba(139, 92, 246, 0.15);
      color: var(--chat-accent-purple);
    }

    &--success {
      background: rgba(16, 185, 129, 0.15);
      color: var(--chat-accent-green);
    }

    &--warning {
      background: rgba(245, 158, 11, 0.15);
      color: var(--chat-accent-orange);
    }

    &--info {
      background: rgba(6, 182, 212, 0.15);
      color: var(--chat-accent-cyan);
    }
  }

  &__content {
    flex: 1;
  }

  &__value {
    font-size: 24px;
    font-weight: 700;
    color: var(--chat-text-primary);
    line-height: 1.2;
  }

  &__label {
    font-size: 12px;
    color: var(--chat-text-muted);
    margin-top: 2px;
  }
}

.mcp-manager-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  max-width: 1200px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.search-box {
  flex: 1;
  max-width: 400px;
  position: relative;

  svg {
    position: absolute;
    left: 14px;
    top: 50%;
    transform: translateY(-50%);
    width: 18px;
    height: 18px;
    color: var(--chat-text-tertiary);
  }

  input {
    width: 100%;
    padding: 12px 14px 12px 42px;
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 10px;
    color: var(--chat-text-primary);
    font-size: 14px;
    transition: all 0.2s ease;

    &::placeholder {
      color: var(--chat-text-muted);
    }

    &:focus {
      outline: none;
      border-color: var(--chat-accent-purple);
      background: var(--chat-surface-glass-hover);
    }
  }
}

.toolbar-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;

  svg {
    width: 16px;
    height: 16px;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &--primary {
    background: var(--chat-gradient-primary);
    color: var(--chat-accent-text);

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: var(--chat-shadow-glow-purple);
    }
  }

  &--secondary {
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    color: var(--chat-text-secondary);

    &:hover:not(:disabled) {
      background: var(--chat-surface-glass-hover);
      border-color: var(--chat-border-default);
    }
  }

  &--ghost {
    background: transparent;
    color: var(--chat-text-tertiary);

    &:hover:not(:disabled) {
      color: var(--chat-text-primary);
      background: var(--chat-surface-glass);
    }
  }

  &--success {
    background: linear-gradient(135deg, var(--chat-accent-green) 0%, #16a34a 100%);
    color: white;

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(16, 185, 129, 0.4);
    }
  }
}

.error-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 10px;
  color: var(--chat-accent-red);
  font-size: 14px;
  width: 100%;
  max-width: 1200px;
  margin-bottom: 16px;

  svg {
    width: 18px;
    height: 18px;
    flex-shrink: 0;
  }
}

.mcp-manager-page__content {
  width: 100%;
  max-width: 1200px;
  flex: 1;
  min-height: 200px;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--chat-text-tertiary);

  .loading-spinner {
    width: 40px;
    height: 40px;
    border: 3px solid var(--chat-border-default);
    border-top-color: var(--chat-accent-purple);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    margin-bottom: 16px;
  }

  p {
    font-size: 14px;
  }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.server-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.server-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 14px;
  padding: 16px;
  transition: all 0.25s ease;

  &:hover {
    background: var(--chat-surface-glass-hover);
    border-color: var(--chat-border-default);
    transform: translateY(-2px);
    box-shadow: var(--chat-shadow-lg);
  }

  &--selected {
    border-color: rgba(139, 92, 246, 0.4);
    background: rgba(139, 92, 246, 0.05);
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
  }

  &__status {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--chat-text-muted);

    &--running {
      background: var(--chat-accent-green);
      box-shadow: 0 0 8px rgba(16, 185, 129, 0.5);
      animation: pulse 2s ease-in-out infinite;
    }
  }

  &__name {
    flex: 1;
    font-size: 15px;
    font-weight: 600;
    color: var(--chat-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__checkbox {
    position: relative;
    width: 20px;
    height: 20px;
    cursor: pointer;

    input {
      opacity: 0;
      width: 0;
      height: 0;

      &:checked + .checkbox-mark {
        background: var(--chat-gradient-primary);
        border-color: transparent;

        &::after {
          opacity: 1;
          transform: rotate(45deg) scale(1);
        }
      }
    }

    .checkbox-mark {
      position: absolute;
      top: 0;
      left: 0;
      width: 20px;
      height: 20px;
      background: var(--chat-surface-glass);
      border: 1px solid var(--chat-border-default);
      border-radius: 6px;
      transition: all 0.2s ease;

      &::after {
        content: '';
        position: absolute;
        left: 6px;
        top: 2px;
        width: 5px;
        height: 10px;
        border: solid white;
        border-width: 0 2px 2px 0;
        opacity: 0;
        transform: rotate(45deg) scale(0.5);
        transition: all 0.2s ease;
      }
    }
  }

  &__desc {
    font-size: 13px;
    color: var(--chat-text-tertiary);
    line-height: 1.5;
    margin-bottom: 12px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
  }

  &__tools {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--chat-text-tertiary);

    svg {
      width: 14px;
      height: 14px;
    }
  }

  &__type {
    font-size: 11px;
    padding: 2px 8px;
    background: var(--chat-surface-glass);
    border-radius: 4px;
    color: var(--chat-text-muted);
  }

  &__actions {
    display: flex;
    gap: 8px;
  }

  &__btn {
    flex: 1;
    padding: 8px 12px;
    border-radius: 8px;
    font-size: 12px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s ease;
    border: none;

    &--start {
      background: rgba(16, 185, 129, 0.15);
      color: var(--chat-accent-green);

      &:hover {
        background: rgba(16, 185, 129, 0.25);
      }
    }

    &--stop {
      background: rgba(239, 68, 68, 0.15);
      color: var(--chat-accent-red);

      &:hover {
        background: rgba(239, 68, 68, 0.25);
      }
    }
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  color: var(--chat-text-muted);

  &__icon {
    width: 64px;
    height: 64px;
    margin-bottom: 16px;
    opacity: 0.4;
  }

  &__text {
    font-size: 16px;
  }
}

.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--chat-surface-glass);
  border-top: 1px solid var(--chat-border-subtle);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  z-index: 100;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.1);

  .selected-count {
    font-size: 14px;
    color: var(--chat-text-secondary);

    strong {
      color: var(--chat-accent-purple);
      font-size: 18px;
    }
  }

  .action-buttons {
    display: flex;
    gap: 10px;
  }
}

:deep(.config-dialog) {
  .el-dialog {
    background: var(--chat-bg-elevated);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 16px;

    .el-dialog__header {
      padding: 20px 24px;
      border-bottom: 1px solid var(--chat-border-subtle);

      .el-dialog__title {
        color: var(--chat-text-primary);
        font-weight: 600;
      }
    }

    .el-dialog__body {
      padding: 24px;
    }

    .el-dialog__footer {
      padding: 16px 24px;
      border-top: 1px solid var(--chat-border-subtle);
    }
  }

  .el-textarea__inner {
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    color: var(--chat-text-primary);
    font-family: var(--chat-font-mono);
    font-size: 13px;
  }
}

@media (max-width: 1024px) {
  .mcp-manager-page__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .mcp-manager-page__main {
    padding: 16px;
  }

  .mcp-manager-page__header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;

    .header-stats {
      width: 100%;
      justify-content: space-around;
    }
  }

  .mcp-manager-page__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .mcp-manager-page__toolbar {
    flex-direction: column;

    .search-box {
      max-width: 100%;
    }

    .toolbar-actions {
      width: 100%;
      justify-content: flex-end;
    }
  }

  .server-grid {
    grid-template-columns: 1fr;
  }

  .action-bar {
    flex-direction: column;
    gap: 12px;

    .action-buttons {
      width: 100%;
      flex-direction: column;

      .btn {
        width: 100%;
        justify-content: center;
      }
    }
  }

  :deep(.config-dialog) {
    .el-dialog {
      width: calc(100% - 32px) !important;
      max-width: 90% !important;
      margin: 16px auto !important;
      border-radius: 12px;
      overflow: hidden;

      .el-dialog__header {
        padding: 12px 16px;
        flex-shrink: 0;

        .el-dialog__title {
          font-size: 15px;
        }

        .el-dialog__headerbtn {
          top: 12px;
          right: 12px;
        }
      }

      .el-dialog__body {
        padding: 16px;
        max-height: calc(100vh - 200px);
        overflow-y: auto;
        box-sizing: border-box;
      }

      .el-dialog__footer {
        padding: 12px 16px;
        flex-shrink: 0;
      }
    }

    .el-textarea__inner {
      min-height: 200px;
      font-size: 12px;
    }
  }
}
</style>
