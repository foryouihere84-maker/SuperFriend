<template>
  <div class="user-profile-page">
    <div class="page-header">
      <h1>用户画像</h1>
      <div class="header-actions">
        <div class="stats-cards">
          <div class="stat-card">
            <span class="stat-value">{{ profile?.interests?.length || 0 }}</span>
            <span class="stat-label">兴趣</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ profile?.traits?.length || 0 }}</span>
            <span class="stat-label">特质</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ profile?.preferences?.length || 0 }}</span>
            <span class="stat-label">偏好</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ profile?.skills?.length || 0 }}</span>
            <span class="stat-label">技能</span>
          </div>
        </div>
        <el-button type="danger" plain size="small" @click="showClearDialog" :disabled="!hasProfileData">
          <el-icon><Delete /></el-icon>
          清理画像
        </el-button>
      </div>
    </div>

    <div class="main-content">
      <div class="profile-summary">
        <div class="summary-card">
          <div class="card-header">
            <div class="header-left">
              <el-icon><User /></el-icon>
              <span>画像摘要</span>
            </div>
            <el-button type="primary" link size="small" @click="openSummaryEdit">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
          </div>
          <div class="summary-content" v-if="profile?.summary">
            {{ profile.summary }}
          </div>
          <div class="summary-empty" v-else>
            <el-icon><Document /></el-icon>
            <p>暂无画像摘要</p>
            <span>点击编辑添加摘要</span>
          </div>
        </div>

        <div class="category-card">
          <div class="card-header">
            <div class="header-left">
              <el-icon><Star /></el-icon>
              <span>兴趣方向</span>
            </div>
            <el-button type="primary" link size="small" @click="openItemEdit('interest')">
              <el-icon><Plus /></el-icon>
              添加
            </el-button>
          </div>
          <div class="item-list">
            <div v-for="item in profile?.interests" :key="item.name" class="item-row">
              <div class="item-info">
                <span class="item-name">{{ item.name }}</span>
                <div class="item-meta">
                  <span class="confidence-bar">
                    <span class="confidence-fill" :style="{ width: (item.confidence * 100) + '%' }"></span>
                  </span>
                  <span class="confidence-value">{{ (item.confidence * 100).toFixed(0) }}%</span>
                </div>
              </div>
              <div class="item-actions">
                <span class="mention-badge" v-if="item.mentionCount">×{{ item.mentionCount }}</span>
                <el-button type="primary" link size="small" @click="openItemEdit('interest', item)">
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button type="danger" link size="small" @click="handleDeleteItem('interest', item.name)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
            <div v-if="!profile?.interests?.length" class="empty-hint">暂无兴趣数据，点击添加</div>
          </div>
        </div>

        <div class="category-card">
          <div class="card-header">
            <div class="header-left">
              <el-icon><Collection /></el-icon>
              <span>性格特质</span>
            </div>
            <el-button type="primary" link size="small" @click="openItemEdit('trait')">
              <el-icon><Plus /></el-icon>
              添加
            </el-button>
          </div>
          <div class="item-list">
            <div v-for="item in profile?.traits" :key="item.name" class="item-row trait-row">
              <div class="item-info">
                <span class="item-name">{{ item.name }}</span>
                <div class="item-meta">
                  <span class="confidence-bar">
                    <span class="confidence-fill trait-fill" :style="{ width: (item.confidence * 100) + '%' }"></span>
                  </span>
                  <span class="confidence-value">{{ (item.confidence * 100).toFixed(0) }}%</span>
                </div>
              </div>
              <div class="item-actions">
                <el-button type="primary" link size="small" @click="openItemEdit('trait', item)">
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button type="danger" link size="small" @click="handleDeleteItem('trait', item.name)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div class="context-tags" v-if="item.contexts?.length">
                <span v-for="ctx in item.contexts.slice(0, 3)" :key="ctx" class="context-tag">{{ ctx }}</span>
              </div>
            </div>
            <div v-if="!profile?.traits?.length" class="empty-hint">暂无特质数据，点击添加</div>
          </div>
        </div>
      </div>

      <div class="profile-details">
        <div class="category-card">
          <div class="card-header">
            <div class="header-left">
              <el-icon><Setting /></el-icon>
              <span>用户偏好</span>
            </div>
            <el-button type="primary" link size="small" @click="openItemEdit('preference')">
              <el-icon><Plus /></el-icon>
              添加
            </el-button>
          </div>
          <div class="preference-grid">
            <div v-for="item in profile?.preferences" :key="item.name" class="preference-item">
              <div class="preference-header">
                <span class="preference-name">{{ item.name }}</span>
                <div class="preference-actions">
                  <el-button type="primary" link size="small" @click="openItemEdit('preference', item)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button type="danger" link size="small" @click="handleDeleteItem('preference', item.name)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              <span class="preference-category" v-if="item.category">{{ item.category }}</span>
              <div class="preference-confidence">
                <span class="confidence-mini-bar">
                  <span class="confidence-mini-fill" :style="{ width: (item.confidence * 100) + '%' }"></span>
                </span>
                <span class="confidence-mini-value">{{ (item.confidence * 100).toFixed(0) }}%</span>
              </div>
            </div>
            <div v-if="!profile?.preferences?.length" class="empty-hint grid-empty">暂无偏好数据，点击添加</div>
          </div>
        </div>

        <div class="category-card skills-card">
          <div class="card-header">
            <div class="header-left">
              <el-icon><TrendCharts /></el-icon>
              <span>技能图谱</span>
            </div>
            <el-button type="primary" link size="small" @click="openItemEdit('skill')">
              <el-icon><Plus /></el-icon>
              添加
            </el-button>
          </div>
          <div class="skills-list">
            <div v-for="item in profile?.skills" :key="item.name" class="skill-item">
              <div class="skill-header">
                <span class="skill-name">{{ item.name }}</span>
                <div class="skill-actions">
                  <span class="skill-level" :class="getLevelClass(item.level)">{{ item.level }}</span>
                  <el-button type="primary" link size="small" @click="openItemEdit('skill', item)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button type="danger" link size="small" @click="handleDeleteItem('skill', item.name)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              <div class="skill-bar">
                <div class="skill-bar-fill" :style="{ width: getSkillWidth(item) + '%' }"></div>
              </div>
            </div>
            <div v-if="!profile?.skills?.length" class="empty-hint">暂无技能数据，点击添加</div>
          </div>
        </div>

        <div class="update-info" v-if="profile">
          <el-icon><Clock /></el-icon>
          <span>最后更新: {{ formatTime(profile.updatedTime) }}</span>
          <span class="version">v{{ profile.version }}</span>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="clearDialogVisible"
      title="确认清理用户画像"
      :width="dialogWidth"
      class="profile-dialog"
      :close-on-click-modal="false"
    >
      <div class="dialog-content">
        <el-icon class="warning-icon"><WarningFilled /></el-icon>
        <p>确定要清理用户画像吗？</p>
        <span class="warning-text">此操作将删除所有兴趣、特质、偏好、技能数据，且不可恢复。</span>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="clearDialogVisible = false">取消</el-button>
          <el-button type="danger" @click="handleClearProfile" :loading="clearLoading">
            确认清理
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="summaryDialogVisible"
      title="编辑画像摘要"
      :width="dialogWidth"
      class="profile-dialog"
      :close-on-click-modal="false"
    >
      <el-input
        v-model="summaryEditValue"
        type="textarea"
        :rows="5"
        placeholder="请输入用户画像摘要..."
        maxlength="500"
        show-word-limit
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="summaryDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveSummary" :loading="saveLoading">
            保存
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="itemDialogVisible"
      :title="itemDialogTitle"
      :width="dialogWidth"
      class="profile-dialog"
      :close-on-click-modal="false"
    >
      <el-form :model="itemForm" label-width="80px" class="item-form">
        <el-form-item label="名称" required>
          <el-input
            v-model="itemForm.name"
            placeholder="请输入名称"
            :disabled="!!editingItem"
            maxlength="50"
          />
        </el-form-item>

        <el-form-item label="置信度" v-if="itemFormType !== 'skill'">
          <div class="confidence-slider">
            <el-slider v-model="itemForm.confidencePercent" :min="0" :max="100" :step="5" />
            <span class="confidence-display">{{ itemForm.confidencePercent }}%</span>
          </div>
        </el-form-item>

        <el-form-item label="技能级别" v-if="itemFormType === 'skill'">
          <el-select v-model="itemForm.level" placeholder="请选择技能级别" class="input-full">
            <el-option label="了解" value="了解" />
            <el-option label="掌握" value="掌握" />
            <el-option label="熟练" value="熟练" />
            <el-option label="精通" value="精通" />
          </el-select>
        </el-form-item>

        <el-form-item label="分类" v-if="itemFormType === 'preference'">
          <el-input v-model="itemForm.category" placeholder="请输入分类（可选）" maxlength="30" />
        </el-form-item>

        <el-form-item label="上下文" v-if="itemFormType === 'trait'" class="input-full">
          <el-select
            v-model="itemForm.contexts"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入后回车添加上下文标签"
          >
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="itemDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveItem" :loading="saveLoading">
            保存
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import {
  getOrCreateUserProfile,
  clearUserProfile,
  upsertInterest,
  deleteInterest,
  upsertTrait,
  deleteTrait,
  upsertPreference,
  deletePreference,
  upsertSkill,
  deleteSkill,
  updateSummary,
  type UserProfile,
  type InterestItem,
  type TraitItem,
  type PreferenceItem,
  type SkillItem
} from '@/api/userProfile'
import { User, Document, Star, Collection, Setting, TrendCharts, Clock, Delete, WarningFilled, Edit, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)
const dialogWidth = computed(() => isMobile.value ? '95%' : '450px')

const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
const profile = ref<UserProfile | null>(null)
const loading = ref(false)
const clearDialogVisible = ref(false)
const clearLoading = ref(false)
const saveLoading = ref(false)

const summaryDialogVisible = ref(false)
const summaryEditValue = ref('')

const itemDialogVisible = ref(false)
const itemFormType = ref<'interest' | 'trait' | 'preference' | 'skill'>('interest')
const editingItem = ref<any>(null)
const itemForm = ref({
  name: '',
  confidencePercent: 80,
  level: '了解',
  category: '',
  contexts: [] as string[]
})

const hasProfileData = computed(() => {
  if (!profile.value) return false
  return (
    (profile.value.interests?.length ?? 0) > 0 ||
    (profile.value.traits?.length ?? 0) > 0 ||
    (profile.value.preferences?.length ?? 0) > 0 ||
    (profile.value.skills?.length ?? 0) > 0 ||
    !!profile.value.summary
  )
})

const itemDialogTitle = computed(() => {
  const typeNames: Record<string, string> = {
    interest: '兴趣',
    trait: '性格特质',
    preference: '偏好',
    skill: '技能'
  }
  const action = editingItem.value ? '编辑' : '添加'
  return `${action}${typeNames[itemFormType.value]}`
})

const loadProfile = async () => {
  const userId = userStore.user?.id
  if (!userId) return

  loading.value = true
  try {
    const result = await getOrCreateUserProfile(Number(userId))
    profile.value = result
  } catch (error) {
    console.error('加载用户画像失败:', error)
  } finally {
    loading.value = false
  }
}

const getLevelClass = (level: string): string => {
  switch (level) {
    case '精通': return 'level-expert'
    case '熟练': return 'level-proficient'
    case '掌握': return 'level-intermediate'
    default: return 'level-beginner'
  }
}

const getSkillWidth = (item: { level: string; confidence: number }): number => {
  const levelWeight: Record<string, number> = {
    '精通': 100,
    '熟练': 75,
    '掌握': 50,
    '了解': 25
  }
  const base = levelWeight[item.level] || 25
  const confidence = item.confidence || 0.5
  return base * confidence
}

const formatTime = (time: string): string => {
  if (!time) return '未知'
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const showClearDialog = () => {
  clearDialogVisible.value = true
}

const handleClearProfile = async () => {
  const userId = userStore.user?.id
  if (!userId) return

  clearLoading.value = true
  try {
    await clearUserProfile(Number(userId))
    ElMessage.success('用户画像已清理')
    clearDialogVisible.value = false
    await loadProfile()
  } catch (error) {
    console.error('清理用户画像失败:', error)
    ElMessage.error('清理用户画像失败')
  } finally {
    clearLoading.value = false
  }
}

const openSummaryEdit = () => {
  summaryEditValue.value = profile.value?.summary || ''
  summaryDialogVisible.value = true
}

const handleSaveSummary = async () => {
  const userId = userStore.user?.id
  if (!userId) return

  saveLoading.value = true
  try {
    await updateSummary(Number(userId), summaryEditValue.value)
    if (profile.value) {
      profile.value.summary = summaryEditValue.value
    }
    ElMessage.success('摘要已更新')
    summaryDialogVisible.value = false
    await loadProfile()
  } catch (error) {
    console.error('更新摘要失败:', error)
    ElMessage.error('更新摘要失败')
  } finally {
    saveLoading.value = false
  }
}

const openItemEdit = (type: 'interest' | 'trait' | 'preference' | 'skill', item?: any) => {
  itemFormType.value = type
  editingItem.value = item || null

  if (item) {
    itemForm.value = {
      name: item.name,
      confidencePercent: Math.round((item.confidence || 0.8) * 100),
      level: item.level || '了解',
      category: item.category || '',
      contexts: item.contexts || []
    }
  } else {
    itemForm.value = {
      name: '',
      confidencePercent: 80,
      level: '了解',
      category: '',
      contexts: []
    }
  }

  itemDialogVisible.value = true
}

const handleSaveItem = async () => {
  const userId = userStore.user?.id
  if (!userId || !itemForm.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }

  saveLoading.value = true
  try {
    const confidence = itemForm.value.confidencePercent / 100

    switch (itemFormType.value) {
      case 'interest': {
        const item: InterestItem = {
          name: itemForm.value.name.trim(),
          confidence
        }
        await upsertInterest(Number(userId), item)
        break
      }
      case 'trait': {
        const item: TraitItem = {
          name: itemForm.value.name.trim(),
          confidence,
          contexts: itemForm.value.contexts
        }
        await upsertTrait(Number(userId), item)
        break
      }
      case 'preference': {
        const item: PreferenceItem = {
          name: itemForm.value.name.trim(),
          confidence,
          category: itemForm.value.category || 'general'
        }
        await upsertPreference(Number(userId), item)
        break
      }
      case 'skill': {
        const item: SkillItem = {
          name: itemForm.value.name.trim(),
          level: itemForm.value.level,
          confidence: confidence
        }
        await upsertSkill(Number(userId), item)
        break
      }
    }

    ElMessage.success(editingItem.value ? '已更新' : '已添加')
    itemDialogVisible.value = false
    await loadProfile()
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saveLoading.value = false
  }
}

const handleDeleteItem = async (type: 'interest' | 'trait' | 'preference' | 'skill', name: string) => {
  try {
    await ElMessageBox.confirm(`确定要删除"${name}"吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  const userId = userStore.user?.id
  if (!userId) return

  try {
    let success = false
    switch (type) {
      case 'interest':
        success = await deleteInterest(Number(userId), name)
        break
      case 'trait':
        success = await deleteTrait(Number(userId), name)
        break
      case 'preference':
        success = await deletePreference(Number(userId), name)
        break
      case 'skill':
        success = await deleteSkill(Number(userId), name)
        break
    }

    if (success) {
      ElMessage.success('已删除')
      await loadProfile()
    } else {
      ElMessage.warning('删除失败')
    }
  } catch (error) {
    console.error('删除失败:', error)
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.user-profile-page {
  padding: 12px;
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--chat-page-bg);
  color: var(--chat-text-primary);
  box-sizing: border-box;

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

  > * {
    position: relative;
    z-index: 1;
  }
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  flex-shrink: 0;

  h1 {
    font-size: 18px;
    font-weight: 700;
    background: var(--chat-gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    margin: 0;
    white-space: nowrap;
  }
}

.stats-cards {
  display: flex;
  gap: 8px;

  .stat-card {
    background: var(--chat-surface-glass);
    backdrop-filter: blur(12px);
    border: 1px solid var(--chat-border-subtle);
    border-radius: var(--chat-radius-md);
    padding: 4px 10px;
    display: flex;
    align-items: center;
    gap: 4px;

    .stat-value {
      font-size: 14px;
      font-weight: 700;
      color: var(--chat-accent-purple);
    }

    .stat-label {
      font-size: 11px;
      color: var(--chat-text-tertiary);
    }
  }
}

.main-content {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}

.profile-summary {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.profile-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.summary-card,
.category-card {
  background: var(--chat-surface-glass);
  backdrop-filter: blur(12px);
  border: 1px solid var(--chat-border-subtle);
  border-radius: var(--chat-radius-lg);
  overflow: hidden;

  .card-header {
    padding: 10px 14px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-bottom: 1px solid var(--chat-border-subtle);
    font-weight: 600;
    font-size: 13px;
    color: var(--chat-text-primary);

    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .el-icon {
      width: 16px;
      height: 16px;
      color: var(--chat-accent-purple);
    }
  }
}

.summary-card {
  .summary-content {
    padding: 14px;
    font-size: 13px;
    line-height: 1.7;
    color: var(--chat-text-secondary);
  }

  .summary-empty {
    padding: 24px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    color: var(--chat-text-muted);

    .el-icon {
      width: 32px;
      height: 32px;
      opacity: 0.5;
    }

    p {
      margin: 0;
      font-size: 13px;
    }

    span {
      font-size: 11px;
    }
  }
}

.item-list {
  max-height: 200px;
  overflow-y: auto;
}

.item-row {
  padding: 8px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--chat-border-subtle);

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: rgba(139, 92, 246, 0.05);
  }

  .item-info {
    flex: 1;
    min-width: 0;
  }

  .item-name {
    font-size: 12px;
    font-weight: 500;
    color: var(--chat-text-primary);
    display: block;
    margin-bottom: 4px;
  }

  .item-meta {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .confidence-bar {
    flex: 1;
    height: 4px;
    background: var(--chat-bg-elevated);
    border-radius: 2px;
    overflow: hidden;
    max-width: 100px;
  }

  .confidence-fill {
    height: 100%;
    background: var(--chat-gradient-primary);
    border-radius: 2px;
    transition: width 0.3s ease;
  }

  .trait-fill {
    background: linear-gradient(90deg, var(--chat-accent-cyan), var(--chat-accent-purple));
  }

  .confidence-value {
    font-size: 10px;
    font-weight: 600;
    color: var(--chat-accent-purple);
    font-family: var(--chat-font-mono);
    min-width: 32px;
  }

  .item-actions {
    display: flex;
    align-items: center;
    gap: 4px;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover .item-actions {
    opacity: 1;
  }

  .mention-badge {
    font-size: 10px;
    padding: 2px 6px;
    background: var(--chat-bg-elevated);
    border-radius: var(--chat-radius-sm);
    color: var(--chat-text-tertiary);
    font-family: var(--chat-font-mono);
  }

  .context-tags {
    display: flex;
    gap: 4px;
    margin-top: 4px;
    flex-wrap: wrap;
  }

  .context-tag {
    font-size: 9px;
    padding: 2px 6px;
    background: rgba(139, 92, 246, 0.1);
    border-radius: var(--chat-radius-sm);
    color: var(--chat-accent-purple);
  }
}

.trait-row {
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;

  .item-actions {
    position: absolute;
    right: 14px;
    top: 8px;
  }
}

.preference-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
  padding: 12px;
}

.preference-item {
  background: var(--chat-bg-elevated);
  border-radius: var(--chat-radius-md);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: relative;

  &:hover {
    .preference-actions {
      opacity: 1;
    }
  }

  .preference-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .preference-name {
    font-size: 12px;
    font-weight: 500;
    color: var(--chat-text-primary);
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .preference-actions {
    display: flex;
    gap: 2px;
    opacity: 0;
    transition: opacity 0.2s;
  }

  .preference-category {
    font-size: 10px;
    color: var(--chat-text-muted);
  }

  .preference-confidence {
    display: flex;
    align-items: center;
    gap: 8px;

    .confidence-mini-bar {
      flex: 1;
      height: 3px;
      background: var(--chat-border-subtle);
      border-radius: 2px;
      overflow: hidden;
    }

    .confidence-mini-fill {
      display: block;
      height: 100%;
      background: var(--chat-accent-orange);
      border-radius: 2px;
    }

    .confidence-mini-value {
      font-size: 9px;
      color: var(--chat-text-muted);
      font-family: var(--chat-font-mono);
    }
  }
}

.grid-empty {
  grid-column: 1 / -1;
}

.skills-card {
  flex: 1;
  min-height: 0;
}

.skills-list {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 250px;
  overflow-y: auto;
}

.skill-item {
  &:hover {
    .skill-actions {
      .el-button {
        opacity: 1;
      }
    }
  }

  .skill-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
  }

  .skill-name {
    font-size: 12px;
    font-weight: 500;
    color: var(--chat-text-primary);
  }

  .skill-actions {
    display: flex;
    align-items: center;
    gap: 4px;

    .el-button {
      opacity: 0;
      transition: opacity 0.2s;
    }
  }

  .skill-level {
    font-size: 10px;
    padding: 2px 8px;
    border-radius: var(--chat-radius-sm);
    font-weight: 600;

    &.level-expert {
      background: rgba(239, 68, 68, 0.15);
      color: #ef4444;
    }

    &.level-proficient {
      background: rgba(245, 158, 11, 0.15);
      color: #f59e0b;
    }

    &.level-intermediate {
      background: rgba(6, 182, 212, 0.15);
      color: #06b6d4;
    }

    &.level-beginner {
      background: rgba(107, 114, 128, 0.15);
      color: #6b7280;
    }
  }

  .skill-bar {
    height: 6px;
    background: var(--chat-bg-elevated);
    border-radius: 3px;
    overflow: hidden;
  }

  .skill-bar-fill {
    height: 100%;
    background: var(--chat-gradient-primary);
    border-radius: 3px;
    transition: width 0.3s ease;
  }
}

.update-info {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: var(--chat-surface-glass);
  backdrop-filter: blur(12px);
  border: 1px solid var(--chat-border-subtle);
  border-radius: var(--chat-radius-md);
  font-size: 11px;
  color: var(--chat-text-muted);

  .el-icon {
    width: 14px;
    height: 14px;
  }

  .version {
    margin-left: auto;
    font-family: var(--chat-font-mono);
    color: var(--chat-accent-purple);
  }
}

.empty-hint {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--chat-text-muted);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-dialog {
  .dialog-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 20px 0;
    text-align: center;

    .warning-icon {
      width: 48px;
      height: 48px;
      color: #f59e0b;
      margin-bottom: 16px;
    }

    p {
      margin: 0 0 8px;
      font-size: 16px;
      font-weight: 500;
      color: var(--chat-text-primary);
    }

    .warning-text {
      font-size: 13px;
      color: var(--chat-text-muted);
    }
  }

  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
}

.item-form {
  .confidence-slider {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;

    .el-slider {
      flex: 1;
    }

    .confidence-display {
      min-width: 40px;
      text-align: right;
      font-family: var(--chat-font-mono);
      font-size: 13px;
      color: var(--chat-accent-purple);
      font-weight: 600;
    }
  }
}

:deep(.profile-dialog) {
  .el-dialog {
    background: var(--chat-bg-elevated);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 16px;

    .el-dialog__header {
      padding: 16px 20px;
      border-bottom: 1px solid var(--chat-border-subtle);

      .el-dialog__title {
        color: var(--chat-text-primary);
        font-weight: 600;
      }
    }

    .el-dialog__body {
      padding: 20px;
    }

    .el-dialog__footer {
      padding: 16px 20px;
      border-top: 1px solid var(--chat-border-subtle);
    }
  }
}

@media (max-width: 768px) {
  .user-profile-page {
    padding: 16px;

    .page-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 12px;

      h1 {
        font-size: 20px;
      }

      .header-actions {
        width: 100%;
        flex-wrap: wrap;
      }

      .stats-cards {
        width: 100%;
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 8px;

        .stat-card {
          flex-direction: column;
          text-align: center;
          padding: 8px 4px;

          .stat-value {
            font-size: 18px;
          }

          .stat-label {
            font-size: 10px;
          }
        }
      }
    }

    .main-content {
      flex-direction: column;
      gap: 16px;

      .profile-summary {
        width: 100%;
      }

      .profile-details {
        width: 100%;
      }
    }

    .category-card {
      .card-header {
        padding: 12px 16px;
      }

      .item-list {
        .item-row {
          padding: 10px 16px;
        }
      }

      .preference-grid {
        grid-template-columns: 1fr;
      }
    }
  }

  :deep(.profile-dialog) {
    .el-dialog {
      width: calc(100% - 32px) !important;
      max-width: 500px;
      margin: 16px auto;
      border-radius: 12px;

      .el-dialog__header {
        padding: 12px 16px;

        .el-dialog__title {
          font-size: 15px;
        }

        .el-dialog__headerbtn {
          top: 12px;
          right: 12px;

          .el-dialog__close {
            font-size: 18px;
          }
        }
      }

      .el-dialog__body {
        padding: 16px;
      }

      .el-dialog__footer {
        padding: 12px 16px;
      }
    }

    .dialog-content {
      padding: 16px 0;

      .warning-icon {
        width: 40px;
        height: 40px;
      }

      p {
        font-size: 14px;
      }

      .warning-text {
        font-size: 12px;
      }
    }

    .item-form {
      .el-form-item {
        margin-bottom: 14px;

        .el-form-item__label {
          padding: 0 0 4px 0;
          font-size: 13px;
        }
      }

      .el-input,
      .el-select {
        .el-input__wrapper,
        .el-select__wrapper {
          min-height: 40px;
          padding: 4px 12px;
        }

        .el-input__inner {
          font-size: 15px;
        }
      }

      .confidence-slider {
        flex-direction: column;
        align-items: stretch;
        gap: 8px;

        .confidence-display {
          text-align: center;
        }
      }
    }
  }
}

// 通用表单样式类
.input-full {
  width: 100%;
}
</style>
