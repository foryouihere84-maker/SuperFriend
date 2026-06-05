<template>
  <div class="stat-card">
    <div class="stat-card__icon">
      <svg v-if="icon === 'data'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="18" y1="20" x2="18" y2="10" />
        <line x1="12" y1="20" x2="12" y2="4" />
        <line x1="6" y1="20" x2="6" y2="14" />
      </svg>
      <svg v-else-if="icon === 'refresh'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="23 4 23 10 17 10" />
        <polyline points="1 20 1 14 7 14" />
        <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
      </svg>
      <svg v-else-if="icon === 'trend'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
      </svg>
      <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
        <line x1="8" y1="21" x2="16" y2="21" />
        <line x1="12" y1="17" x2="12" y2="21" />
      </svg>
    </div>
    <div class="stat-card__content">
      <div class="stat-card__value">{{ formattedValue }}</div>
      <div class="stat-card__label">{{ label }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatNumber } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    value: number | string
    label: string
    icon?: 'data' | 'refresh' | 'trend' | 'monitor'
  }>(),
  {
    icon: 'data'
  }
)

const formattedValue = computed(() => {
  if (typeof props.value === 'number') {
    return formatNumber(props.value)
  }
  return props.value
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.stat-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: var(--chat-radius-lg);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all var(--chat-transition-normal);
  backdrop-filter: blur(12px);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: linear-gradient(135deg, rgba(139, 92, 246, 0.05) 0%, transparent 50%);
    opacity: 0;
    transition: opacity var(--chat-transition-normal);
  }

  &:hover {
    transform: translateY(-4px);
    border-color: rgba(139, 92, 246, 0.3);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3), 0 0 20px rgba(139, 92, 246, 0.1);

    &::before {
      opacity: 1;
    }
  }

  &__icon {
    width: 52px;
    height: 52px;
    border-radius: var(--chat-radius-md);
    background: var(--chat-gradient-primary);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    position: relative;
    z-index: 1;

    svg {
      width: 26px;
      height: 26px;
      color: white;
    }
  }

  &__content {
    flex: 1;
    position: relative;
    z-index: 1;
  }

  &__value {
    font-size: 28px;
    font-weight: 700;
    background: var(--chat-gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    margin-bottom: 4px;
    font-family: var(--chat-font-display);
  }

  &__label {
    font-size: 13px;
    color: var(--chat-text-secondary);
    font-weight: 500;
  }
}
</style>
