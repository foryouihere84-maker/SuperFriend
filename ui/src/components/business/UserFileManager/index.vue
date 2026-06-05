<template>
  <div class="user-file-manager">
    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>
    <div v-else-if="files.length === 0" class="empty-state">
      <el-icon><FolderOpened /></el-icon>
      <span>暂无文件</span>
    </div>
    <div v-else class="file-list">
      <div v-for="file in files" :key="file.fileId" class="file-item">
        <div class="file-icon">
          <el-icon><Document /></el-icon>
        </div>
        <div class="file-info">
          <div class="file-name">{{ file.fileName }}</div>
          <div class="file-meta">
            <span>{{ formatSize(file.fileSize) }}</span>
            <span>{{ formatTime(file.uploadTime) }}</span>
          </div>
        </div>
        <el-button type="danger" size="small" text @click="handleDelete(file)">
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
    </div>
    <div v-if="stats && showStats" class="storage-stats">
      <div class="stats-bar">
        <div class="stats-used" :style="{ width: Math.min(stats.usedPercent, 100) + '%' }"></div>
      </div>
      <div class="stats-text">
        已使用 {{ formatSize(stats.totalSizeBytes) }} / {{ stats.maxSizeMB }} MB
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, FolderOpened, Document, Delete } from '@element-plus/icons-vue'
import { fileApi, type UserFileInfo, type StorageStats } from '@/api/file'
import { useUserStore } from '@/stores/user'

const props = withDefaults(
  defineProps<{
    sessionId?: string
    showStats?: boolean
    compact?: boolean
  }>(),
  {
    sessionId: '',
    showStats: false,
    compact: false
  }
)

const emit = defineEmits<{
  (e: 'file-deleted', fileId: string): void
}>()

const userStore = useUserStore()
const loading = ref(false)
const files = ref<UserFileInfo[]>([])
const stats = ref<StorageStats | null>(null)

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const formatTime = (time: string): string => {
  if (!time) return ''
  const date = new Date(time)
  return `${date.getMonth() + 1}-${date.getDate()} ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
}

const loadFiles = async () => {
  const userId = userStore.user?.id ? Number(userStore.user.id) : null
  if (!userId) return

  loading.value = true
  try {
    const [fileList, statsData] = await Promise.all([
      fileApi.getUserFiles(userId),
      props.showStats ? fileApi.getStorageStats(userId) : Promise.resolve(null)
    ])
    files.value = fileList
    stats.value = statsData
  } catch (error: any) {
    console.error('加载文件列表失败:', error)
    // 不显示错误提示，避免干扰用户
  } finally {
    loading.value = false
  }
}

const refreshFiles = () => {
  loadFiles()
}

const handleDelete = async (file: UserFileInfo) => {
  const userId = userStore.user?.id ? Number(userStore.user.id) : null
  if (!userId) return

  try {
    await ElMessageBox.confirm(
      `确定要删除文件 "${file.fileName}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const success = await fileApi.deleteFile(userId, file.fileId)
    if (success) {
      ElMessage.success('文件已删除')
      loadFiles()
      emit('file-deleted', file.fileId)
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除文件失败:', error)
      ElMessage.error('删除文件失败')
    }
  }
}

onMounted(() => {
  loadFiles()
})

watch(() => props.sessionId, () => {
  loadFiles()
})

defineExpose({
  refreshFiles,
  loadFiles
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.user-file-manager {
  padding: 12px;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: var(--sf-text-tertiary);
  gap: 8px;

  .el-icon {
    font-size: 32px;
  }

  span {
    font-size: 13px;
  }
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--sf-bg-gray-100);
  border-radius: var(--sf-radius-md);
  transition: all var(--sf-transition-fast);

  &:hover {
    background: var(--sf-bg-gray-200);
  }
}

.file-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--sf-accent);
  border-radius: var(--sf-radius-sm);
  color: white;

  .el-icon {
    font-size: 18px;
  }
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--sf-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-meta {
  display: flex;
  gap: 8px;
  margin-top: 2px;
  font-size: 11px;
  color: var(--sf-text-tertiary);
}

.storage-stats {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--sf-border-light);
}

.stats-bar {
  height: 6px;
  background: var(--sf-bg-gray-200);
  border-radius: 3px;
  overflow: hidden;
}

.stats-used {
  height: 100%;
  background: var(--sf-accent);
  border-radius: 3px;
  transition: width var(--sf-transition-normal);
}

.stats-text {
  margin-top: 6px;
  font-size: 11px;
  color: var(--sf-text-tertiary);
}
</style>
