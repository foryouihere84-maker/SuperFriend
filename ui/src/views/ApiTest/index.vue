<template>
  <div class="api-test-page">
    <div class="api-test-page__bg">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
      <div class="bg-noise"></div>
    </div>

    <div class="api-test-page__main">
      <div class="page-header">
        <div class="header-content">
          <h1>API 连接测试</h1>
          <p>检查后端服务连接状态</p>
        </div>
        <button class="btn btn--ghost" @click="goHome">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
            <polyline points="9 22 9 12 15 12 15 22" />
          </svg>
          返回首页
        </button>
      </div>

      <div class="config-card">
        <div class="card-header">
          <div class="card-header__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3" />
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
            </svg>
          </div>
          <h2>连接配置</h2>
        </div>
        <div class="config-grid">
          <div class="config-item">
            <span class="config-item__label">API Base URL</span>
            <span class="config-item__value">{{ apiBaseUrl }}</span>
          </div>
          <div class="config-item">
            <span class="config-item__label">前端端口</span>
            <span class="config-item__value">{{ frontendPort }}</span>
          </div>
          <div class="config-item">
            <span class="config-item__label">后端端口</span>
            <span class="config-item__value">{{ backendPort }}</span>
          </div>
        </div>
      </div>

      <div class="test-card">
        <div class="card-header">
          <div class="card-header__icon card-header__icon--success">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
            </svg>
          </div>
          <h2>健康检查</h2>
        </div>
        <div class="test-actions">
          <button class="btn btn--primary" :disabled="testingHealth" @click="testHealth">
            <svg v-if="!testingHealth" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
            </svg>
            <span class="spinner" v-else></span>
            {{ testingHealth ? '测试中...' : '测试健康检查接口' }}
          </button>
          <button class="btn btn--success" :disabled="testingAuth" @click="testAuth">
            <svg v-if="!testingAuth" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
            <span class="spinner" v-else></span>
            {{ testingAuth ? '测试中...' : '测试认证接口' }}
          </button>
        </div>

        <div v-if="testResults.length > 0" class="test-results">
          <div
            v-for="(result, index) in testResults"
            :key="index"
            class="result-item"
            :class="result.success ? 'result-item--success' : 'result-item--error'"
          >
            <div class="result-item__header">
              <span class="result-item__title">{{ result.title }}</span>
              <span class="status-badge" :class="result.success ? 'status-badge--success' : 'status-badge--error'">
                {{ result.success ? '成功' : '失败' }}
              </span>
            </div>
            <div class="result-item__details">
              <div class="detail-row">
                <span class="detail-row__label">URL</span>
                <span class="detail-row__value">{{ result.url }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-row__label">状态</span>
                <span class="detail-row__value">{{ result.status }}</span>
              </div>
              <div v-if="result.error" class="detail-row">
                <span class="detail-row__label">错误</span>
                <span class="detail-row__value detail-row__value--error">{{ result.error }}</span>
              </div>
              <div v-if="result.response" class="detail-row detail-row--code">
                <span class="detail-row__label">响应</span>
                <pre class="code-block">{{ JSON.stringify(result.response, null, 2) }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="troubleshoot-card">
        <div class="card-header">
          <div class="card-header__icon card-header__icon--warning">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
          </div>
          <h2>故障排除建议</h2>
        </div>
        <div class="troubleshoot-content">
          <div class="troubleshoot-section">
            <h3>如果测试失败，请检查：</h3>
            <ul class="check-list">
              <li>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                后端服务是否已启动（端口 8080）
              </li>
              <li>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                数据库连接是否正常
              </li>
              <li>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                防火墙是否阻止了连接
              </li>
              <li>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                API路径配置是否正确
              </li>
              <li>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                CORS配置是否允许前端访问
              </li>
            </ul>
          </div>
          <div class="troubleshoot-section">
            <h3>启动后端服务：</h3>
            <div class="terminal">
              <div class="terminal__header">
                <span class="terminal__dot terminal__dot--red"></span>
                <span class="terminal__dot terminal__dot--yellow"></span>
                <span class="terminal__dot terminal__dot--green"></span>
              </div>
              <div class="terminal__body">
                <code>cd c:\Users\iherefor\Desktop\project\HarmonyNotes</code>
                <code>mvn spring-boot:run</code>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { API_BASE_URL } from '@/config/api'

const router = useRouter()

const apiBaseUrl = API_BASE_URL
const frontendPort = '3000'
const backendPort = '8080'

const testingHealth = ref(false)
const testingAuth = ref(false)
const testResults = ref<any[]>([])

const goHome = () => {
  router.push('/')
}

interface TestResult {
  title: string
  url: string
  success: boolean
  status: string
  error?: string
  response?: any
}

const addResult = (result: TestResult) => {
  testResults.value.unshift(result)
  if (testResults.value.length > 10) {
    testResults.value.pop()
  }
}

const testHealth = async () => {
  testingHealth.value = true
  try {
    const response = await axios.get(`${apiBaseUrl}/health`, {
      timeout: 5000
    })
    addResult({
      title: '健康检查',
      url: `${apiBaseUrl}/health`,
      success: true,
      status: `HTTP ${response.status}`,
      response: response.data
    })
  } catch (error: any) {
    addResult({
      title: '健康检查',
      url: `${apiBaseUrl}/health`,
      success: false,
      status: 'Connection Failed',
      error: error.message || '连接失败，请检查后端服务是否启动'
    })
  } finally {
    testingHealth.value = false
  }
}

const testAuth = async () => {
  testingAuth.value = true
  try {
    const response = await axios.post(`${apiBaseUrl}/api/auth/login`, {
      username: 'test',
      password: 'test'
    }, {
      timeout: 5000,
      headers: {
        'Content-Type': 'application/json'
      }
    })
    addResult({
      title: '认证接口',
      url: `${apiBaseUrl}/api/auth/login`,
      success: true,
      status: `HTTP ${response.status}`,
      response: response.data
    })
  } catch (error: any) {
    const errorMsg = error.response?.data?.message || error.message || '认证接口测试失败'
    addResult({
      title: '认证接口',
      url: `${apiBaseUrl}/api/auth/login`,
      success: error.response?.status !== 404,
      status: error.response?.status ? `HTTP ${error.response.status}` : 'Connection Failed',
      error: errorMsg,
      response: error.response?.data
    })
  } finally {
    testingAuth.value = false
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.api-test-page {
  @include page-container;
}

.api-test-page__bg {
  @include page-background;
}

.api-test-page__main {
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
  justify-content: space-between;
  width: 100%;
  max-width: 900px;
  margin-bottom: 24px;
  gap: 20px;

  .header-content {
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
}

.config-card,
.test-card,
.troubleshoot-card {
  width: 100%;
  max-width: 900px;
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;

  &__icon {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    background: color-mix(in srgb, var(--chat-accent-purple) 15%, transparent);
    color: var(--chat-accent-purple);

    svg {
      width: 20px;
      height: 20px;
    }

    &--success {
      background: color-mix(in srgb, var(--chat-accent-green) 15%, transparent);
      color: var(--chat-accent-green);
    }

    &--warning {
      background: color-mix(in srgb, var(--chat-accent-orange) 15%, transparent);
      color: var(--chat-accent-orange);
    }
  }

  h2 {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: var(--chat-text-primary);
  }
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  background: color-mix(in srgb, var(--chat-surface-glass) 50%, transparent);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 12px;

  &__label {
    font-size: 12px;
    color: var(--chat-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__value {
    font-size: 14px;
    color: var(--chat-text-primary);
    font-weight: 500;
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  }
}

.test-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 20px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;

  svg {
    width: 18px;
    height: 18px;
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

  &--success {
    background: var(--chat-gradient-success);
    color: white;

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px color-mix(in srgb, var(--chat-accent-green) 40%, transparent);
    }
  }

  &--ghost {
    background: transparent;
    color: var(--chat-text-secondary);
    border: 1px solid var(--chat-border-subtle);

    &:hover:not(:disabled) {
      color: var(--chat-text-primary);
      background: var(--chat-surface-glass);
    }
  }
}

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid color-mix(in srgb, var(--chat-text-primary) 30%, transparent);
  border-top-color: var(--chat-text-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.test-results {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-item {
  padding: 16px;
  border-radius: 12px;
  border-left: 4px solid;

  &--success {
    background: color-mix(in srgb, var(--chat-accent-green) 5%, transparent);
    border-left-color: var(--chat-accent-green);
  }

  &--error {
    background: color-mix(in srgb, var(--chat-accent-red) 5%, transparent);
    border-left-color: var(--chat-accent-red);
  }

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
    color: var(--chat-text-primary);
  }

  &__details {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
}

.status-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 6px;

  &--success {
    background: color-mix(in srgb, var(--chat-accent-green) 15%, transparent);
    color: var(--chat-accent-green);
  }

  &--error {
    background: color-mix(in srgb, var(--chat-accent-red) 15%, transparent);
    color: var(--chat-accent-red);
  }
}

.detail-row {
  display: flex;
  gap: 12px;

  &__label {
    font-size: 13px;
    color: var(--chat-text-muted);
    min-width: 60px;
  }

  &__value {
    font-size: 13px;
    color: var(--chat-text-primary);
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;

    &--error {
      color: var(--chat-accent-red);
    }
  }

  &--code {
    flex-direction: column;
    gap: 8px;
  }
}

.code-block {
  background: color-mix(in srgb, var(--chat-bg-primary) 30%, transparent);
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
  color: var(--chat-text-secondary);
  overflow-x: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.troubleshoot-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.troubleshoot-section {
  h3 {
    font-size: 14px;
    font-weight: 600;
    color: var(--chat-text-primary);
    margin: 0 0 12px 0;
  }
}

.check-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;

  li {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 14px;
    color: var(--chat-text-secondary);

    svg {
      width: 16px;
      height: 16px;
      color: var(--chat-accent-green);
      flex-shrink: 0;
    }
  }
}

.terminal {
  background: color-mix(in srgb, var(--chat-bg-primary) 40%, transparent);
  border-radius: 10px;
  overflow: hidden;

  &__header {
    display: flex;
    gap: 8px;
    padding: 12px 16px;
    background: var(--chat-surface-glass);
    border-bottom: 1px solid var(--chat-border-subtle);
  }

  &__dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;

    &--red {
      background: #ff5f56;
    }

    &--yellow {
      background: #ffbd2e;
    }

    &--green {
      background: #27ca40;
    }
  }

  &__body {
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 8px;

    code {
      font-size: 13px;
      color: var(--chat-text-secondary);
      font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
    }
  }
}

@media (max-width: 768px) {
  .api-test-page__main {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;

    .btn {
      width: 100%;
    }
  }

  .config-grid {
    grid-template-columns: 1fr;
  }

  .test-actions {
    flex-direction: column;

    .btn {
      width: 100%;
    }
  }
}
</style>
