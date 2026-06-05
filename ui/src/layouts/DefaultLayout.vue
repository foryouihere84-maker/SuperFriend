<template>
  <div class="layout" :class="{ 'dark-mode': isDarkMode }">
    <el-container>
      <el-header class="layout-header">
        <div class="header-content">
          <div class="logo">
            <div class="logo-icon"></div>
            <span class="logo-text">Super Friend</span>
          </div>
          <div class="header-search">
            <el-input
              v-model="searchQuery"
              placeholder="搜索你想搜索的内容..."
              @keyup.enter="handleSearch"
              clearable
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </div>
          <div class="header-actions">
            <el-tooltip content="热点新闻" placement="bottom">
              <el-button
                class="news-btn"
                @click="showNewsDialog = true"
              >
                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <rect x="4" y="5" width="16" height="14" rx="2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                  <line x1="8" y1="9" x2="16" y2="9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <line x1="8" y1="12" x2="13" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <circle cx="16" cy="14" r="2" stroke="currentColor" stroke-width="1.5"/>
                  <line x1="17.5" y1="15.5" x2="18.5" y2="16.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                </svg>
                <span class="news-btn__badge" v-if="newsCount > 0">{{ newsCount }}</span>
              </el-button>
            </el-tooltip>
            <el-dropdown trigger="click" @command="handleCommand">
              <div class="user-info">
                <el-avatar :size="36" :src="userStore.user?.avatar">
                  {{ userStore.user?.username?.charAt(0).toUpperCase() }}
                </el-avatar>
                <span class="username">{{ userStore.user?.username }}</span>
                <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="about">
                    <el-icon><InfoFilled /></el-icon>
                    关于
                  </el-dropdown-item>
                  <el-dropdown-item command="profile">
                    <el-icon><User /></el-icon>
                    个人资料
                  </el-dropdown-item>
                  <el-dropdown-item command="settings">
                    <el-icon><Setting /></el-icon>
                    设置
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view v-slot="{ Component, route }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
    <NewsDialog v-model="showNewsDialog" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled, User, Setting, ArrowDown, SwitchButton, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { newsApi } from '@/api/news'
import NewsDialog from '@/components/business/NewsDialog/index.vue'
import { SEARCH_SERVICE_URL } from '@/config/api'

const router = useRouter()
const userStore = useUserStore()

const isDarkMode = computed(() => userStore.isDarkMode)
const showNewsDialog = ref(false)
const newsCount = ref(0)
const searchQuery = ref('')

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    window.open(`${SEARCH_SERVICE_URL}/search?q=${encodeURIComponent(searchQuery.value.trim())}`, '_blank')
  }
}

const fetchNewsCount = async () => {
  try {
    const status = await newsApi.getStatus()
    newsCount.value = status?.newsCount || 0
  } catch (error) {
    console.error('获取新闻数量失败:', error)
  }
}

const handleCommand = async (command: string) => {
  switch (command) {
    case 'about':
      router.push('/about')
      break
    case 'profile':
      router.push('/profile')
      break
    case 'settings':
      router.push('/settings')
      break
    case 'logout':
      try {
        await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        await userStore.logout()
        ElMessage.success('已退出登录')
        router.push('/login')
      } catch {
      }
      break
  }
}

onMounted(() => {
  userStore.initAuth()
  userStore.initTheme()
  fetchNewsCount()
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.layout {
  @include page-container;
  background: var(--chat-page-bg);
  position: relative;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--chat-page-gradient);
    pointer-events: none;
    z-index: 0;
  }

  &-header {
    position: sticky;
    top: 0;
    z-index: 1000;
    padding: 0;
    height: 64px;
    background: var(--chat-bg-primary);
    border-bottom: 1px solid var(--chat-border-subtle);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
  }

  .header-content {
    max-width: 1400px;
    margin: 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 100%;
    padding: 0 24px;
    position: relative;
    z-index: 1;
  }

  .logo {
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    transition: transform var(--chat-transition-normal);

    &:hover {
      .logo-icon {
        transform: scale(1.05);
      }
    }

    &-icon {
      width: 32px;
      height: 32px;
      background-image: url('@/assets/icons/sf-logo_icon.svg');
      background-size: contain;
      background-repeat: no-repeat;
      transition: transform var(--chat-transition-normal);
    }

    &-text {
      color: var(--sf-accent);
      font-family: var(--chat-font-display);
      font-size: 22px;
      font-weight: 700;
      letter-spacing: 0.5px;
    }
  }

  .header-search {
    flex: 1;
    max-width: 400px;
    margin: 0 24px;

    :deep(.el-input) {
      --el-input-bg-color: var(--chat-surface-glass);
      --el-input-border-color: #555;
      --el-input-text-color: var(--chat-text-primary);
      --el-input-placeholder-color: var(--chat-text-tertiary);
      --el-input-hover-border-color: #333;
      --el-input-focus-border-color: #333;

      .el-input__wrapper {
        border-radius: var(--chat-radius-lg);
        box-shadow: none;
        border: 0.5px solid #555;
        background: var(--chat-surface-glass);
        backdrop-filter: blur(10px);
        transition: all var(--chat-transition-normal);

        &:hover {
          border-color: #333;
        }

        &.is-focus {
          border-color: #333;
          box-shadow: none;
        }
      }

      .el-input__prefix {
        color: var(--chat-text-tertiary);
      }
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .news-btn {
    position: relative;
    width: 40px;
    height: 40px;
    border-radius: var(--chat-radius-md);
    border: 1px solid var(--chat-border-subtle);
    background: var(--chat-surface-glass);
    color: var(--chat-text-secondary);
    transition: all var(--chat-transition-normal);
    padding: 0;
    display: flex;
    align-items: center;
    justify-content: center;

    svg {
      width: 18px;
      height: 18px;
    }

    &:hover {
      background: var(--chat-surface-glass-hover);
      color: var(--sf-accent);
      border-color: var(--sf-border-default);
      transform: translateY(-1px);
    }

    &__badge {
      position: absolute;
      top: -4px;
      right: -4px;
      min-width: 18px;
      height: 18px;
      padding: 0 5px;
      border-radius: var(--chat-radius-full);
      background: var(--chat-gradient-primary);
      color: var(--chat-accent-text);
      font-size: 11px;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: var(--chat-shadow-sm);
    }
  }

  .theme-toggle {
    width: 40px;
    height: 40px;
    border-radius: var(--chat-radius-md);
    border: 1px solid var(--chat-border-subtle);
    background: var(--chat-surface-glass);
    color: var(--chat-text-secondary);
    transition: all var(--chat-transition-normal);

    &:hover {
      transform: rotate(180deg);
      background: var(--chat-surface-glass-hover);
      color: var(--chat-accent-purple-light);
      border-color: var(--chat-accent-purple);
    }
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 14px 6px 8px;
    border-radius: var(--chat-radius-lg);
    cursor: pointer;
    transition: all var(--chat-transition-normal);
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);

    &:hover {
      background: var(--chat-surface-glass-hover);
      border-color: var(--chat-border-default);
      transform: translateY(-1px);
      box-shadow: var(--chat-shadow-sm);
    }

    :deep(.el-avatar) {
      background: var(--chat-gradient-primary);
      color: var(--chat-accent-text);
      font-weight: 600;
    }

    .username {
      font-family: var(--chat-font-body);
      font-size: 14px;
      font-weight: 500;
      color: var(--chat-text-primary);
      max-width: 100px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .dropdown-icon {
      font-size: 12px;
      color: var(--chat-text-tertiary);
      transition: transform var(--chat-transition-fast);
    }

    &:hover .dropdown-icon {
      transform: translateY(2px);
    }
  }
}

.layout-main {
  padding: 0;
  background: transparent;
  min-height: calc(100vh - 64px);
  position: relative;
  z-index: 1;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--chat-transition-normal), transform var(--chat-transition-normal);
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

@media (max-width: 768px) {
  .layout {
    &-header {
      height: 56px;
    }

    .header-content {
      padding: 0 12px;
    }

    .logo {
      &-icon {
        width: 28px;
        height: 28px;
      }

      &-text {
        font-size: 18px;
      }
    }

    .header-search {
      display: none;
    }

    .user-info {
      padding: 6px 10px;

      .username {
        display: none;
      }

      .dropdown-icon {
        display: none;
      }
    }
  }

  .layout-main {
    min-height: calc(100vh - 56px);
  }
}
</style>
