<template>
  <div class="settings-page">
    <div class="settings-page__bg">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
      <div class="bg-noise"></div>
    </div>

    <div class="settings-page__main">
      <div class="settings-page__header">
        <button class="back-btn" @click="router.push('/')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
          <span>返回</span>
        </button>
        <div class="header-content">
          <h1>设置中心</h1>
          <p>管理您的应用偏好和配置</p>
        </div>
      </div>

      <div class="settings-page__content">
        <div class="settings-section">
          <div class="section-header">
            <div class="section-icon section-icon--ai">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a2 2 0 0 1 2 2c0 .74-.4 1.39-1 1.73V7h1a7 7 0 0 1 7 7h1a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-1v1a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-1H2a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1h1a7 7 0 0 1 7-7h1V5.73c-.6-.34-1-.99-1-1.73a2 2 0 0 1 2-2M7.5 13A1.5 1.5 0 0 0 6 14.5 1.5 1.5 0 0 0 7.5 16 1.5 1.5 0 0 0 9 14.5 1.5 1.5 0 0 0 7.5 13m9 0a1.5 1.5 0 0 0-1.5 1.5 1.5 1.5 0 0 0 1.5 1.5 1.5 1.5 0 0 0 1.5-1.5 1.5 1.5 0 0 0-1.5-1.5M12 17a1 1 0 0 0-1 1 1 1 0 0 0 1 1 1 1 0 0 0 1-1 1 1 0 0 0-1-1z"/>
              </svg>
            </div>
            <span class="section-title">AI 模型</span>
          </div>
          <div class="settings-card" @click="router.push('/settings/models')">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="3" />
                <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
              </svg>
            </div>
            <div class="card-info">
              <div class="card-name">模型配置</div>
              <div class="card-desc">管理 AI 大模型，配置 API Key，选择默认模型</div>
            </div>
            <svg class="card-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </div>
          <div class="settings-card" @click="router.push('/settings/token-stats')">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="1" x2="12" y2="23" />
                <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
              </svg>
            </div>
            <div class="card-info">
              <div class="card-name">Token 统计</div>
              <div class="card-desc">查看 API 调用成本和 Token 使用情况</div>
            </div>
            <svg class="card-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </div>
        </div>

        <div class="settings-section">
          <div class="section-header">
            <div class="section-icon section-icon--mcp">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2L2 7l10 5 10-5-10-5z" />
                <path d="M2 17l10 5 10-5" />
                <path d="M2 12l10 5 10-5" />
              </svg>
            </div>
            <span class="section-title">MCP 服务器</span>
          </div>
          <div class="settings-card" @click="router.push('/mcp')">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
                <line x1="8" y1="21" x2="16" y2="21" />
                <line x1="12" y1="17" x2="12" y2="21" />
              </svg>
            </div>
            <div class="card-info">
              <div class="card-name">MCP 管理</div>
              <div class="card-desc">配置和管理 MCP 服务器与工具</div>
            </div>
            <svg class="card-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </div>
        </div>

        <div class="settings-section">
          <div class="section-header">
            <div class="section-icon section-icon--skills">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
              </svg>
            </div>
            <span class="section-title">Skills</span>
          </div>
          <div class="settings-card" @click="router.push('/skills')">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
              </svg>
            </div>
            <div class="card-info">
              <div class="card-name">技能管理</div>
              <div class="card-desc">管理和配置 Agent 技能，查看、创建和编辑技能</div>
            </div>
            <svg class="card-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </div>
        </div>

        <div class="settings-section">
          <div class="section-header">
            <div class="section-icon section-icon--theme">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="5" />
                <line x1="12" y1="1" x2="12" y2="3" />
                <line x1="12" y1="21" x2="12" y2="23" />
                <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
                <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
                <line x1="1" y1="12" x2="3" y2="12" />
                <line x1="21" y1="12" x2="23" y2="12" />
                <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
                <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
              </svg>
            </div>
            <span class="section-title">外观</span>
          </div>
          <div class="settings-card settings-card--toggle">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
              </svg>
            </div>
            <div class="card-info">
              <div class="card-name">深色模式</div>
              <div class="card-desc">切换应用的明暗主题</div>
            </div>
            <label class="theme-switch">
              <input type="checkbox" :checked="isDark" @change="handleThemeChange" />
              <span class="theme-slider">
                <span class="theme-slider__icon theme-slider__icon--sun">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="5" />
                    <line x1="12" y1="1" x2="12" y2="3" />
                    <line x1="12" y1="21" x2="12" y2="23" />
                    <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
                    <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
                    <line x1="1" y1="12" x2="3" y2="12" />
                    <line x1="21" y1="12" x2="23" y2="12" />
                    <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
                    <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
                  </svg>
                </span>
                <span class="theme-slider__icon theme-slider__icon--moon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
                  </svg>
                </span>
              </span>
            </label>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const isDark = computed({
  get: () => userStore.isDarkMode,
  set: () => {}
})

const handleThemeChange = () => {
  userStore.toggleTheme()
}
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.settings-page {
  @include page-container;
}

.settings-page__bg {
  @include page-background;
}

.settings-page__main {
  @include page-main;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.settings-page__header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
  width: 100%;
  max-width: 680px;

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
}

.settings-page__content {
  width: 100%;
  max-width: 680px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-bottom: 40px;
}

.settings-section {
  .section-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
    padding-left: 4px;
  }

  .section-icon {
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 8px;

    svg {
      width: 16px;
      height: 16px;
    }

    &--ai {
      background: color-mix(in srgb, var(--chat-accent-purple) 20%, transparent);
      color: var(--chat-accent-purple);
    }

    &--mcp {
      background: color-mix(in srgb, var(--chat-accent-cyan) 20%, transparent);
      color: var(--chat-accent-cyan);
    }

    &--agent {
      background: color-mix(in srgb, var(--chat-accent-green) 20%, transparent);
      color: var(--chat-accent-green);
    }

    &--skills {
      background: color-mix(in srgb, var(--chat-accent-orange) 20%, transparent);
      color: var(--chat-accent-orange);
    }

    &--theme {
      background: color-mix(in srgb, var(--chat-accent-pink) 20%, transparent);
      color: var(--chat-accent-pink);
    }
  }

  .section-title {
    font-size: 12px;
    font-weight: 600;
    color: var(--chat-text-muted);
    text-transform: uppercase;
    letter-spacing: 1px;
  }
}

.settings-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.25s ease;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: linear-gradient(135deg, var(--chat-surface-glass-hover) 0%, transparent 50%);
    opacity: 0;
    transition: opacity 0.25s ease;
  }

  &:hover {
    background: var(--chat-surface-glass-hover);
    border-color: var(--chat-border-default);
    transform: translateY(-2px);
    box-shadow: var(--chat-shadow-lg);

    &::before {
      opacity: 1;
    }

    .card-arrow {
      transform: translateX(4px);
      color: var(--chat-text-secondary);
    }
  }

  &--secondary {
    margin-top: 8px;
  }

  &--toggle {
    cursor: default;

    &:hover {
      transform: none;
    }
  }

  .card-icon {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--chat-surface-glass);
    border-radius: 12px;
    flex-shrink: 0;

    svg {
      width: 22px;
      height: 22px;
      color: var(--chat-text-secondary);
    }
  }

  .card-info {
    flex: 1;
    min-width: 0;
  }

  .card-name {
    font-size: 15px;
    font-weight: 600;
    color: var(--chat-text-primary);
    margin-bottom: 4px;
  }

  .card-desc {
    font-size: 13px;
    color: var(--chat-text-muted);
    line-height: 1.4;
  }

  .card-arrow {
    width: 20px;
    height: 20px;
    color: var(--chat-text-muted);
    flex-shrink: 0;
    transition: all 0.2s ease;
  }
}

.theme-switch {
  position: relative;
  display: inline-block;
  width: 56px;
  height: 30px;
  flex-shrink: 0;

  input {
    opacity: 0;
    width: 0;
    height: 0;

    &:checked + .theme-slider {
      background: var(--chat-gradient-primary);

      .theme-slider__icon--sun {
        opacity: 0;
        transform: rotate(180deg) scale(0.5);
      }

      .theme-slider__icon--moon {
        opacity: 1;
        transform: rotate(0deg) scale(1);
      }
    }
  }

  .theme-slider {
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--chat-surface-glass-active);
    border-radius: 30px;
    transition: all 0.3s ease;
    border: 1px solid var(--chat-border-subtle);

    &::before {
      content: '';
      position: absolute;
      width: 24px;
      height: 24px;
      left: 3px;
      bottom: 2px;
      background: var(--chat-text-primary);
      border-radius: 50%;
      transition: all 0.3s ease;
      box-shadow: var(--chat-shadow-sm);
    }

    &__icon {
      position: absolute;
      width: 16px;
      height: 16px;
      top: 50%;
      transform: translateY(-50%);
      transition: all 0.3s ease;

      svg {
        width: 14px;
        height: 14px;
        stroke: var(--chat-text-primary);
      }

      &--sun {
        right: 8px;
        opacity: 1;
        transform: translateY(-50%) rotate(0deg) scale(1);
      }

      &--moon {
        left: 8px;
        opacity: 0;
        transform: translateY(-50%) rotate(-180deg) scale(0.5);
      }
    }
  }

  input:checked + .theme-slider::before {
    transform: translateX(26px);
  }
}

@media (max-width: 768px) {
  .settings-page__main {
    padding: 16px;
  }

  .settings-page__header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;

    .header-content h1 {
      font-size: 24px;
    }
  }

  .settings-card {
    padding: 16px;

    .card-icon {
      width: 40px;
      height: 40px;
    }
  }
}
</style>
