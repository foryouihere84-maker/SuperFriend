<template>
  <nav class="mobile-bottom-nav" :class="{ 'mobile-bottom-nav--hidden': !isVisible }">
    <div class="mobile-bottom-nav__tabs">
      <button
        class="mobile-bottom-nav__tab"
        :class="{ 'mobile-bottom-nav__tab--active': activeTab === 'chats' }"
        @click="handleTabClick('chats')"
      >
        <svg class="tab-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
        <span class="tab-label">对话</span>
      </button>

      <button
        class="mobile-bottom-nav__tab mobile-bottom-nav__tab--primary"
        :class="{ 'mobile-bottom-nav__tab--active': activeTab === 'new' }"
        @click="handleTabClick('new')"
      >
        <svg class="tab-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        <span class="tab-label">新建</span>
      </button>

      <button
        class="mobile-bottom-nav__tab"
        :class="{ 'mobile-bottom-nav__tab--active': activeTab === 'settings' }"
        @click="handleTabClick('settings')"
      >
        <svg class="tab-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3" />
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
        </svg>
        <span class="tab-label">设置</span>
      </button>
    </div>
    <div class="mobile-bottom-nav__safe-area"></div>
  </nav>
</template>

<script setup lang="ts">
type TabType = 'chats' | 'new' | 'settings'

defineProps<{
  activeTab?: TabType
  isVisible?: boolean
}>()

const emit = defineEmits<{
  (e: 'tab-change', tab: TabType): void
}>()

const handleTabClick = (tab: TabType) => {
  emit('tab-change', tab)
}
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.mobile-bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  background: var(--sf-surface);
  border-top: 1px solid var(--sf-border-light);
  padding-bottom: env(safe-area-inset-bottom);
  display: none;

  @media (max-width: 768px) {
    display: block;
  }

  &--hidden {
    display: none;
  }

  &__tabs {
    display: flex;
    justify-content: space-around;
    align-items: center;
    height: 56px;
  }

  &__tab {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: 8px 16px;
    background: none;
    border: none;
    cursor: pointer;
    color: var(--sf-text-tertiary);
    transition: color var(--sf-transition-fast);

    &--active {
      color: var(--sf-accent);

      .tab-icon {
        transform: scale(1.05);
      }
    }

    &--primary {
      .tab-icon {
        width: 32px;
        height: 32px;
        background: var(--sf-accent);
        border-radius: 50%;
        padding: 6px;
        color: #ffffff;
        box-shadow: 0 2px 8px rgba(0, 102, 255, 0.3);
        transition: all var(--sf-transition-fast);
      }

      &.mobile-bottom-nav__tab--active .tab-icon {
        transform: scale(1.1);
        box-shadow: 0 4px 12px rgba(0, 102, 255, 0.4);
      }

      .tab-label {
        color: var(--sf-accent);
      }
    }

    &:active {
      transform: scale(0.95);
    }
  }

  .tab-icon {
    width: 22px;
    height: 22px;
    transition: all var(--sf-transition-fast);
  }

  .tab-label {
    font-size: 11px;
    font-weight: 500;
    transition: color var(--sf-transition-fast);
  }

  &__safe-area {
    height: env(safe-area-inset-bottom);
    background: transparent;
  }
}
</style>
