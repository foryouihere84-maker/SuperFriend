<template>
  <div class="image-uploader">
    <!-- 上传按钮 -->
    <button
      type="button"
      class="upload-btn"
      :disabled="disabled"
      @click="triggerUpload"
      title="上传图片 (支持拖拽、粘贴)"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
        <circle cx="8.5" cy="8.5" r="1.5" />
        <polyline points="21 15 16 10 5 21" />
      </svg>
    </button>

    <!-- 隐藏的文件输入 -->
    <input
      ref="fileInputRef"
      type="file"
      accept="image/*"
      multiple
      class="hidden-input"
      @change="handleFileSelect"
    />

    <!-- 图片预览区域 -->
    <div v-if="images.length > 0" class="image-preview-container">
      <div
        v-for="(image, index) in images"
        :key="index"
        class="image-preview-item"
      >
        <img :src="image.url" :alt="`图片 ${index + 1}`" />
        <button
          type="button"
          class="remove-btn"
          @click="removeImage(index)"
          title="移除图片"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
        <div v-if="image.loading" class="loading-overlay">
          <div class="loading-spinner"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UPLOAD_CONFIG } from '@/config/app'

interface ImageItem {
  url: string
  file?: File
  loading?: boolean
}

const props = withDefaults(
  defineProps<{
    disabled?: boolean
    maxImages?: number
    maxSizeMB?: number
  }>(),
  {
    disabled: false,
    maxImages: UPLOAD_CONFIG.IMAGE_MAX_COUNT,
    maxSizeMB: UPLOAD_CONFIG.IMAGE_MAX_SIZE_MB
  }
)

const emit = defineEmits<{
  (e: 'change', images: ImageItem[]): void
}>()

const fileInputRef = ref<HTMLInputElement>()
const images = ref<ImageItem[]>([])

// 触发文件选择
const triggerUpload = () => {
  if (props.disabled) return
  fileInputRef.value?.click()
}

// 处理文件选择
const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (files) {
    processFiles(Array.from(files))
  }
  // 重置 input 以便可以重复选择相同文件
  target.value = ''
}

// 处理文件
const processFiles = async (files: File[]) => {
  const remainingSlots = props.maxImages - images.value.length
  if (remainingSlots <= 0) {
    ElMessage.warning(`最多只能上传 ${props.maxImages} 张图片`)
    return
  }

  const filesToProcess = files.slice(0, remainingSlots)
  const maxSizeBytes = props.maxSizeMB * 1024 * 1024

  for (const file of filesToProcess) {
    // 验证文件类型
    if (!file.type.startsWith('image/')) {
      ElMessage.warning(`${file.name} 不是图片文件`)
      continue
    }

    // 验证文件大小
    if (file.size > maxSizeBytes) {
      ElMessage.warning(`${file.name} 超过 ${props.maxSizeMB}MB 限制`)
      continue
    }

    // 添加加载状态
    const imageItem: ImageItem = {
      url: '',
      file,
      loading: true
    }
    images.value.push(imageItem)

    try {
      // 转換為 base64
      const base64 = await fileToBase64(file)
      imageItem.url = base64
      imageItem.loading = false
    } catch (error) {
      console.error('图片处理失败:', error)
      ElMessage.error(`${file.name} 处理失败`)
      // 移除失败的图片
      const index = images.value.indexOf(imageItem)
      if (index > -1) {
        images.value.splice(index, 1)
      }
    }
  }

  emit('change', images.value)
}

// 文件转 base64
const fileToBase64 = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

// 移除图片
const removeImage = (index: number) => {
  images.value.splice(index, 1)
  emit('change', images.value)
}

// 清空所有图片
const clearImages = () => {
  images.value = []
  emit('change', images.value)
}

// 处理拖拽
const handleDragOver = (event: DragEvent) => {
  if (props.disabled) return
  event.preventDefault()
  event.stopPropagation()
}

const handleDrop = (event: DragEvent) => {
  if (props.disabled) return
  event.preventDefault()
  event.stopPropagation()

  const files = event.dataTransfer?.files
  if (files && files.length > 0) {
    const imageFiles = Array.from(files).filter(f => f.type.startsWith('image/'))
    if (imageFiles.length > 0) {
      processFiles(imageFiles)
    }
  }
}

// 处理粘贴
const handlePaste = (event: ClipboardEvent) => {
  if (props.disabled) return

  const items = event.clipboardData?.items
  if (!items) return

  const imageFiles: File[] = []
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        imageFiles.push(file)
      }
    }
  }

  if (imageFiles.length > 0) {
    processFiles(imageFiles)
  }
}

// 暴露方法给父组件
defineExpose({
  clearImages,
  getImages: () => images.value
})

// 全局事件监听
onMounted(() => {
  document.addEventListener('paste', handlePaste)
  document.addEventListener('dragover', handleDragOver)
  document.addEventListener('drop', handleDrop)
})

onUnmounted(() => {
  document.removeEventListener('paste', handlePaste)
  document.removeEventListener('dragover', handleDragOver)
  document.removeEventListener('drop', handleDrop)
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.image-uploader {
  display: flex;
  align-items: center;
  gap: 8px;
}

.upload-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--sf-radius-md);
  border: 1px solid var(--sf-border-default);
  background: var(--sf-bg-white);
  color: var(--sf-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--sf-transition-fast);

  svg {
    width: 18px;
    height: 18px;
  }

  &:hover:not(:disabled) {
    border-color: var(--sf-accent);
    color: var(--sf-accent);
    background: var(--sf-accent-light);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.hidden-input {
  display: none;
}

.image-preview-container {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  max-width: 400px;
}

.image-preview-item {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: var(--sf-radius-md);
  overflow: hidden;
  border: 1px solid var(--sf-border-default);
  background: var(--sf-bg-gray-100);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .remove-btn {
    position: absolute;
    top: 4px;
    right: 4px;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    border: none;
    background: rgba(0, 0, 0, 0.6);
    color: white;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity var(--sf-transition-fast);

    svg {
      width: 12px;
      height: 12px;
    }

    &:hover {
      background: var(--sf-error);
    }
  }

  &:hover .remove-btn {
    opacity: 1;
  }

  .loading-overlay {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .loading-spinner {
    width: 20px;
    height: 20px;
    border: 2px solid rgba(255, 255, 255, 0.3);
    border-top-color: white;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
