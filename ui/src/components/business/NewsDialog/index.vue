<template>
  <Teleport to="body">
    <Transition name="news-modal">
      <div v-if="visible" class="news-overlay" @click.self="handleClose">
        <div class="news-panel">
          <div class="news-header">
            <div class="news-header__left">
              <div class="news-header__badge">
                <span class="news-header__badge-dot"></span>
                <span>LIVE</span>
              </div>
              <div class="news-header__info">
                <h3 class="news-header__title">热点新闻</h3>
                <span class="news-header__update" v-if="updateTime">
                  {{ updateTime }} 更新
                </span>
              </div>
            </div>
            <div class="news-header__actions">
              <button 
                class="news-header__refresh" 
                :class="{ 'is-loading': refreshing }"
                :disabled="refreshing"
                @click="handleRefresh"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M23 4v6h-6M1 20v-6h6"/>
                  <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                </svg>
              </button>
              <button class="news-header__close" @click="handleClose">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>

          <div class="news-content" v-loading="loading">
            <div class="news-list">
              <TransitionGroup name="news-item">
                <article
                  v-for="(item, index) in newsList"
                  :key="item.rank"
                  class="news-card"
                  :style="{ '--delay': index * 0.05 + 's' }"
                  @click="openNews(item)"
                >
                  <div class="news-card__rank" :class="getRankClass(item.rank)">
                    <span class="news-card__rank-num">{{ item.rank }}</span>
                    <span class="news-card__rank-label" v-if="item.rank <= 3">TOP</span>
                  </div>
                  
                  <div class="news-card__body">
                    <h4 class="news-card__title">{{ item.title }}</h4>
                    <div class="news-card__meta">
                      <span class="news-card__category" v-if="item.category">{{ item.category }}</span>
                      <span class="news-card__source" v-if="item.source">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <circle cx="12" cy="12" r="10"/>
                          <path d="M12 6v6l4 2"/>
                        </svg>
                        {{ item.source }}
                      </span>
                      <span class="news-card__time" v-if="item.publishTime">{{ item.publishTime }}</span>
                    </div>
                  </div>

                  <div class="news-card__action">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M18 13v6a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2h6"/>
                      <polyline points="15 3 21 3 21 9"/>
                      <line x1="10" y1="14" x2="21" y2="3"/>
                    </svg>
                  </div>
                </article>
              </TransitionGroup>
            </div>

            <div v-if="!loading && newsList.length === 0" class="news-empty">
              <div class="news-empty__icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M19 20H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v12a2 2 0 01-2 2z"/>
                  <path d="M7 8h10M7 12h6"/>
                </svg>
              </div>
              <p class="news-empty__text">暂无新闻数据</p>
              <button class="news-empty__btn" @click="handleRefresh">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M23 4v6h-6M1 20v-6h6"/>
                  <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                </svg>
                刷新获取
              </button>
            </div>
          </div>

          <div class="news-footer">
            <div class="news-footer__brand">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M2 12h20M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/>
              </svg>
              <span>TianAPI</span>
            </div>
            <div class="news-footer__stats">
              <span class="news-footer__count">{{ newsList.length }}</span>
              <span class="news-footer__label">条新闻</span>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { newsApi, type NewsItem } from '@/api/news'
import { cache, CacheKeys, CacheTTL } from '@/utils/cache'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = ref(props.modelValue)
const loading = ref(false)
const refreshing = ref(false)
const newsList = ref<NewsItem[]>([])
const updateTime = ref('')

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    fetchNews()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const handleEscape = (e: KeyboardEvent) => {
  if (e.key === 'Escape' && visible.value) {
    handleClose()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscape)
})

interface NewsCache {
  news: NewsItem[]
  updateTime: string
}

const NEWS_CACHE_TTL = CacheTTL.HOUR * 4

const fetchNews = async (forceRefresh = false) => {
  if (!forceRefresh) {
    const cached = cache.get<NewsCache>(CacheKeys.NEWS_LIST)
    if (cached) {
      newsList.value = cached.news
      updateTime.value = cached.updateTime
      return
    }
  }

  loading.value = true
  try {
    const response = await newsApi.getHotNews()
    if (response.success) {
      newsList.value = response.news || []
      updateTime.value = response.updateTime || ''
      cache.set(CacheKeys.NEWS_LIST, {
        news: newsList.value,
        updateTime: updateTime.value
      }, { ttl: NEWS_CACHE_TTL })
    } else {
      ElMessage.warning(response.message || '获取新闻失败')
    }
  } catch (error) {
    console.error('获取新闻失败:', error)
    ElMessage.error('获取新闻失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleRefresh = async () => {
  refreshing.value = true
  try {
    const response = await newsApi.refreshNews()
    if (response.success) {
      cache.remove(CacheKeys.NEWS_LIST)
      await fetchNews(true)
      ElMessage.success('新闻已刷新')
    } else {
      ElMessage.warning(response.message || '刷新失败')
    }
  } catch (error) {
    console.error('刷新新闻失败:', error)
    ElMessage.error('刷新失败，请稍后重试')
  } finally {
    refreshing.value = false
  }
}

const getRankClass = (rank: number) => {
  if (rank === 1) return 'rank--gold'
  if (rank === 2) return 'rank--silver'
  if (rank === 3) return 'rank--bronze'
  return ''
}

const openNews = (item: NewsItem) => {
  if (item.url) {
    window.open(item.url, '_blank')
  } else {
    ElMessage.info('暂无详情链接')
  }
}

const handleClose = () => {
  emit('update:modelValue', false)
}
</script>

<style lang="scss">
@use '@/styles/sf-theme.scss' as *;

.news-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.news-panel {
  width: 520px;
  max-height: 80vh;
  background: var(--chat-bg-elevated);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 24px;
  box-shadow: var(--chat-shadow-lg), 0 0 0 1px var(--chat-border-subtle);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.news-modal-enter-active {
  animation: modalFadeIn 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  
  .news-panel {
    animation: panelSlideIn 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  }
}

.news-modal-leave-active {
  animation: modalFadeOut 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  
  .news-panel {
    animation: panelSlideOut 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  }
}

@keyframes modalFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes modalFadeOut {
  from { opacity: 1; }
  to { opacity: 0; }
}

@keyframes panelSlideIn {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

@keyframes panelSlideOut {
  from {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
  to {
    opacity: 0;
    transform: scale(0.95) translateY(10px);
  }
}

.news-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: var(--chat-surface-glass);
  border-bottom: 1px solid var(--chat-border-subtle);
  flex-shrink: 0;

  &__left {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  &__badge {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    background: rgba(239, 68, 68, 0.12);
    border: 1px solid rgba(239, 68, 68, 0.25);
    border-radius: 20px;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.5px;
    color: var(--chat-accent-red);
    text-transform: uppercase;

    &-dot {
      width: 6px;
      height: 6px;
      background: var(--chat-accent-red);
      border-radius: 50%;
      animation: pulse 2s ease-in-out infinite;
    }
  }

  &__info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__title {
    margin: 0;
    font-size: 20px;
    font-weight: 700;
    color: var(--chat-text-primary);
    letter-spacing: -0.02em;
  }

  &__update {
    font-size: 12px;
    color: var(--chat-text-muted);
    font-weight: 400;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__refresh,
  &__close {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 10px;
    color: var(--chat-text-secondary);
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

    svg {
      width: 16px;
      height: 16px;
      transition: transform 0.6s cubic-bezier(0.4, 0, 0.2, 1);
    }

    &:hover {
      background: var(--chat-gradient-primary);
      border-color: transparent;
      color: white;
      transform: scale(1.05);
      
      svg {
        transform: rotate(180deg);
      }
    }

    &.is-loading svg {
      animation: spin 1s linear infinite;
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  &__close {
    &:hover svg {
      transform: rotate(90deg);
    }
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.news-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  min-height: 300px;
  max-height: 50vh;
  
  &::-webkit-scrollbar {
    width: 5px;
  }
  
  &::-webkit-scrollbar-track {
    background: transparent;
    margin: 8px 0;
  }
  
  &::-webkit-scrollbar-thumb {
    background: var(--chat-border-default);
    border-radius: 10px;
    
    &:hover {
      background: var(--chat-text-muted);
    }
  }
}

.news-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.news-item-enter-active {
  animation: slideIn 0.4s cubic-bezier(0.4, 0, 0.2, 1) forwards;
  animation-delay: var(--delay);
}

.news-item-leave-active {
  animation: slideOut 0.3s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes slideOut {
  from {
    opacity: 1;
    transform: translateX(0);
  }
  to {
    opacity: 0;
    transform: translateX(20px);
  }
}

.news-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 16px;
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--chat-gradient-primary);
    opacity: 0;
    transition: opacity 0.3s ease;
    pointer-events: none;
  }

  &:hover {
    border-color: var(--chat-accent-purple);
    transform: translateY(-2px);
    box-shadow: var(--chat-shadow-md);

    &::before {
      opacity: 0.03;
    }

    .news-card__action {
      opacity: 1;
      transform: translateX(0);
    }

    .news-card__rank:not(.rank--gold):not(.rank--silver):not(.rank--bronze) {
      background: var(--chat-accent-purple);
      color: white;
    }
  }

  &:active {
    transform: translateY(0);
  }

  &__rank {
    width: 36px;
    height: 36px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    flex-shrink: 0;
    transition: all 0.3s ease;

    &-num {
      font-size: 14px;
      font-weight: 700;
      line-height: 1;
      color: var(--chat-text-tertiary);
    }

    &-label {
      font-size: 7px;
      font-weight: 600;
      letter-spacing: 0.5px;
      margin-top: 1px;
      opacity: 0.8;
      color: inherit;
    }

    &.rank--gold {
      background: linear-gradient(135deg, #FFD700 0%, #FFA500 50%, #FF8C00 100%);
      border: none;
      box-shadow: 0 4px 12px rgba(255, 165, 0, 0.35);
      
      .news-card__rank-num,
      .news-card__rank-label {
        color: white;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
      }
    }

    &.rank--silver {
      background: linear-gradient(135deg, #C0C0C0 0%, #A8A8A8 50%, #909090 100%);
      border: none;
      box-shadow: 0 4px 12px rgba(160, 160, 160, 0.35);
      
      .news-card__rank-num,
      .news-card__rank-label {
        color: white;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
      }
    }

    &.rank--bronze {
      background: linear-gradient(135deg, #CD7F32 0%, #B8860B 50%, #8B6914 100%);
      border: none;
      box-shadow: 0 4px 12px rgba(184, 134, 11, 0.35);
      
      .news-card__rank-num,
      .news-card__rank-label {
        color: white;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
      }
    }
  }

  &__body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__title {
    margin: 0;
    font-size: 14px;
    font-weight: 500;
    color: var(--chat-text-primary);
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    transition: color 0.2s ease;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  &__category {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    background: linear-gradient(135deg, var(--chat-accent-purple) 0%, var(--chat-accent-cyan) 100%);
    border-radius: 4px;
    font-size: 10px;
    font-weight: 600;
    color: var(--chat-accent-text);
    letter-spacing: 0.3px;
  }

  &__source {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    color: var(--chat-accent-cyan);
    font-weight: 500;

    svg {
      width: 12px;
      height: 12px;
      opacity: 0.7;
    }
  }

  &__time {
    font-size: 11px;
    color: var(--chat-text-muted);
  }

  &__action {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 8px;
    color: var(--chat-text-muted);
    opacity: 0;
    transform: translateX(-8px);
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    flex-shrink: 0;

    svg {
      width: 14px;
      height: 14px;
    }
  }
}

.news-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 280px;
  padding: 48px 24px;
  text-align: center;

  &__icon {
    width: 80px;
    height: 80px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 20px;
    margin-bottom: 20px;

    svg {
      width: 36px;
      height: 36px;
      color: var(--chat-text-muted);
      opacity: 0.5;
    }
  }

  &__text {
    margin: 0 0 20px;
    font-size: 15px;
    color: var(--chat-text-muted);
    font-weight: 500;
  }

  &__btn {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 20px;
    background: var(--chat-gradient-primary);
    border: none;
    border-radius: 10px;
    color: white;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s ease;

    svg {
      width: 16px;
      height: 16px;
    }

    &:hover {
      transform: translateY(-2px);
      box-shadow: var(--chat-shadow-glow-purple);
    }

    &:active {
      transform: translateY(0);
    }
  }
}

.news-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--chat-surface-glass);
  border-top: 1px solid var(--chat-border-subtle);
  flex-shrink: 0;

  &__brand {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: var(--chat-text-muted);
    font-weight: 500;

    svg {
      width: 16px;
      height: 16px;
      opacity: 0.6;
    }
  }

  &__stats {
    display: flex;
    align-items: baseline;
    gap: 4px;
  }

  &__count {
    font-size: 20px;
    font-weight: 700;
    background: var(--chat-gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  &__label {
    font-size: 12px;
    color: var(--chat-text-muted);
  }
}

@media (max-width: 768px) {
  .news-overlay {
    align-items: flex-end;
  }

  .news-panel {
    width: 100%;
    max-height: 85vh;
    border-radius: 16px 16px 0 0;
    margin: 0;
  }

  .news-modal-enter-active {
    animation: modalFadeInMobile 0.3s cubic-bezier(0.4, 0, 0.2, 1);

    .news-panel {
      animation: panelSlideUp 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }
  }

  .news-modal-leave-active {
    animation: modalFadeOutMobile 0.2s cubic-bezier(0.4, 0, 0.2, 1);

    .news-panel {
      animation: panelSlideDown 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    }
  }

  @keyframes modalFadeInMobile {
    from { opacity: 0; }
    to { opacity: 1; }
  }

  @keyframes modalFadeOutMobile {
    from { opacity: 1; }
    to { opacity: 0; }
  }

  @keyframes panelSlideUp {
    from { transform: translateY(100%); }
    to { transform: translateY(0); }
  }

  @keyframes panelSlideDown {
    from { transform: translateY(0); }
    to { transform: translateY(100%); }
  }

  .news-header {
    padding: 16px;

    &__left {
      gap: 10px;
    }

    &__badge {
      padding: 3px 8px;
      font-size: 9px;
    }

    &__title {
      font-size: 16px;
    }

    &__update {
      font-size: 11px;
    }

    &__refresh,
    &__close {
      width: 32px;
      height: 32px;
      border-radius: 8px;
    }
  }

  .news-content {
    max-height: calc(85vh - 120px);
    overflow-y: auto;
  }

  .news-card {
    padding: 12px;
    gap: 12px;

    &__rank {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      flex-shrink: 0;
    }

    &__title {
      font-size: 13px;
      -webkit-line-clamp: 2;
    }

    &__meta {
      gap: 8px;
    }

    &__action {
      width: 28px;
      height: 28px;
      border-radius: 6px;
      opacity: 1;
      transform: none;
    }
  }

  .news-footer {
    padding: 12px 16px;
    flex-wrap: wrap;
    gap: 8px;

    &__brand {
      font-size: 11px;
    }

    &__count {
      font-size: 18px;
    }

    &__label {
      font-size: 11px;
    }
  }
}
</style>
