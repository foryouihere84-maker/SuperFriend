<template>
  <div :class="['task-plan-card', `task-plan-card--${plan.status}`]">
    <div class="task-plan-card__header" @click="expanded = !expanded">
      <div class="task-plan-card__title">
        <svg class="task-plan-card__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
        </svg>
        <span class="task-plan-card__label">执行计划</span>
        <span class="task-plan-card__progress">
          {{ plan.completedSteps }}/{{ plan.totalSteps }} 步骤
        </span>
      </div>
      <div class="task-plan-card__header-right">
        <div class="task-plan-card__progress-bar-mini">
          <div
            class="task-plan-card__progress-bar-mini__fill"
            :style="{ width: progressPercent + '%' }"
          />
        </div>
        <svg
          :class="['task-plan-card__arrow', { 'task-plan-card__arrow--expanded': expanded }]"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </div>
    </div>

    <div v-if="plan.summary" class="task-plan-card__summary">
      {{ plan.summary }}
    </div>

    <transition name="plan-collapse">
      <div v-show="expanded" class="task-plan-card__steps">
        <div
          v-for="step in plan.steps"
          :key="step.stepNumber"
          :class="['task-step', `task-step--${step.status}`]"
        >
          <div class="task-step__indicator">
            <svg v-if="step.status === 'completed'" class="task-step__icon task-step__icon--completed" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            <svg v-else-if="step.status === 'failed'" class="task-step__icon task-step__icon--failed" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
            <svg v-else-if="step.status === 'executing'" class="task-step__icon task-step__icon--executing" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
            </svg>
            <span v-else class="task-step__number">{{ step.stepNumber }}</span>
          </div>

          <div class="task-step__content">
            <div class="task-step__header">
              <span class="task-step__description">{{ step.description }}</span>
              <span v-if="step.duration" class="task-step__duration">{{ formatDuration(step.duration) }}</span>
            </div>
            <div v-if="step.toolName" class="task-step__tool">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="task-step__tool-icon">
                <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z" />
              </svg>
              {{ step.toolName }}
            </div>
            <div v-if="step.error" class="task-step__error">
              {{ step.error }}
            </div>
            <div v-if="step.result && expanded" class="task-step__result">
              {{ step.result }}
            </div>
          </div>
        </div>
      </div>
    </transition>

    <div class="task-plan-card__progress-bar">
      <div
        class="task-plan-card__progress-bar__fill"
        :class="`task-plan-card__progress-bar__fill--${plan.status}`"
        :style="{ width: progressPercent + '%' }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TaskPlanInfo } from '@/types/chat'

const props = defineProps<{
  plan: TaskPlanInfo
}>()

const expanded = ref(true)

const progressPercent = computed(() => {
  if (props.plan.totalSteps === 0) return 0
  return Math.round((props.plan.completedSteps / props.plan.totalSteps) * 100)
})

const formatDuration = (ms: number) => {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.floor(ms / 60000)}m ${(Math.floor(ms / 1000) % 60)}s`
}
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.task-plan-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: var(--chat-radius-lg);
  overflow: hidden;
  font-size: 13px;
  position: relative;
  backdrop-filter: blur(12px);

  &--executing {
    border-color: color-mix(in srgb, var(--chat-accent-purple) 40%, transparent);
    box-shadow: 0 0 0 1px color-mix(in srgb, var(--chat-accent-purple) 10%, transparent), 0 0 20px color-mix(in srgb, var(--chat-accent-purple) 5%, transparent);
  }

  &--completed {
    border-color: color-mix(in srgb, var(--chat-accent-green) 40%, transparent);
  }

  &--failed {
    border-color: color-mix(in srgb, var(--chat-accent-red) 40%, transparent);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 16px;
    cursor: pointer;
    user-select: none;
    transition: background var(--chat-transition-fast);

    &:hover {
      background: var(--chat-surface-glass-hover);
    }
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__icon {
    width: 18px;
    height: 18px;
    color: var(--chat-accent-purple);
    flex-shrink: 0;
  }

  &__label {
    font-weight: 600;
    color: var(--chat-text-primary);
    font-size: 14px;
  }

  &__progress {
    color: var(--chat-text-tertiary);
    font-size: 12px;
    font-weight: 600;
    background: color-mix(in srgb, var(--chat-accent-purple) 10%, transparent);
    padding: 3px 12px;
    border-radius: var(--chat-radius-full);
  }

  &__header-right {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__progress-bar-mini {
    width: 60px;
    height: 4px;
    background: var(--chat-bg-elevated);
    border-radius: 2px;
    overflow: hidden;

    &__fill {
      height: 100%;
      background: var(--chat-gradient-primary);
      border-radius: 2px;
      transition: width 0.4s ease;
    }
  }

  &__arrow {
    width: 16px;
    height: 16px;
    color: var(--chat-text-tertiary);
    transition: transform var(--chat-transition-fast);
    flex-shrink: 0;

    &--expanded {
      transform: rotate(180deg);
    }
  }

  &__summary {
    padding: 0 16px 12px;
    color: var(--chat-text-secondary);
    font-size: 13px;
    line-height: 1.5;
  }

  &__steps {
    padding: 0 16px 14px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__progress-bar {
    height: 3px;
    background: var(--chat-bg-elevated);

    &__fill {
      height: 100%;
      transition: width 0.5s ease;
      border-radius: 0 2px 2px 0;

      &--executing {
        background: var(--chat-gradient-primary);
        animation: shimmer 2s ease-in-out infinite;
      }

      &--completed {
        background: var(--chat-gradient-success);
      }

      &--failed {
        background: linear-gradient(135deg, var(--chat-accent-red), #dc2626);
      }

      &--pending {
        background: var(--chat-border-default);
      }
    }
  }
}

@keyframes shimmer {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.plan-collapse-enter-active,
.plan-collapse-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}

.plan-collapse-enter-from,
.plan-collapse-leave-to {
  opacity: 0;
  max-height: 0;
}

.task-step {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--chat-radius-md);
  background: var(--chat-bg-secondary);
  border: 1px solid transparent;
  transition: all var(--chat-transition-fast);

  &--executing {
    border-color: color-mix(in srgb, var(--chat-accent-purple) 30%, transparent);
    background: color-mix(in srgb, var(--chat-accent-purple) 5%, transparent);
  }

  &--completed {
    opacity: 0.85;
  }

  &--failed {
    border-color: color-mix(in srgb, var(--chat-accent-red) 30%, transparent);
    background: color-mix(in srgb, var(--chat-accent-red) 5%, transparent);
  }

  &__indicator {
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 1px;
  }

  &__icon {
    width: 20px;
    height: 20px;

    &--completed {
      color: var(--chat-accent-green);
    }

    &--failed {
      color: var(--chat-accent-red);
    }

    &--executing {
      color: var(--chat-accent-purple);
      animation: spin 1.5s linear infinite;
    }
  }

  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }

  &__number {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: var(--chat-bg-elevated);
    color: var(--chat-text-tertiary);
    font-size: 11px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__content {
    flex: 1;
    min-width: 0;
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__description {
    color: var(--chat-text-primary);
    font-size: 13px;
    line-height: 1.4;
    flex: 1;
    min-width: 0;
  }

  &__duration {
    color: var(--chat-text-tertiary);
    font-size: 11px;
    flex-shrink: 0;
    font-family: var(--chat-font-mono);
  }

  &__tool {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 6px;
    color: var(--chat-text-tertiary);
    font-size: 11px;
    font-family: var(--chat-font-mono);
  }

  &__tool-icon {
    width: 12px;
    height: 12px;
    flex-shrink: 0;
    color: var(--chat-accent-cyan);
  }

  &__error {
    margin-top: 8px;
    padding: 8px 10px;
    background: color-mix(in srgb, var(--chat-accent-red) 10%, transparent);
    border-radius: var(--chat-radius-sm);
    color: var(--chat-accent-red);
    font-size: 11px;
    line-height: 1.4;
  }

  &__result {
    margin-top: 8px;
    padding: 8px 10px;
    background: var(--chat-bg-elevated);
    border-radius: var(--chat-radius-sm);
    color: var(--chat-text-secondary);
    font-size: 11px;
    line-height: 1.4;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-all;
  }
}
</style>
