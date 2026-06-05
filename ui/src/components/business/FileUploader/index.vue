<template>
  <div class="file-uploader">
    <button
      type="button"
      class="upload-btn"
      :disabled="disabled"
      @click="triggerUpload"
      :title="`上传文件 (支持拖拽、粘贴，支持图片/文档/音频/视频，最大${maxSizeMB}MB)`"
    >
      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.49" />
      </svg>
    </button>

    <input
      ref="fileInputRef"
      type="file"
      :accept="acceptedTypes"
      multiple
      class="hidden-input"
      @change="handleFileSelect"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fileApi } from '@/api/file'
import { UPLOAD_CONFIG } from '@/config/app'

type FileType = 'image' | 'document' | 'audio' | 'video' | 'unknown'

interface FileItem {
  id: string
  url: string
  previewUrl?: string
  name: string
  type: FileType
  mimeType: string
  fileSize: number
  loading: boolean
  error?: string
  persistentFileId?: string
}

const props = withDefaults(
  defineProps<{
    disabled?: boolean
    maxFiles?: number
    maxSizeMB?: number
    allowedTypes?: FileType[]
    userId?: number
    sessionId?: string
    isSensitive?: boolean
  }>(),
  {
    disabled: false,
    maxFiles: UPLOAD_CONFIG.FILE_MAX_COUNT,
    maxSizeMB: UPLOAD_CONFIG.FILE_MAX_SIZE_MB,
    allowedTypes: () => ['image', 'document', 'audio', 'video'],
    isSensitive: false
  }
)

const emit = defineEmits<{
  (e: 'change', files: FileItem[]): void
}>()

const fileInputRef = ref<HTMLInputElement>()

const FILE_EXTENSIONS: Record<FileType, string[]> = {
  image: ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'],
  document: ['pdf', 'docx', 'doc', 'pptx', 'ppt', 'xlsx', 'xls', 'txt', 'md'],
  audio: ['mp3', 'wav', 'm4a', 'flac', 'aac', 'ogg'],
  video: ['mp4', 'avi', 'mov', 'mkv', 'webm'],
  unknown: []
}

const acceptedTypes = computed(() => {
  const types: string[] = []
  for (const fileType of props.allowedTypes) {
    if (fileType === 'image') types.push('image/*')
    else if (fileType === 'audio') types.push('audio/*')
    else if (fileType === 'video') types.push('video/*')
    else if (fileType === 'document') {
      types.push('.pdf', '.doc', '.docx', '.ppt', '.pptx', '.xls', '.xlsx', '.txt', '.md')
    }
  }
  return types.join(',')
})

const detectFileType = (file: File): FileType => {
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  const mimeType = file.type.toLowerCase()

  for (const [type, exts] of Object.entries(FILE_EXTENSIONS)) {
    if (exts.includes(ext)) {
      return type as FileType
    }
  }

  if (mimeType.startsWith('image/')) return 'image'
  if (mimeType.startsWith('audio/')) return 'audio'
  if (mimeType.startsWith('video/')) return 'video'
  if (mimeType.includes('pdf') || mimeType.includes('document') ||
      mimeType.includes('sheet') || mimeType.includes('presentation') ||
      mimeType.includes('word') || mimeType === 'text/plain') return 'document'

  return 'unknown'
}

const fileToBase64 = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

const triggerUpload = () => {
  if (props.disabled) return
  fileInputRef.value?.click()
}

const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  const selectedFiles = target.files
  if (selectedFiles) {
    processFiles(Array.from(selectedFiles))
  }
  target.value = ''
}

const processFiles = async (selectedFiles: File[]) => {
  const maxSizeBytes = props.maxSizeMB * 1024 * 1024
  const processedFiles: FileItem[] = []

  for (const file of selectedFiles) {
    const fileType = detectFileType(file)

    if (!props.allowedTypes.includes(fileType) && fileType !== 'unknown') {
      ElMessage.warning(`${file.name} 类型不支持`)
      continue
    }

    if (file.size > maxSizeBytes) {
      ElMessage.warning(`${file.name} 超过 ${props.maxSizeMB}MB 限制`)
      continue
    }

    let previewUrl: string | undefined
    if (fileType === 'image') {
      previewUrl = await fileToBase64(file)
    }

    try {
      const result = await fileApi.uploadFile(file, {
        type: fileType,
        userId: props.userId,
        sessionId: props.sessionId,
        isSensitive: props.isSensitive
      })
      processedFiles.push({
        id: result.fileId,
        url: result.fileUrl,
        previewUrl,
        name: file.name,
        type: fileType,
        mimeType: file.type,
        fileSize: file.size,
        loading: false,
        persistentFileId: result.persistentFileId
      })
    } catch (error: any) {
      console.error('文件上传失败:', error)
      ElMessage.error(`${file.name} 上传失败`)
    }
  }

  emit('change', processedFiles)
}

const handleDragOver = (event: DragEvent) => {
  if (props.disabled) return
  event.preventDefault()
  event.stopPropagation()
}

const handleDrop = (event: DragEvent) => {
  if (props.disabled) return
  event.preventDefault()
  event.stopPropagation()

  const droppedFiles = event.dataTransfer?.files
  if (droppedFiles && droppedFiles.length > 0) {
    processFiles(Array.from(droppedFiles))
  }
}

const handlePaste = (event: ClipboardEvent) => {
  if (props.disabled) return

  const items = event.clipboardData?.items
  if (!items) return

  const pastedFiles: File[] = []
  for (const item of items) {
    if (item.kind === 'file') {
      const file = item.getAsFile()
      if (file) {
        pastedFiles.push(file)
      }
    }
  }

  if (pastedFiles.length > 0) {
    processFiles(pastedFiles)
  }
}

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

.file-uploader {
  display: flex;
  align-items: center;
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
</style>
