<template>
  <div :class="['chat-message', `chat-message--${message.role}`, { 'chat-message--streaming': !message.done }]">
    <div v-if="showAvatar" class="chat-message__avatar">
      <div class="avatar-glow"></div>
      <div class="avatar-inner">
        <svg v-if="message.role === 'user'" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.5"/>
          <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="4" y="4" width="16" height="16" rx="4" stroke="currentColor" stroke-width="1.5"/>
          <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/>
          <circle cx="12" cy="12" r="1" fill="currentColor"/>
        </svg>
      </div>
    </div>

    <div class="chat-message__body">
      <div v-if="showTimestamp" class="chat-message__meta">
        <span class="chat-message__time">{{ formatTime(message.timestamp) }}</span>
        <span v-if="!message.done && message.loading" class="chat-message__status">
          <span class="status-dot"></span>
          {{ agentPhaseText || '生成中' }}
        </span>
      </div>

      <div v-if="message.role === 'assistant'" class="chat-message__assistant">
        <div v-if="message.error && message.done" class="error-block">
          <div class="error-block__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
          </div>
          <div class="error-block__content">
            <div class="error-block__title">{{ message.errorTitle || '响应出错' }}</div>
            <div class="error-block__message">{{ message.errorSuggestion || message.error }}</div>
          </div>
          <button
            v-if="message.retryable !== false"
            class="error-block__retry"
            @click="$emit('retry-message', message.id)"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M1 4v6h6" />
              <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" />
            </svg>
            重试
          </button>
        </div>

        <div v-if="message.thinking || message.toolCalls.length > 0" class="thinking-block" :class="{ 
          'thinking-block--expanded': thinkingExpanded, 
          'thinking-block--streaming': isCurrentlyStreamingThinking
        }">
          <div class="thinking-block__header" @click="thinkingExpanded = !thinkingExpanded">
            <div class="thinking-block__left">
              <div class="thinking-block__icon" :class="{ 
                'thinking-block__icon--done': message.done || (message.content && !isCurrentlyStreamingThinking),
                'thinking-block__icon--active': isCurrentlyStreamingThinking
              }">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10" />
                  <path d="M12 6v6l4 2" />
                </svg>
              </div>
              <span class="thinking-block__label">
                {{ isCurrentlyStreamingThinking ? '正在思考...' : (message.done || message.content ? '思考完成' : (agentPhaseText || '正在思考')) }}
              </span>
              <span v-if="message.toolCalls.length > 0" class="thinking-block__badge">
                {{ message.toolCalls.length }} 工具
              </span>
            </div>
            <svg class="thinking-block__arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </div>
          <transition name="thinking-slide">
            <div v-show="thinkingExpanded" class="thinking-block__content">
              <div v-if="message.thinking" class="thinking-block__text">
                <span class="thinking-text__content">{{ message.thinking }}</span>
                <span v-if="isCurrentlyStreamingThinking" class="thinking-text__cursor"></span>
              </div>
              <div v-if="message.toolCalls.length > 0" class="thinking-block__tools">
                <div v-for="(tool, idx) in message.toolCalls" :key="idx" class="tool-item" :class="`tool-item--${tool.status}`">
                  <div class="tool-item__indicator">
                    <span class="tool-item__dot"></span>
                  </div>
                  <div class="tool-item__info">
                    <span class="tool-item__name">{{ tool.name }}</span>
                    <span class="tool-item__status">{{ getToolStatusText(tool.status) }}</span>
                  </div>
                  <div v-if="tool.result" class="tool-item__result">{{ tool.result }}</div>
                </div>
              </div>
            </div>
          </transition>
        </div>

        <task-plan-card v-if="message.taskPlan" :plan="message.taskPlan" />

        <div v-if="message.skillRecommendations && message.skillRecommendations.length > 0" class="skill-recommendations">
          <div class="skill-recommendations__header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
            </svg>
            <span>推荐技能</span>
          </div>
          <div class="skill-recommendations__list">
            <div v-for="(skill, idx) in message.skillRecommendations" :key="idx" class="skill-card" :class="{ 'skill-card--auto': skill.autoExecute }">
              <div class="skill-card__header">
                <span class="skill-card__name">{{ skill.skillName }}</span>
                <span class="skill-card__tag" :class="skill.autoExecute ? 'skill-card__tag--auto' : ''">
                  {{ skill.autoExecute ? '自动' : '推荐' }}
                </span>
              </div>
              <div class="skill-card__desc">{{ skill.description }}</div>
              <div class="skill-card__footer">
                <span class="skill-card__category">{{ skill.category }}</span>
                <span class="skill-card__score">{{ (skill.score * 100).toFixed(0) }}%</span>
              </div>
              <div class="skill-card__actions">
                <button class="skill-card__btn skill-card__btn--primary" @click="$emit('executeSkill', skill)">执行</button>
                <button class="skill-card__btn" @click="$emit('ignoreSkill', skill)">忽略</button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="message.content || (message.loading && message.thinking)" class="content-block" :class="{ 'content-block--streaming': isCurrentlyStreamingContent }">
          <button
            v-if="message.done && message.content"
            class="content-block__copy-btn"
            @click="copyContent"
            title="复制全部内容"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
          </button>
          <div v-if="isCurrentlyStreamingContent" class="content-block__text content-block__text--streaming" v-html="formattedContent"></div>
          <div v-else-if="message.done && message.content" class="content-block__text" v-html="formattedContent"></div>
          <div v-else-if="message.content && !isCurrentlyStreamingContent" class="content-block__text content-block__text--paused" v-html="formattedContent"></div>
          <span v-if="isCurrentlyStreamingContent" class="streaming-cursor"></span>
        </div>

        <div v-if="!message.content && !message.thinking && message.loading" class="content-block content-block--loading">
          <div class="loading-dots">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>

      <div v-else class="chat-message__user">
        <!-- 用户图片 -->
        <div v-if="message.images && message.images.length > 0" class="chat-message__user-images">
          <img
            v-for="(img, idx) in message.images"
            :key="idx"
            :src="getImageUrl(img)"
            :alt="img.name || `图片 ${idx + 1}`"
            class="chat-message__user-image"
            @click="previewImage(getImageUrl(img))"
          />
        </div>
        <!-- 用户文档 -->
        <div v-if="message.documents && message.documents.length > 0" class="chat-message__user-files">
          <div v-for="(doc, idx) in message.documents" :key="idx" class="chat-message__user-file">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="16" y1="13" x2="8" y2="13" />
              <line x1="16" y1="17" x2="8" y2="17" />
            </svg>
            <span class="file-name">{{ doc.name || '文档' }}</span>
          </div>
        </div>
        <!-- 用户音频 -->
        <div v-if="message.audios && message.audios.length > 0" class="chat-message__user-files">
          <div v-for="(audio, idx) in message.audios" :key="idx" class="chat-message__user-file">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 18V5l12-2v13" />
              <circle cx="6" cy="18" r="3" />
              <circle cx="18" cy="16" r="3" />
            </svg>
            <span class="file-name">{{ audio.name || '音频' }}</span>
          </div>
        </div>
        <!-- 用户视频 -->
        <div v-if="message.videos && message.videos.length > 0" class="chat-message__user-files">
          <div v-for="(video, idx) in message.videos" :key="idx" class="chat-message__user-file">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="23 7 16 12 23 17 23 7" />
              <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
            </svg>
            <span class="file-name">{{ video.name || '视频' }}</span>
          </div>
        </div>
        <!-- 用户文本 -->
        <div v-if="message.content" class="chat-message__user-content" v-html="formattedContent"></div>
      </div>
    </div>

    <!-- 文件预览组件 -->
    <FilePreview v-model="previewVisible" :file-id="previewFileId" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { fileDB, type StoredFile } from '@/utils/fileDB'
import { imageDB } from '@/utils/imageDB'
import FilePreview from '@/components/business/FilePreview/index.vue'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css'
import { formatTime } from '@/utils/format'
import TaskPlanCard from '@/components/business/TaskPlanCard/index.vue'
import type { ChatMessage } from '@/types/chat'
import { ElMessage } from 'element-plus'

marked.use({
  breaks: true,
  gfm: true,
  extensions: [
    {
      name: 'code',
      renderer(token) {
        const code = token.text
        const lang = token.lang
        let highlighted: string
        if (lang && hljs.getLanguage(lang)) {
          try {
            highlighted = hljs.highlight(code, { language: lang }).value
          } catch {
            highlighted = hljs.highlightAuto(code).value
          }
        } else {
          highlighted = hljs.highlightAuto(code).value
        }
        // 使用 data-code 属性存储代码，通过全局函数处理复制
        const encodedCode = encodeURIComponent(code)
        return `<div class="code-block-wrapper">
  <div class="code-block-header">
    <span class="code-block-lang">${lang || 'code'}</span>
    <button class="code-block-copy" data-code="${encodedCode}" title="复制代码">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
      </svg>
      <span class="code-block-copy__text">复制</span>
    </button>
  </div>
  <pre><code class="hljs language-${lang || 'auto'}">${highlighted}</code></pre>
</div>`
      }
    }
  ]
})

const props = withDefaults(
  defineProps<{
    message: ChatMessage
    showAvatar?: boolean
    showTimestamp?: boolean
    agentPhaseText?: string
    sessionId?: string  // 当前会话 ID，用于按会话加载文件
    isLast?: boolean    // 是否是最后一条消息
  }>(),
  {
    showAvatar: true,
    showTimestamp: true,
    agentPhaseText: '',
    sessionId: '',
    isLast: false
  }
)

defineEmits<{
  (e: 'executeSkill', skill: any): void
  (e: 'ignoreSkill', skill: any): void
  (e: 'retry-message', messageId: string): void
}>()

const thinkingExpanded = ref(false)
const currentPhase = ref<'thinking' | 'content' | 'idle'>('idle')

// 异步加载 IndexedDB 中的图片
const loadIndexedDBImages = async () => {
  // 等待 DOM 更新完成
  await nextTick()

  const imageElements = document.querySelectorAll('.indexeddb-image[data-image-id]')
  console.log('[ImageLoad] 查找到的图片占位符数量:', imageElements.length)

  for (const img of imageElements) {
    const imageId = img.getAttribute('data-image-id')
    console.log('[ImageLoad] 处理图片 ID:', imageId)
    if (!imageId) continue

    // 如果图片已经是 data URL（可能由 v-html 渲染的），跳过处理
    const currentSrc = img.getAttribute('src')
    if (currentSrc && currentSrc.startsWith('data:image') && !currentSrc.includes('加载中')) {
      console.log('[ImageLoad] 跳过已加载图片:', imageId)
      continue
    }

    try {
      const storedImage = await imageDB.get(imageId)
      console.log('[ImageLoad] IndexedDB 查询结果:', imageId, storedImage ? '找到' : '未找到')
      if (storedImage?.dataUrl) {
        img.setAttribute('src', storedImage.dataUrl)
        img.removeAttribute('data-image-id')
        img.classList.remove('indexeddb-image')
        console.log('[ImageLoad] 图片加载成功:', imageId)
        // 标记为已显示
        if (props.sessionId) {
          getDisplayedImageSet(props.sessionId).add(imageId)
        }
      } else {
        console.log('[ImageLoad] 图片数据为空:', imageId)
        img.setAttribute('src', "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 150'%3E%3Crect fill='%23fee2e2' width='200' height='150'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' fill='%23dc2626' font-size='12'%3E图片加载失败%3C/text%3E%3C/svg%3E")
      }
    } catch (err) {
      console.error('[ImageLoad] 加载失败:', imageId, err)
      img.setAttribute('src', "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 150'%3E%3Crect fill='%23fee2e2' width='200' height='150'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' fill='%23dc2626' font-size='12'%3E图片加载失败%3C/text%3E%3C/svg%3E")
    }
  }
}

// 异步加载 IndexedDB 中的文件
const loadIndexedDBFiles = async () => {
  // 等待 DOM 更新完成
  await nextTick()

  const fileElements = document.querySelectorAll('.indexeddb-file[data-file-id]')

  for (const fileEl of fileElements) {
    const fileId = fileEl.getAttribute('data-file-id')
    const fileName = fileEl.getAttribute('data-file-name')
    if (!fileId) continue

    // 立即移除标记，防止其他调用重复处理
    fileEl.removeAttribute('data-file-id')
    fileEl.classList.remove('indexeddb-file')

    try {
      let storedFile: StoredFile | null = null

      // 首先尝试直接通过 ID 获取
      storedFile = await fileDB.get(fileId)

      // 如果没找到且有 sessionId，尝试从该会话的文件中查找
      if (!storedFile && props.sessionId) {
        const sessionFiles = await fileDB.getBySession(props.sessionId)
        // 通过文件名匹配
        storedFile = sessionFiles.find(f => f.fileName === fileName) || null
      }

      if (storedFile) {
        // 获取文件大小
        let fileSize = ''
        if (storedFile.size) {
          fileSize = formatFileSize(storedFile.size)
        } else if (storedFile.metadata?.fileSize) {
          fileSize = formatFileSize(storedFile.metadata.fileSize)
        } else if (storedFile.dataUrl) {
          fileSize = formatFileSize(storedFile.dataUrl.length * 0.75)
        }

        // 获取文件图标
        const icon = getFileIcon(storedFile.fileType)

        // 构建文件卡片 HTML（添加预览按钮）
        const fileCardHtml = `
          <div class="file-card">
            <div class="file-card__icon">${icon}</div>
            <div class="file-card__info">
              <div class="file-card__name">${fileName}</div>
              <div class="file-card__meta">
                <span class="file-card__type">${storedFile.fileType.toUpperCase()}</span>
                ${fileSize ? `<span class="file-card__size">${fileSize}</span>` : ''}
              </div>
            </div>
            <div class="file-card__actions">
              <button class="file-card__preview" onclick="window.previewFileLink(this)" data-file-id="${storedFile.id}" title="预览">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
              <button class="file-card__download" onclick="window.downloadFileLink(this)" data-file-id="${storedFile.id}" data-file-name="${fileName}" title="下载">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="7 10 12 15 17 10" />
                  <line x1="12" y1="15" x2="12" y2="3" />
                </svg>
              </button>
            </div>
          </div>
        `
        fileEl.innerHTML = fileCardHtml
      } else {
        // 文件未找到，显示错误提示
        fileEl.innerHTML = `<div class="file-card file-card--error"><span>📄 文件加载失败: ${fileName}</span></div>`
              }
    } catch (err) {
            fileEl.innerHTML = `<div class="file-card file-card--error"><span>📄 文件加载失败: ${fileName}</span></div>`
    }
  }
}

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
}

// 获取文件类型图标
const getFileIcon = (fileType: string): string => {
  switch (fileType.toLowerCase()) {
    case 'pdf': return '📄'
    case 'docx':
    case 'doc': return '📝'
    case 'xlsx':
    case 'xls': return '📊'
    case 'pptx':
    case 'ppt': return '📽️'
    case 'md':
    case 'txt': return '📝'
    default: return '📎'
  }
}

// 异步加载 IndexedDB 中的音频
const loadIndexedDBAudio = async () => {
  await nextTick()
  const audioElements = document.querySelectorAll('.indexeddb-audio[data-audio-id]')
  for (const el of audioElements) {
    const audioId = el.getAttribute('data-audio-id')
    if (!audioId) continue
    el.removeAttribute('data-audio-id')
    el.classList.remove('indexeddb-audio')

    try {
      const storedFile = await fileDB.get(audioId)
      if (storedFile?.dataUrl) {
        el.innerHTML = `
          <div class="audio-player">
            <audio controls preload="metadata" src="${storedFile.dataUrl}" style="width:100%;height:40px;" />
          </div>`
              } else {
        el.innerHTML = '<div class="audio-player audio-player--error">🎵 音频加载失败</div>'
      }
    } catch (err) {
      el.innerHTML = '<div class="audio-player audio-player--error">🎵 音频加载失败</div>'
    }
  }
}

// 异步加载 IndexedDB 中的视频
const loadIndexedDBVideo = async () => {
  await nextTick()
  const videoElements = document.querySelectorAll('.indexeddb-video[data-video-id]')
  for (const el of videoElements) {
    const videoId = el.getAttribute('data-video-id')
    if (!videoId) continue
    el.removeAttribute('data-video-id')
    el.classList.remove('indexeddb-video')

    try {
      const storedFile = await fileDB.get(videoId)
      if (storedFile?.dataUrl) {
        el.innerHTML = `
          <div class="video-player">
            <video controls preload="metadata" playsinline src="${storedFile.dataUrl}" />
          </div>`
      } else {
        el.innerHTML = '<div class="video-player video-player--error">🎬 视频加载失败</div>'
      }
    } catch (err) {
      el.innerHTML = '<div class="video-player video-player--error">🎬 视频加载失败</div>'
    }
  }
}

// 全局标记：记录每个会话已显示过的图片 ID（确保每张图片只显示一次）
// key: sessionId, value: Set<imageId>
const globalDisplayedImages = new Map<string, Set<string>>()
// 全局标记：记录每个会话已显示过的文件 ID
const globalDisplayedFiles = new Map<string, Set<string>>()
// 全局标记：记录正在处理中的 sessionId（防止并发执行）
const globalPendingSessionLoad = new Set<string>()

// 获取或创建会话的已显示图片集合
const getDisplayedImageSet = (sessionId: string): Set<string> => {
  if (!globalDisplayedImages.has(sessionId)) {
    globalDisplayedImages.set(sessionId, new Set<string>())
  }
  return globalDisplayedImages.get(sessionId)!
}

// 获取或创建会话的已显示文件集合
const getDisplayedFileSet = (sessionId: string): Set<string> => {
  if (!globalDisplayedFiles.has(sessionId)) {
    globalDisplayedFiles.set(sessionId, new Set<string>())
  }
  return globalDisplayedFiles.get(sessionId)!
}

// 保底机制：从 sessionId 加载该会话的所有图片（差异化补充）
// 核心逻辑：每张图片只显示一次，通过 globalDisplayedImages 跟踪
const loadSessionImages = async (sessionId: string) => {
  if (!sessionId) return

  // 检查是否正在处理中
  if (globalPendingSessionLoad.has(sessionId + '-images')) {
    return
  }
  globalPendingSessionLoad.add(sessionId + '-images')

  try {
    const sessionImages = await imageDB.getBySession(sessionId)

    if (sessionImages.length === 0) {
      globalPendingSessionLoad.delete(sessionId + '-images')
      return
    }

    // 获取该会话已显示的图片 ID 集合
    const displayedSet = getDisplayedImageSet(sessionId)

    // 过滤出尚未显示过的图片
    const missingImages = sessionImages.filter(img => !displayedSet.has(img.id))

    if (missingImages.length === 0) {
      globalPendingSessionLoad.delete(sessionId + '-images')
      return
    }

    // 找到最后一条 assistant 消息的内容区域
    const contentBlocks = document.querySelectorAll('.chat-message--assistant .content-block__text')
    const lastContentBlock = contentBlocks[contentBlocks.length - 1]

    if (!lastContentBlock) {
      globalPendingSessionLoad.delete(sessionId + '-images')
      return
    }

    // 为每张遗漏的图片创建 img 标签并追加到最后一条消息
    for (const img of missingImages) {
      // 标记为已显示
      displayedSet.add(img.id)

      const imgEl = document.createElement('img')
      imgEl.className = 'indexeddb-image'
      imgEl.setAttribute('data-image-id', img.id)
      imgEl.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 150"%3E%3Crect fill="%23f0f0f0" width="200" height="150"/%3E%3Ctext x="50%25" y="50%25" dominant-baseline="middle" text-anchor="middle" fill="%23999" font-size="12"%3E加载中...%3C/text%3E%3C/svg%3E'
      imgEl.style.cssText = 'max-width: 400px; border-radius: 8px; margin: 8px 0; display: block;'
      lastContentBlock.appendChild(imgEl)
    }

    // 加载图片数据
    setTimeout(() => {
      loadIndexedDBImages()
      globalPendingSessionLoad.delete(sessionId + '-images')
    }, 0)
  } catch (err) {
    globalPendingSessionLoad.delete(sessionId + '-images')
  }
}

// 保底机制：从 sessionId 加载该会话的所有文件（差异化补充）
const loadSessionFiles = async (sessionId: string) => {
  if (!sessionId) return

  // 检查是否正在处理中
  if (globalPendingSessionLoad.has(sessionId)) {
    return
  }
  globalPendingSessionLoad.add(sessionId)

  try {
    const sessionFiles = await fileDB.getBySession(sessionId)

    if (sessionFiles.length === 0) {
      globalPendingSessionLoad.delete(sessionId)
      return
    }

    // 获取该会话已显示的文件 ID 集合
    const displayedSet = getDisplayedFileSet(sessionId)

    // 过滤出尚未显示过的文件
    const missingFiles = sessionFiles.filter(f => !displayedSet.has(f.id))

    if (missingFiles.length === 0) {
      globalPendingSessionLoad.delete(sessionId)
      return
    }

    // 找到最后一条 assistant 消息的内容区域
    const contentBlocks = document.querySelectorAll('.chat-message--assistant .content-block__text')
    const lastContentBlock = contentBlocks[contentBlocks.length - 1]

    if (!lastContentBlock) {
      globalPendingSessionLoad.delete(sessionId)
      return
    }

    // 为每个遗漏的文件创建占位符并追加到最后一条消息
    for (const file of missingFiles) {
      // 标记为已显示
      displayedSet.add(file.id)

      const placeholder = document.createElement('div')
      placeholder.className = 'indexeddb-file'
      placeholder.setAttribute('data-file-id', file.id)
      placeholder.setAttribute('data-file-name', file.fileName)
      placeholder.innerHTML = `<span class="file-placeholder">📄 加载中... ${file.fileName}</span>`
      lastContentBlock.appendChild(placeholder)
    }

    // 加载文件卡片
    setTimeout(() => {
      loadIndexedDBFiles()
      globalPendingSessionLoad.delete(sessionId)
    }, 0)
  } catch (err) {
    globalPendingSessionLoad.delete(sessionId)
  }
}

// 监听 sessionId 变化，清除旧会话的显示记录
watch(
  () => props.sessionId,
  (newSessionId, oldSessionId) => {
    if (oldSessionId && newSessionId !== oldSessionId) {
      // 切换会话时，清除旧会话的显示记录和进行中的标记
      globalDisplayedImages.delete(oldSessionId)
      globalDisplayedFiles.delete(oldSessionId)
      globalPendingSessionLoad.delete(oldSessionId)
      globalPendingSessionLoad.delete(oldSessionId + '-images')
    }
  }
)

watch(
  () => [props.message.thinking, props.message.toolCalls, props.message.done, props.message.content],
  ([thinking, toolCalls, done, content]) => {
    if (done) {
      currentPhase.value = 'idle'
      if (thinking && !content) {
        thinkingExpanded.value = true
      } else if (content) {
        thinkingExpanded.value = false
      }
    }

    // 无论 done 状态，只要内容中有图片/文件引用就尝试加载
    if (content && typeof content === 'string') {
      nextTick(() => {
        if (content.includes('image:')) {
          loadIndexedDBImages()
        }
        if (content.includes('[file:')) {
          loadIndexedDBFiles()
        }
        if (content.includes('[audio:')) {
          loadIndexedDBAudio()
        }
        if (content.includes('[video:')) {
          loadIndexedDBVideo()
        }
      })
    }

    if (!done) {
      const hasThinking = !!thinking
      const hasContent = !!content
      const toolCallsCount = Array.isArray(toolCalls) ? toolCalls.length : 0

      if (hasThinking || toolCallsCount > 0) {
        thinkingExpanded.value = true
      }

      if (hasContent) {
        currentPhase.value = 'content'
      } else if (hasThinking) {
        currentPhase.value = 'thinking'
      }
    }
  },
  { immediate: true }
)

const isCurrentlyStreamingThinking = computed(() => {
  return !props.message.done && currentPhase.value === 'thinking' && props.message.thinking
})

const isCurrentlyStreamingContent = computed(() => {
  return !props.message.done && currentPhase.value === 'content' && props.message.content
})

const getToolStatusText = (status: string) => {
  switch (status) {
    case 'running': return '执行中'
    case 'success': return '完成'
    case 'error': return '失败'
    default: return status
  }
}

const formattedContent = computed(() => {
  const content = props.message.content
  if (!content) return ''

  try {
    // 先处理 [file:xxx:filename] 语法，转换为带 data-file-id 属性的占位卡片
    // 这些文件会在组件挂载后异步加载
    let processedContent = content.replace(
      /\[file:([^:]+):([^\]]+)\]/g,
      '<div class="indexeddb-file" data-file-id="$1" data-file-name="$2"><span class="file-placeholder">📄 加载中... $2</span></div>'
    )

    // 处理 ![alt](image:xxx) 语法，转换为带 data-image-id 属性的占位图片
    // 这些图片会在组件挂载后异步加载
    processedContent = processedContent.replace(
      /!\[([^\]]*)\]\(image:([^)]+)\)/g,
      '<img class="indexeddb-image" data-image-id="$2" alt="$1" src="data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' viewBox=\'0 0 200 150\'%3E%3Crect fill=\'%23f0f0f0\' width=\'200\' height=\'150\'/%3E%3Ctext x=\'50%25\' y=\'50%25\' dominant-baseline=\'middle\' text-anchor=\'middle\' fill=\'%23999\' font-size=\'14\'%3E加载中...%3C/text%3E%3C/svg%3E" />'
    )

    // 处理 [audio:xxx] 语法，转换为音频播放器占位符
    processedContent = processedContent.replace(
      /\[audio:([^\]]+)\]/g,
      '<div class="indexeddb-audio" data-audio-id="$1"><span class="audio-placeholder">🎵 加载中...</span></div>'
    )

    // 处理 [video:xxx] 语法，转换为视频播放器占位符
    processedContent = processedContent.replace(
      /\[video:([^\]]+)\]/g,
      '<div class="indexeddb-video" data-video-id="$1"><span class="video-placeholder">🎬 加载中...</span></div>'
    )

    const markdownLinkRegex = /\[[^\]]*\]\([^)]+\)/g
    const placeholders: string[] = []
    processedContent = processedContent.replace(markdownLinkRegex, (match) => {
      const placeholder = `__MARKDOWN_LINK_${placeholders.length}__`
      placeholders.push(match)
      return placeholder
    })

    const urlRegex = /(https?:\/\/[^\s<]+[^<.,:;"')\]\s])/g
    processedContent = processedContent.replace(urlRegex, '[$1]($1)')

    placeholders.forEach((link, index) => {
      processedContent = processedContent.replace(`__MARKDOWN_LINK_${index}__`, link)
    })

    const html = marked.parse(processedContent) as string
    const sanitized = DOMPurify.sanitize(html, {
      ADD_ATTR: ['target', 'rel', 'data-file-id', 'data-file-name', 'data-image-id', 'data-audio-id', 'data-video-id', 'data-code'],
      ADD_TAGS: ['iframe']
    })
    return sanitized.replace(/<a /g, '<a target="_blank" rel="noopener noreferrer" ')
  } catch {
    const formatted = content
      .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="lang-$1">$2</code></pre>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/`(.*?)`/g, '<code>$1</code>')
      .replace(/\n/g, '<br>')

    return DOMPurify.sanitize(formatted)
  }
})

// 从 ChatMessageContent 中提取图片 URL
const getImageUrl = (img: { type: string; imageUrl?: { url: string } }): string => {
  if (img.type === 'image_url' && img.imageUrl?.url) {
    return img.imageUrl.url
  }
  return ''
}

// 图片预览（简单实现，可以后续用 Element Plus 的图片预览组件）
const previewImage = (url: string) => {
  window.open(url, '_blank')
}

// 复制消息内容
const copyContent = async () => {
  if (!props.message.content) return
  try {
    await navigator.clipboard.writeText(props.message.content)
    ElMessage.success('内容已复制到剪贴板')
  } catch (err) {
    console.error('复制失败:', err)
    ElMessage.error('复制失败')
  }
}

// 文件下载处理函数
const downloadFile = async (fileId: string, _fileName: string) => {
  try {
    await fileDB.download(fileId)
  } catch (err) {
    console.error('[ChatMessage] 下载文件失败:', err)
  }
}

// 文件预览状态
const previewFileId = ref<string | null>(null)
const previewVisible = ref(false)

// 文件预览处理函数
const previewFile = (fileId: string) => {
  previewFileId.value = fileId
  previewVisible.value = true
}

// 代码块复制处理函数
const handleCodeBlockCopy = async (event: Event) => {
  const target = event.target as HTMLElement
  const button = target.closest('.code-block-copy') as HTMLElement
  if (!button) return

  const encodedCode = button.getAttribute('data-code')
  if (!encodedCode) return

  try {
    const code = decodeURIComponent(encodedCode)
    await navigator.clipboard.writeText(code)

    // 更新按钮状态
    const textSpan = button.querySelector('.code-block-copy__text')
    if (textSpan) {
      const originalText = textSpan.textContent
      textSpan.textContent = '已复制'
      button.classList.add('code-block-copy--success')

      setTimeout(() => {
        textSpan.textContent = originalText
        button.classList.remove('code-block-copy--success')
      }, 2000)
    }
  } catch (err) {
    console.error('复制失败:', err)
    ElMessage.error('复制失败')
  }
}

// 组件挂载时注册全局下载函数和加载图片/文件
onMounted(async () => {
  (window as any).downloadFileLink = (element: HTMLElement) => {
    const fileId = element.getAttribute('data-file-id')
    const fileName = element.getAttribute('data-file-name')
    if (fileId && fileName) {
      downloadFile(fileId, fileName)
    }
  }

  // 注册全局预览函数
  (window as any).previewFileLink = (element: HTMLElement) => {
    const fileId = element.getAttribute('data-file-id')
    if (fileId) {
      previewFile(fileId)
    }
  }

  // 添加代码块复制按钮的事件委托
  document.addEventListener('click', handleCodeBlockCopy)

  // 等待 DOM 渲染完成
  await nextTick()
  await nextTick()

  // 异步加载 IndexedDB 中的图片和文件
  loadIndexedDBImages()
  loadIndexedDBFiles()
  loadIndexedDBAudio()
  loadIndexedDBVideo()

  // 保底机制：只在最后一条 assistant 消息上触发，延迟等待正常加载完成
  if (props.isLast && props.message.done && props.sessionId && props.message.role === 'assistant') {
    // 延迟 500ms 等待正常加载完成后再检查
    setTimeout(() => {
      checkAndLoadMissingResources(props.sessionId!)
    }, 500)
  }
})

// 组件卸载时清理全局函数
onUnmounted(() => {
  delete (window as any).downloadFileLink
  delete (window as any).previewFileLink
  document.removeEventListener('click', handleCodeBlockCopy)
})

// 保底机制：检查已加载资源与 IndexedDB 中资源的差异，补充缺失的
// 直接调用 loadSessionImages/loadSessionFiles，它们内部已有防重复逻辑
const checkAndLoadMissingResources = async (sessionId: string) => {
  if (!sessionId) return

  // 等待 Vue 完成多轮 DOM 渲染（v-html 渲染可能需要多个周期）
  await nextTick()
  await nextTick()
  await nextTick()

  // 触发保底加载（内部会检查是否已显示过）
  loadSessionImages(sessionId)
  loadSessionFiles(sessionId)
}
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

// 全局溢出保护
.chat-message {
  max-width: 100%;
  overflow-x: hidden;
  box-sizing: border-box;

  *,
  *::before,
  *::after {
    box-sizing: border-box;
  }
}

.chat-message {
  display: flex;
  gap: 16px;
  padding: 16px 0;
  position: relative;
  @include animate-slide-up;

  &--user {
    flex-direction: row-reverse;

    .chat-message__avatar {
      .avatar-inner {
        background: var(--sf-accent);
      }
    }

    .chat-message__user {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 4px;
      max-width: 75%;
      margin-left: auto;
      overflow-wrap: break-word;
      word-wrap: break-word;
      word-break: break-word;

      @media (max-width: 768px) {
        max-width: calc(100% - 52px);
        width: 100%;
      }
    }

    .chat-message__user-images {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      justify-content: flex-end;
    }

    .chat-message__user-image {
      max-width: 200px;
      max-height: 200px;
      border-radius: var(--sf-radius-md);
      cursor: pointer;
      transition: transform var(--sf-transition-fast);
      box-shadow: var(--sf-shadow-sm);

      &:hover {
        transform: scale(1.02);
      }
    }

    .chat-message__user-files {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      justify-content: flex-end;
    }

    .chat-message__user-file {
      display: flex;
      align-items: center;
      gap: 8px;
      background: var(--sf-bg-gray-100);
      border: 1px solid var(--sf-border-light);
      border-radius: var(--sf-radius-md);
      padding: 8px 12px;
      font-size: 13px;
      color: var(--sf-text-primary);

      svg {
        width: 18px;
        height: 18px;
        flex-shrink: 0;
        color: var(--sf-accent);
      }

      .file-name {
        max-width: 150px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .chat-message__user-content {
      background: var(--sf-accent);
      color: white;
      border-radius: 20px 4px 20px 20px;
      padding: 14px 20px;
      font-size: 14px;
      line-height: 1.7;

      :deep(code) {
        background: rgba(255, 255, 255, 0.2);
        padding: 2px 6px;
        border-radius: 4px;
        font-family: var(--sf-font-mono);
        font-size: 13px;
      }
    }
  }

  &--assistant {
    .chat-message__avatar {
      .avatar-inner {
        background: var(--sf-bg-gray-200);
        border: 1px solid var(--sf-border-light);
      }
    }
  }

  &--streaming {
    .chat-message__avatar .avatar-glow {
      opacity: 1;
      animation: chatGlow 2s ease-in-out infinite;
    }
  }

  &__avatar {
    position: relative;
    flex-shrink: 0;

    .avatar-glow {
      position: absolute;
      inset: -4px;
      border-radius: 50%;
      background: var(--sf-accent);
      opacity: 0;
      filter: blur(8px);
      transition: opacity var(--sf-transition-normal);
    }

    .avatar-inner {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      z-index: 1;

      svg {
        width: 18px;
        height: 18px;
        color: white;
      }
    }
  }

  &__body {
    flex: 1;
    min-width: 0;
    max-width: 85%;
    overflow-wrap: break-word;
    word-wrap: break-word;
    word-break: break-word;

    @media (max-width: 768px) {
      max-width: calc(100% - 52px);
      min-width: 0;
      width: 100%;
    }
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 8px;
    padding-left: 2px;
  }

  &__time {
    font-size: 11px;
    color: var(--sf-text-tertiary);
    font-family: var(--sf-font-mono);
  }

  &__status {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    color: var(--sf-accent);
    font-weight: 500;

    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--sf-accent);
      @include animate-pulse;
    }
  }

  &__assistant {
    display: flex;
    flex-direction: column;
    gap: 12px;
    max-width: 100%;
    overflow-wrap: break-word;
    word-wrap: break-word;
    word-break: break-word;
  }
}

.error-block {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  border-left: 3px solid var(--sf-error);
  padding: 16px;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 12px;

  &__icon {
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    border-radius: var(--sf-radius-md);
    background: rgba(239, 68, 68, 0.1);
    display: flex;
    align-items: center;
    justify-content: center;

    svg {
      width: 20px;
      height: 20px;
      color: var(--sf-error);
    }
  }

  &__content {
    flex: 1;
    min-width: 0;
  }

  &__title {
    font-weight: 600;
    color: var(--sf-error);
    font-size: 14px;
    margin-bottom: 4px;
  }

  &__message {
    font-size: 13px;
    color: var(--sf-text-secondary);
    line-height: 1.5;
    word-break: break-word;
  }

  &__retry {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    background: rgba(0, 102, 255, 0.1);
    border: 1px solid rgba(0, 102, 255, 0.3);
    border-radius: var(--sf-radius-md);
    color: var(--sf-accent);
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    transition: all var(--sf-transition-fast);

    svg {
      width: 14px;
      height: 14px;
    }

    &:hover {
      background: rgba(0, 102, 255, 0.2);
      border-color: var(--sf-accent);
    }
  }
}

.thinking-block {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  overflow: hidden;
  transition: all var(--sf-transition-normal);

  &--expanded {
    border-color: var(--sf-border-default);
  }

  &--streaming {
    border-color: var(--sf-accent);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    cursor: pointer;
    user-select: none;
    transition: background var(--sf-transition-fast);

    &:hover {
      background: var(--sf-bg-gray-50);
    }
  }

  &__left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__icon {
    width: 20px;
    height: 20px;
    color: var(--sf-accent);
    @include animate-pulse;

    svg {
      width: 100%;
      height: 100%;
    }

    &--done {
      color: var(--sf-success);
      animation: none;
    }

    &--active {
      color: var(--sf-accent);
      animation: thinkingPulse 1.5s ease-in-out infinite;
    }
  }

  &__label {
    font-size: 13px;
    font-weight: 500;
    color: var(--sf-text-secondary);
  }

  &__badge {
    padding: 2px 8px;
    background: rgba(0, 102, 255, 0.1);
    border-radius: var(--sf-radius-full);
    font-size: 11px;
    font-weight: 600;
    color: var(--sf-accent);
  }

  &__arrow {
    width: 16px;
    height: 16px;
    color: var(--sf-text-tertiary);
    transition: transform var(--sf-transition-fast);
  }

  &--expanded &__arrow {
    transform: rotate(180deg);
  }

  &__content {
    padding: 0 16px 16px;
    border-top: 1px solid var(--sf-border-light);
  }

  &__text {
    padding-top: 12px;
    font-size: 12px;
    color: var(--sf-text-tertiary);
    line-height: 1.6;
    white-space: pre-wrap;
    font-family: var(--sf-font-mono);
    background: var(--sf-bg-gray-100);
    border-radius: var(--sf-radius-sm);
    padding: 12px;
    margin-top: 0;
  }

  &--streaming {
    .thinking-block__icon {
      animation: thinkingPulse 1.5s ease-in-out infinite;
    }

    .thinking-block__label {
      color: var(--sf-accent);
    }
  }

  .thinking-text__content {
    display: inline;
  }

  .thinking-text__cursor {
    display: inline-block;
    width: 2px;
    height: 1em;
    background: var(--sf-accent);
    margin-left: 2px;
    vertical-align: text-bottom;
    animation: cursorBlink 0.8s ease-in-out infinite;
    border-radius: 1px;
  }

  &__tools {
    padding-top: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
}

.thinking-slide-enter-active,
.thinking-slide-leave-active {
  transition: all var(--sf-transition-normal);
  overflow: hidden;
}

.thinking-slide-enter-from,
.thinking-slide-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.tool-item {
  background: var(--sf-bg-gray-100);
  border-radius: var(--sf-radius-md);
  padding: 10px 12px;
  border-left: 3px solid var(--sf-accent);

  &--success {
    border-left-color: var(--sf-success);
  }

  &--error {
    border-left-color: var(--sf-error);
  }

  &__indicator {
    display: flex;
    align-items: center;
    margin-bottom: 4px;
  }

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--sf-accent);
    @include animate-pulse;

    .tool-item--success & {
      background: var(--sf-success);
      animation: none;
    }

    .tool-item--error & {
      background: var(--sf-error);
      animation: none;
    }
  }

  &__info {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  &__name {
    font-size: 12px;
    font-weight: 600;
    color: var(--sf-text-primary);
    font-family: var(--sf-font-mono);
  }

  &__status {
    font-size: 11px;
    color: var(--sf-text-tertiary);
  }

  &__result {
    margin-top: 8px;
    padding: 8px;
    background: var(--sf-bg-gray-200);
    border-radius: var(--sf-radius-sm);
    font-size: 11px;
    color: var(--sf-text-secondary);
    font-family: var(--sf-font-mono);
    max-height: 100px;
    overflow-y: auto;
    white-space: pre-wrap;
  }
}

.content-block {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: 16px 20px;
  position: relative;
  transition: all var(--sf-transition-normal);
  max-width: 100%;
  overflow-wrap: break-word;
  word-wrap: break-word;
  word-break: break-word;
  box-sizing: border-box;

  &--streaming {
    border-color: var(--sf-accent);
  }

  &--loading {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
  }

  &__copy-btn {
    position: absolute;
    top: 12px;
    right: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    background: var(--sf-bg-gray-100);
    border: 1px solid var(--sf-border-light);
    border-radius: var(--sf-radius-sm);
    color: var(--sf-text-tertiary);
    cursor: pointer;
    opacity: 0;
    transition: all var(--sf-transition-fast);
    z-index: 10;

    svg {
      width: 16px;
      height: 16px;
    }

    &:hover {
      background: var(--sf-bg-gray-200);
      border-color: var(--sf-accent);
      color: var(--sf-accent);
    }
  }

  &:hover &__copy-btn {
    opacity: 1;
  }

  &__text--streaming {
    // Inherit all the normal markdown styles from &__text
  }

  .streaming-cursor {
    display: inline-block;
    width: 2px;
    height: 1em;
    background: var(--sf-accent);
    margin-left: 2px;
    vertical-align: text-bottom;
    animation: cursorBlink 0.8s ease-in-out infinite;
    border-radius: 1px;
  }

  &__text {
    font-size: 14px;
    line-height: 1.75;
    color: var(--sf-text-primary);
    opacity: 1;
    transition: opacity var(--sf-transition-normal);
    word-wrap: break-word;
    overflow-wrap: break-word;
    word-break: break-word;
    max-width: 100%;
    overflow-x: hidden;

    @media (max-width: 768px) {
      font-size: 13px;
      line-height: 1.6;
      max-width: 100%;
    }

    &--paused {
      opacity: 0.85;
    }

    :deep(strong) {
      font-weight: 600;
      color: var(--sf-text-primary);
    }

    :deep(em) {
      color: var(--sf-text-secondary);
    }

    :deep(code) {
      background: rgba(0, 102, 255, 0.1);
      padding: 2px 6px;
      border-radius: 4px;
      font-family: var(--sf-font-mono);
      font-size: 13px;
      color: var(--sf-accent);
    }

    // Code block wrapper with copy button
    :deep(.code-block-wrapper) {
      position: relative;
      margin: 12px 0;
      border-radius: var(--sf-radius-md);
      overflow: hidden;
      background: var(--sf-bg-gray-100);
      border: 1px solid var(--sf-border-light);
    }

    :deep(.code-block-header) {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 12px;
      background: var(--sf-bg-gray-200);
      border-bottom: 1px solid var(--sf-border-light);
    }

    :deep(.code-block-lang) {
      font-size: 12px;
      font-weight: 500;
      color: var(--sf-text-tertiary);
      font-family: var(--sf-font-mono);
      text-transform: uppercase;
    }

    :deep(.code-block-copy) {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 4px 10px;
      background: transparent;
      border: 1px solid var(--sf-border-default);
      border-radius: var(--sf-radius-sm);
      color: var(--sf-text-secondary);
      font-size: 12px;
      cursor: pointer;
      transition: all var(--sf-transition-fast);
      font-family: var(--sf-font-default);

      svg {
        width: 14px;
        height: 14px;
      }

      &:hover {
        background: var(--sf-bg-gray-100);
        border-color: var(--sf-accent);
        color: var(--sf-accent);
      }
    }

    :deep(.code-block-copy--success) {
      background: rgba(16, 185, 129, 0.1);
      border-color: var(--sf-success);
      color: var(--sf-success);
    }

    :deep(.code-block-copy__text) {
      pointer-events: none;
    }

    :deep(pre) {
      background: transparent;
      border: none;
      border-radius: 0;
      padding: 16px;
      margin: 0;
      overflow-x: auto;
      position: relative;
      max-width: 100%;
      box-sizing: border-box;

      code {
        background: none;
        padding: 0;
        color: var(--sf-text-primary);
        font-size: 13px;
        font-family: var(--sf-font-mono);
        line-height: 1.6;
        white-space: pre-wrap;
        word-wrap: break-word;
        overflow-wrap: break-word;
        word-break: break-all;
        display: block;
        max-width: 100%;
      }

      @media (max-width: 768px) {
        padding: 12px;
        font-size: 12px;
        max-width: 100%;
      }

      // Code language badge
      .lang- {
        position: relative;
      }
    }

    // Blockquotes
    :deep(blockquote) {
      border-left: 3px solid var(--sf-accent);
      margin: 12px 0;
      padding: 8px 16px;
      background: rgba(0, 102, 255, 0.05);
      border-radius: 0 var(--sf-radius-sm) var(--sf-radius-sm) 0;
      color: var(--sf-text-secondary);
      font-style: italic;

      p {
        margin: 0;
      }
    }

    // Lists
    :deep(ul), :deep(ol) {
      margin: 8px 0;
      padding-left: 24px;

      li {
        margin: 4px 0;
        line-height: 1.6;
      }
    }

    :deep(ul) {
      list-style-type: disc;
    }

    :deep(ol) {
      list-style-type: decimal;
    }

    // Tables
    :deep(table) {
      width: 100%;
      border-collapse: collapse;
      margin: 12px 0;
      font-size: 13px;
      display: block;
      overflow-x: auto;
      max-width: 100%;

      th, td {
        border: 1px solid var(--sf-border-light);
        padding: 8px 12px;
        text-align: left;
        word-wrap: break-word;
        overflow-wrap: break-word;
      }

      th {
        background: var(--sf-bg-gray-100);
        font-weight: 600;
        color: var(--sf-text-primary);
      }

      td {
        color: var(--sf-text-secondary);
      }

      tr:nth-child(even) td {
        background: var(--sf-bg-gray-50);
      }
    }

    // Headings
    :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
      margin: 16px 0 8px;
      font-weight: 600;
      color: var(--sf-text-primary);
      line-height: 1.4;
    }

    :deep(h1) { font-size: 1.5em; }
    :deep(h2) { font-size: 1.3em; }
    :deep(h3) { font-size: 1.15em; }
    :deep(h4) { font-size: 1em; }

    // Horizontal rule
    :deep(hr) {
      border: none;
      border-top: 1px solid var(--sf-border-light);
      margin: 16px 0;
    }

    // Links
    :deep(a) {
      color: var(--sf-accent);
      text-decoration: none;
      border-bottom: 1px solid transparent;
      transition: border-color var(--sf-transition-fast);
      word-break: break-all;
      overflow-wrap: break-word;
      max-width: 100%;
      display: inline-block;

      &:hover {
        border-bottom-color: var(--sf-accent);
      }
    }

    // File links
    :deep(.file-link) {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      background: rgba(0, 102, 255, 0.1);
      border: 1px solid rgba(0, 102, 255, 0.3);
      border-radius: var(--sf-radius-md);
      color: var(--sf-accent);
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: all var(--sf-transition-fast);
      text-decoration: none;
      border-bottom: none;

      &:hover {
        background: rgba(0, 102, 255, 0.2);
        border-color: var(--sf-accent);
      }
    }

    // Paragraphs
    :deep(p) {
      margin: 8px 0;
      max-width: 100%;
      overflow-wrap: break-word;
      word-wrap: break-word;
      word-break: break-word;

      &:first-child {
        margin-top: 0;
      }

      &:last-child {
        margin-bottom: 0;
      }
    }

    // Images
    :deep(img) {
      max-width: 100%;
      height: auto;
      border-radius: var(--sf-radius-md);
      margin: 8px 0;
      display: block;
    }

    // IndexedDB file placeholders and file cards (v-html rendered)
    :deep(.indexeddb-file) {
      margin: 8px 0;
      display: block;
    }

    :deep(.file-placeholder) {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      background: var(--sf-bg-gray-100);
      border: 1px dashed var(--sf-border-default);
      border-radius: var(--sf-radius-md);
      color: var(--sf-text-tertiary);
      font-size: 13px;
    }

    :deep(.file-card) {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      background: var(--sf-bg-gray-100);
      border: 1px solid var(--sf-border-light);
      border-radius: var(--sf-radius-md);
      margin: 8px 0;
      transition: all var(--sf-transition-fast);

      &:hover {
        border-color: var(--sf-accent);
        box-shadow: var(--sf-shadow-sm);
      }
    }

    :deep(.file-card--error) {
      background: rgba(239, 68, 68, 0.1);
      border-color: rgba(239, 68, 68, 0.3);
      color: var(--sf-error);
    }

    :deep(.file-card__icon) {
      font-size: 24px;
      flex-shrink: 0;
    }

    :deep(.file-card__info) {
      flex: 1;
      min-width: 0;
    }

    :deep(.file-card__name) {
      font-size: 14px;
      font-weight: 500;
      color: var(--sf-text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    :deep(.file-card__meta) {
      display: flex;
      gap: 8px;
      margin-top: 4px;
      font-size: 12px;
      color: var(--sf-text-tertiary);
    }

    :deep(.file-card__type) {
      padding: 2px 6px;
      background: rgba(0, 102, 255, 0.1);
      border-radius: var(--sf-radius-sm);
      color: var(--sf-accent);
      font-weight: 500;
    }

    :deep(.file-card__size) {
      color: var(--sf-text-tertiary);
    }

    :deep(.file-card__actions) {
      display: flex;
      gap: 8px;
      flex-shrink: 0;
    }

    :deep(.file-card__preview) {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      background: rgba(16, 185, 129, 0.1);
      border: 1px solid rgba(16, 185, 129, 0.3);
      border-radius: var(--sf-radius-md);
      color: #10b981;
      cursor: pointer;
      transition: all var(--sf-transition-fast);
      flex-shrink: 0;

      &:hover {
        background: #10b981;
        border-color: #10b981;
        color: white;
      }

      svg {
        width: 18px;
        height: 18px;
      }
    }

    :deep(.file-card__download) {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      background: rgba(0, 102, 255, 0.1);
      border: 1px solid rgba(0, 102, 255, 0.3);
      border-radius: var(--sf-radius-md);
      color: var(--sf-accent);
      cursor: pointer;
      transition: all var(--sf-transition-fast);
      flex-shrink: 0;

      &:hover {
        background: var(--sf-accent);
        border-color: var(--sf-accent);
        color: white;
      }

      svg {
        width: 18px;
        height: 18px;
      }
    }
  }

  &__cursor {
    display: inline-flex;
    align-items: center;
    margin-left: 2px;
  }
}

.cursor-line {
  display: inline-block;
  width: 2px;
  height: 18px;
  background: var(--sf-accent);
  border-radius: 1px;
  animation: typing 0.8s ease-in-out infinite;
}

.loading-dots {
  display: flex;
  gap: 6px;

  span {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--sf-accent);
    animation: loadBounce 1.4s ease-in-out infinite;

    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}

@keyframes loadBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.skill-recommendations {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  overflow: hidden;

  &__header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px;
    background: rgba(0, 102, 255, 0.05);
    border-bottom: 1px solid var(--sf-border-light);
    font-size: 13px;
    font-weight: 600;
    color: var(--sf-accent);

    svg {
      width: 16px;
      height: 16px;
    }
  }

  &__list {
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
}

.skill-card {
  background: var(--sf-bg-gray-100);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  padding: 12px;
  transition: all var(--sf-transition-fast);

  &:hover {
    border-color: var(--sf-accent);
    box-shadow: var(--sf-shadow-sm);
  }

  &--auto {
    border-color: rgba(16, 185, 129, 0.3);
    background: rgba(16, 185, 129, 0.05);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
  }

  &__name {
    font-weight: 600;
    font-size: 14px;
    color: var(--sf-text-primary);
  }

  &__tag {
    padding: 2px 8px;
    border-radius: var(--sf-radius-full);
    font-size: 10px;
    font-weight: 600;
    background: rgba(0, 102, 255, 0.1);
    color: var(--sf-accent);

    &--auto {
      background: rgba(16, 185, 129, 0.1);
      color: var(--sf-success);
    }
  }

  &__desc {
    font-size: 12px;
    color: var(--sf-text-secondary);
    line-height: 1.5;
    margin-bottom: 8px;
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 11px;
    color: var(--sf-text-tertiary);
    margin-bottom: 10px;
  }

  &__category {
    padding: 2px 8px;
    background: var(--sf-bg-gray-200);
    border-radius: var(--sf-radius-sm);
  }

  &__score {
    color: var(--sf-accent);
    font-weight: 600;
  }

  &__actions {
    display: flex;
    gap: 8px;
  }

  &__btn {
    padding: 6px 14px;
    border-radius: var(--sf-radius-sm);
    font-size: 12px;
    font-weight: 500;
    cursor: pointer;
    transition: all var(--sf-transition-fast);
    background: var(--sf-bg-gray-200);
    border: 1px solid var(--sf-border-default);
    color: var(--sf-text-secondary);

    &:hover {
      background: var(--sf-bg-gray-100);
      color: var(--sf-text-primary);
    }

    &--primary {
      background: var(--sf-accent);
      border: none;
      color: white;

      &:hover {
        background: var(--sf-accent-hover);
      }
    }
  }
}

@keyframes cursorBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

@keyframes thinkingPulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.6;
    transform: scale(0.95);
  }
}

// Audio player
:deep(.audio-player) {
  margin: 8px 0;
  background: var(--sf-bg-gray-100);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  padding: 8px 12px;
  max-width: 100%;

  audio {
    width: 100%;
    height: 40px;
    border-radius: var(--sf-radius-sm);
    outline: none;
  }
}

:deep(.audio-player--error) {
  color: var(--sf-error);
  font-size: 13px;
  padding: 12px 16px;
}

:deep(.audio-placeholder) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--sf-bg-gray-100);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  color: var(--sf-text-tertiary);
  font-size: 13px;
}

// Video player
:deep(.video-player) {
  margin: 8px 0;
  border-radius: var(--sf-radius-lg);
  overflow: hidden;
  background: #000;
  max-width: 100%;

  video {
    width: 100%;
    max-height: 400px;
    display: block;
    border-radius: var(--sf-radius-lg);
  }
}

:deep(.video-player--error) {
  background: var(--sf-bg-gray-100);
  color: var(--sf-error);
  font-size: 13px;
  padding: 12px 16px;
  border-radius: var(--sf-radius-lg);
}

:deep(.video-placeholder) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--sf-bg-gray-100);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  color: var(--sf-text-tertiary);
  font-size: 13px;
}

// 移动端适配
@media (max-width: 768px) {
  .chat-message {
    gap: 10px;
    padding: 12px 0;
    max-width: 100vw;
    overflow-x: hidden;

    &__body {
      max-width: calc(100% - 46px);
      min-width: 0;
      width: 100%;
      overflow-wrap: break-word;
      word-wrap: break-word;
      word-break: break-word;
    }

    &--user {
      .chat-message__user {
        max-width: calc(100% - 46px);
        width: 100%;
      }
    }
  }

  .error-block {
    flex-direction: column;
    padding: 12px;
    gap: 10px;

    &__icon {
      width: 32px;
      height: 32px;

      svg {
        width: 16px;
        height: 16px;
      }
    }

    &__title {
      font-size: 13px;
    }

    &__message {
      font-size: 12px;
    }

    &__retry {
      width: 100%;
      justify-content: center;
      padding: 10px 16px;
    }
  }

  .thinking-block {
    max-width: 100%;
    box-sizing: border-box;
    overflow-wrap: break-word;
    word-wrap: break-word;
    word-break: break-word;

    &__header {
      padding: 10px 12px;
    }

    &__content {
      padding: 0 12px 12px;
      max-width: 100%;
      overflow-wrap: break-word;
      word-wrap: break-word;
      word-break: break-word;
    }

    &__text {
      font-size: 11px;
      padding: 10px;
      max-width: 100%;
      overflow-wrap: break-word;
      word-wrap: break-word;
      word-break: break-word;
    }
  }

  .content-block {
    padding: 12px 14px;
    max-width: 100%;
    box-sizing: border-box;
    overflow-wrap: break-word;
    word-wrap: break-word;
    word-break: break-word;

    &__text {
      font-size: 13px;
      line-height: 1.6;
      max-width: 100%;
      overflow-wrap: break-word;
      word-wrap: break-word;
      word-break: break-word;
    }
  }

  :deep(.audio-player) {
    audio {
      height: 36px;
    }
  }

  :deep(.video-player) {
    video {
      max-height: 240px;
    }
  }
}
</style>
