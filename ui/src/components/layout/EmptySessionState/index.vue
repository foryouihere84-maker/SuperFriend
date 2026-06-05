<template>
  <div class="empty-session-state">
    <div class="empty-session-state__icon">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      </svg>
    </div>
    <h3 class="empty-session-state__title">开始新对话</h3>
    <p class="empty-session-state__desc">
      点击下方按钮或使用 <kbd>{{ shortcutModifier }}+N</kbd> 创建新对话
    </p>
    <div class="empty-session-state__actions">
      <button class="empty-session-state__btn" @click="$emit('new-chat')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        新对话
      </button>
    </div>
    <div class="empty-session-state__tips">
      <h4>快捷键提示</h4>
      <ul>
        <li><kbd>{{ shortcutModifier }}+B</kbd> 切换侧边栏</li>
        <li><kbd>{{ shortcutModifier }}+K</kbd> 搜索历史</li>
        <li><kbd>{{ shortcutModifier }}+N</kbd> 新对话</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineEmits<{
  (e: 'new-chat'): void
}>()

const isMac = computed(() => {
  return navigator.platform.toUpperCase().indexOf('MAC') >= 0 ||
    navigator.userAgent.indexOf('Mac OS') !== -1
})

const shortcutModifier = computed(() => isMac.value ? '⌘' : 'Ctrl')
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.empty-session-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;

  &__icon {
    width: 64px;
    height: 64px;
    margin-bottom: 24px;
    color: var(--sf-text-muted);

    svg {
      width: 100%;
      height: 100%;
    }
  }

  &__title {
    font-size: 20px;
    font-weight: 600;
    color: var(--sf-text-primary);
    margin: 0 0 8px;
  }

  &__desc {
    font-size: 14px;
    color: var(--sf-text-secondary);
    margin: 0 0 24px;

    kbd {
      padding: 2px 6px;
      font-family: var(--sf-font-sans);
      font-size: 12px;
      background: var(--sf-bg-gray-100);
      border: 1px solid var(--sf-border-default);
      border-radius: 4px;
    }
  }

  &__actions {
    margin-bottom: 32px;
  }

  &__btn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 12px 24px;
    background: var(--sf-accent);
    color: #ffffff;
    border: none;
    border-radius: var(--sf-radius-lg);
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all var(--sf-transition-fast);

    svg {
      width: 18px;
      height: 18px;
    }

    &:hover {
      background: var(--sf-accent-hover);
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(0, 102, 255, 0.3);
    }

    &:active {
      transform: translateY(0);
    }
  }

  &__tips {
    padding: 16px 20px;
    background: var(--sf-bg-gray-50);
    border-radius: var(--sf-radius-lg);
    text-align: left;
    max-width: 280px;

    h4 {
      font-size: 13px;
      font-weight: 600;
      color: var(--sf-text-secondary);
      margin: 0 0 12px;
    }

    ul {
      list-style: none;
      margin: 0;
      padding: 0;
    }

    li {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: var(--sf-text-tertiary);
      margin-bottom: 8px;

      &:last-child {
        margin-bottom: 0;
      }

      kbd {
        padding: 2px 6px;
        font-family: var(--sf-font-sans);
        font-size: 11px;
        background: var(--sf-bg-white);
        border: 1px solid var(--sf-border-default);
        border-radius: 4px;
        color: var(--sf-text-secondary);
      }
    }
  }
}
</style>
