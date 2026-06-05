<template>
  <div class="empty-layout">
    <router-view />
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped lang="scss">
.empty-layout {
  min-height: 100vh;
  width: 100%;
  background: var(--chat-bg-gray-50);
}
</style>
