<template>
  <router-view />
</template>

<script setup lang="ts">
import { useCacheInit } from '@/composables/useCacheInit'

// 初始化缓存系统（预热缓存、自动清理过期缓存）
useCacheInit({
  warmup: true,
  healthCheck: true,
  autoClean: true
})
</script>

<style>
</style>
