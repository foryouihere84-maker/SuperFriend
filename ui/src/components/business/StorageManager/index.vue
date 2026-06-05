<template>
  <el-dialog
    v-model="visible"
    title="存储管理"
    width="700px"
    destroy-on-close
  >
    <!-- 存储概览 -->
    <el-card class="storage-overview" shadow="never">
      <div class="overview-header">
        <h3>存储空间</h3>
        <el-button text type="primary" @click="refreshStats">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <div class="storage-progress">
        <el-progress
          :percentage="stats.usagePercent"
          :status="progressStatus"
          :stroke-width="20"
          :format="formatProgress"
        />
        <div class="storage-detail">
          <span>已使用: {{ stats.formattedSize }}</span>
          <span>最大限制: 200 MB</span>
        </div>
      </div>

      <div class="storage-alerts">
        <el-tag v-if="stats.expiredCount > 0" type="warning">
          {{ stats.expiredCount }} 个过期文件待清理
        </el-tag>
        <el-tag v-if="stats.count === 0" type="info">
          暂无存储文件
        </el-tag>
      </div>
    </el-card>

    <!-- 按类型统计 -->
    <el-card class="type-stats" shadow="never">
      <h3>文件类型分布</h3>
      <div v-if="Object.keys(stats.byType).length > 0" class="type-list">
        <div
          v-for="(data, type) in stats.byType"
          :key="type"
          class="type-item"
          @click="filterByType(type)"
        >
          <div class="type-icon">{{ getTypeIcon(type) }}</div>
          <div class="type-info">
            <span class="type-name">{{ type.toUpperCase() }}</span>
            <span class="type-meta">{{ data.count }} 个文件 · {{ formatBytes(data.size) }}</span>
          </div>
          <div class="type-bar">
            <div
              class="type-bar-fill"
              :style="{ width: (data.size / stats.totalSize * 100) + '%' }"
            />
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无文件" :image-size="60" />
    </el-card>

    <!-- 文件列表 -->
    <el-card class="file-list-card" shadow="never">
      <div class="file-list-header">
        <h3>文件列表</h3>
        <div class="file-list-actions">
          <el-select v-model="filterType" placeholder="筛选类型" clearable size="small" style="width: 120px">
            <el-option label="全部" value="" />
            <el-option v-for="type in fileTypes" :key="type" :label="type.toUpperCase()" :value="type" />
          </el-select>
          <el-button
            v-if="stats.expiredCount > 0"
            type="warning"
            size="small"
            @click="cleanupExpired"
          >
            清理过期 ({{ stats.expiredCount }})
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredFiles"
        max-height="300"
        size="small"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column prop="fileName" label="文件名" min-width="180">
          <template #default="{ row }">
            <div class="file-name-cell">
              <span class="file-icon">{{ getTypeIcon(row.fileType) }}</span>
              <span class="file-name" :title="row.fileName">{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ row.fileType.toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="size" label="大小" width="90">
          <template #default="{ row }">
            {{ formatBytes(row.size) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="140">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.expiresAt < Date.now()" type="danger" size="small">已过期</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="previewFile(row)">
              预览
            </el-button>
            <el-button text type="danger" size="small" @click="deleteFile(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 批量操作 -->
      <div v-if="selectedFiles.length > 0" class="batch-actions">
        <span>已选择 {{ selectedFiles.length }} 个文件</span>
        <el-button type="danger" size="small" @click="deleteSelected">
          批量删除
        </el-button>
      </div>
    </el-card>

    <!-- 底部操作 -->
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="cleanupInactive">清理 30 天未访问</el-button>
        <el-button type="danger" @click="clearAll">
          <el-icon><Delete /></el-icon>
          清空所有
        </el-button>
        <el-button type="primary" @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>

  <!-- 文件预览 -->
  <FilePreview v-model="previewVisible" :file-id="previewFileId" />
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { fileDB, type StoredFile, type StorageStats } from '@/utils/fileDB'
import FilePreview from '@/components/business/FilePreview/index.vue'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 状态
const loading = ref(false)
const stats = ref<StorageStats>({
  count: 0,
  totalSize: 0,
  formattedSize: '0 B',
  usagePercent: 0,
  byType: {},
  expiredCount: 0
})
const files = ref<StoredFile[]>([])
const filterType = ref('')
const selectedFiles = ref<StoredFile[]>([])

// 预览
const previewVisible = ref(false)
const previewFileId = ref<string | null>(null)

// 文件类型列表
const fileTypes = ['pdf', 'docx', 'xlsx', 'pptx', 'image', 'audio', 'video', 'md', 'txt', 'text', 'archive', 'other']

// 计算属性
const progressStatus = computed(() => {
  if (stats.value.usagePercent >= 90) return 'exception'
  if (stats.value.usagePercent >= 70) return 'warning'
  return ''
})

const filteredFiles = computed(() => {
  if (!filterType.value) return files.value
  return files.value.filter(f => f.fileType === filterType.value)
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const [statsResult, filesResult] = await Promise.all([
      fileDB.getStats(),
      fileDB.getAll()
    ])
    stats.value = statsResult
    files.value = filesResult.sort((a, b) => b.createdAt - a.createdAt)
  } catch (err: any) {
    console.error('加载数据失败:', err)
    ElMessage.error('加载数据失败: ' + err.message)
  } finally {
    loading.value = false
  }
}

const refreshStats = async () => {
  const statsResult = await fileDB.getStats()
  stats.value = statsResult
}

// 清理过期文件
const cleanupExpired = async () => {
  try {
    const count = await fileDB.cleanupExpired()
    if (count > 0) {
      ElMessage.success(`已清理 ${count} 个过期文件`)
      await loadData()
    } else {
      ElMessage.info('没有过期文件')
    }
  } catch (err: any) {
    ElMessage.error('清理失败: ' + err.message)
  }
}

// 清理长时间未访问的文件
const cleanupInactive = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清理 30 天未访问的文件吗？此操作不可恢复。',
      '确认清理',
      { type: 'warning' }
    )

    const count = await fileDB.cleanupInactive(30)
    if (count > 0) {
      ElMessage.success(`已清理 ${count} 个文件`)
      await loadData()
    } else {
      ElMessage.info('没有需要清理的文件')
    }
  } catch {
    // 用户取消
  }
}

// 清空所有
const clearAll = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有文件吗？此操作不可恢复。',
      '确认清空',
      { type: 'warning', confirmButtonClass: 'el-button--danger' }
    )

    await fileDB.clear()
    ElMessage.success('已清空所有文件')
    await loadData()
  } catch {
    // 用户取消
  }
}

// 删除单个文件
const deleteFile = async (file: StoredFile) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除 "${file.fileName}" 吗？`,
      '确认删除',
      { type: 'warning' }
    )

    await fileDB.delete(file.id)
    ElMessage.success('文件已删除')
    await loadData()
  } catch {
    // 用户取消
  }
}

// 批量删除
const deleteSelected = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedFiles.value.length} 个文件吗？`,
      '确认删除',
      { type: 'warning' }
    )

    for (const file of selectedFiles.value) {
      await fileDB.delete(file.id)
    }
    ElMessage.success(`已删除 ${selectedFiles.value.length} 个文件`)
    selectedFiles.value = []
    await loadData()
  } catch {
    // 用户取消
  }
}

// 预览文件
const previewFile = (file: StoredFile) => {
  previewFileId.value = file.id
  previewVisible.value = true
}

// 筛选类型
const filterByType = (type: string) => {
  filterType.value = type
}

// 选择变化
const handleSelectionChange = (selection: StoredFile[]) => {
  selectedFiles.value = selection
}

// 工具函数
const formatBytes = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
}

const formatDate = (timestamp: number): string => {
  return new Date(timestamp).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatProgress = (percentage: number): string => {
  return `${percentage}%`
}

const getTypeIcon = (type: string): string => {
  const icons: Record<string, string> = {
    pdf: '📄',
    docx: '📝',
    doc: '📝',
    xlsx: '📊',
    xls: '📊',
    pptx: '📽️',
    ppt: '📽️',
    image: '🖼️',
    audio: '🎵',
    video: '🎬',
    md: '📝',
    txt: '📄',
    text: '📃',
    archive: '📦',
    other: '📎'
  }
  return icons[type] || '📎'
}

// 监听对话框打开
watch(visible, (val) => {
  if (val) {
    loadData()
  }
})
</script>

<style scoped>
.storage-overview {
  margin-bottom: 16px;
}

.overview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.overview-header h3 {
  margin: 0;
  font-size: 16px;
}

.storage-progress {
  margin-bottom: 12px;
}

.storage-detail {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
}

.storage-alerts {
  display: flex;
  gap: 8px;
}

.type-stats {
  margin-bottom: 16px;
}

.type-stats h3 {
  margin: 0 0 16px;
  font-size: 16px;
}

.type-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.type-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.type-item:hover {
  background: #e9ecf0;
}

.type-icon {
  font-size: 24px;
}

.type-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.type-name {
  font-weight: 500;
}

.type-meta {
  font-size: 12px;
  color: #909399;
}

.type-bar {
  width: 100px;
  height: 6px;
  background: #e4e7ed;
  border-radius: 3px;
  overflow: hidden;
}

.type-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #409eff, #67c23a);
  border-radius: 3px;
  transition: width 0.3s;
}

.file-list-card {
  margin-bottom: 16px;
}

.file-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.file-list-header h3 {
  margin: 0;
  font-size: 16px;
}

.file-list-actions {
  display: flex;
  gap: 8px;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-icon {
  font-size: 18px;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: #fdf6ec;
  border-radius: 8px;
  margin-top: 12px;
}

.batch-actions span {
  color: #e6a23c;
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

:deep(.el-card__body) {
  padding: 16px;
}
</style>
