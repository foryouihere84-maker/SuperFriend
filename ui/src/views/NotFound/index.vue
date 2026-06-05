<template>
  <div class="not-found-page">
    <div class="not-found-page__bg">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
      <div class="bg-noise"></div>
      <div class="bg-glow"></div>
    </div>

    <div class="not-found-content">
      <div class="error-code">
        <span>4</span>
        <span class="error-code__zero">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10" />
          </svg>
        </span>
        <span>4</span>
      </div>
      <h1 class="error-title">页面不存在</h1>
      <p class="error-desc">抱歉，您访问的页面不存在或已被移除。</p>
      <div class="action-buttons">
        <button class="action-btn action-btn--primary" @click="goHome">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
            <polyline points="9 22 9 12 15 12 15 22" />
          </svg>
          返回首页
        </button>
        <button class="action-btn action-btn--secondary" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="19" y1="12" x2="5" y2="12" />
            <polyline points="12 19 5 12 12 5" />
          </svg>
          返回上一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/')
}

const goBack = () => {
  router.back()
}
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.not-found-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: var(--chat-page-bg);
}

.not-found-page__bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 0;
  overflow: hidden;

  .bg-gradient {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--chat-page-gradient);
  }

  .bg-grid {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-image: 
      linear-gradient(var(--chat-border-subtle) 1px, transparent 1px),
      linear-gradient(90deg, var(--chat-border-subtle) 1px, transparent 1px);
    background-size: 60px 60px;
    mask-image: radial-gradient(ellipse 80% 60% at 50% 50%, black, transparent);
  }

  .bg-noise {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    opacity: 0.15;
    background-image: var(--chat-page-noise);
    pointer-events: none;
  }

  .bg-glow {
    position: absolute;
    top: 50%;
    left: 50%;
    width: 600px;
    height: 600px;
    transform: translate(-50%, -50%);
    background: radial-gradient(circle, rgba(139, 92, 246, 0.15) 0%, transparent 70%);
    animation: pulse 4s ease-in-out infinite;
  }
}

.not-found-content {
  text-align: center;
  position: relative;
  z-index: 1;
  padding: 40px;
  animation: slideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.error-code {
  font-size: 120px;
  font-weight: 700;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;

  span {
    background: var(--chat-gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    text-shadow: 0 4px 30px rgba(139, 92, 246, 0.3);
  }

  &__zero {
    display: flex;
    align-items: center;
    justify-content: center;
    animation: rotate 10s linear infinite;

    svg {
      width: 100px;
      height: 100px;
      stroke: var(--chat-accent-purple);
    }
  }
}

.error-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--chat-text-primary);
  margin-bottom: 12px;
  letter-spacing: -0.5px;
}

.error-desc {
  font-size: 16px;
  color: var(--chat-text-tertiary);
  margin-bottom: 40px;
  line-height: 1.6;
}

.action-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  flex-wrap: wrap;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 28px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  border: none;

  svg {
    width: 18px;
    height: 18px;
  }

  &--primary {
    background: var(--chat-gradient-primary);
    color: var(--chat-accent-text);
    box-shadow: var(--chat-shadow-glow-purple);

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 30px rgba(139, 92, 246, 0.4);
    }

    &:active {
      transform: translateY(0);
    }
  }

  &--secondary {
    background: var(--chat-surface-glass);
    color: var(--chat-text-primary);
    border: 1px solid var(--chat-border-subtle);

    &:hover {
      background: var(--chat-surface-glass-hover);
      border-color: var(--chat-border-default);
      transform: translateY(-2px);
    }

    &:active {
      transform: translateY(0);
    }
  }
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.15;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 0.25;
    transform: translate(-50%, -50%) scale(1.1);
  }
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .not-found-content {
    padding: 24px;
  }

  .error-code {
    font-size: 80px;

    &__zero svg {
      width: 70px;
      height: 70px;
    }
  }

  .error-title {
    font-size: 24px;
  }

  .error-desc {
    font-size: 14px;
  }

  .action-buttons {
    flex-direction: column;
    width: 100%;
    max-width: 280px;
    margin: 0 auto;
  }

  .action-btn {
    width: 100%;
    justify-content: center;
  }
}
</style>
