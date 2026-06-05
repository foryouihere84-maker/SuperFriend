<template>
  <transition name="approval-fade">
    <div v-if="visible" class="approval-overlay" @click.self="handleDeny">
      <div class="approval-dialog">
        <div class="approval-dialog__header">
          <div class="approval-dialog__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            </svg>
          </div>
          <h3 class="approval-dialog__title">工具调用审批</h3>
          <span class="approval-dialog__badge">{{ approval?.operation || 'execute' }}</span>
        </div>

        <div class="approval-dialog__body">
          <div class="approval-info">
            <div class="approval-info__row">
              <span class="approval-info__label">工具</span>
              <span class="approval-info__value approval-info__value--tool">
                {{ approval?.serverName }}.{{ approval?.toolName }}
              </span>
            </div>
            <div v-if="approval?.description" class="approval-info__desc">
              {{ approval.description }}
            </div>
          </div>

          <div v-if="parsedArguments && Object.keys(parsedArguments).length > 0" class="approval-args">
            <div class="approval-args__title">参数详情</div>
            <div class="approval-args__list">
              <div
                v-for="(value, key) in parsedArguments"
                :key="key"
                class="approval-args__item"
              >
                <span class="approval-args__key">{{ key }}</span>
                <span class="approval-args__value">{{ formatArgValue(value) }}</span>
              </div>
            </div>
          </div>

          <div class="approval-dialog__timer">
            <div class="approval-dialog__timer-bar" :style="{ width: timerPercent + '%' }" />
            <span class="approval-dialog__timer-text">{{ timerSeconds }}s</span>
          </div>
        </div>

        <div class="approval-dialog__footer">
          <button class="approval-btn approval-btn--deny" @click="handleDeny">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
            拒绝
          </button>
          <button class="approval-btn approval-btn--approve" @click="handleApprove(false)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            允许
          </button>
          <button class="approval-btn approval-btn--always" @click="handleApprove(true)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
            总是允许
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import type { ApprovalRequest } from '@/api/permission'
import { submitApprovalDecision } from '@/api/permission'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  visible: boolean
  approval: ApprovalRequest | null
}>()

const emit = defineEmits<{
  (e: 'decided', decision: { requestId: string; approved: boolean; alwaysAllow: boolean }): void
  (e: 'close'): void
}>()

const timerSeconds = ref(30)
const timerPercent = ref(100)
let timerInterval: ReturnType<typeof setInterval> | null = null

const parsedArguments = computed(() => {
  if (!props.approval?.arguments) return null
  try {
    const args = props.approval.arguments
    if (args.startsWith('{') || args.startsWith('[')) {
      return JSON.parse(args)
    }
    return null
  } catch {
    return null
  }
})

const formatArgValue = (value: any): string => {
  const str = typeof value === 'string' ? value : JSON.stringify(value)
  if (str.length > 120) return str.substring(0, 120) + '...'
  return str
}

const startTimer = () => {
  stopTimer()
  timerSeconds.value = 30
  timerPercent.value = 100
  timerInterval = setInterval(() => {
    timerSeconds.value--
    timerPercent.value = (timerSeconds.value / 30) * 100
    if (timerSeconds.value <= 0) {
      stopTimer()
      handleDeny()
    }
  }, 1000)
}

const stopTimer = () => {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
}

const handleApprove = async (alwaysAllow: boolean) => {
  if (!props.approval) return
  stopTimer()

  try {
    await submitApprovalDecision({
      requestId: props.approval.requestId,
      decision: 'approved',
      reason: alwaysAllow ? '用户选择总是允许' : '用户允许',
      alwaysAllow
    })
    emit('decided', {
      requestId: props.approval.requestId,
      approved: true,
      alwaysAllow
    })
    emit('close')
  } catch (error: any) {
    ElMessage.error('审批提交失败: ' + (error?.message || '未知错误'))
  }
}

const handleDeny = async () => {
  if (!props.approval) return
  stopTimer()

  try {
    await submitApprovalDecision({
      requestId: props.approval.requestId,
      decision: 'denied',
      reason: '用户拒绝',
      alwaysAllow: false
    })
    emit('decided', {
      requestId: props.approval.requestId,
      approved: false,
      alwaysAllow: false
    })
    emit('close')
  } catch (error: any) {
    ElMessage.error('审批提交失败: ' + (error?.message || '未知错误'))
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    startTimer()
  } else {
    stopTimer()
  }
})

onUnmounted(() => {
  stopTimer()
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.approval-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(8px);
}

.approval-dialog {
  background: var(--chat-bg-primary);
  border: 1px solid var(--chat-border-subtle);
  border-radius: var(--chat-radius-xl);
  box-shadow: 0 25px 80px rgba(0, 0, 0, 0.5), 0 0 40px rgba(139, 92, 246, 0.1);
  width: 480px;
  max-width: 90vw;
  overflow: hidden;
  animation: dialogSlideIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  text-align: center;

  &__header {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    padding: 20px 24px 16px;
    border-bottom: 1px solid var(--chat-border-subtle);
    background: linear-gradient(135deg, rgba(139, 92, 246, 0.05) 0%, transparent 50%);
  }

  &__icon {
    width: 40px;
    height: 40px;
    border-radius: var(--chat-radius-md);
    background: linear-gradient(135deg, var(--chat-accent-orange), #d97706);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    svg {
      width: 22px;
      height: 22px;
      color: var(--chat-accent-text);
    }
  }

  &__title {
    font-size: 18px;
    font-weight: 600;
    color: var(--chat-text-primary);
    margin: 0;
    flex: 1;
  }

  &__badge {
    font-size: 10px;
    font-weight: 700;
    padding: 4px 12px;
    border-radius: var(--chat-radius-full);
    background: color-mix(in srgb, var(--chat-accent-orange) 15%, transparent);
    color: var(--chat-accent-orange);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__body {
    padding: 20px 24px;
  }

  &__footer {
    display: flex;
    gap: 10px;
    padding: 16px 24px 20px;
    border-top: 1px solid var(--chat-border-subtle);
    background: var(--chat-bg-secondary);
  }

  &__timer {
    position: relative;
    height: 4px;
    background: var(--chat-bg-elevated);
    border-radius: 2px;
    margin-top: 16px;
    overflow: hidden;

    &-bar {
      height: 100%;
      background: linear-gradient(135deg, var(--chat-accent-orange), #d97706);
      border-radius: 2px;
      transition: width 1s linear;
    }

    &-text {
      position: absolute;
      right: 0;
      top: -20px;
      font-size: 11px;
      color: var(--chat-text-tertiary);
      font-family: var(--chat-font-mono);
    }
  }
}

.approval-info {
  display: flex;
  flex-direction: column;
  align-items: center;

  &__row {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
  }

  &__label {
    font-size: 12px;
    color: var(--chat-text-tertiary);
    min-width: 32px;
  }

  &__value {
    font-size: 14px;
    font-weight: 500;
    color: var(--chat-text-primary);

    &--tool {
      font-family: var(--chat-font-mono);
      font-size: 13px;
      background: var(--chat-bg-elevated);
      padding: 4px 10px;
      border-radius: var(--chat-radius-sm);
      color: var(--chat-accent-cyan);
    }
  }

  &__desc {
    margin-top: 12px;
    font-size: 13px;
    color: var(--chat-text-secondary);
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 120px;
    overflow-y: auto;
    padding: 12px 14px;
    background: var(--chat-bg-secondary);
    border-radius: var(--chat-radius-md);
    border: 1px solid var(--chat-border-subtle);
  }
}

.approval-args {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;

  &__title {
    font-size: 11px;
    font-weight: 700;
    color: var(--chat-text-tertiary);
    margin-bottom: 10px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__list {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    max-height: 150px;
    overflow-y: auto;
    width: 100%;
  }

  &__item {
    display: flex;
    justify-content: center;
    gap: 10px;
    padding: 8px 12px;
    background: var(--chat-bg-secondary);
    border-radius: var(--chat-radius-sm);
    border: 1px solid var(--chat-border-subtle);
    width: 100%;
    max-width: 400px;
  }

  &__key {
    font-family: var(--chat-font-mono);
    font-size: 12px;
    font-weight: 600;
    color: var(--chat-accent-purple);
    min-width: 80px;
    flex-shrink: 0;
  }

  &__value {
    font-size: 12px;
    color: var(--chat-text-secondary);
    word-break: break-all;
    line-height: 1.4;
  }
}

.approval-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  border-radius: var(--chat-radius-md);
  padding: 12px 16px;
  transition: all var(--chat-transition-fast);
  cursor: pointer;
  border: none;

  svg {
    width: 16px;
    height: 16px;
  }

  &--deny {
    background: color-mix(in srgb, var(--chat-accent-red) 10%, transparent);
    border: 1px solid color-mix(in srgb, var(--chat-accent-red) 30%, transparent);
    color: var(--chat-accent-red);

    &:hover {
      background: color-mix(in srgb, var(--chat-accent-red) 20%, transparent);
      border-color: color-mix(in srgb, var(--chat-accent-red) 50%, transparent);
    }
  }

  &--approve {
    background: var(--chat-gradient-primary);
    color: var(--chat-accent-text);
    box-shadow: 0 4px 16px rgba(139, 92, 246, 0.2);

    &:hover {
      transform: translateY(-1px);
      box-shadow: 0 6px 24px rgba(139, 92, 246, 0.3);
    }
  }

  &--always {
    background: color-mix(in srgb, var(--chat-accent-green) 10%, transparent);
    border: 1px solid color-mix(in srgb, var(--chat-accent-green) 30%, transparent);
    color: var(--chat-accent-green);

    &:hover {
      background: color-mix(in srgb, var(--chat-accent-green) 20%, transparent);
      border-color: color-mix(in srgb, var(--chat-accent-green) 50%, transparent);
    }
  }
}

@keyframes dialogSlideIn {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(-10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.approval-fade-enter-active {
  transition: opacity 0.2s ease;
}

.approval-fade-leave-active {
  transition: opacity 0.15s ease;
}

.approval-fade-enter-from,
.approval-fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .approval-dialog {
    width: calc(100% - 32px);
    max-width: calc(100% - 32px);
    margin: 16px auto;
    border-radius: 12px;

    &__header {
      padding: 16px;
      flex-wrap: wrap;
      gap: 8px;

      .approval-dialog__icon {
        width: 36px;
        height: 36px;

        svg {
          width: 20px;
          height: 20px;
        }
      }

      .approval-dialog__title {
        font-size: 16px;
        width: 100%;
        order: 3;
      }

      .approval-dialog__badge {
        font-size: 9px;
        padding: 3px 10px;
      }
    }

    &__body {
      padding: 16px;
      max-height: calc(100vh - 300px);
      overflow-y: auto;
      box-sizing: border-box;
    }

    &__footer {
      flex-direction: column;
      padding: 12px 16px 16px;
      gap: 8px;
    }
  }

  .approval-btn {
    padding: 14px 16px;
    font-size: 14px;
    width: 100%;

    svg {
      width: 18px;
      height: 18px;
    }
  }

  .approval-info {
    &__value--tool {
      font-size: 12px;
      padding: 3px 8px;
    }

    &__desc {
      font-size: 12px;
      padding: 10px 12px;
      max-height: 100px;
    }
  }

  .approval-args {
    &__item {
      flex-direction: column;
      gap: 4px;
      align-items: flex-start;
      max-width: 100%;
    }

    &__key {
      min-width: unset;
      font-size: 11px;
    }

    &__value {
      font-size: 11px;
    }
  }
}
</style>
