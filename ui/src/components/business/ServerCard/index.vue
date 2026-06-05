<template>
  <div
    :class="['server-card', { 'server-card--selected': server.selected, 'server-card--running': server.running }]"
  >
    <div class="server-card__header">
      <label class="server-card__checkbox">
        <input
          type="checkbox"
          :checked="server.selected"
          @change="handleToggle"
        />
        <span class="server-card__checkbox-custom">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
            <polyline points="20 6 9 17 4 12" />
          </svg>
        </span>
      </label>
      <div class="server-card__info">
        <div class="server-card__name">{{ server.name }}</div>
        <span class="server-card__status" :class="server.running ? 'server-card__status--running' : 'server-card__status--stopped'">
          <span class="status-dot"></span>
          {{ server.running ? '运行中' : '已停止' }}
        </span>
      </div>
    </div>

    <div class="server-card__description">{{ server.description || '暂无描述' }}</div>

    <div class="server-card__details">
      <div class="server-card__detail">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
        </svg>
        <span>工具数: {{ server.toolCount }}</span>
      </div>
    </div>

    <div class="server-card__actions">
      <button
        v-if="server.selected"
        class="server-card__btn server-card__btn--danger"
        @click="handleDeselect"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
        取消选择
      </button>
      <button
        v-else
        class="server-card__btn server-card__btn--primary"
        @click="handleSelect"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="20 6 9 17 4 12" />
        </svg>
        选择
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { MCPServer } from '@/types/mcp'

const props = defineProps<{
  server: MCPServer
}>()

const emit = defineEmits<{
  (e: 'toggle', serverName: string): void
  (e: 'select', serverName: string): void
  (e: 'deselect', serverName: string): void
}>()

const handleToggle = () => {
  emit('toggle', props.server.name)
}

const handleSelect = () => {
  emit('select', props.server.name)
}

const handleDeselect = () => {
  emit('deselect', props.server.name)
}
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.server-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: var(--chat-radius-lg);
  padding: 20px;
  transition: all var(--chat-transition-normal);
  position: relative;
  overflow: hidden;
  backdrop-filter: blur(12px);

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: linear-gradient(135deg, rgba(139, 92, 246, 0.03) 0%, transparent 50%);
    opacity: 0;
    transition: opacity var(--chat-transition-normal);
  }

  &:hover {
    border-color: rgba(139, 92, 246, 0.3);
    transform: translateY(-3px);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3), 0 0 20px rgba(139, 92, 246, 0.1);

    &::before {
      opacity: 1;
    }
  }

  &--selected {
    border-color: rgba(139, 92, 246, 0.5);
    background: linear-gradient(135deg, rgba(139, 92, 246, 0.08) 0%, rgba(6, 182, 212, 0.05) 100%);

    &::before {
      opacity: 1;
    }
  }

  &--running::after {
    content: '';
    position: absolute;
    top: 12px;
    right: 12px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--chat-accent-green);
    box-shadow: 0 0 10px rgba(16, 185, 129, 0.5);
    animation: pulse 2s ease-in-out infinite;
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
    position: relative;
    z-index: 1;
  }

  &__checkbox {
    cursor: pointer;
    display: flex;
    align-items: center;

    input {
      display: none;

      &:checked + .server-card__checkbox-custom {
        background: var(--chat-gradient-primary);
        border-color: transparent;

        svg {
          opacity: 1;
          transform: scale(1);
        }
      }
    }

    &-custom {
      width: 22px;
      height: 22px;
      border: 2px solid var(--chat-border-default);
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all var(--chat-transition-fast);
      background: var(--chat-bg-secondary);

      svg {
        width: 14px;
        height: 14px;
        color: white;
        opacity: 0;
        transform: scale(0.5);
        transition: all var(--chat-transition-fast);
      }

      &:hover {
        border-color: var(--chat-accent-purple);
      }
    }
  }

  &__info {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__name {
    font-size: 16px;
    font-weight: 600;
    color: var(--chat-text-primary);
  }

  &__status {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    border-radius: var(--chat-radius-full);
    font-size: 11px;
    font-weight: 600;

    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }

    &--running {
      background: color-mix(in srgb, var(--chat-accent-green) 15%, transparent);
      color: var(--chat-accent-green);

      .status-dot {
        background: var(--chat-accent-green);
        animation: pulse 2s ease-in-out infinite;
      }
    }

    &--stopped {
      background: color-mix(in srgb, var(--chat-accent-red) 15%, transparent);
      color: var(--chat-accent-red);

      .status-dot {
        background: var(--chat-accent-red);
      }
    }
  }

  &__description {
    color: var(--chat-text-secondary);
    font-size: 13px;
    line-height: 1.6;
    margin-bottom: 12px;
    position: relative;
    z-index: 1;
  }

  &__details {
    display: flex;
    gap: 16px;
    margin-bottom: 16px;
    flex-wrap: wrap;
    position: relative;
    z-index: 1;
  }

  &__detail {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--chat-text-tertiary);
    font-size: 12px;
    padding: 6px 12px;
    background: rgba(255, 255, 255, 0.03);
    border-radius: var(--chat-radius-sm);

    svg {
      width: 14px;
      height: 14px;
      color: var(--chat-accent-cyan);
    }
  }

  &__actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
    position: relative;
    z-index: 1;
  }

  &__btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 10px 18px;
    border-radius: var(--chat-radius-md);
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: all var(--chat-transition-fast);
    border: none;

    svg {
      width: 16px;
      height: 16px;
    }

    &--primary {
      background: var(--chat-gradient-primary);
      color: var(--chat-accent-text);
      box-shadow: 0 4px 16px rgba(139, 92, 246, 0.2);

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 6px 24px rgba(139, 92, 246, 0.3);
      }
    }

    &--danger {
      background: color-mix(in srgb, var(--chat-accent-red) 15%, transparent);
      color: var(--chat-accent-red);
      border: 1px solid color-mix(in srgb, var(--chat-accent-red) 30%, transparent);

      &:hover {
        background: color-mix(in srgb, var(--chat-accent-red) 25%, transparent);
        border-color: color-mix(in srgb, var(--chat-accent-red) 50%, transparent);
      }
    }
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>
