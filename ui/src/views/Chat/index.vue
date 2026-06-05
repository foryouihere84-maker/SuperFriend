<template>
  <div class="chat-page">
    <!-- 简洁背景 -->
    <div class="chat-page__bg"></div>

    <!-- 网络状态横幅 -->
    <Transition name="slide-down">
      <div v-if="!networkStatus.isOnline.value" class="network-banner network-banner--offline">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="1" y1="1" x2="23" y2="23" />
          <path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55" />
          <path d="M5 12.55a10.94 10.94 0 0 1 5.17-2.39" />
          <path d="M10.71 5.05A16 16 0 0 1 22.58 9" />
          <path d="M1.42 9a16 16 0 0 1 4.7-2.88" />
          <path d="M8.53 16.11a6 6 0 0 1 6.95 0" />
          <line x1="12" y1="20" x2="12.01" y2="20" />
        </svg>
        <span>网络已断开，正在等待恢复...</span>
      </div>
    </Transition>

    <!-- 网络恢复提示 -->
    <Transition name="slide-down">
      <div v-if="networkStatus.wasOffline.value && networkStatus.isOnline.value && showNetworkRecovered" class="network-banner network-banner--online">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M5 12.55a11 11 0 0 1 14.08 0" />
          <path d="M1.42 9a16 16 0 0 1 21.16 0" />
          <path d="M8.53 16.11a6 6 0 0 1 6.95 0" />
          <line x1="12" y1="20" x2="12.01" y2="20" />
        </svg>
        <span>网络已恢复</span>
        <button v-if="chatStore.pendingRequest" class="network-banner__btn" @click="handleRetryPending">
          重试消息
        </button>
        <button class="network-banner__close" @click="showNetworkRecovered = false">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>
    </Transition>

    <div ref="mainContentRef" class="chat-page__main">
      <!-- Mobile: Hamburger button -->
      <button class="mobile-menu-btn" @click="toggleDrawer">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="3" y1="6" x2="21" y2="6" />
          <line x1="3" y1="12" x2="21" y2="12" />
          <line x1="3" y1="18" x2="21" y2="18" />
        </svg>
      </button>

      <!-- Mobile drawer overlay -->
      <Transition name="fade">
        <div v-if="drawerVisible" class="drawer-overlay" @click="closeDrawer"></div>
      </Transition>

      <div class="chat-page__content">
        <!-- 无消息时：居中布局 -->
        <template v-if="chatStore.messages.length === 0">
          <div class="welcome-view">
            <div class="welcome-view__header">
              <Logo />
            </div>
            <div class="welcome-view__input-wrapper">
              <!-- 文件预览区域 -->
              <div v-if="pendingFiles.length > 0" class="file-preview-bar">
                <div
                  v-for="(file, index) in pendingFiles"
                  :key="index"
                  class="preview-item"
                  :class="`preview-item--${file.type}`"
                >
                  <img v-if="file.type === 'image'" :src="file.previewUrl || file.url" :alt="`图片 ${index + 1}`" />
                  <div v-else class="file-icon-wrapper">
                    <svg v-if="file.type === 'document'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <polyline points="14 2 14 8 20 8" />
                    </svg>
                    <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10" />
                      <path d="M12 16v-4" />
                      <path d="M12 8h.01" />
                    </svg>
                    <span class="file-name-preview">{{ file.name }}</span>
                  </div>
                  <button class="preview-remove" @click="removePendingFile(index)" title="移除">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="18" y1="6" x2="6" y2="18" />
                      <line x1="6" y1="6" x2="18" y2="18" />
                    </svg>
                  </button>
                </div>
              </div>

              <!-- 输入行 -->
              <div class="input-row">
                <!-- 文件上传按钮（悬浮显示） -->
                <div class="file-upload-trigger">
                  <FileUploader
                    ref="fileUploaderRef"
                    :disabled="chatStore.isLoading"
                    :max-files="5"
                    :max-size-m-b="50"
                    :user-id="userStore.user?.id ? Number(userStore.user.id) : undefined"
                    :session-id="chatStore.currentSessionId || undefined"
                    @change="handleFileChange"
                  />
                </div>

                <!-- 输入框 -->
                <div class="input-area input-area--centered">
                  <textarea
                    id="message-input"
                    name="messageInput"
                    v-model="messageInput"
                    :placeholder="chatStore.isLoading ? 'Agent 正在执行中...' : 'How can I help you Today?'"
                    :disabled="chatStore.isLoading"
                    class="input-textarea"
                    rows="1"
                    @keydown.enter.exact="handleSend" @keydown.shift.ctrl.enter.prevent
                  ></textarea>
                </div>

                <!-- 发送按钮 -->
                <button
                  v-if="!chatStore.isLoading"
                  class="send-btn"
                  :disabled="(!messageInput.trim() && pendingFiles.length === 0) || chatStore.isLoading"
                  @click="handleSend"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="22" y1="2" x2="11" y2="13" />
                    <polygon points="22 2 15 22 11 13 2 9 22 2" />
                  </svg>
                </button>
              </div>

              <!-- 控制栏：模型选择和模式选择 -->
              <div class="input-controls">
                <div class="model-selector">
                  <select v-model="selectedModel" :disabled="chatStore.isLoading || availableModels.length === 0" class="model-select">
                    <option value="" disabled>选择模型</option>
                    <option v-for="m in availableModels" :key="m.configId" :value="m.modelId">{{ m.name }}</option>
                  </select>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="6 9 12 15 18 9" />
                  </svg>
                </div>
                <button v-if="availableModels.length === 0" class="config-btn" @click="$router.push('/settings/models')">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="3" />
                    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
                  </svg>
                  配置模型
                </button>
                <div class="mode-selector">
                  <button
                    v-for="mode in chatModes"
                    :key="mode.value"
                    :class="['mode-btn', { 'mode-btn--active': chatMode === mode.value }]"
                    @click="chatMode = mode.value"
                    :disabled="chatStore.isLoading"
                    :title="mode.desc"
                  >{{ mode.label }}</button>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 有消息时：常规布局 -->
        <template v-else>
          <div ref="messagesContainer" class="messages-container">
            <div v-if="chatStore.isLoading" class="connection-status" :class="`connection-status--${connectionStatus}`">
              <span class="connection-status__dot"></span>
              <span class="connection-status__text">{{ connectionStatusText }}</span>
            </div>

            <chat-message
              v-for="(message, index) in chatStore.messages"
              :key="message.id"
              :message="message"
              :show-avatar="true"
              :show-timestamp="true"
              :session-id="chatStore.currentSessionId || undefined"
              :agent-phase-text="!message.done && message.role === 'assistant' ? currentAgentPhaseText : ''"
              :is-last="index === chatStore.messages.length - 1"
              @retry-message="handleRetryMessage"
            />

            <!-- 待重试消息提示 -->
            <div v-if="chatStore.pendingRequest && !chatStore.isLoading" class="retry-prompt">
              <div class="retry-prompt__content">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M10 2a8 8 0 1 0 8 8" />
                  <path d="M10 12V6" />
                  <path d="M10 6l3 3" />
                </svg>
                <span>有未发送的消息</span>
              </div>
              <button class="retry-prompt__btn retry-prompt__btn--primary" @click="handleRetryPending">
                重试发送
              </button>
              <button class="retry-prompt__btn" @click="chatStore.clearPendingRequest()">
                取消
              </button>
            </div>
          </div>

          <div class="chat-page__input">
            <div v-if="isAppendMode" class="append-bar">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 5v14M5 12h14" />
              </svg>
              <span>追加指令模式</span>
              <input
                v-model="appendInput"
                type="text"
                placeholder="输入追加的指令..."
                class="append-bar__input"
                @keydown.enter="handleAppend"
              />
              <button class="append-bar__btn append-bar__btn--primary" @click="handleAppend" :disabled="!appendInput.trim()">发送</button>
              <button class="append-bar__btn" @click="cancelAppendMode">取消</button>
            </div>

            <!-- 图片预览区域 -->
            <div v-if="pendingFiles.length > 0" class="file-preview-bar">
              <div
                v-for="(file, index) in pendingFiles"
                :key="index"
                class="preview-item"
                :class="`preview-item--${file.type}`"
              >
                <img v-if="file.type === 'image'" :src="file.previewUrl || file.url" :alt="`图片 ${index + 1}`" />
                <div v-else class="file-icon-wrapper">
                  <svg v-if="file.type === 'document'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <polyline points="14 2 14 8 20 8" />
                    <line x1="16" y1="13" x2="8" y2="13" />
                    <line x1="16" y1="17" x2="8" y2="17" />
                  </svg>
                  <svg v-else-if="file.type === 'audio'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M9 18V5l12-2v13" />
                    <circle cx="6" cy="18" r="3" />
                    <circle cx="18" cy="16" r="3" />
                  </svg>
                  <svg v-else-if="file.type === 'video'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polygon points="23 7 16 12 23 17 23 7" />
                    <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
                    <polyline points="13 2 13 9 20 9" />
                  </svg>
                  <span class="file-name-preview">{{ file.name }}</span>
                </div>
                <button class="preview-remove" @click="removePendingFile(index)" title="移除">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              </div>
            </div>

            <!-- 输入行 -->
            <div class="input-row">
              <!-- 文件上传按钮（悬浮显示） -->
              <div class="file-upload-trigger">
                <FileUploader
                  ref="fileUploaderRef"
                  :disabled="chatStore.isLoading"
                  :max-files="5"
                  :max-size-m-b="50"
                  :user-id="userStore.user?.id ? Number(userStore.user.id) : undefined"
                  :session-id="chatStore.currentSessionId || undefined"
                  @change="handleFileChange"
                />
              </div>

              <!-- 输入框 -->
              <div class="input-area input-area--centered">
                <textarea
                  id="message-input"
                  name="messageInput"
                  v-model="messageInput"
                  :placeholder="chatStore.isLoading ? 'Agent 正在执行中...' : 'How can I help you Today?'"
                  :disabled="chatStore.isLoading"
                  class="input-textarea"
                  rows="1"
                  @keydown.enter.exact="handleSend" @keydown.shift.ctrl.enter.prevent
                ></textarea>
              </div>

              <!-- 右侧按钮 -->
              <div class="input-row__right">
                <button
                  v-if="chatStore.isLoading"
                  class="action-btn action-btn--danger"
                  @click="handleCancel"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="6" y="6" width="12" height="12" />
                  </svg>
                  停止
                </button>
                <button
                  v-if="chatStore.isLoading && chatMode !== 'conversation'"
                  class="action-btn action-btn--warning"
                  @click="enterAppendMode"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 5v14M5 12h14" />
                  </svg>
                  追加
                </button>
                <button
                  v-if="!chatStore.isLoading"
                  class="send-btn"
                  :disabled="(!messageInput.trim() && pendingFiles.length === 0) || chatStore.isLoading"
                  @click="handleSend"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="22" y1="2" x2="11" y2="13" />
                    <polygon points="22 2 15 22 11 13 2 9 22 2" />
                  </svg>
                </button>
              </div>
            </div>

            <!-- 控制栏：模型选择和模式选择 -->
            <div class="input-controls">
              <div class="model-selector">
                <select v-model="selectedModel" :disabled="chatStore.isLoading || availableModels.length === 0" class="model-select">
                  <option value="" disabled>选择模型</option>
                  <option v-for="m in availableModels" :key="m.configId" :value="m.modelId">{{ m.name }}</option>
                </select>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </div>
              <button v-if="availableModels.length === 0" class="config-btn" @click="$router.push('/settings/models')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="3" />
                  <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l-.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
                </svg>
                配置模型
              </button>
              <div class="mode-selector">
                <button
                  v-for="mode in chatModes"
                  :key="mode.value"
                  :class="['mode-btn', { 'mode-btn--active': chatMode === mode.value }]"
                  @click="chatMode = mode.value"
                  :disabled="chatStore.isLoading"
                  :title="mode.desc"
                >{{ mode.label }}</button>
              </div>
              <button
                :class="['extraction-btn', { 'extraction-btn--active': enableKnowledgeExtraction }]"
                @click="enableKnowledgeExtraction = !enableKnowledgeExtraction"
                :disabled="chatStore.isLoading"
                title="开启后，本轮对话结束时会提取知识到图谱"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"/>
                </svg>
                <span>提取</span>
              </button>
              <!-- LLM 监控按钮 -->
              <LLMMonitorButton
                v-if="chatStore.currentSessionId"
                :session-id="chatStore.currentSessionId"
                :record-count="llmMonitorStore.records.length"
              />
            </div>
          </div>
        </template>
      </div>

      <aside :class="['chat-page__sidebar', {
        'chat-page__sidebar--collapsed': sidebarCollapsed && !drawerVisible,
        'chat-page__sidebar--mobile-open': drawerVisible
      }]">
        <div class="sidebar-content">
          <div class="sidebar-section">
          <!-- Normal header -->
          <template v-if="!batchMode">
            <div class="section-header">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
              <span>对话历史</span>
              <button class="section-action" @click="toggleSearch" :class="{ 'section-action--active': showSearch }" title="搜索">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8" />
                  <line x1="21" y1="21" x2="16.65" y2="16.65" />
                </svg>
              </button>
              <button class="section-action" @click="enterBatchMode" title="批量管理">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 11 12 14 22 4" />
                  <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
                </svg>
              </button>
            </div>
            <!-- Search bar -->
            <div v-if="showSearch" class="history-search">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
              <input
                ref="searchInputRef"
                v-model="searchQuery"
                type="text"
                placeholder="搜索对话..."
                class="history-search__input"
                @keydown.escape="showSearch = false; searchQuery = ''"
              />
              <button v-if="searchQuery" class="history-search__clear" @click="searchQuery = ''">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>
          </template>
          <!-- Batch mode header -->
          <template v-else>
            <div class="section-header section-header--batch">
              <button class="section-action" @click="exitBatchMode" title="取消">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
              <span>已选 {{ selectedSessionIds.size }} 项</span>
              <button class="section-action" @click="toggleSelectAll" :title="isAllSelected ? '取消全选' : '全选'">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                  <polyline v-if="isAllSelected" points="9 11 12 14 22 4" />
                </svg>
              </button>
            </div>
          </template>

          <div class="history-list">
            <EmptySessionState
              v-if="filteredGroupedSessions.length === 0"
              @new-chat="handleNewChat"
            />
            <template v-for="group in filteredGroupedSessions" :key="group.id">
              <!-- Collapsible group header -->
              <button
                class="history-group-header"
                @click="toggleGroup(group.id)"
              >
                <svg
                  class="history-group-header__arrow"
                  :class="{ 'history-group-header__arrow--expanded': expandedGroups.has(group.id) }"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <polyline points="9 18 15 12 9 6" />
                </svg>
                <svg v-if="group.icon === 'pin'" class="history-group-header__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 17v5M9 10.76V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v6.76" />
                </svg>
                <svg v-else-if="group.icon === 'folder'" class="history-group-header__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                </svg>
                <svg v-else-if="group.icon === 'today'" class="history-group-header__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
                <svg v-else class="history-group-header__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
                <span class="history-group-header__label">{{ group.label }}</span>
                <span class="history-group-header__count">{{ group.sessions.length }}</span>
              </button>

              <!-- Session items (collapsible) -->
              <div v-if="expandedGroups.has(group.id)" class="history-group-content">
                <div
                  v-for="session in group.sessions"
                  :key="session.sessionId"
                  :class="['history-item', {
                    'history-item--active': session.sessionId === chatStore.currentSessionId,
                    'history-item--selected': selectedSessionIds.has(session.sessionId)
                  }]"
                  @click="batchMode ? toggleSessionSelection(session.sessionId) : handleLoadSession(session.sessionId)"
                >
                  <!-- Batch checkbox -->
                  <div v-if="batchMode" class="history-item__checkbox">
                    <svg v-if="selectedSessionIds.has(session.sessionId)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                      <polyline points="9 11 12 14 22 4" />
                    </svg>
                  </div>
                  <div class="history-item__icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                    </svg>
                  </div>
                  <div class="history-item__content">
                    <div
                      v-if="editingSessionId !== session.sessionId"
                      class="history-item__title"
                      @dblclick.stop="startRename(session.sessionId, session.title)"
                    >{{ session.title || '无标题' }}</div>
                    <input
                      v-else
                      ref="renameInputRef"
                      v-model="renameTitle"
                      class="history-item__title-input"
                      @keydown.enter="confirmRename"
                      @keydown.escape="cancelRename"
                      @blur="confirmRename"
                      @click.stop
                    />
                    <div class="history-item__meta">
                      <span class="history-item__mode">{{ getModeLabel(session.mode) }}</span>
                      <span class="history-item__time">{{ formatSessionTime(session.updatedTime) }}</span>
                    </div>
                  </div>
                  <button v-if="!batchMode" class="history-item__graph" @click.stop="showSessionGraph(session.sessionId)" title="查看知识图谱">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="3"/>
                    <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
                  </svg>
                </button>
                <button v-if="!batchMode" class="history-item__extract" @click.stop="handleExtractKnowledge(session.sessionId)" title="提取知识">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"/>
                  </svg>
                </button>
              </div>
              </div>
            </template>

            <!-- Batch action bar -->
            <div v-if="batchMode && selectedSessionIds.size > 0" class="batch-action-bar">
              <button class="batch-action-btn batch-action-btn--primary" @click="handleCreateFolder">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                  <line x1="12" y1="11" x2="12" y2="17" />
                  <line x1="9" y1="14" x2="15" y2="14" />
                </svg>
                新建文件夹
              </button>
              <button class="batch-action-btn batch-action-btn--danger" @click="handleBatchDelete">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6" />
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                </svg>
                删除 ({{ selectedSessionIds.size }})
              </button>
            </div>
          </div>
        </div>

        <div class="sidebar-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3" />
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
            </svg>
            <span>设置</span>
          </div>
          <div class="settings-content">
            <div class="setting-row">
              <span class="setting-label">Session</span>
              <span class="setting-value">{{ chatStore.currentSessionId?.slice(0, 12) }}...</span>
            </div>
            <div class="setting-row">
              <span class="setting-label">历史大小</span>
              <div class="progress-bar">
                <div class="progress-bar__fill" :style="{ width: historySizePercentage + '%' }"></div>
              </div>
              <span class="setting-value">{{ formatFileSize(historySize) }}</span>
            </div>
          </div>
          <div class="settings-actions">
            <button class="settings-btn" @click="handleRefreshStats">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="23 4 23 10 17 10" />
                <polyline points="1 20 1 14 7 14" />
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
              </svg>
            </button>
            <button class="settings-btn" @click="showStorageManager = true" title="存储管理">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <ellipse cx="12" cy="5" rx="9" ry="3" />
                <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
                <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
              </svg>
            </button>
            <button class="settings-btn settings-btn--danger" @click="handleClearHistory">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
              </svg>
            </button>
          </div>
        </div>

        <!-- 用户文件管理 -->
        <div class="sidebar-section">
          <div class="section-header" @click="showUserFiles = !showUserFiles" style="cursor: pointer;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
            </svg>
            <span>我的文件</span>
            <svg class="section-toggle" :class="{ 'section-toggle--expanded': showUserFiles }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </div>
          <div v-if="showUserFiles" class="user-files-section">
            <UserFileManager
              ref="userFileManagerRef"
              :session-id="chatStore.currentSessionId || undefined"
              :show-stats="true"
              :compact="true"
              @file-deleted="handleFileDeleted"
            />
          </div>
        </div>

        <div v-if="statsLastUpdate" class="last-update">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
          </svg>
          <span>{{ formatLastUpdate(statsLastUpdate) }}</span>
        </div>
        </div>
      </aside>

      <!-- Sidebar toggle buttons -->
      <div v-show="!approvalDialogVisible" class="sidebar-toggle-group" :class="{ 'sidebar-toggle-group--expanded': !sidebarCollapsed }">
        <button class="sidebar-toggle-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6" />
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="18" x2="21" y2="18" />
          </svg>
        </button>
        <button class="sidebar-toggle-btn" @click="handleNewChat">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>
      </div>
    </div>

    <approval-dialog
      :visible="approvalDialogVisible"
      :approval="currentApprovalRequest"
      @decided="handleApprovalDecided"
      @close="approvalDialogVisible = false"
    />

    <!-- 知识图谱抽屉 -->
    <el-drawer
      v-model="showKnowledgeGraph"
      :title="viewGraphSessionId ? '对话知识图谱' : '全局知识图谱'"
      direction="rtl"
      :size="isMobile ? '100%' : '60%'"
      class="knowledge-graph-drawer"
    >
      <KnowledgeGraphView :session-id="viewGraphSessionId || undefined" />
    </el-drawer>

    <!-- 移动端底部导航 -->
    <MobileBottomNav
      :active-tab="currentBottomNavTab"
      :is-visible="isMobile"
      @tab-change="handleBottomNavTabChange"
    />

    <!-- 存储管理 -->
    <StorageManager v-model="showStorageManager" />

    <!-- LLM 监控面板 -->
    <LLMMonitorPanel />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useLLMMonitorStore } from '@/stores/llmMonitor'
import { formatFileSize } from '@/utils/format'
import { useRealtimeUpdate } from '@/composables/useRealtime'
import { useNetworkStatus } from '@/composables/useNetworkStatus'
import { useMessageRetry, type PendingMessage } from '@/composables/useMessageRetry'
import { useSessionFolders } from '@/composables/useSessionFolders'
import ChatMessage from '@/components/business/ChatMessage/index.vue'
import FileUploader from '@/components/business/FileUploader/index.vue'
import ApprovalDialog from '@/components/business/ApprovalDialog/index.vue'
import KnowledgeGraphView from '@/views/KnowledgeGraph/index.vue'
import LLMMonitorButton from '@/components/business/LLMMonitorButton/index.vue'
import LLMMonitorPanel from '@/components/business/LLMMonitorPanel/index.vue'
import { getChatSessions, getChatSessionMessages, batchDeleteChatSessions, renameChatSession, extractChatSession, deleteChatSession } from '@/api/ai'
import { getAvailableModels, type AIModelConfigVO } from '@/api/modelConfig'
import { interruptSession } from '@/api/mcp'
import { getSessionStatus } from '@/api/chat'
import type { ApprovalRequest } from '@/api/permission'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ChatHistorySession } from '@/api/ai'
import { imageDB, type StoredImage } from '@/utils/imageDB'
import { fileDB } from '@/utils/fileDB'
import { recordTokenUsage } from '@/utils/tokenTracker'
import { fixHistoryImages } from '@/utils/fixHistoryImages'
import Logo from '@/components/Logo/index.vue'
import MobileBottomNav from '@/components/layout/MobileBottomNav/index.vue'
import EmptySessionState from '@/components/layout/EmptySessionState/index.vue'
import StorageManager from '@/components/business/StorageManager/index.vue'
import UserFileManager from '@/components/business/UserFileManager/index.vue'
import { useSwipeGesture } from '@/composables/useSwipeGesture'
import { debug } from '@/utils/debug'

// 工具函数：Base64 转 Blob
const dataUrlToBlob = (dataUrl: string): Blob => {
  const arr = dataUrl.split(',')
  const mime = arr[0].match(/:(.*?);/)?.[1] || 'application/octet-stream'
  const bstr = atob(arr[1])
  let n = bstr.length
  const u8arr = new Uint8Array(n)
  while (n--) {
    u8arr[n] = bstr.charCodeAt(n)
  }
  return new Blob([u8arr], { type: mime })
}

// 工具函数：获取 MIME 类型
const getMimeType = (fileType: string): string => {
  const mimeTypes: Record<string, string> = {
    pdf: 'application/pdf',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    doc: 'application/msword',
    xls: 'application/vnd.ms-excel',
    ppt: 'application/vnd.ms-powerpoint',
    mp3: 'audio/mpeg',
    mp4: 'video/mp4',
    png: 'image/png',
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    gif: 'image/gif',
    webp: 'image/webp',
    svg: 'image/svg+xml',
    txt: 'text/plain',
    md: 'text/markdown',
    json: 'application/json',
    zip: 'application/zip'
  }
  return mimeTypes[fileType.toLowerCase()] || 'application/octet-stream'
}

// 工具函数：格式化字节
const formatBytes = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
}

const chatStore = useChatStore()
const llmMonitorStore = useLLMMonitorStore()
const userStore = useUserStore()
const messageInput = ref('')
const selectedModel = ref('')
const chatMode = ref('lite-task')  // 默认 Lite 模式，与后端 ChatMode.LITE_TASK 一致
const showKnowledgeGraph = ref(false)
const showStorageManager = ref(false)
const showUserFiles = ref(true)
const userFileManagerRef = ref<InstanceType<typeof UserFileManager>>()
const viewGraphSessionId = ref<string | null>(null) // 当前查看的对话图谱 sessionId，null 表示全局图谱

// Mobile drawer state
const drawerVisible = ref(false)

const toggleDrawer = () => {
  drawerVisible.value = !drawerVisible.value
}

const closeDrawer = () => {
  drawerVisible.value = false
}

// Mobile bottom nav
const currentBottomNavTab = ref<'chats' | 'new' | 'settings'>('chats')

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

const isMac = computed(() => {
  return navigator.platform.toUpperCase().indexOf('MAC') >= 0 ||
    navigator.userAgent.indexOf('Mac OS') !== -1
})

// 快捷键修饰符（用于显示快捷键提示）
const _shortcutModifier = computed(() => isMac.value ? '⌘' : 'Ctrl')
void _shortcutModifier

const handleBottomNavTabChange = (tab: 'chats' | 'new' | 'settings') => {
  currentBottomNavTab.value = tab
  if (tab === 'chats') {
    toggleDrawer()
  } else if (tab === 'new') {
    handleNewChat()
  } else if (tab === 'settings') {
    window.location.href = '/settings'
  }
}

// Swipe gesture for drawer
const mainContentRef = ref<HTMLElement | null>(null)
useSwipeGesture(mainContentRef, {
  onSwipeRight: () => {
    if (window.innerWidth <= 1024 && !drawerVisible.value) {
      drawerVisible.value = true
    }
  },
  onSwipeLeft: () => {
    if (window.innerWidth <= 1024 && drawerVisible.value) {
      drawerVisible.value = false
    }
  }
})

// 网络状态管理
const networkStatus = useNetworkStatus({
  onOnline: () => {
    ElMessage.success('网络已恢复')
    // 检查是否有待恢复的会话
    checkSessionRecovery()
  },
  onOffline: () => {
    ElMessage.warning('网络已断开')
  }
})

// 消息重试管理
const messageRetry = useMessageRetry({
  maxRetries: 3,
  autoRetryOnOnline: true,
  sendFunction: async (pendingMsg: PendingMessage) => {
    // 恢复请求数据
    const data = pendingMsg.requestData
    messageInput.value = data.content
    selectedModel.value = data.model
    chatMode.value = data.mode
    if (data.sessionId) {
      chatStore.setSessionId(data.sessionId)
    }
    // 执行发送
    await executeSend(data)
  },
  onRetrySuccess: (_msg) => {
    ElMessage.success('消息重试成功')
  },
  onRetryFailed: (_msg, error) => {
    ElMessage.error(`重试失败: ${error.message}`)
  }
})

// 网络恢复后检查会话状态
const checkSessionRecovery = async () => {
  const sessionId = chatStore.currentSessionId
  if (!sessionId || !chatStore.isLoading) return

  try {
    const status = await getSessionStatus(sessionId)
    if (status.isExecuting) {
      ElMessage.info('检测到正在执行的会话，尝试恢复...')
      // 会话仍在执行，可以尝试重新连接 SSE
      // 这里简化处理，提示用户可以重试
    } else if (status.isCancelled) {
      ElMessage.info('会话已被取消')
      chatStore.setLoading(false)
    } else {
      // 会话已结束
      chatStore.setLoading(false)
    }
  } catch (e) {
    console.error('检查会话状态失败:', e)
  }
}

// Sidebar state
const sidebarCollapsed = ref(true)

// 网络恢复提示显示状态
const showNetworkRecovered = ref(false)

// 监听网络恢复
watch(() => networkStatus.isOnline.value, (isOnline, wasOnline) => {
  if (isOnline && !wasOnline) {
    // 网络从离线变为在线
    showNetworkRecovered.value = true
    // 5秒后自动隐藏
    setTimeout(() => {
      showNetworkRecovered.value = false
    }, 5000)
  }
})

// 重试待发送的消息
const handleRetryPending = async () => {
  const pending = chatStore.pendingRequest
  if (!pending) return

  showNetworkRecovered.value = false
  chatStore.clearPendingRequest()

  // 恢复请求数据
  messageInput.value = pending.content
  selectedModel.value = pending.model
  chatMode.value = pending.mode
  if (pending.sessionId) {
    chatStore.setSessionId(pending.sessionId)
  }

  // 执行发送
  await executeSend(pending)
}

// Search state
const showSearch = ref(false)
const searchQuery = ref('')
const searchInputRef = ref<HTMLInputElement>()

// Batch mode state
const batchMode = ref(false)
const selectedSessionIds = ref<Set<string>>(new Set())

// Rename state
const editingSessionId = ref<string | null>(null)
const renameTitle = ref('')
const renameInputRef = ref<HTMLInputElement[]>()

// 模式名称 - 与后端 ChatMode.java 保持一致（使用连字符格式）
const chatModes = [
  { value: 'lite-task', label: 'Lite', desc: '轻量模式，纯对话无工具调用' },
  { value: 'medium-task', label: 'Pro', desc: '专业模式，支持工具调用的 ReAct 推理' },
  { value: 'complex-task', label: 'ProPlus', desc: '高级模式，复杂任务规划与执行（Plan-Execute）' }
]

// 知识提取开关状态
const enableKnowledgeExtraction = ref(false)

// 模式到 API URL 的映射
const getApiUrl = (mode: string) => {
  const apiUrlMap: Record<string, string> = {
    'lite-task': '/api/v16/chat/lite-task',
    'medium-task': '/api/v16/chat/medium-task',
    'complex-task': '/api/v16/chat/complex-task'
  }
  return apiUrlMap[mode] || '/api/v16/chat/lite-task'
}

const getModeLabel = (mode: string) => {
  const modeMap: Record<string, string> = {
    'lite-task': 'Lite',
    'medium-task': 'Pro',
    'complex-task': 'ProPlus',
    'mcp': 'MCP对话'
  }
  return modeMap[mode] || mode || 'Lite'
}
const availableModels = ref<AIModelConfigVO[]>([])
const messagesContainer = ref<HTMLElement>()
const isAppendMode = ref(false)
const appendInput = ref('')
const approvalDialogVisible = ref(false)
const currentApprovalRequest = ref<ApprovalRequest | null>(null)
const MAX_HISTORY_SIZE = 16 * 1024
const abortController = ref<AbortController | null>(null)
const connectionStatus = ref<'connecting' | 'streaming' | 'error' | 'idle'>('idle')
const currentAgentPhase = ref<'thinking' | 'tool_use' | 'generating' | 'idle'>('idle')

// 多模态文件上传
const fileUploaderRef = ref<InstanceType<typeof FileUploader>>()
type PendingFile = { url: string; previewUrl?: string; name: string; type: string; mimeType: string; fileSize?: number }
const pendingFiles = ref<PendingFile[]>([])

const {
  lastUpdate: statsLastUpdate,
  stop: stopStatsUpdate
} = useRealtimeUpdate(
  () => Promise.resolve(),
  {
    interval: 10000,
    enabled: false
  }
)

const historySize = computed(() => {
  return JSON.stringify(chatStore.messages).length
})

const historySizePercentage = computed(() => {
  return Math.min((historySize.value / MAX_HISTORY_SIZE) * 100, 100)
})

const connectionStatusText = computed(() => {
  switch (connectionStatus.value) {
    case 'connecting': return '正在连接...'
    case 'streaming': return `接收中... (${currentAgentPhase.value === 'thinking' ? '思考中' : currentAgentPhase.value === 'tool_use' ? '工具调用中' : '生成回复'})`
    case 'error': return '连接异常'
    case 'idle': return ''
    default: return ''
  }
})

const currentAgentPhaseText = computed(() => {
  switch (currentAgentPhase.value) {
    case 'thinking': return '思考中'
    case 'tool_use': return '工具调用中'
    case 'generating': return '生成回复'
    default: return ''
  }
})

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const formatLastUpdate = (timestamp: number) => {
  const now = Date.now()
  const diff = now - timestamp

  if (diff < 60000) {
    return '刚刚'
  } else if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}m`
  } else if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}h`
  } else {
    return `${Math.floor(diff / 86400000)}d`
  }
}

const readSSEStream = async (
  reader: ReadableStreamDefaultReader<Uint8Array>,
  signal: AbortSignal,
  onData: (data: any) => void,
  onDone: () => void,
  onError: (error: any) => void
) => {
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    if (signal.aborted) {
      onError({ error: '用户取消了请求', errorType: 'USER_CANCELLED', retryable: true })
      return
    }
    const { done, value } = await reader.read()
    if (done || signal.aborted) break

    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n\r?\n/)
    buffer = parts.pop() || ''

    for (const part of parts) {
      if (signal.aborted) {
        onError({ error: '用户取消了请求', errorType: 'USER_CANCELLED', retryable: true })
        return
      }
      const lines = part.split(/\r?\n/)
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data: ')) {
          const data = trimmed.slice(6)
          if (data === '[DONE]') {
            onDone()
            continue
          }
          if (!data) continue

          try {
            const jsonData = JSON.parse(data)
            if (jsonData.error && !jsonData.done) {
              onError(jsonData)
              continue
            }
            onData(jsonData)
            if (jsonData.done === true) {
              onDone()
              return
            }
          } catch (e) {
            console.error('Failed to parse SSE data:', e, 'raw:', data)
          }
        }
      }
    }
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    const parts = buffer.split(/\r?\n\r?\n/)
    for (const part of parts) {
      const lines = part.split(/\r?\n/)
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data: ')) {
          const data = trimmed.slice(6)
          if (data === '[DONE]' || !data) continue
          try {
            const jsonData = JSON.parse(data)
            if (jsonData.error && !jsonData.done) {
              onError(jsonData)
              continue
            }
            onData(jsonData)
            if (jsonData.done === true) {
              onDone()
              return
            }
          } catch (e) {
            console.error('Failed to parse SSE data:', e, 'raw:', data)
          }
        }
      }
    }
  }
}

// 处理文件变化 - 合并新上传的文件到现有列表
const handleFileChange = (uploadedFiles: PendingFile[]) => {
  pendingFiles.value = [...pendingFiles.value, ...uploadedFiles]
}

// 移除待发送的文件
const removePendingFile = (index: number) => {
  pendingFiles.value.splice(index, 1)
}

const handleSend = async () => {
  const content = messageInput.value.trim()
  const hasFiles = pendingFiles.value.length > 0

  // 必须有文本或文件
  if (!content && !hasFiles) return

  // 网络检测
  if (!networkStatus.isOnline.value) {
    ElMessage.warning('网络已断开，请检查网络连接')
    // 保存待发送的消息，网络恢复后可重试
    messageRetry.addPendingMessage({
      content,
      model: selectedModel.value,
      mode: chatMode.value,
      sessionId: chatStore.currentSessionId || '',
      userId: userStore.user?.id ? Number(userStore.user.id) : undefined,
      images: pendingFiles.value.filter(f => f.type === 'image').map(f => f.previewUrl || f.url),
      documents: pendingFiles.value.filter(f => f.type === 'document').map(f => f.url),
      audios: pendingFiles.value.filter(f => f.type === 'audio').map(f => f.url),
      videos: pendingFiles.value.filter(f => f.type === 'video').map(f => f.url)
    })
    return
  }

  if (chatStore.isLoading) {
    ElMessage.warning('请等待当前回复完成后再发送')
    return
  }

  if (!selectedModel.value || availableModels.value.length === 0) {
    ElMessage.warning('请先配置模型后再发送消息')
    return
  }

  // 准备文件 URL 列表
  // 图片使用 previewUrl（base64 数据）以确保可以离线显示；其他文件使用 url（temp:// 协议）
  const imageUrls = pendingFiles.value.filter(f => f.type === 'image').map(f => f.previewUrl || f.url)
  const documentUrls = pendingFiles.value.filter(f => f.type === 'document').map(f => f.url)
  const audioUrls = pendingFiles.value.filter(f => f.type === 'audio').map(f => f.url)
  const videoUrls = pendingFiles.value.filter(f => f.type === 'video').map(f => f.url)

  // 构建请求数据
  const requestData = {
    content,
    model: selectedModel.value,
    mode: chatMode.value,
    sessionId: chatStore.currentSessionId || '',
    userId: userStore.user?.id ? Number(userStore.user.id) : undefined,
    images: imageUrls,
    documents: documentUrls,
    audios: audioUrls,
    videos: videoUrls
  }

  // 保存最后一次请求（用于重试）
  chatStore.saveLastRequest(requestData)

  await executeSend(requestData)
}

// 执行发送消息
const executeSend = async (requestData: {
  content: string
  model: string
  mode: string
  sessionId: string
  userId?: number
  images?: string[]
  documents?: string[]
  audios?: string[]
  videos?: string[]
}) => {
  const { content, model, mode, sessionId, userId, images = [], documents = [], audios = [], videos = [] } = requestData
  const hasFiles = images.length > 0 || documents.length > 0 || audios.length > 0 || videos.length > 0

  const userMessage: any = {
    id: `${Date.now()}-user`,
    role: 'user',
    content,
    thinking: '',
    toolCalls: [],
    done: true,
    loading: false,
    timestamp: Date.now()
  }

  // 如果有文件，添加到消息中（使用 URL 构建简化结构）
  if (hasFiles) {
    if (images.length > 0) {
      userMessage.images = images.map(url => ({
        type: 'image_url',
        imageUrl: { url },
        name: url.split('/').pop() || '图片',
        mimeType: 'image/jpeg'
      }))
    }
    if (documents.length > 0) {
      userMessage.documents = documents.map(url => ({
        url,
        name: url.split('/').pop() || '文档',
        mimeType: 'application/octet-stream'
      }))
    }
    if (audios.length > 0) {
      userMessage.audios = audios.map(url => ({
        url,
        name: url.split('/').pop() || '音频',
        mimeType: 'audio/mpeg'
      }))
    }
    if (videos.length > 0) {
      userMessage.videos = videos.map(url => ({
        url,
        name: url.split('/').pop() || '视频',
        mimeType: 'video/mp4'
      }))
    }
  }

  chatStore.addMessage(userMessage)
  messageInput.value = ''
  // 清空待发送的文件
  pendingFiles.value = []
  await scrollToBottom()

  const controller = new AbortController()
  abortController.value = controller

  try {
    chatStore.setLoading(true)
    connectionStatus.value = 'connecting'
    currentAgentPhase.value = 'thinking'

    const assistantMsgId = `${Date.now()}-assistant`
    chatStore.addMessage({
      id: assistantMsgId,
      role: 'assistant',
      content: '',
      thinking: '',
      toolCalls: [],
      done: false,
      loading: true,
      error: undefined,
      timestamp: Date.now()
    })
    await scrollToBottom()

    const apiUrl = getApiUrl(mode)
    const effectiveSessionId = sessionId || chatStore.currentSessionId || `session-${Date.now()}`
    if (!chatStore.currentSessionId) {
      chatStore.setSessionId(effectiveSessionId)
    }

    // 构建请求体
    const requestBody: any = {
      message: content,
      sessionId: effectiveSessionId,
      model: model,
      userId: userId || null,
      enableKnowledgeExtraction: enableKnowledgeExtraction.value,
      history: chatStore.messages.slice(0, -2).map(m => {
        let msgContent = m.content || ''
        const fileDescriptions: string[] = []

        // 收集图片描述
        if (m.images && m.images.length > 0) {
          const imageDescriptions = m.images.map((img: any) => {
            const name = img.name || '未知图片'
            return `【图片】${name}`
          })
          fileDescriptions.push(...imageDescriptions)
        }

        // 收集文档描述
        if (m.documents && m.documents.length > 0) {
          const docDescriptions = m.documents.map((doc: any) => {
            const name = doc.name || '未知文件'
            const ext = name.split('.').pop()?.toUpperCase() || '文件'
            return `【${ext}】${name}`
          })
          fileDescriptions.push(...docDescriptions)
        }

        // 收集音频描述
        if (m.audios && m.audios.length > 0) {
          const audioDescriptions = m.audios.map((audio: any) => {
            const name = audio.name || '未知音频'
            const ext = name.split('.').pop()?.toUpperCase() || '音频'
            return `【${ext}】${name}`
          })
          fileDescriptions.push(...audioDescriptions)
        }

        // 收集视频描述
        if (m.videos && m.videos.length > 0) {
          const videoDescriptions = m.videos.map((video: any) => {
            const name = video.name || '未知视频'
            const ext = name.split('.').pop()?.toUpperCase() || '视频'
            return `【${ext}】${name}`
          })
          fileDescriptions.push(...videoDescriptions)
        }

        // 构建最终消息内容
        if (fileDescriptions.length > 0) {
          msgContent = `用户上传 ${fileDescriptions.join('、')}` + (msgContent ? `，并输入：${msgContent}` : '')
        }

        return {
          role: m.role,
          content: msgContent
        }
      })
    }

    // 添加文件 URL
    if (images.length > 0) requestBody.images = images
    if (documents.length > 0) requestBody.documents = documents
    if (audios.length > 0) requestBody.audios = audios
    if (videos.length > 0) requestBody.videos = videos

    const response = await fetch(apiUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestBody),
      signal: controller.signal
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }

    connectionStatus.value = 'streaming'

    const reader = response.body?.getReader()

    if (reader) {
      const assistantMsgId = chatStore.messages[chatStore.messages.length - 1]?.id

      await readSSEStream(
        reader,
        controller.signal,
        (jsonData) => {
          const msgIndex = chatStore.messages.findIndex(m => m.id === assistantMsgId)
          if (msgIndex === -1) return
          const lastMsg = chatStore.messages[msgIndex]

          const msgType = jsonData.type || 'result'
          // 调试：打印所有 SSE 消息类型
          if (msgType !== 'result' && msgType !== 'cost') {
            console.log('[SSE] 收到消息类型:', msgType, jsonData)
          }

          if (msgType === 'thinking' || msgType === 'thinking_step' || msgType === 'thinking_content' || msgType === 'thinking_complete') {
            currentAgentPhase.value = 'thinking'
            const thinkingContent = jsonData.reasoningContent || jsonData.content
            if (thinkingContent) {
              chatStore.messages[msgIndex] = {
                ...lastMsg,
                thinking: lastMsg.thinking + thinkingContent
              }
            }
          } else if (msgType === 'cost') {
            const inputTokens = jsonData.inputTokens || 0
            const outputTokens = jsonData.outputTokens || 0
            const cost = jsonData.cost || 0
            const modelId = jsonData.model || requestBody.model || 'unknown'
            const sessionTotalCost = jsonData.totalCost || undefined
            const sessionTotalTokens = jsonData.totalTokens || undefined

            if (inputTokens > 0 || outputTokens > 0 || cost > 0) {
              recordTokenUsage({
                sessionId: effectiveSessionId,
                modelId,
                inputTokens,
                outputTokens,
                cost,
                sessionTotalCost,
                sessionTotalTokens
              })
            }
          } else if (msgType === 'result') {
            currentAgentPhase.value = 'generating'
            const newContent = jsonData.content != null ? lastMsg.content + jsonData.content : lastMsg.content
            const updates: Partial<typeof lastMsg> = {
              content: newContent
            }
            if (jsonData.thought) {
              updates.thinking = jsonData.thought
            }
            if (jsonData.done === true) {
              updates.done = true
              updates.loading = false
              updates.error = undefined
            }
            if (jsonData.error && jsonData.done) {
              updates.error = jsonData.error
            }
            chatStore.messages[msgIndex] = { ...lastMsg, ...updates }
            
            const toolCallsData = jsonData.toolCalls || jsonData.tool_calls
            if (toolCallsData && Array.isArray(toolCallsData) && toolCallsData.length > 0) {
              currentAgentPhase.value = 'tool_use'
              const newToolCalls = toolCallsData.map((tc: any) => ({
                name: tc.function?.name || tc.name || 'unknown',
                status: 'running' as const,
                result: undefined
              }))
              const existingNames = new Set(lastMsg.toolCalls.map((t: any) => t.name))
              const mergedToolCalls = [...lastMsg.toolCalls]
              newToolCalls.forEach((tc: any) => {
                if (!existingNames.has(tc.name)) {
                  mergedToolCalls.push(tc)
                }
              })
              chatStore.messages[msgIndex] = { ...chatStore.messages[msgIndex], toolCalls: mergedToolCalls }
            }
          } else if (msgType === 'compression') {
            if (jsonData.content) {
              chatStore.messages[msgIndex] = {
                ...lastMsg,
                thinking: lastMsg.thinking + jsonData.content + '\n'
              }
            }
          } else if (msgType === 'error') {
            currentAgentPhase.value = 'idle'
            chatStore.messages[msgIndex] = {
              ...lastMsg,
              error: jsonData.error,
              errorType: jsonData.errorType,
              errorTitle: jsonData.errorTitle,
              errorSuggestion: jsonData.errorSuggestion,
              retryable: jsonData.retryable,
              done: true,
              loading: false
            }
            scrollToBottom()
          } else if (msgType === 'plan') {
            currentAgentPhase.value = 'tool_use'
            const planData = jsonData.tool_calls || {}
            chatStore.messages[msgIndex] = {
              ...lastMsg,
              taskPlan: {
                planId: planData.planId || '',
                summary: planData.summary || '',
                totalSteps: planData.totalSteps || 0,
                completedSteps: 0,
                status: 'executing',
                steps: (planData.steps || []).map((s: any) => ({
                  stepNumber: s.stepNumber,
                  description: s.description || '',
                  toolName: s.toolName || undefined,
                  status: 'pending' as const
                }))
              }
            }
          } else if (msgType === 'plan_failed') {
            // 【改进】处理计划生成失败事件
            currentAgentPhase.value = 'tool_use'
            const failedData = jsonData.tool_calls || {}
            debug.log('[Chat] 计划生成失败，切换到 ReAct 模式:', failedData.reason)
            // 可以在 UI 上显示提示，但继续执行
          } else if (msgType === 'plan_complete') {
            // 【修复】计划执行完成/失败，更新 taskPlan 状态
            const completeData = jsonData.tool_calls || {}
            if (lastMsg.taskPlan) {
              const completedSteps = completeData.completedSteps ?? lastMsg.taskPlan.totalSteps
              const steps = lastMsg.taskPlan.steps.map(s => {
                // 未完成的步骤标记为 skipped
                if (s.status !== 'completed' && s.status !== 'failed' && s.status !== 'timeout') {
                  return { ...s, status: (completeData.status === 'failed' && s.stepNumber === completeData.failedStep) ? 'failed' as const : 'completed' as const }
                }
                return s
              })
              chatStore.messages[msgIndex] = {
                ...lastMsg,
                taskPlan: {
                  ...lastMsg.taskPlan,
                  status: completeData.status || 'completed',
                  completedSteps,
                  steps
                }
              }
            }
          } else if (msgType === 'step_start') {
            currentAgentPhase.value = 'tool_use'
            const stepData = jsonData.tool_calls || {}
            if (lastMsg.taskPlan) {
              const steps = lastMsg.taskPlan.steps.map(s => 
                s.stepNumber === stepData.stepNumber ? { ...s, status: 'executing' as const } : s
              )
              chatStore.messages[msgIndex] = {
                ...lastMsg,
                taskPlan: { ...lastMsg.taskPlan, steps, status: 'executing' }
              }
            }
          } else if (msgType === 'step_complete') {
            const stepData = jsonData.tool_calls || {}
            if (lastMsg.taskPlan) {
              const steps = lastMsg.taskPlan.steps.map(s =>
                s.stepNumber === stepData.stepNumber
                  ? { ...s, status: 'completed' as const, result: stepData.result, duration: stepData.duration }
                  : s
              )
              // 【修复】准确计算已完成步骤数，优先使用后端值，否则实时计算
              const actualCompleted = stepData.completedSteps
                ? stepData.completedSteps
                : steps.filter(s => s.status === 'completed').length
              chatStore.messages[msgIndex] = {
                ...lastMsg,
                taskPlan: {
                  ...lastMsg.taskPlan,
                  steps,
                  completedSteps: actualCompleted 
                }
              }
            }
          } else if (msgType === 'step_error') {
            const stepData = jsonData.tool_calls || {}
            if (lastMsg.taskPlan) {
              const steps = lastMsg.taskPlan.steps.map(s =>
                s.stepNumber === stepData.stepNumber
                  ? { ...s, status: 'failed' as const, error: stepData.error }
                  : s
              )
              chatStore.messages[msgIndex] = { ...lastMsg, taskPlan: { ...lastMsg.taskPlan, steps } }
            }
          } else if (msgType === 'step_progress') {
            // 【新增】步骤进度更新
            const stepData = jsonData.tool_calls || {}
            if (lastMsg.taskPlan) {
              const steps = lastMsg.taskPlan.steps.map(s =>
                s.stepNumber === stepData.stepNumber
                  ? { ...s, iteration: stepData.iteration, maxIterations: stepData.maxIterations }
                  : s
              )
              chatStore.messages[msgIndex] = { ...lastMsg, taskPlan: { ...lastMsg.taskPlan, steps } }
            }
          } else if (msgType === 'step_timeout') {
            // 【新增】步骤执行超时
            const stepData = jsonData.tool_calls || {}
            if (lastMsg.taskPlan) {
              const steps = lastMsg.taskPlan.steps.map(s =>
                s.stepNumber === stepData.stepNumber
                  ? { ...s, status: 'timeout' as const, elapsedMs: stepData.elapsedMs }
                  : s
              )
              chatStore.messages[msgIndex] = { ...lastMsg, taskPlan: { ...lastMsg.taskPlan, steps } }
            }
          } else if (msgType === 'step_tool_call') {
            // 【新增】步骤内工具调用
            const stepData = jsonData.tool_calls || {}
            currentAgentPhase.value = 'tool_use'
            if (lastMsg.taskPlan) {
              const steps = lastMsg.taskPlan.steps.map(s =>
                s.stepNumber === stepData.stepNumber
                  ? { ...s, currentTool: stepData.toolName, currentServer: stepData.serverName }
                  : s
              )
              chatStore.messages[msgIndex] = { ...lastMsg, taskPlan: { ...lastMsg.taskPlan, steps } }
            }
          } else if (msgType === 'plan_adjusted') {
            // 【新增】计划动态调整
            const adjustData = jsonData.tool_calls || {}
            if (lastMsg.taskPlan) {
              const adjustedSteps = adjustData.adjustedSteps || []
              const steps = adjustedSteps.map((s: any) => ({
                stepNumber: s.stepNumber,
                description: s.description,
                toolName: s.toolName,
                status: s.status?.toLowerCase() || 'pending',
                result: undefined
              }))
              chatStore.messages[msgIndex] = {
                ...lastMsg,
                taskPlan: {
                  ...lastMsg.taskPlan,
                  steps,
                  adjustmentCount: adjustData.adjustmentCount,
                  adjustmentReason: adjustData.reason,
                  planSummary: adjustData.planSummary || lastMsg.taskPlan.planSummary
                }
              }
            }
          } else if (msgType === 'approval_request') {
            const approvalData = jsonData.tool_calls || {}
            currentApprovalRequest.value = {
              requestId: approvalData.requestId || '',
              sessionId: approvalData.sessionId || chatStore.currentSessionId,
              toolName: approvalData.toolName || '',
              serverName: approvalData.serverName || '',
              operation: approvalData.operation || 'execute',
              arguments: approvalData.arguments || '{}',
              permissionLevel: approvalData.permissionLevel || 'confirm',
              description: approvalData.description || '',
              userId: approvalData.userId || 0
            }
            approvalDialogVisible.value = true
          } else if (msgType === 'approval_result') {
            approvalDialogVisible.value = false
            currentApprovalRequest.value = null
          } else if (msgType === 'thinking_tool_call') {
            currentAgentPhase.value = 'tool_use'
            const toolData = jsonData.tool_calls || {}
            const toolNames = toolData.toolNames || []
            if (toolNames.length > 0) {
              let newThinking = lastMsg.thinking + `\n准备调用工具 (${toolNames.length} 个)\n\n`
              toolNames.forEach((toolName: string, idx: number) => {
                newThinking += `${idx + 1}. ${toolName}\n`
              })
              newThinking += '\n'
              const mergedToolCalls = [...lastMsg.toolCalls]
              toolNames.forEach((tName: string) => {
                const exists = mergedToolCalls.some((t: any) => t.name === tName)
                if (!exists) {
                  mergedToolCalls.push({ name: tName, status: 'running', result: undefined })
                }
              })
              chatStore.messages[msgIndex] = { ...lastMsg, thinking: newThinking, toolCalls: mergedToolCalls }
            }
          } else if (msgType === 'thinking_tool_result' || msgType === 'thinking_tool_error') {
            const toolData = jsonData.tool_calls || {}
            const serverName = toolData.serverName || ''
            const toolName = toolData.toolName || ''
            const success = toolData.success !== false
            const preview = toolData.preview || ''
            const error = toolData.error || ''

            let newThinking = lastMsg.thinking + `\n${success ? '完成' : '失败'} 工具调用 ${serverName}.${toolName}\n\n`
            if (success) {
              newThinking += `返回结果:\n${preview}\n\n`
            } else {
              newThinking += `错误信息:\n${error}\n\n`
            }

            const updatedToolCalls = lastMsg.toolCalls.map((t: any) => {
              if (t.name === toolName || t.name === `${serverName}.${toolName}`) {
                return { ...t, status: success ? 'success' : 'error', result: success ? preview : error }
              }
              return t
            })
            chatStore.messages[msgIndex] = { ...lastMsg, thinking: newThinking, toolCalls: updatedToolCalls }
          } else if (msgType === 'image') {
            currentAgentPhase.value = 'generating'
            const imageDataUrl = jsonData.content
            // 使用后端返回的 resourceId 或生成稳定的 ID
            const imageId = jsonData.resourceId || `${effectiveSessionId}-${Date.now()}`
            console.log('[Chat] 收到图片消息, resourceId:', jsonData.resourceId, '生成的 imageId:', imageId)
            if (imageDataUrl) {
              const storedImage: StoredImage = {
                id: imageId,
                sessionId: effectiveSessionId,
                prompt: messageInput.value || lastMsg.content.slice(0, 200),
                dataUrl: imageDataUrl,
                createdAt: Date.now(),
                metadata: jsonData.metadata
              }
              console.log('[Chat] 准备保存图片到 IndexedDB, id:', imageId, 'sessionId:', effectiveSessionId)
              // 先保存图片到 IndexedDB，完成后再更新消息内容
              imageDB.save(storedImage).then(() => {
                console.log('[Chat] 图片已保存到 IndexedDB:', storedImage.id)
                // 保存完成后再更新消息内容，确保加载时数据已存在
                const imageMarkdown = `![生成的图片](image:${imageId})\n\n`
                chatStore.messages[msgIndex] = {
                  ...chatStore.messages[msgIndex],
                  content: (chatStore.messages[msgIndex]?.content || '') + imageMarkdown
                }
              }).catch((err) => {
                console.error('[Chat] 保存图片失败:', err)
                // 即使保存失败，也显示图片（使用 dataUrl）
                const imageMarkdown = `![生成的图片](${imageDataUrl})\n\n`
                chatStore.messages[msgIndex] = {
                  ...chatStore.messages[msgIndex],
                  content: (chatStore.messages[msgIndex]?.content || '') + imageMarkdown
                }
              })
            }
          } else if (msgType === 'audio') {
            currentAgentPhase.value = 'generating'
            const audioDataUrl = jsonData.content
            const audioId = jsonData.resourceId || `${effectiveSessionId}-${Date.now()}`
            if (audioDataUrl) {
              // 将 Base64 转换为 Blob 存储
              const blob = dataUrlToBlob(audioDataUrl)

              fileDB.save({
                id: audioId,
                sessionId: effectiveSessionId,
                fileName: `audio_${Date.now()}.mp3`,
                fileType: 'audio',
                blob: blob,
                metadata: { generationTimeMs: jsonData.generationTimeMs, mimeType: 'audio/mpeg' }
              }).then(() => {
                debug.log('[Chat] 音频已保存到 IndexedDB:', audioId, formatBytes(blob.size))
                // 保存成功后再更新消息内容
                const audioMarkdown = `\n\n[audio:${audioId}]\n\n`
                chatStore.messages[msgIndex] = {
                  ...chatStore.messages[msgIndex],
                  content: (chatStore.messages[msgIndex]?.content || '') + audioMarkdown
                }
              }).catch((err) => {
                console.error('[Chat] 保存音频失败:', err)
                // 保存失败时，显示错误提示
                const errorMsg = `\n\n⚠️ 音频保存失败，请稍后重试\n\n`
                chatStore.messages[msgIndex] = {
                  ...chatStore.messages[msgIndex],
                  content: (chatStore.messages[msgIndex]?.content || '') + errorMsg
                }
              })
            }
          } else if (msgType === 'video') {
            currentAgentPhase.value = 'generating'
            const videoUrl = jsonData.content
            const videoId = jsonData.resourceId || `${effectiveSessionId}-${Date.now()}`
            if (videoUrl) {
              // 将 Base64 转换为 Blob 存储
              const blob = dataUrlToBlob(videoUrl)

              fileDB.save({
                id: videoId,
                sessionId: effectiveSessionId,
                fileName: `video_${Date.now()}.mp4`,
                fileType: 'video',
                blob: blob,
                metadata: { generationTimeMs: jsonData.generationTimeMs, mimeType: 'video/mp4' }
              }).then(() => {
                debug.log('[Chat] 视频已保存到 IndexedDB:', videoId, formatBytes(blob.size))
                // 保存成功后再更新消息内容
                const videoMarkdown = `\n\n[video:${videoId}]\n\n`
                chatStore.messages[msgIndex] = {
                  ...chatStore.messages[msgIndex],
                  content: (chatStore.messages[msgIndex]?.content || '') + videoMarkdown
                }
              }).catch((err) => {
                console.error('[Chat] 保存视频失败:', err)
                // 保存失败时，显示错误提示
                const errorMsg = `\n\n⚠️ 视频保存失败，请稍后重试\n\n`
                chatStore.messages[msgIndex] = {
                  ...chatStore.messages[msgIndex],
                  content: (chatStore.messages[msgIndex]?.content || '') + errorMsg
                }
              })
            }
          } else if (msgType === 'file' || msgType === 'pdf') {
            currentAgentPhase.value = 'generating'
            const fileDataUrl = jsonData.content
            const fileName = jsonData.fileName || jsonData.filename || `document_${Date.now()}.pdf`
            const fileType = jsonData.fileType || jsonData.file_type || 'other'
            const fileSize = jsonData.fileSize || 0
            // 使用后端返回的 resourceId 或生成稳定的 ID
            const fileId = jsonData.resourceId || `${effectiveSessionId}-${Date.now()}`

            // 调试日志
            console.log('[Chat] 收到文件消息:', {
              fileId,
              fileName,
              fileType,
              fileSize,
              hasContent: !!fileDataUrl,
              contentLength: fileDataUrl ? fileDataUrl.length : 0,
              contentPreview: fileDataUrl ? fileDataUrl.substring(0, 50) + '...' : '(empty)'
            })

            if (fileDataUrl) {
              // 将 Base64 转换为 Blob 存储（减少体积约 33%）
              const blob = dataUrlToBlob(fileDataUrl)

              fileDB.save({
                id: fileId,
                sessionId: effectiveSessionId,
                fileName: fileName,
                fileType: fileType as any,
                blob: blob,
                metadata: {
                  title: jsonData.title,
                  author: jsonData.author,
                  fileSize: fileSize || blob.size,
                  generationTimeMs: jsonData.generationTimeMs,
                  mimeType: getMimeType(fileType)
                }
              }).then(() => {
                debug.log('[Chat] 文件已保存到 IndexedDB:', fileId, fileName, formatBytes(blob.size))
                // 保存成功后再更新消息内容
                const fileMarkdown = `\n\n[file:${fileId}:${fileName}]\n\n`
                chatStore.messages[msgIndex] = {
                  ...chatStore.messages[msgIndex],
                  content: (chatStore.messages[msgIndex]?.content || '') + fileMarkdown
                }
              }).catch((err) => {
                console.error('[Chat] 保存文件失败:', err)
                // 保存失败时，显示错误提示
                const errorMsg = `\n\n⚠️ 文件 "${fileName}" 保存失败，请稍后重试\n\n`
                chatStore.messages[msgIndex] = {
                  ...chatStore.messages[msgIndex],
                  content: (chatStore.messages[msgIndex]?.content || '') + errorMsg
                }
              })
            } else {
              console.error('[Chat] 文件消息缺少 content 字段:', jsonData)
              const errorMsg = `\n\n⚠️ 文件 "${fileName}" 加载失败：缺少文件内容\n\n`
              chatStore.messages[msgIndex] = {
                ...chatStore.messages[msgIndex],
                content: (chatStore.messages[msgIndex]?.content || '') + errorMsg
              }
            }
          } else if (msgType === 'reflection') {
            // 反思结果：显示修正提示
            const reflectionData = jsonData.toolCalls || jsonData.tool_calls || {}
            if (reflectionData.needsCorrection && reflectionData.correctionHint) {
              const reflectionHint = `\n\n💡 **结果验证提示**: ${reflectionData.correctionHint}\n`
              chatStore.messages[msgIndex] = {
                ...lastMsg,
                thinking: lastMsg.thinking + reflectionHint
              }
            }
          } else if (msgType === 'progress') {
            // 【修复】处理执行进度事件
            const progressData = jsonData.toolCalls || jsonData.tool_calls || {}
            const phase = progressData.phase || ''
            const desc = progressData.description || ''
            if (desc) {
              // 将进度信息追加到 thinking 区域显示
              const phaseLabel = phase ? `【${phase}】` : ''
              chatStore.messages[msgIndex] = {
                ...lastMsg,
                thinking: lastMsg.thinking + (lastMsg.thinking ? '\n' : '') + `${phaseLabel}${desc}`
              }
            }
          } else if (msgType === 'clarification') {
            // 【修复】处理 Agent 澄清请求
            const clarificationData = jsonData.toolCalls || jsonData.tool_calls || {}
            const question = clarificationData.question || '请提供更多信息'
            const missingInfo = clarificationData.missingInfo || []
            let clarificationText = `\n\n❓ **需要补充信息**: ${question}`
            if (missingInfo.length > 0) {
              clarificationText += `\n缺少: ${missingInfo.join('、')}`
            }
            chatStore.messages[msgIndex] = {
              ...lastMsg,
              thinking: lastMsg.thinking + clarificationText
            }
          } else if (msgType === 'react_iteration') {
            // 【修复】处理 ReAct 迭代进度
            const iterData = jsonData.toolCalls || jsonData.tool_calls || {}
            const iteration = iterData.iteration || 0
            const maxIterations = iterData.maxIterations || 0
            const errors = iterData.consecutiveErrors || 0
            const toolCalls = iterData.toolCallsCount || 0
            if (iteration > 0 && maxIterations > 0) {
              const iterText = `[推理迭代 ${iteration}/${maxIterations}] 工具调用: ${toolCalls}${errors > 0 ? ` | 连续错误: ${errors}` : ''}`
              chatStore.messages[msgIndex] = {
                ...lastMsg,
                thinking: lastMsg.thinking + (lastMsg.thinking ? '\n' : '') + iterText
              }
            }
          } else if (msgType === 'plan_progress') {
            // 【修复】处理计划整体进度
            const planProgressData = jsonData.toolCalls || jsonData.tool_calls || {}
            if (lastMsg.taskPlan) {
              chatStore.messages[msgIndex] = {
                ...lastMsg,
                taskPlan: {
                  ...lastMsg.taskPlan,
                  planSummary: planProgressData.planSummary || lastMsg.taskPlan.planSummary
                }
              }
            }
          } else if (msgType === 'generating') {
            currentAgentPhase.value = 'generating'
            if (jsonData.content) {
              chatStore.messages[msgIndex] = { ...lastMsg, content: lastMsg.content + jsonData.content }
            }
          } else {
            if (jsonData.content) {
              chatStore.messages[msgIndex] = { ...lastMsg, content: lastMsg.content + jsonData.content }
            }
          }

          scrollToBottom()
        },
        () => {
          connectionStatus.value = 'idle'
          currentAgentPhase.value = 'idle'
          const msgIndex = chatStore.messages.findIndex(m => m.id === assistantMsgId)
          if (msgIndex !== -1) {
            const lastMsg = chatStore.messages[msgIndex]
            const updates: Partial<typeof lastMsg> = {
              done: true,
              loading: false
            }
            if (lastMsg.taskPlan && lastMsg.taskPlan.status === 'executing') {
              const hasError = lastMsg.taskPlan.steps.some(s => s.status === 'failed')
              updates.taskPlan = { ...lastMsg.taskPlan, status: hasError ? 'failed' : 'completed' }
            }
            chatStore.messages[msgIndex] = { ...lastMsg, ...updates }
          }
        },
        (error) => {
          if (!controller.signal.aborted) {
            connectionStatus.value = 'error'
          }
          currentAgentPhase.value = 'idle'
          const msgIndex = chatStore.messages.findIndex(m => m.id === assistantMsgId)
          if (msgIndex !== -1) {
            const lastMsg = chatStore.messages[msgIndex]
            chatStore.messages[msgIndex] = {
              ...lastMsg,
              error: !controller.signal.aborted ? (error.error || error) : lastMsg.error,
              errorType: error.errorType,
              errorTitle: error.errorTitle,
              errorSuggestion: error.errorSuggestion,
              retryable: error.retryable,
              done: true,
              loading: false
            }
            scrollToBottom()
          }
        }
      )
    }

    await scrollToBottom()
    await loadChatSessions()
  } catch (error: any) {
    connectionStatus.value = 'error'
    currentAgentPhase.value = 'idle'
    if (error?.name === 'AbortError') {
      debug.log('请求被用户取消')
    } else {
      console.error('Failed to send message:', error)
      const msgIndex = chatStore.messages.findIndex(m => m.role === 'assistant' && !m.done)
      if (msgIndex !== -1) {
        const lastMsg = chatStore.messages[msgIndex]
        const errorMsg = error?.message || '未知错误'
        const isRetryable = error?.retryable !== false

        // 判断是否是网络错误
        const isNetworkError = !networkStatus.isOnline.value ||
          errorMsg.includes('Failed to fetch') ||
          errorMsg.includes('NetworkError') ||
          errorMsg.includes('network') ||
          errorMsg.includes('ERR_INTERNET_DISCONNECTED')

        chatStore.messages[msgIndex] = {
          ...lastMsg,
          error: error?.error || (isNetworkError ? '网络连接中断' : errorMsg),
          errorType: error?.errorType,
          errorTitle: error?.errorTitle,
          errorSuggestion: error?.errorSuggestion,
          retryable: isRetryable && (isNetworkError || error?.retryable === true),
          done: true,
          loading: false
        }

        // 如果是可重试错误且是网络错误，保存待重试消息
        if (isNetworkError && isRetryable && chatStore.lastRequestData) {
          chatStore.savePendingRequest({
            content: chatStore.lastRequestData.content,
            model: chatStore.lastRequestData.model,
            mode: chatStore.lastRequestData.mode,
            sessionId: chatStore.lastRequestData.sessionId,
            userId: chatStore.lastRequestData.userId,
            images: chatStore.lastRequestData.images,
            documents: chatStore.lastRequestData.documents,
            audios: chatStore.lastRequestData.audios,
            videos: chatStore.lastRequestData.videos
          })
          ElMessage.warning('网络连接中断，消息已保存，网络恢复后可重试')
        } else {
          ElMessage.error('发送失败: ' + errorMsg)
        }
      } else {
        ElMessage.error('发送失败: ' + (error?.message || '未知错误'))
      }
    }
  } finally {
    abortController.value = null
    chatStore.setLoading(false)
    if (connectionStatus.value === 'streaming' || connectionStatus.value === 'connecting') {
      connectionStatus.value = 'idle'
    }
    currentAgentPhase.value = 'idle'
  }
}

const handleCancel = async () => {
  try {
    if (abortController.value) {
      abortController.value.abort()
    }

    const sessionId = chatStore.currentSessionId
    if (sessionId) {
      try {
        await interruptSession(sessionId, { mode: 'cancel' })
      } catch (e) {
        debug.log('后端中断请求失败（可能已结束）:', e)
      }
    }

    ElMessage.success('已停止生成')

    const lastMsg = chatStore.messages[chatStore.messages.length - 1]
    if (lastMsg && lastMsg.role === 'assistant') {
      lastMsg.done = true
      lastMsg.loading = false
      if (!lastMsg.content && !lastMsg.error) {
        lastMsg.content = '生成已被用户停止。'
      }
    }
    chatStore.setLoading(false)
    isAppendMode.value = false
    connectionStatus.value = 'idle'
    currentAgentPhase.value = 'idle'
  } catch (error: any) {
    console.error('取消任务失败:', error)
  }
}

const handleRetryMessage = async (messageId: string) => {
  const msgIndex = chatStore.messages.findIndex(m => m.id === messageId)
  if (msgIndex < 0) return

  let lastUserMsgIndex = -1
  for (let i = msgIndex - 1; i >= 0; i--) {
    if (chatStore.messages[i].role === 'user') {
      lastUserMsgIndex = i
      break
    }
  }
  if (lastUserMsgIndex < 0) {
    ElMessage.warning('找不到对应的用户消息，无法重试')
    return
  }

  const userMsg = chatStore.messages[lastUserMsgIndex]

  chatStore.messages.splice(msgIndex)
  messageInput.value = userMsg.content
  ElMessage.info('已加载上次消息，点击发送即可重新尝试')
}

const enterAppendMode = () => {
  isAppendMode.value = true
  appendInput.value = ''
}

const cancelAppendMode = () => {
  isAppendMode.value = false
  appendInput.value = ''
}

const handleApprovalDecided = (decision: { requestId: string; approved: boolean; alwaysAllow: boolean }) => {
  approvalDialogVisible.value = false
  currentApprovalRequest.value = null
  if (decision.approved && decision.alwaysAllow) {
    ElMessage.success('已允许，并设为总是允许此工具')
  } else if (decision.approved) {
    ElMessage.success('已允许工具调用')
  } else {
    ElMessage.info('已拒绝工具调用')
  }
}

const handleAppend = async () => {
  const content = appendInput.value.trim()
  if (!content) return

  try {
    const sessionId = chatStore.currentSessionId
    if (!sessionId) return

    await interruptSession(sessionId, { mode: 'append', context: content })
    ElMessage.success('追加指令已发送，Agent 将调整执行方向')
    isAppendMode.value = false
    appendInput.value = ''

    chatStore.addMessage({
      id: `${Date.now()}-append`,
      role: 'user',
      content,
      thinking: '',
      toolCalls: [],
      done: true,
      loading: false,
      timestamp: Date.now()
    })
    await scrollToBottom()
  } catch (error: any) {
    console.error('追加指令失败:', error)
    ElMessage.error('追加指令失败: ' + (error?.message || '未知错误'))
  }
}

const chatSessions = ref<ChatHistorySession[]>([])

const loadChatSessions = async () => {
  try {
    const uid = userStore.user?.id ? Number(userStore.user.id) : undefined
    chatSessions.value = await getChatSessions(undefined, uid)
  } catch (error: any) {
    console.error('加载对话历史失败:', error)
    ElMessage.error('加载对话列表失败: ' + (error?.message || '未知错误'))
  }
}

const handleLoadSession = async (sessionId: string | null) => {
  if (!sessionId) return

  chatStore.setSessionId(sessionId)
  chatStore.clearMessages()
  try {
    const detail = await getChatSessionMessages(sessionId)
    if (detail.messages && detail.messages.length > 0) {
      for (const msg of detail.messages) {
        chatStore.addMessage({
          id: `${msg.id}-${msg.role}`,
          role: msg.role as 'user' | 'assistant' | 'system',
          content: msg.content,
          thinking: '',
          toolCalls: [],
          done: true,
          loading: false,
          timestamp: new Date(msg.createdTime).getTime()
        })
      }
      // 修复历史消息中的图片引用
      await fixHistoryImages(chatStore.messages, sessionId)
      await scrollToBottom()
    }
    if (detail.history?.mode) {
      // 统一使用连字符格式的模式名称（与后端 ChatMode.java 一致）
      const validModes = ['lite-task', 'medium-task', 'complex-task']
      if (validModes.includes(detail.history.mode)) {
        chatMode.value = detail.history.mode
      } else if (detail.history.mode === 'mcp') {
        chatMode.value = 'complex-task'
      }
      // 兼容旧数据中的驼峰格式
      else if (detail.history.mode === 'liteTask') {
        chatMode.value = 'lite-task'
      } else if (detail.history.mode === 'mediumTask') {
        chatMode.value = 'medium-task'
      } else if (detail.history.mode === 'complexTask') {
        chatMode.value = 'complex-task'
      }
    }
  } catch (error: any) {
    console.error('加载对话消息失败:', error)
    ElMessage.error('加载对话历史失败: ' + (error?.message || '未知错误'))
  }
}

const handleNewChat = async () => {
  chatStore.setSessionId(`session-${Date.now()}`)
  chatStore.clearMessages()
}

// 手动提取知识
const extractionStatusMap = ref<Map<string, string>>(new Map())

const handleExtractKnowledge = async (sessionId: string) => {
  const currentStatus = extractionStatusMap.value.get(sessionId)
  if (currentStatus === 'EXTRACTING') {
    ElMessage.warning('该对话正在提取中，请稍后再试')
    return
  }

  try {
    extractionStatusMap.value.set(sessionId, 'EXTRACTING')

    // 1. 获取对话消息
    let messages: Array<{ role: string; content: string }> = []

    if (sessionId === chatStore.currentSessionId) {
      // 当前对话，直接使用 chatStore 中的消息
      messages = chatStore.messages.map(m => ({
        role: m.role,
        content: m.content
      }))
      debug.log('[手动提取] 使用当前对话消息:', messages.length, '条')
    } else {
      // 历史对话，需要从后端加载
      debug.log('[手动提取] 加载历史对话消息:', sessionId)
      const detail = await getChatSessionMessages(sessionId)
      if (detail.messages && detail.messages.length > 0) {
        messages = detail.messages.map(m => ({
          role: m.role,
          content: m.content
        }))
      }
      debug.log('[手动提取] 加载到消息:', messages.length, '条')
    }

    if (messages.length === 0) {
      ElMessage.warning('该对话没有消息内容，无法提取知识')
      extractionStatusMap.value.delete(sessionId)
      return
    }

    // 2. 调用后端提取接口
    const result = await extractChatSession(
      sessionId,
      messages,
      userStore.user?.id ? Number(userStore.user.id) : undefined,
      selectedModel.value || undefined
    )

    debug.log('[手动提取] 后端返回:', result)

    if (result.status === 'SUCCESS') {
      ElMessage.success('知识提取已触发，请稍后刷新查看结果')
    } else if (result.status === 'EXTRACTING') {
      ElMessage.warning('该对话正在提取中，请稍后再试')
    } else if (result.status === 'FAILED' || result.status === 'NO_MESSAGES') {
      ElMessage.error(result.message || '知识提取失败')
    }
  } catch (e: any) {
    console.error('[手动提取] 提取知识失败:', e)
    ElMessage.error('提取知识失败: ' + (e.message || '未知错误'))
    extractionStatusMap.value.delete(sessionId)
  } finally {
    // 3秒后清除状态
    setTimeout(() => {
      extractionStatusMap.value.delete(sessionId)
    }, 3000)
  }
}

const formatSessionTime = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${Math.floor(diff / 86400000)}天前`
}

// --- Search & filter ---
const toggleSearch = () => {
  showSearch.value = !showSearch.value
  if (showSearch.value) {
    nextTick(() => searchInputRef.value?.focus())
  } else {
    searchQuery.value = ''
  }
}

const filteredSessions = computed(() => {
  if (!searchQuery.value.trim()) return chatSessions.value
  const q = searchQuery.value.toLowerCase()
  return chatSessions.value.filter(s =>
    (s.title || '').toLowerCase().includes(q)
  )
})

// --- Session folders ---
const {
  folders,
  isPinned,
  getSessionFolder,
  pinSession: _pinSession,
  unpinSession: _unpinSession,
  moveToFolder: _moveToFolder,
  createFolder,
  renameFolder: _renameFolder,
  deleteFolder: _deleteFolder
} = useSessionFolders()

const expandedGroups = ref<Set<string>>(new Set(['pinned', 'today']))

const toggleGroup = (groupId: string) => {
  const newSet = new Set(expandedGroups.value)
  if (newSet.has(groupId)) {
    newSet.delete(groupId)
  } else {
    newSet.add(groupId)
  }
  expandedGroups.value = newSet
}


const handleCreateFolder = () => {
  const name = prompt('请输入文件夹名称：')
  if (name && name.trim()) {
    createFolder(name.trim())
    ElMessage.success('文件夹已创建')
  }
}

// --- Time-based grouping with folders ---
interface SessionGroup {
  id: string
  label: string
  icon?: 'pin' | 'folder' | 'today' | 'yesterday' | 'week' | 'older'
  sessions: ChatHistorySession[]
  isPinnedGroup?: boolean
  folderId?: string
}

const filteredGroupedSessions = computed<SessionGroup[]>(() => {
  const sessions = filteredSessions.value
  if (sessions.length === 0) return []

  const { pinnedSessions, unpinnedSessions } = {
    pinnedSessions: sessions.filter(s => isPinned(s.sessionId)),
    unpinnedSessions: sessions.filter(s => !isPinned(s.sessionId))
  }

  const result: SessionGroup[] = []

  // Add pinned group if exists
  if (pinnedSessions.length > 0) {
    result.push({
      id: 'pinned',
      label: '已固定',
      icon: 'pin',
      sessions: pinnedSessions,
      isPinnedGroup: true
    })
  }

  // Group by folder
  const folderMap = new Map<string, ChatHistorySession[]>()
  const uncategorized: ChatHistorySession[] = []

  for (const session of unpinnedSessions) {
    const folderId = getSessionFolder(session.sessionId)
    if (folderId) {
      if (!folderMap.has(folderId)) {
        folderMap.set(folderId, [])
      }
      folderMap.get(folderId)!.push(session)
    } else {
      uncategorized.push(session)
    }
  }

  // Add folders
  for (const folder of folders.value) {
    const folderSessions = folderMap.get(folder.id) || []
    if (folderSessions.length > 0) {
      result.push({
        id: folder.id,
        label: folder.name,
        icon: 'folder',
        sessions: folderSessions,
        folderId: folder.id
      })
    }
  }

  // Add uncategorized grouped by time
  if (uncategorized.length > 0) {
    const now = new Date()
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const yesterday = new Date(today.getTime() - 86400000)
    const weekAgo = new Date(today.getTime() - 7 * 86400000)

    const groups: Map<string, ChatHistorySession[]> = new Map([
      ['today', []],
      ['yesterday', []],
      ['week', []],
      ['older', []]
    ])

    const labelMap: Record<string, string> = {
      'today': '今天',
      'yesterday': '昨天',
      'week': '最近 7 天',
      'older': '更早'
    }

    for (const session of uncategorized) {
      const date = new Date(session.updatedTime)
      if (date >= today) {
        groups.get('today')!.push(session)
      } else if (date >= yesterday) {
        groups.get('yesterday')!.push(session)
      } else if (date >= weekAgo) {
        groups.get('week')!.push(session)
      } else {
        groups.get('older')!.push(session)
      }
    }

    for (const [id, items] of groups) {
      if (items.length > 0) {
        result.push({
          id,
          label: labelMap[id],
          icon: id as 'today' | 'yesterday' | 'week' | 'older',
          sessions: items
        })
      }
    }
  }

  return result
})

// --- Batch mode ---
const isAllSelected = computed(() => {
  return filteredSessions.value.length > 0 &&
    filteredSessions.value.every(s => selectedSessionIds.value.has(s.sessionId))
})

const enterBatchMode = () => {
  batchMode.value = true
  selectedSessionIds.value = new Set()
}

const exitBatchMode = () => {
  batchMode.value = false
  selectedSessionIds.value = new Set()
}

const toggleSessionSelection = (sessionId: string) => {
  const newSet = new Set(selectedSessionIds.value)
  if (newSet.has(sessionId)) {
    newSet.delete(sessionId)
  } else {
    newSet.add(sessionId)
  }
  selectedSessionIds.value = newSet
}

const toggleSelectAll = () => {
  if (isAllSelected.value) {
    selectedSessionIds.value = new Set()
  } else {
    selectedSessionIds.value = new Set(filteredSessions.value.map(s => s.sessionId))
  }
}

const showSessionGraph = (sessionId: string) => {
  viewGraphSessionId.value = sessionId
  showKnowledgeGraph.value = true
}

const handleBatchDelete = async () => {
  const count = selectedSessionIds.value.size
  if (count === 0) return
  try {
    const ids = Array.from(selectedSessionIds.value)
    await batchDeleteChatSessions(ids)
    chatSessions.value = chatSessions.value.filter(s => !selectedSessionIds.value.has(s.sessionId))
    if (selectedSessionIds.value.has(chatStore.currentSessionId)) {
      handleNewChat()
    }
    exitBatchMode()
    ElMessage.success(`已删除 ${count} 个对话`)
  } catch (error: any) {
    console.error('批量删除失败:', error)
    ElMessage.error('批量删除失败: ' + (error?.message || '未知错误'))
  }
}

// --- Rename ---
const startRename = (sessionId: string, currentTitle: string | null) => {
  editingSessionId.value = sessionId
  renameTitle.value = currentTitle || ''
  nextTick(() => {
    if (renameInputRef.value && renameInputRef.value.length > 0) {
      renameInputRef.value[0]?.focus()
      renameInputRef.value[0]?.select()
    }
  })
}

const confirmRename = async () => {
  if (!editingSessionId.value) return
  const sid = editingSessionId.value
  const title = renameTitle.value.trim()
  if (!title) {
    cancelRename()
    return
  }
  try {
    await renameChatSession(sid, title)
    const session = chatSessions.value.find(s => s.sessionId === sid)
    if (session) session.title = title
  } catch (error: any) {
    console.error('重命名失败:', error)
    ElMessage.error('重命名失败')
  }
  editingSessionId.value = null
  renameTitle.value = ''
}

const cancelRename = () => {
  editingSessionId.value = null
  renameTitle.value = ''
}

// --- Keyboard shortcuts ---
const handleKeydown = (e: KeyboardEvent) => {
  // Ignore when typing in inputs
  const tag = (e.target as HTMLElement)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return

  if (e.ctrlKey || e.metaKey) {
    switch (e.key.toLowerCase()) {
      case 'b':
        e.preventDefault()
        sidebarCollapsed.value = !sidebarCollapsed.value
        break
      case 'n':
        e.preventDefault()
        handleNewChat()
        break
      case 'k':
        e.preventDefault()
        if (!sidebarCollapsed.value) {
          showSearch.value = true
          nextTick(() => searchInputRef.value?.focus())
        }
        break
    }
  }
}

const handleRefreshStats = async () => {
  // loadStats 已移除，此函数暂时禁用
}

const handleFileDeleted = (_fileId: string) => {
  // 文件删除后刷新文件管理器
  userFileManagerRef.value?.loadFiles()
}

const handleClearHistory = async () => {
  const sessionId = chatStore.currentSessionId
  if (!sessionId) {
    ElMessage.warning('当前没有活跃的对话')
    return
  }

  try {
    await ElMessageBox.confirm(
      '确定要删除当前对话吗？删除后将清除对话消息、知识图谱节点和关系，此操作不可恢复。',
      '删除对话',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )

    // 用户确认删除
    try {
      // 调用后端 API 删除对话
      await deleteChatSession(sessionId)

      // 从本地列表中移除
      chatSessions.value = chatSessions.value.filter(s => s.sessionId !== sessionId)

      // 清除 IndexedDB 中的图片和文件
      try {
        await imageDB.deleteBySession(sessionId)
        await fileDB.deleteBySession(sessionId)
      } catch (dbError) {
        console.warn('清理本地数据库失败:', dbError)
      }

      // 清除当前会话状态
      chatStore.clearMessages()
      chatStore.setSessionId(`session-${Date.now()}`)

      // 关闭移动端侧边栏
      closeDrawer()

      ElMessage.success('对话已删除')
    } catch (error: any) {
      console.error('删除对话失败:', error)
      ElMessage.error('删除对话失败: ' + (error?.message || '未知错误'))
    }
  } catch {
    // 用户取消删除，不做任何操作
  }
}

let scrollRafId: number | null = null
const throttledScrollToBottom = () => {
  if (scrollRafId !== null) return
  scrollRafId = requestAnimationFrame(() => {
    scrollToBottom()
    scrollRafId = null
  })
}

watch(() => chatStore.messages, throttledScrollToBottom, { deep: true })

onMounted(() => {
  if (!chatStore.currentSessionId) {
    chatStore.setSessionId(`session-${Date.now()}`)
  }
  loadChatSessions()
  loadAvailableModels()
  document.addEventListener('keydown', handleKeydown)
})

const loadAvailableModels = async () => {
  try {
    const uid = userStore.user?.id ? Number(userStore.user.id) : undefined
    const models = await getAvailableModels(uid)
    availableModels.value = models
    const defaultModel = models.find(m => m.isDefault && m.isEnabled)
    if (defaultModel) {
      selectedModel.value = defaultModel.modelId
    } else if (models.length > 0) {
      selectedModel.value = models[0].modelId
    }
  } catch (e) {
    console.error('加载可用模型失败:', e)
  }
}

onUnmounted(() => {
  stopStatsUpdate()
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

// 网络状态横幅动画
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}

// Mobile menu button
.mobile-menu-btn {
  display: none;
  position: fixed;
  top: 16px;
  left: 16px;
  z-index: 50;
  width: 40px;
  height: 40px;
  padding: 8px;
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  cursor: pointer;
  align-items: center;
  justify-content: center;

  svg {
    width: 20px;
    height: 20px;
    color: var(--sf-text-primary);
  }

  @media (max-width: 1024px) {
    display: flex;
  }
}

// Drawer overlay
.drawer-overlay {
  display: none;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 100;

  @media (max-width: 1024px) {
    display: block;
  }
}

// Drawer transitions
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

// Sidebar drawer mode on mobile
.chat-page__sidebar {
  @media (max-width: 768px) {
    position: fixed;
    top: 0;
    left: 0;
    width: 320px;
    max-width: 85vw;
    height: 100vh;
    z-index: 101;
    transform: translateX(-100%);
    transition: transform 0.35s cubic-bezier(0.32, 0.72, 0, 1);
    border-right: 1px solid var(--sf-border-light);

    &--mobile-open {
      transform: translateX(0);
    }
  }
}

// 网络状态横幅
.network-banner {
  position: fixed;
  top: 64px;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 500;

  svg {
    width: 18px;
    height: 18px;
  }

  &--offline {
    background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
    color: white;
    box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
  }

  &--online {
    background: linear-gradient(135deg, #10b981 0%, #059669 100%);
    color: white;
    box-shadow: 0 2px 8px rgba(16, 185, 129, 0.3);
  }

  &__btn {
    margin-left: 12px;
    padding: 4px 12px;
    background: rgba(255, 255, 255, 0.2);
    border: 1px solid rgba(255, 255, 255, 0.3);
    border-radius: 4px;
    color: white;
    font-size: 13px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: rgba(255, 255, 255, 0.3);
    }
  }

  &__close {
    margin-left: auto;
    padding: 4px;
    background: transparent;
    border: none;
    color: white;
    cursor: pointer;
    opacity: 0.7;
    transition: opacity 0.2s;

    &:hover {
      opacity: 1;
    }

    svg {
      width: 16px;
      height: 16px;
    }
  }
}

// 重试提示
.retry-prompt {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 12px 16px;
  margin: 16px 0;
  background: var(--sf-bg-gray-100);
  border: 1px solid var(--sf-border-default);
  border-radius: var(--sf-radius-lg);

  &__content {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--sf-text-secondary);
    font-size: 14px;

    svg {
      width: 16px;
      height: 16px;
      color: var(--sf-warning);
    }
  }

  &__btn {
    padding: 6px 12px;
    background: var(--sf-bg-white);
    border: 1px solid var(--sf-border-default);
    border-radius: var(--sf-radius-sm);
    color: var(--sf-text-secondary);
    font-size: 13px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: var(--sf-border-strong);
    }

    &--primary {
      background: var(--sf-accent);
      border-color: var(--sf-accent);
      color: white;

      &:hover {
        background: var(--sf-accent-hover);
        border-color: var(--sf-accent-hover);
      }
    }
  }
}

.chat-page {
  width: 100%;
  height: calc(100vh - 64px);
  position: relative;
  background: var(--chat-page-bg);
  overflow: hidden;
  font-family: var(--chat-font-body);

  &__bg {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 0;
    background: var(--sf-page-bg);
  }

  &__main {
    position: relative;
    z-index: 1;
    height: 100%;
    display: flex;
    gap: 0;
  }

  &__content {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
    height: 100%;
    align-items: center;
  }

  &__input {
    flex-shrink: 0;
    padding: 16px 24px 24px;
    background: linear-gradient(to top, var(--sf-bg-white) 0%, transparent 100%);
    width: 100%;
    max-width: 900px;
  }

  &__sidebar {
    width: 360px;
    flex-shrink: 0;
    height: 100%;
    position: relative;
    background: var(--sf-bg-gray-50);
    border-left: 1px solid var(--sf-border-light);
    overflow-y: auto;
    transition: width 0.35s cubic-bezier(0.32, 0.72, 0, 1), opacity 0.25s ease;

    &--collapsed {
      width: 0;
      border-left: none;
      overflow: hidden;

      .sidebar-content {
        opacity: 0;
        pointer-events: none;
      }
    }

    .sidebar-content {
      position: relative;
      z-index: 1;
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 16px;
      height: 100%;
      min-width: 360px;
      transition: opacity 0.2s ease, transform 0.2s ease;
      transform: translateX(0);

      // 第一个 sidebar-section (历史对话) 占 4/5 高度
      > .sidebar-section:first-child {
        flex: 4;
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
    }

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }

    &::-webkit-scrollbar-thumb {
      background: var(--sf-border-default);
      border-radius: 2px;
    }
  }
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;

  @media (max-width: 768px) {
    padding: 16px;
    max-width: 100%;
    overflow-x: hidden;
  }

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--sf-border-default);
    border-radius: 3px;

    &:hover {
      background: var(--sf-border-strong);
    }
  }
}

// 欢迎页居中布局（Grok 风格）
.welcome-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  padding-top: 15vh;
  flex: 1;
  width: 100%;
  padding-left: 24px;
  padding-right: 24px;
  padding-bottom: 24px;
  box-sizing: border-box;

  &__header {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 12px;
    margin-bottom: 8px;
  }

  &__logo {
    width: 36px;
    height: 36px;

    svg {
      width: 100%;
      height: 100%;
      color: var(--sf-accent);
    }
  }

  &__title {
    font-family: var(--sf-font-sans);
    font-size: 28px;
    font-weight: 600;
    color: var(--sf-text-primary);
    margin: 0;
    letter-spacing: -0.02em;
  }

  &__input-wrapper {
    width: 100%;
    max-width: 960px;
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-top: 72px;
    margin-left: auto;
    margin-right: auto;
    padding: 0 24px;
    box-sizing: border-box;
  }
}

.input-area--centered {
  min-height: auto;
  border-radius: 48px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 4px 0;

  .el-input__inner {
    border: 1.5px solid #000 !important;
    border-radius: 48px;
  }
}

.connection-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  margin-bottom: 16px;
  border-radius: var(--sf-radius-full);
  font-size: 12px;
  font-weight: 500;
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  animation: chatFadeIn 0.3s ease-out forwards;

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--sf-accent);
    animation: chatPulse 2s ease-in-out infinite;
  }

  &__text {
    color: var(--sf-text-secondary);
  }

  &--connecting {
    border-color: rgba(245, 158, 11, 0.3);
    .connection-status__dot { background: var(--sf-warning); }
    .connection-status__text { color: var(--sf-warning); }
  }

  &--streaming {
    border-color: rgba(16, 185, 129, 0.3);
    .connection-status__dot { background: var(--sf-success); }
    .connection-status__text { color: var(--sf-success); }
  }

  &--error {
    border-color: rgba(239, 68, 68, 0.3);
    .connection-status__dot { background: var(--sf-error); animation: none; }
    .connection-status__text { color: var(--sf-error); }
  }
}

.input-area {
  background: transparent;
  border: none;
  border-radius: 48px;
  padding: 0;
  transition: border-radius 0.2s ease, border-color 0.2s ease;

  &:focus-within {
    border: none;
  }

  &__main {
    margin-bottom: 10px;
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

:root.dark .input-area {
  background: transparent;
}

// 文件预览区域
.file-preview-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  padding: 12px;
  background: var(--sf-bg-gray-100);
  border-radius: var(--sf-radius-md);
  border: 1px solid var(--sf-border-light);

  .preview-item {
    position: relative;
    width: 72px;
    height: 72px;
    border-radius: var(--sf-radius-sm);
    overflow: hidden;
    border: 1px solid var(--sf-border-default);

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .file-icon-wrapper {
      width: 100%;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      background: var(--sf-bg-white);
      padding: 8px;

      svg {
        width: 28px;
        height: 28px;
        flex-shrink: 0;
      }

      .file-name-preview {
        font-size: 9px;
        color: var(--sf-text-muted);
        margin-top: 4px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 100%;
      }
    }

    // 不同文件类型的颜色
    &--document {
      .file-icon-wrapper svg { color: #3b82f6; }
      .file-icon-wrapper { background: rgba(59, 130, 246, 0.05); }
    }

    &--audio {
      .file-icon-wrapper svg { color: #10b981; }
      .file-icon-wrapper { background: rgba(16, 185, 129, 0.05); }
    }

    &--video {
      .file-icon-wrapper svg { color: #f59e0b; }
      .file-icon-wrapper { background: rgba(245, 158, 11, 0.05); }
    }

    .preview-remove {
      position: absolute;
      top: 4px;
      right: 4px;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      border: none;
      background: rgba(239, 68, 68, 0.9);
      color: white;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      transition: opacity var(--sf-transition-fast);

      svg {
        width: 12px;
        height: 12px;
      }

      &:hover {
        background: var(--sf-error);
      }
    }

    &:hover .preview-remove {
      opacity: 1;
    }
  }
}

.input-textarea {
  width: 100%;
  height: 22px;
  max-height: 200px;
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  font-family: var(--sf-font-sans);
  font-size: 14px;
  line-height: 1.5;
  color: var(--sf-text-primary);

  &::placeholder {
    color: var(--sf-text-secondary);
    font-size: 15px;
  }

  &::-webkit-scrollbar {
    display: none;
  }
}

.model-selector {
  position: relative;

  .model-select {
    appearance: none;
    background: var(--sf-bg-gray-100);
    border: 1px solid var(--sf-border-light);
    border-radius: var(--sf-radius-md);
    padding: 8px 32px 8px 12px;
    font-family: var(--sf-font-sans);
    font-size: 13px;
    color: var(--sf-text-primary);
    cursor: pointer;
    transition: all var(--sf-transition-fast);
    min-width: 140px;

    &:hover {
      border-color: var(--sf-border-default);
    }

    &:focus {
      outline: none;
      border-color: var(--sf-accent);
    }

    option {
      background: var(--sf-bg-white);
      color: var(--sf-text-primary);
    }
  }

  svg {
    position: absolute;
    right: 10px;
    top: 50%;
    transform: translateY(-50%);
    width: 14px;
    height: 14px;
    color: var(--sf-text-tertiary);
    pointer-events: none;
  }
}

.config-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(0, 102, 255, 0.1);
  border: 1px solid rgba(0, 102, 255, 0.3);
  border-radius: var(--sf-radius-md);
  font-size: 13px;
  color: var(--sf-accent);
  cursor: pointer;
  transition: all var(--sf-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: rgba(0, 102, 255, 0.2);
  }
}

.mode-selector {
  display: flex;
  background: var(--sf-bg-gray-100);
  border-radius: var(--sf-radius-md);
  padding: 3px;
}

.mode-btn {
  padding: 6px 14px;
  background: transparent;
  border: none;
  border-radius: var(--sf-radius-sm);
  font-family: var(--sf-font-sans);
  font-size: 12px;
  font-weight: 600;
  color: var(--sf-text-tertiary);
  cursor: pointer;
  transition: all var(--sf-transition-fast);

  &:hover {
    color: var(--sf-text-secondary);
  }

  &--active {
    background: var(--sf-accent);
    color: white;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.extraction-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: var(--sf-bg-gray-100);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  font-family: var(--sf-font-sans);
  font-size: 12px;
  font-weight: 500;
  color: var(--sf-text-tertiary);
  cursor: pointer;
  transition: all var(--sf-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover:not(:disabled) {
    border-color: var(--sf-accent);
    color: var(--sf-accent);
  }

  &--active {
    background: rgba(0, 102, 255, 0.1);
    border-color: var(--sf-accent);
    color: var(--sf-accent);

    svg {
      animation: pulse 2s ease-in-out infinite;
    }
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  @keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.6; }
  }
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--sf-radius-md);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--sf-transition-fast);
  border: 1px solid var(--sf-border-light);
  background: var(--sf-bg-gray-100);
  color: var(--sf-text-secondary);

  svg {
    width: 14px;
    height: 14px;
  }

  &--danger {
    background: rgba(239, 68, 68, 0.1);
    border-color: rgba(239, 68, 68, 0.3);
    color: var(--sf-error);

    &:hover {
      background: rgba(239, 68, 68, 0.2);
    }
  }

  &--warning {
    background: rgba(245, 158, 11, 0.1);
    border-color: rgba(245, 158, 11, 0.3);
    color: var(--sf-warning);

    &:hover {
      background: rgba(245, 158, 11, 0.2);
    }
  }
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: var(--sf-accent);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;

  svg {
    width: 16px;
    height: 16px;
  }

  &:hover:not(:disabled) {
    background: var(--sf-accent-hover);
    transform: scale(1.02);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.append-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: var(--sf-radius-lg);

  svg {
    width: 18px;
    height: 18px;
    color: var(--sf-warning);
    flex-shrink: 0;
  }

  span {
    font-size: 13px;
    font-weight: 500;
    color: var(--sf-warning);
    white-space: nowrap;
  }

  &__input {
    flex: 1;
    background: var(--sf-bg-gray-100);
    border: 1px solid var(--sf-border-light);
    border-radius: var(--sf-radius-md);
    padding: 8px 12px;
    font-family: var(--sf-font-sans);
    font-size: 13px;
    color: var(--sf-text-primary);
    outline: none;

    &:focus {
      border-color: var(--sf-warning);
    }
  }

  &__btn {
    padding: 8px 14px;
    border-radius: var(--sf-radius-md);
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    transition: all var(--sf-transition-fast);
    background: var(--sf-bg-gray-100);
    border: 1px solid var(--sf-border-light);
    color: var(--sf-text-secondary);

    &--primary {
      background: var(--sf-accent);
      border: none;
      color: white;

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }
  }
}

.sidebar-section {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--sf-border-light);

  svg {
    width: 16px;
    height: 16px;
    color: var(--sf-accent);
  }

  span {
    flex: 1;
    font-size: 13px;
    font-weight: 600;
    color: var(--sf-text-primary);
  }

  .section-action {
    width: 28px;
    height: 28px;
    border-radius: var(--sf-radius-sm);
    border: none;
    background: var(--sf-bg-gray-100);
    color: var(--sf-text-tertiary);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all var(--sf-transition-fast);

    svg {
      width: 14px;
      height: 14px;
    }

    &:hover {
      background: var(--sf-accent);
      color: white;
    }
  }
}

.history-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: relative;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--sf-border-default);
    border-radius: 2px;
  }
}

.history-empty {
  text-align: center;
  color: var(--sf-text-tertiary);
  font-size: 13px;
  padding: 24px 0;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--sf-radius-md);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  border: 1px solid transparent;

  &:hover {
    background: var(--sf-bg-gray-100);
    transform: translateX(3px);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

    .history-item__graph,
    .history-item__extract {
      opacity: 1;
      transform: scale(1);
    }
  }

  &:active {
    transform: translateX(1px);
    transition: all 0.1s ease;
  }

  &--active {
    background: rgba(0, 102, 255, 0.08);
    border: 1px solid rgba(0, 102, 255, 0.25);
    box-shadow: 0 0 0 1px rgba(0, 102, 255, 0.1);

    .history-item__icon {
      color: var(--sf-accent);
    }

    .history-item__title {
      color: var(--sf-accent);
    }
  }

  &__icon {
    width: 32px;
    height: 32px;
    border-radius: var(--sf-radius-sm);
    background: var(--sf-bg-gray-100);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    svg {
      width: 14px;
      height: 14px;
      color: var(--sf-text-tertiary);
    }
  }

  &__content {
    flex: 1;
    min-width: 0;
  }

  &__title {
    font-size: 13px;
    font-weight: 500;
    color: var(--sf-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    margin-bottom: 2px;
    max-width: 100%;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__mode {
    font-size: 10px;
    font-weight: 600;
    padding: 2px 6px;
    border-radius: var(--chat-radius-sm);
    background: rgba(139, 92, 246, 0.1);
    color: var(--chat-accent-purple);
  }

  &__time {
    font-size: 10px;
    color: var(--chat-text-muted);
  }
}

.settings-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}

.user-files-section {
  padding: 8px 0;
}

.section-toggle {
  width: 16px;
  height: 16px;
  margin-left: auto;
  transition: transform 0.2s ease;

  &--expanded {
    transform: rotate(180deg);
  }
}

.setting-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.setting-label {
  font-size: 12px;
  color: var(--sf-text-tertiary);
  min-width: 60px;
}

.setting-value {
  font-size: 11px;
  font-family: var(--sf-font-mono);
  color: var(--sf-text-secondary);
  margin-left: auto;
}

.progress-bar {
  flex: 1;
  height: 4px;
  background: var(--sf-bg-gray-100);
  border-radius: 2px;
  overflow: hidden;

  &__fill {
    height: 100%;
    background: var(--sf-accent);
    border-radius: 2px;
    transition: width var(--sf-transition-normal);
  }
}

.settings-actions {
  display: flex;
  gap: 8px;
}

.settings-btn {
  flex: 1;
  height: 36px;
  border-radius: var(--sf-radius-md);
  border: 1px solid var(--sf-border-light);
  background: var(--sf-bg-gray-100);
  color: var(--sf-text-tertiary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--sf-transition-fast);

  svg {
    width: 16px;
    height: 16px;
  }

  &:hover {
    background: var(--sf-bg-gray-200);
    color: var(--sf-text-primary);
  }

  &--danger {
    &:hover {
      background: rgba(239, 68, 68, 0.1);
      border-color: rgba(239, 68, 68, 0.3);
      color: var(--sf-error);
    }
  }
}

.last-update {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--sf-bg-gray-100);
  border-radius: var(--sf-radius-md);
  font-size: 11px;
  color: var(--sf-text-muted);
  margin-top: auto;

  svg {
    width: 12px;
    height: 12px;
    animation: spin 2s linear infinite;
  }
}

// --- Sidebar toggle group ---
.sidebar-toggle-group {
  position: absolute;
  right: 16px;
  top: 16px;
  display: flex;
  gap: 8px;
  z-index: 10;
  transition: right 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &--expanded {
    right: 376px; // 360px sidebar + 16px margin
  }
}

.sidebar-toggle-btn {
  padding: 8px 12px;
  border-radius: var(--sf-radius-md);
  border: 1px solid var(--sf-border-light);
  background: var(--sf-surface);
  color: var(--sf-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);

  svg {
    width: 18px;
    height: 18px;
    flex-shrink: 0;
    transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  }

  &:hover {
    background: var(--sf-bg-gray-100);
    color: var(--sf-text-primary);
    border-color: var(--sf-border-default);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    transform: translateY(-2px);

    svg {
      transform: scale(1.08);
    }
  }

  &:active {
    transform: translateY(0);
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
    transition: all 0.1s ease;
  }
}

// --- Search bar ---
.history-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--sf-bg-gray-100);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  margin-bottom: 8px;

  svg {
    width: 14px;
    height: 14px;
    color: var(--sf-text-muted);
    flex-shrink: 0;
  }

  &__input {
    flex: 1;
    background: transparent;
    border: none;
    outline: none;
    font-family: var(--sf-font-sans);
    font-size: 12px;
    color: var(--sf-text-primary);

    &::placeholder {
      color: var(--sf-text-muted);
    }
  }

  &__clear {
    width: 18px;
    height: 18px;
    border: none;
    background: transparent;
    color: var(--sf-text-muted);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0;
    border-radius: 4px;

    svg {
      width: 12px;
      height: 12px;
    }

    &:hover {
      color: var(--sf-error);
    }
  }
}

// --- Section header batch mode ---
.section-header--batch {
  background: rgba(239, 68, 68, 0.08);
  border-radius: var(--sf-radius-md);
  padding: 8px 12px;
  border-bottom-color: rgba(239, 68, 68, 0.2);

  span {
    color: var(--sf-error);
  }
}

// --- Section action active ---
.section-action--active {
  background: var(--sf-accent) !important;
  color: white !important;
}

// --- History group header (collapsible) ---
.history-group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 4px;
  background: none;
  border: none;
  cursor: pointer;
  border-radius: var(--sf-radius-md);
  transition: all var(--sf-transition-fast);

  &:hover {
    background: var(--sf-bg-gray-100);
  }

  &__arrow {
    width: 14px;
    height: 14px;
    color: var(--sf-text-muted);
    transition: transform var(--sf-transition-fast);
    flex-shrink: 0;

    &--expanded {
      transform: rotate(90deg);
    }
  }

  &__icon {
    width: 14px;
    height: 14px;
    color: var(--sf-text-tertiary);
    flex-shrink: 0;
  }

  &__label {
    flex: 1;
    font-size: 12px;
    font-weight: 600;
    color: var(--sf-text-secondary);
    text-align: left;
  }

  &__count {
    font-size: 11px;
    color: var(--sf-text-muted);
    background: var(--sf-bg-gray-100);
    padding: 2px 6px;
    border-radius: var(--sf-radius-full);
  }
}

.history-group-content {
  padding-left: 8px;
}

// --- History item selected ---
.history-item--selected {
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.2);
}

// --- Checkbox ---
.history-item__checkbox {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  border: 2px solid var(--sf-border-default);
  background: var(--sf-bg-gray-100);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all var(--sf-transition-fast);

  svg {
    width: 14px;
    height: 14px;
    color: var(--sf-error);
  }

  .history-item--selected & {
    background: var(--sf-error);
    border-color: var(--sf-error);

    svg {
      color: white;
    }
  }
}

// --- Rename input ---
.history-item__title-input {
  width: 100%;
  background: var(--sf-bg-gray-100);
  border: 1px solid var(--sf-accent);
  border-radius: 4px;
  padding: 2px 6px;
  font-family: var(--sf-font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--sf-text-primary);
  outline: none;
  margin-bottom: 2px;
}

// --- Batch action bar ---
.batch-action-bar {
  display: flex;
  gap: 8px;
  padding: 12px 0 0;
  position: sticky;
  bottom: 0;
  background: var(--sf-surface);
  margin-top: auto;
  z-index: 10;
}

.batch-action-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: var(--sf-radius-md);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all var(--sf-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &--primary {
    background: var(--sf-accent-light);
    color: var(--sf-accent);

    &:hover {
      background: rgba(0, 102, 255, 0.2);
    }
  }

  &--danger {
    background: rgba(239, 68, 68, 0.15);
    color: var(--sf-error);

    &:hover {
      background: rgba(239, 68, 68, 0.3);
    }
  }
}

@media (max-width: 1024px) {
  .chat-page {
    &__sidebar {
      position: fixed;
      top: 0;
      left: 0;
      width: 320px;
      max-width: 85vw;
      height: 100vh;
      z-index: 101;
      transform: translateX(-100%);
      transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      border-right: 1px solid var(--sf-border-light);

      &--mobile-open {
        transform: translateX(0);
      }
    }
  }

  .sidebar-toggle-group {
    display: none;
  }

  .sidebar-content {
    min-width: unset !important;
  }

  .sidebar-section {
    min-width: unset !important;
  }
}

@media (max-width: 768px) {
  .chat-page {
    &__content {
      padding-bottom: 0;
      position: relative;
    }

    &__input {
      position: relative;
      padding: 12px 16px calc(12px + env(safe-area-inset-bottom) + 56px);
      background: var(--sf-bg-white);
      border-top: 1px solid var(--sf-border-light);
    }
  }

  .messages-container {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
    padding-bottom: calc(160px + env(safe-area-inset-bottom));
    min-height: 0;
    max-width: 100%;
    box-sizing: border-box;
  }

  .welcome-view {
    padding-top: 5vh;
    padding-left: 16px;
    padding-right: 16px;
    justify-content: flex-start;
    position: relative;

    &__header {
      position: absolute;
      top: 10%;
      gap: 8px;
    }

    &__logo {
      width: 28px;
      height: 28px;
    }

    &__input-wrapper {
      position: absolute;
      top: 25%;
      padding: 0;
      width: 100%;
      max-width: 100%;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      align-items: center;
    }
  }

  .welcome-message {
    padding: 24px;

    &__title {
      font-size: 28px;
    }

    &__features {
      gap: 20px;
    }
  }

  .input-area__footer {
    flex-wrap: wrap;
    gap: 8px;
  }

  .input-area__left {
    flex-wrap: wrap;
  }

  .input-controls {
    justify-content: flex-start;
    gap: 12px;
    flex-wrap: wrap;
  }

  .model-selector {
    .model-select {
      min-width: 120px;
      font-size: 12px;
      padding: 6px 28px 6px 10px;
    }
  }

  .mode-selector {
    .mode-btn {
      padding: 5px 10px;
      font-size: 11px;
    }
  }

  // Mobile sidebar styles
  .sidebar-content {
    padding: 12px !important;
    min-width: unset !important;
  }

  .sidebar-section {
    padding: 12px !important;
    min-width: unset !important;
  }

  .history-group-header {
    padding: 10px 4px !important;

    &__label {
      font-size: 13px !important;
    }

    &__count {
      font-size: 10px !important;
      padding: 2px 8px !important;
    }
  }

  .history-group-content {
    padding-left: 4px !important;
  }

  .history-item {
    padding: 12px 8px !important;

    &__title {
      font-size: 14px !important;
    }

    &__meta {
      font-size: 11px !important;
    }
  }

  .batch-action-bar {
    position: sticky;
    bottom: 0;
    background: var(--sf-surface);
    padding: 12px !important;
    border-top: 1px solid var(--sf-border-light);
    z-index: 10;
  }

  .batch-action-btn {
    padding: 10px 12px !important;
    font-size: 14px !important;

    svg {
      width: 16px;
      height: 16px;
    }
  }
}

.knowledge-graph-fab {
  position: fixed;
  left: 24px;
  top: 88px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--sf-accent);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(0, 102, 255, 0.3);
  transition: all 0.3s ease;
  z-index: 100;

  svg {
    width: 20px;
    height: 20px;
  }

  &:hover {
    transform: scale(1.1);
    box-shadow: 0 6px 30px rgba(0, 102, 255, 0.4);
  }
}

.knowledge-graph-drawer {
  :deep(.el-drawer__header) {
    margin-bottom: 0;
    padding: 16px 20px;
    border-bottom: 1px solid var(--sf-border-light);
    background: var(--sf-bg-white);
  }

  :deep(.el-drawer__body) {
    padding: 0;
    background: var(--sf-page-bg);
  }
}

@media (max-width: 768px) {
  .knowledge-graph-drawer {
    :deep(.el-drawer) {
      width: 100% !important;
      max-width: 100% !important;
    }

    :deep(.el-drawer__header) {
      padding: 12px 16px;

      .el-drawer__title {
        font-size: 16px;
      }

      .el-drawer__headerbtn {
        top: 12px;
        right: 12px;

        .el-drawer__close {
          font-size: 18px;
        }
      }
    }

    :deep(.el-drawer__body) {
      height: calc(100vh - 60px);
      max-height: calc(100vh - 60px);
    }
  }
}

// --- History item graph button ---
.history-item__graph,
.history-item__extract {
  opacity: 0;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: var(--sf-bg-gray-100);
  color: var(--sf-text-tertiary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
  transform: scale(0.8);

  svg {
    width: 14px;
    height: 14px;
    transition: transform 0.2s ease;
  }

  &:hover {
    background: rgba(0, 102, 255, 0.12);
    color: var(--sf-accent);
    transform: scale(1.05);

    svg {
      transform: scale(1.1);
    }
  }
}

.history-item:hover .history-item__graph,
.history-item:hover .history-item__extract {
  opacity: 1;
  transform: scale(1);
}

.history-item__extract {
  margin-left: 4px;

  &:hover {
    background: rgba(0, 102, 255, 0.12);
    color: var(--sf-accent);
  }
}

// 新输入区域布局 - 2026-04-18
.input-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
  flex-wrap: wrap;

  &__left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.input-row {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 30px;
  padding: 0 16px;
  transition: all 0.2s ease;
  width: 800px;
  height: 60px;
  margin: 0 auto;
  box-sizing: border-box;
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 4px 24px rgba(0, 0, 0, 0.06),
    0 8px 40px rgba(0, 0, 0, 0.04);

  &:hover {
    box-shadow:
      0 4px 12px rgba(0, 0, 0, 0.06),
      0 8px 32px rgba(0, 0, 0, 0.08),
      0 12px 48px rgba(0, 0, 0, 0.06);
    border-color: rgba(0, 0, 0, 0.1);
  }

  &:focus-within {
    border-color: var(--sf-accent);
  }

  :deep(.el-input__inner) {
    border: none !important;
    border-radius: 48px;
  }
}

:root.dark .input-row {
  background: rgba(30, 30, 35, 0.98);
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.2),
    0 4px 24px rgba(0, 0, 0, 0.3),
    0 8px 40px rgba(0, 0, 0, 0.2);

  &:hover {
    box-shadow:
      0 4px 12px rgba(0, 0, 0, 0.25),
      0 8px 32px rgba(0, 0, 0, 0.35),
      0 12px 48px rgba(0, 0, 0, 0.25);
    border-color: rgba(255, 255, 255, 0.12);
  }
}

.file-upload-trigger {
  position: relative;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: transparent;
  transition: background 0.2s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.06);
  }

  :deep(.file-uploader) {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  :deep(.upload-btn) {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    border: 1px solid transparent;
    background: transparent;
    color: var(--sf-text-secondary);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: color 0.2s ease, border-color 0.2s ease;

    svg {
      width: 18px;
      height: 18px;
    }

    &:hover:not(:disabled) {
      border-color: transparent;
      color: var(--sf-text-primary);
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  :deep(.hidden-input) {
    display: none;
  }

  .upload-icon {
    width: 20px;
    height: 20px;
    color: var(--sf-text-secondary);
    transition: color 0.2s ease;
  }

  &:hover {
    .upload-icon {
      color: var(--sf-accent);
    }
  }
}

.send-btn {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s ease;

  svg {
    width: 14px;
    height: 14px;
    color: #fff;
  }

  &:hover {
    background: var(--sf-accent);
    transform: scale(1.05);
  }

  &:disabled {
    background: var(--sf-bg-gray-200);
    cursor: not-allowed;
    transform: none;
  }
}

@media (max-width: 768px) {
  .input-controls {
    gap: 8px;
    margin-top: 12px;
  }

  .input-row {
    width: calc(100% - 32px);
    max-width: 100%;
    padding: 0 12px;
    gap: 8px;
    box-sizing: border-box;
    margin: 0 auto;
  }

  .input-area--centered {
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    margin: 0 auto;
  }

  .file-upload-trigger {
    width: 36px;
    height: 36px;

    :deep(.upload-btn) {
      width: 36px;
      height: 36px;
    }
  }

  .send-btn {
    width: 32px;
    height: 32px;

    svg {
      width: 16px;
      height: 16px;
    }
  }
}
</style>
