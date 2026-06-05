<template>
  <div class="sf-logo">
    <!-- 人像剪影图标 -->
    <svg class="sf-logo__icon" viewBox="0 0 50 50" xmlns="http://www.w3.org/2000/svg">
      <!-- 头部 -->
      <circle cx="25" cy="10" r="8" fill="currentColor"/>
      <!-- 身体 - 优雅形状 -->
      <path d="M25 18 C17 18 13 24 13 32 C13 42 19 46 25 46 C31 46 37 42 37 32 C37 24 33 18 25 18Z" fill="currentColor"/>
      <!-- 双臂延伸 -->
      <path d="M13 26 C7 24 3 28 2 32 C1 36 4 40 8 39" stroke="currentColor" stroke-width="3" fill="none" stroke-linecap="round"/>
      <path d="M37 26 C43 24 47 28 48 32 C49 36 46 40 42 39" stroke="currentColor" stroke-width="3" fill="none" stroke-linecap="round"/>
    </svg>

    <!-- 艺术字 SuperFriend -->
    <span class="sf-logo__text">SuperFriend</span>
  </div>
</template>

<script setup lang="ts">
// No additional logic needed
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.sf-logo {
  display: flex;
  align-items: center;
  gap: 12px;

  &__icon {
    width: 54px;
    height: 54px;
    flex-shrink: 0;
    color: #1a1a1a;
  }

  &__text {
    font-family: 'Playfair Display', Georgia, 'Times New Roman', serif;
    font-size: 32px;
    font-style: italic;
    font-weight: 500;
    color: #1a1a1a;
    letter-spacing: 0.5px;
  }
}

// Dark mode support
:root.dark .sf-logo {
  &__icon {
    color: #ffffff;
  }

  &__text {
    color: #ffffff;
  }
}
</style>
