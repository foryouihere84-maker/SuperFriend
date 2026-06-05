<template>
  <div class="knowledge-graph-page">
    <div class="page-header">
      <h1>知识图谱</h1>
      <div class="stats-cards">
        <div class="stat-card">
          <span class="stat-value">{{ graphData.stats.totalNodes }}</span>
          <span class="stat-label">节点</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ graphData.stats.totalRelations }}</span>
          <span class="stat-label">关系</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ graphData.stats.nodeTypeCount }}</span>
          <span class="stat-label">类型</span>
        </div>
      </div>
    </div>

    <div class="main-content">
      <div class="graph-container">
        <div class="graph-toolbar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索节点..."
            clearable
            size="small"
            @keyup.enter="handleSearch"
            class="search-input"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button size="small" @click="handleRefresh">
            <el-icon><Refresh /></el-icon>
          </el-button>
          <el-button size="small" @click="handleFitView">
            <el-icon><FullScreen /></el-icon>
          </el-button>
          <el-button type="danger" size="small" @click="handleClearAll">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>

        <div class="graph-canvas-wrapper" ref="canvasWrapper">
          <div class="canvas-bg">
            <div class="bg-gradient"></div>
            <div class="bg-grid"></div>
            <div class="bg-noise"></div>
          </div>
          <div ref="networkContainer" class="network-container"></div>
          <div v-if="loading" class="loading-overlay">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
          <div v-if="!loading && graphData.nodes.length === 0" class="empty-overlay">
            <el-empty description="暂无知识图谱数据">
              <template #description>
                <p>开始对话后，系统会自动从对话中提取知识</p>
              </template>
            </el-empty>
          </div>
        </div>

        <div class="graph-legend">
          <div class="legend-title">节点类型</div>
          <div class="legend-items">
            <div v-for="item in nodeTypeColors" :key="item.type" class="legend-item">
              <span class="legend-color" :style="{ backgroundColor: item.color }"></span>
              <span class="legend-label">{{ item.label }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="sidebar">
        <div class="sidebar-section">
          <div class="section-header">
            <el-icon><List /></el-icon>
            <span>节点列表</span>
            <el-button type="primary" size="small" @click="handleAddNode">
              <el-icon><Plus /></el-icon>
            </el-button>
          </div>
          <div class="node-list">
            <div
              v-for="node in filteredNodes"
              :key="node.id"
              class="node-item"
              @click="focusNode(node)"
            >
              <span class="node-type-badge" :style="{ backgroundColor: getTypeColor(node.type) }">
                {{ node.typeName }}
              </span>
              <span class="node-name">{{ node.name }}</span>
              <el-icon class="node-delete-btn" @click.stop="handleDeleteNode(node)"><Close /></el-icon>
            </div>
          </div>
        </div>

        <div class="sidebar-section">
          <div class="section-header">
            <el-icon><Connection /></el-icon>
            <span>关系列表</span>
            <el-button type="primary" size="small" @click="handleAddRelation">
              <el-icon><Plus /></el-icon>
            </el-button>
          </div>
          <div class="relation-list">
            <div v-for="rel in graphData.relations" :key="rel.id" class="relation-item">
              <div class="relation-visual" @click="handleEditRelation(rel)">
                <span class="relation-node">{{ rel.sourceName }}</span>
                <span class="relation-line"></span>
                <span class="relation-type-badge">{{ rel.typeName }}</span>
                <span class="relation-line"></span>
                <span class="relation-node">{{ rel.targetName }}</span>
              </div>
              <el-icon class="relation-delete-btn" @click="handleDeleteRelation(rel)"><Close /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 节点编辑面板 -->
    <Teleport to="body">
      <Transition name="panel-fade">
        <div v-if="nodeDetailVisible" class="panel-overlay" @click.self="nodeDetailVisible = false; isAddingNode = false">
          <div class="panel-card">
            <div class="panel-header">
              <div class="panel-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/></svg>
                <span>{{ isAddingNode ? '添加节点' : '编辑节点' }}</span>
              </div>
              <button class="panel-close" @click="nodeDetailVisible = false; isAddingNode = false">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            <div v-if="editingNode" class="panel-body">
              <div class="field-group">
                <label class="field-label">名称</label>
                <input v-model="editingNode.name" class="field-input" placeholder="输入节点名称" />
              </div>
              <div class="field-group">
                <label class="field-label">类型</label>
                <div class="type-chips">
                  <button
                    v-for="t in nodeTypeColors"
                    :key="t.type"
                    :class="['type-chip', { 'type-chip--active': editingNode.nodeType === t.type }]"
                    @click="editingNode.nodeType = t.type"
                  >
                    <span class="type-chip__dot" :style="{ backgroundColor: t.color }"></span>
                    {{ t.label }}
                  </button>
                </div>
              </div>
              <div class="field-group">
                <label class="field-label">头像</label>
                <input v-model="editingNode.avatar" class="field-input" placeholder="emoji 或图片URL" />
                <div class="field-hint">支持 emoji 或图片链接，留空使用默认头像</div>
              </div>
              <div class="field-group">
                <label class="field-label">重要性 <span class="field-value">{{ editingNode.importance }}/5</span></label>
                <div class="importance-bar">
                  <button
                    v-for="n in 5"
                    :key="n"
                    :class="['importance-dot', { 'importance-dot--active': n <= editingNode.importance }]"
                    @click="editingNode.importance = n"
                  >{{ n }}</button>
                </div>
              </div>
              <div class="field-group">
                <label class="field-label">关键词</label>
                <input v-model="editingNode.keywords" class="field-input" placeholder="关键词，逗号分隔" />
              </div>
              <div class="field-group">
                <label class="field-label">简短描述</label>
                <textarea v-model="editingNode.description" class="field-textarea" rows="2" placeholder="输入简短描述..."></textarea>
              </div>
              <div class="field-group">
                <label class="field-label">图片链接 <span class="field-optional">（可选）</span></label>
                <input v-model="editingNode.image" class="field-input" placeholder="图片URL，点击头像展示" />
              </div>
              <div class="field-group">
                <label class="field-label">详细描述 <span class="field-optional">（可选）</span></label>
                <textarea v-model="editingNode.detailedDescription" class="field-textarea" rows="4" placeholder="详细描述，点击头像展示..."></textarea>
              </div>
            </div>
            <div class="panel-footer">
              <button class="btn btn--ghost" @click="nodeDetailVisible = false; isAddingNode = false">取消</button>
              <button class="btn btn--primary" @click="handleSaveNode">{{ isAddingNode ? '添加' : '保存' }}</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 关系编辑面板 -->
    <Teleport to="body">
      <Transition name="panel-fade">
        <div v-if="relationDialogVisible" class="panel-overlay" @click.self="relationDialogVisible = false">
          <div class="panel-card">
            <div class="panel-header">
              <div class="panel-title">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
                <span>{{ isEditingRelation ? '编辑关系' : '添加关系' }}</span>
              </div>
              <button class="panel-close" @click="relationDialogVisible = false">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            <div v-if="editingRelation" class="panel-body">
              <div class="field-group">
                <label class="field-label">源节点</label>
                <select v-model="editingRelation.sourceNodeId" class="field-select">
                  <option :value="null" disabled>选择源节点</option>
                  <option v-for="node in graphData.nodes" :key="node.id" :value="node.id">{{ node.name }}</option>
                </select>
              </div>
              <div class="field-group">
                <label class="field-label">关系类型 <span class="field-value">{{ (editingRelation.relationType || '').length }}/10</span></label>
                <input
                  v-model="editingRelation.relationType"
                  class="field-input"
                  placeholder="输入关系描述，如：使用、依赖、包含"
                  maxlength="10"
                />
                <div class="field-hint">支持自由定义关系，最多10个字符</div>
              </div>
              <div class="field-group">
                <label class="field-label">目标节点</label>
                <select v-model="editingRelation.targetNodeId" class="field-select">
                  <option :value="null" disabled>选择目标节点</option>
                  <option v-for="node in graphData.nodes" :key="node.id" :value="node.id">{{ node.name }}</option>
                </select>
              </div>
              <div class="field-group">
                <label class="field-label">权重 <span class="field-value">{{ editingRelation.weight }}/10</span></label>
                <div class="importance-bar importance-bar--10">
                  <button
                    v-for="n in 10"
                    :key="n"
                    :class="['importance-dot', { 'importance-dot--active': n <= editingRelation.weight }]"
                    @click="editingRelation.weight = n"
                  >{{ n }}</button>
                </div>
              </div>
            </div>
            <div class="panel-footer">
              <button class="btn btn--ghost" @click="relationDialogVisible = false">取消</button>
              <button class="btn btn--primary" @click="handleSaveRelation">{{ isEditingRelation ? '保存' : '添加' }}</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 节点详情预览弹窗 -->
    <Teleport to="body">
      <Transition name="panel-fade">
        <div v-if="nodePreviewVisible" class="preview-overlay" @click.self="closePreview">
          <div class="preview-modal">
            <!-- 头部 -->
            <div class="preview-header">
              <div class="preview-header__left">
                <div class="preview-avatar">
                  <img
                    v-if="previewNode && isImageUrl(previewNode.avatar || '')"
                    :src="previewNode.avatar || undefined"
                    :alt="previewNode.name"
                    class="preview-avatar__img"
                  />
                  <span v-else class="preview-avatar__emoji">
                    {{ previewNode ? getEffectiveAvatar(previewNode) : '📌' }}
                  </span>
                </div>
                <div class="preview-title-group">
                  <h3 class="preview-title">{{ previewNode?.name }}</h3>
                  <span class="preview-type-badge" :style="{ backgroundColor: getTypeColor(previewNode?.type || 'CONCEPT') }">
                    {{ previewNode?.typeName }}
                  </span>
                </div>
              </div>
              <div class="preview-header__actions">
                <button class="preview-action-btn" @click="handleEnrichNode" :disabled="enrichingNode || isNodeEnriched" :title="isNodeEnriched ? '已触发丰富化' : 'AI 丰富化'">
                  <svg v-if="!enrichingNode && !isNodeEnriched" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"/>
                    <path d="M21 3v5h-5"/>
                  </svg>
                  <svg v-else-if="enrichingNode" class="is-spinning" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" stroke-dasharray="32" stroke-dashoffset="32"/>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </button>
                <button class="preview-action-btn" @click="editFromPreview" title="编辑">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                </button>
                <button class="preview-close-btn" @click="closePreview" title="关闭">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              </div>
            </div>

            <!-- 内容区 -->
            <div v-if="previewNode" class="preview-content">
              <!-- 图片轮播区 -->
              <div v-if="imageUrls.length > 0" class="preview-gallery">
                <div class="gallery-viewport">
                  <!-- 图片容器 -->
                  <div
                    class="gallery-track"
                    :style="{ transform: `translateX(-${currentImageIndex * 100}%)` }"
                  >
                    <div
                      v-for="(url, idx) in imageUrls"
                      :key="idx"
                      class="gallery-slide"
                    >
                      <img
                        :src="url"
                        :alt="`${previewNode.name} - 图片 ${idx + 1}`"
                        class="gallery-image"
                        :class="{ 'gallery-image--loading': loadingImages[idx] === true }"
                        @load="handleImageLoad(idx)"
                        @error="handleImageLoadError(idx, $event)"
                      />
                      <div v-if="loadingImages[idx] === true" class="gallery-placeholder">
                        <svg class="is-spinning" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <circle cx="12" cy="12" r="10" stroke-dasharray="32" stroke-dashoffset="32"/>
                        </svg>
                      </div>
                      <div v-if="loadingImages[idx] === 'error'" class="gallery-placeholder gallery-placeholder--error">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <circle cx="12" cy="12" r="10"/>
                          <line x1="15" y1="9" x2="9" y2="15"/>
                          <line x1="9" y1="9" x2="15" y2="15"/>
                        </svg>
                        <span>加载失败</span>
                      </div>
                    </div>
                  </div>

                  <!-- 轮播控制 -->
                  <template v-if="imageUrls.length > 1">
                    <button
                      class="gallery-nav gallery-nav--prev"
                      @click="prevImage"
                      :disabled="currentImageIndex === 0"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="15 18 9 12 15 6"/>
                      </svg>
                    </button>
                    <button
                      class="gallery-nav gallery-nav--next"
                      @click="nextImage"
                      :disabled="currentImageIndex === imageUrls.length - 1"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="9 18 15 12 9 6"/>
                      </svg>
                    </button>
                  </template>
                </div>

                <!-- 指示器 -->
                <div v-if="imageUrls.length > 1" class="gallery-indicators">
                  <button
                    v-for="(_, idx) in imageUrls"
                    :key="idx"
                    :class="['gallery-dot', { 'gallery-dot--active': idx === currentImageIndex }]"
                    @click="currentImageIndex = idx"
                  ></button>
                </div>
              </div>

              <!-- 参考资料 -->
              <div v-if="referenceLinks.length > 0" class="preview-section">
                <div class="preview-section__title">参考资料</div>
                <div class="preview-references">
                  <a
                    v-for="(link, idx) in referenceLinks"
                    :key="idx"
                    :href="link.url"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="preview-reference-link"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
                      <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
                    </svg>
                    <span>{{ link.displayText }}</span>
                  </a>
                </div>
              </div>

              <!-- 基本信息 -->
              <div class="preview-section">
                <div class="preview-section__title">基本信息</div>
                <div class="preview-info-grid">
                  <div class="preview-info-item">
                    <span class="preview-info-label">类型</span>
                    <span class="preview-info-value">{{ previewNode.typeName }}</span>
                  </div>
                  <div v-if="previewNode.description" class="preview-info-item preview-info-item--full">
                    <span class="preview-info-label">简介</span>
                    <span class="preview-info-value">{{ previewNode.description }}</span>
                  </div>
                  <div v-if="previewNode.keywords" class="preview-info-item preview-info-item--full">
                    <span class="preview-info-label">关键词</span>
                    <div class="preview-keywords">
                      <span
                        v-for="(kw, idx) in previewNode.keywords.split(',').map(k => k.trim()).filter(k => k)"
                        :key="idx"
                        class="preview-keyword"
                      >{{ kw }}</span>
                    </div>
                  </div>
                  <div v-if="previewNode.importance" class="preview-info-item">
                    <span class="preview-info-label">重要性</span>
                    <div class="preview-importance">
                      <span
                        v-for="n in 5"
                        :key="n"
                        :class="['importance-star', { 'importance-star--active': n <= previewNode.importance }]"
                      >★</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 详细描述 -->
              <div v-if="cleanedDescription" class="preview-section">
                <div class="preview-section__title">详细描述</div>
                <div class="preview-section__content" v-html="renderDescription(cleanedDescription)"></div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { getFullGraph, getConversationGraph, searchNodes, deleteNode, updateNode, addNode, addRelation, deleteRelation, updateRelation, clearAll, clearGlobalGraph, clearConversationGraph, enrichNode, type GraphContext, type GraphNode } from '@/api/knowledgeGraph'
import { Search, Refresh, Loading, List, Connection, Close, Plus, FullScreen, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { Network } from 'vis-network'
import { DataSet } from 'vis-data'
import type { Node, Edge, Options } from 'vis-network'

// Props: 传入 sessionId 时只显示该对话的知识图谱
const props = defineProps<{
  sessionId?: string
}>()

const userStore = useUserStore()
const canvasWrapper = ref<HTMLElement>()
const networkContainer = ref<HTMLElement>()

const loading = ref(false)
const graphData = ref<GraphContext>({
  nodes: [],
  relations: [],
  stats: { totalNodes: 0, totalRelations: 0, nodeTypeCount: 0, relationTypeCount: 0 }
})
const searchKeyword = ref('')
const nodeDetailVisible = ref(false)
const selectedNode = ref<GraphNode | null>(null)
const editingNode = ref<{
  id: number
  name: string
  nodeType: string
  importance: number
  keywords: string
  description: string
  avatar: string
  image: string
  detailedDescription: string
} | null>(null)
const isAddingNode = ref(false)

// 节点详情查看弹窗（展示图片和详细描述）
const nodePreviewVisible = ref(false)
const previewNode = ref<GraphNode | null>(null)
const enrichingNode = ref(false)
// 记录已触发丰富化的节点ID，防止重复点击
const enrichedNodeIds = ref<Set<number>>(new Set())

// 判断当前预览节点是否已被丰富化
const isNodeEnriched = computed(() => {
  if (!previewNode.value) return false
  return enrichedNodeIds.value.has(previewNode.value.id)
})

// 图片轮播相关
const currentImageIndex = ref(0)
const loadingImages = ref<Record<number, boolean | 'error'>>({})

const imageUrls = computed(() => {
  if (!previewNode.value?.image) return []
  return previewNode.value.image
    .split(',')
    .map((url: string) => url.trim())
    .filter((url: string) => url && url !== 'null' && (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:image')))
})

// 解析参考资料链接（从详细描述中提取）
interface ReferenceLink {
  url: string
  displayText: string
}

const referenceLinks = computed<ReferenceLink[]>(() => {
  if (!previewNode.value?.detailedDescription) return []
  const text = previewNode.value.detailedDescription
  const links: ReferenceLink[] = []

  // 匹配 Markdown 链接格式：[百度百科](url) 或 [百度百科: xxx](url)
  const mdLinkRegex = /\[百度百科[^\]]*\]\(([^)]+)\)/gi
  let match
  while ((match = mdLinkRegex.exec(text)) !== null) {
    const url = match[1]
    // 从 URL 提取显示文本（去掉百度百科前缀）
    let displayText = url
    try {
      const urlObj = new URL(url)
      displayText = urlObj.hostname + urlObj.pathname
      if (displayText.length > 40) {
        displayText = displayText.substring(0, 40) + '...'
      }
    } catch {
      displayText = url.length > 40 ? url.substring(0, 40) + '...' : url
    }
    links.push({ url, displayText })
  }

  // 匹配裸露的 URL（百度百科相关）
  const urlRegex = /(https?:\/\/baike\.baidu\.com[^\s<>\[\]()]+)/gi
  while ((match = urlRegex.exec(text)) !== null) {
    const url = match[1]
    // 避免重复添加
    if (!links.find(l => l.url === url)) {
      let displayText = url
      try {
        const urlObj = new URL(url)
        displayText = urlObj.hostname + urlObj.pathname
        if (displayText.length > 40) {
          displayText = displayText.substring(0, 40) + '...'
        }
      } catch {
        displayText = url.length > 40 ? url.substring(0, 40) + '...' : url
      }
      links.push({ url, displayText })
    }
  }

  return links
})

// 清理后的详细描述（去掉参考资料部分）
const cleanedDescription = computed(() => {
  if (!previewNode.value?.detailedDescription) return ''

  let text = previewNode.value.detailedDescription

  // 去掉首行的 "null" 字符串
  text = text.replace(/^null\s*/i, '')

  // 去掉百度百科相关的 Markdown 链接
  text = text.replace(/\[百度百科[^\]]*\]\([^)]+\)/gi, '')

  // 去掉裸露的百度百科 URL
  text = text.replace(/https?:\/\/baike\.baidu\.com[^\s<>\[\]()]+/gi, '')

  // 去掉末尾的参考资料行（如 "- [详细信息]() - 百度百科" 等）
  text = text.replace(/\n*[-]?\s*\[[^\]]*\]\([^)]*\)\s*[-]?\s*百度百科.*$/gi, '')
  text = text.replace(/\n*[-]?\s*百度百科.*$/gi, '')

  // 去掉末尾的 "参考资料：" 行（包括 **参考资料：** 格式）
  text = text.replace(/\n*\*?\*?参考资料[：:]\*?\*?.*$/gi, '')

  // 去掉末尾的 --- 分隔线
  text = text.replace(/\n*[-]{3,}\s*$/g, '')
  text = text.replace(/^[-]{3,}\s*$/gm, '')

  // 去掉多余的空行
  text = text.replace(/\n{3,}/g, '\n\n')

  // 去掉首尾空白
  text = text.trim()

  return text
})

const prevImage = () => {
  if (currentImageIndex.value > 0) {
    currentImageIndex.value--
  }
}

const nextImage = () => {
  if (currentImageIndex.value < imageUrls.value.length - 1) {
    currentImageIndex.value++
  }
}

const handleImageLoad = (index: number) => {
  loadingImages.value[index] = false
}

const handleImageLoadError = (index: number, event: Event) => {
  loadingImages.value[index] = 'error'
  const target = event.target as HTMLImageElement
  target.style.visibility = 'hidden'
}

// 打开节点详情预览
const openNodePreview = (node: GraphNode) => {
  previewNode.value = node
  currentImageIndex.value = 0
  // 初始化图片加载状态
  const urls = node.image
    ? node.image
        .split(',')
        .map((url: string) => url.trim())
        .filter((url: string) => url && url !== 'null' && (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:image')))
    : []
  loadingImages.value = {}
  urls.forEach((_, idx) => {
    loadingImages.value[idx] = true
  })
  nodePreviewVisible.value = true
}

const closePreview = () => {
  nodePreviewVisible.value = false
}

const editFromPreview = () => {
  if (!previewNode.value) return
  closePreview()
  openEditDialog(previewNode.value)
}

// 关系编辑相关
const relationDialogVisible = ref(false)
const isEditingRelation = ref(false)
const editingRelationId = ref<number | null>(null)
const editingRelation = ref<{
  sourceNodeId: number | null
  targetNodeId: number | null
  relationType: string
  weight: number
} | null>(null)

// vis-network 实例
let network: Network | null = null
let nodesDataSet: DataSet<Node> | null = null
let edgesDataSet: DataSet<Edge> | null = null

// 节点类型颜色映射
const nodeTypeColors = [
  { type: 'TECHNOLOGY', label: '技术栈', color: '#3b82f6' },
  { type: 'PROJECT', label: '项目', color: '#10b981' },
  { type: 'PREFERENCE', label: '偏好', color: '#f59e0b' },
  { type: 'TASK', label: '任务', color: '#8b5cf6' },
  { type: 'ERROR', label: '错误', color: '#ef4444' },
  { type: 'SOLUTION', label: '解决方案', color: '#06b6d4' },
  { type: 'CONCEPT', label: '概念', color: '#6b7280' },
  { type: 'USER', label: '用户', color: '#ec4899' }
]

// 关系类型已改为自由输入，不再使用固定选项

const getTypeColor = (type: string): string => {
  const found = nodeTypeColors.find(item => item.type === type)
  return found?.color || '#6b7280'
}

const filteredNodes = computed(() => {
  if (!searchKeyword.value) return graphData.value.nodes
  const keyword = searchKeyword.value.toLowerCase()
  return graphData.value.nodes.filter(node =>
    node.name.toLowerCase().includes(keyword)
  )
})

// 检测当前主题
const isDarkTheme = () => {
  return document.documentElement.classList.contains('dark')
}

// 获取主题相关颜色
const getThemeColors = () => {
  const dark = isDarkTheme()
  return {
    textColor: dark ? '#f5f5fa' : '#1a1a2e',
    edgeColor: dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.15)',
    edgeFontColor: dark ? '#9898a8' : '#6b6b88',
    nodeBorderColor: dark ? 'rgba(255,255,255,0.8)' : 'rgba(0,0,0,0.3)',
    highlightColor: '#a78bfa'
  }
}

// 初始化 vis-network
const initNetwork = () => {
  if (!networkContainer.value) return

  nodesDataSet = new DataSet<Node>([])
  edgesDataSet = new DataSet<Edge>([])

  const themeColors = getThemeColors()

  const options: Options = {
    nodes: {
      shape: 'dot',
      size: 20,
      font: {
        color: themeColors.textColor,
        size: 12,
        face: 'Plus Jakarta Sans, system-ui, sans-serif'
      },
      borderWidth: 2,
      shadow: {
        enabled: true,
        color: 'rgba(0,0,0,0.3)',
        size: 10
      }
    },
    edges: {
      color: {
        color: themeColors.edgeColor,
        highlight: '#8b5cf6',
        hover: '#a78bfa'
      },
      width: 2,
      font: {
        size: 10,
        color: themeColors.edgeFontColor,
        strokeWidth: 0,
        face: 'Plus Jakarta Sans, system-ui, sans-serif'
      },
      smooth: {
        enabled: true,
        type: 'continuous',
        roundness: 0.5
      },
      arrows: {
        to: { enabled: true, scaleFactor: 0.5 }
      }
    },
    physics: {
      enabled: true,
      stabilization: {
        enabled: true,
        iterations: 200,
        updateInterval: 25
      },
      barnesHut: {
        gravitationalConstant: -3000,
        centralGravity: 0.3,
        springLength: 150,
        springConstant: 0.05,
        damping: 0.5
      },
      solver: 'barnesHut'
    },
    interaction: {
      hover: true,
      tooltipDelay: 200,
      zoomView: true,
      dragView: true,
      dragNodes: true,
      selectConnectedEdges: true,
      navigationButtons: false,
      keyboard: {
        enabled: true,
        bindToWindow: false
      }
    },
    layout: {
      improvedLayout: true,
      clusterThreshold: 150
    }
  }

  network = new Network(
    networkContainer.value,
    { nodes: nodesDataSet, edges: edgesDataSet },
    options
  )

  // 点击节点事件 - 打开预览弹窗
  network.on('click', (params) => {
    if (params.nodes && params.nodes.length > 0) {
      const nodeId = params.nodes[0]
      const node = graphData.value.nodes.find(n => n.id === nodeId)
      if (node) {
        openNodePreview(node)
      }
    }
  })

  // 双击聚焦节点
  network.on('doubleClick', (params) => {
    if (params.nodes && params.nodes.length > 0) {
      network?.focus(params.nodes[0], {
        scale: 1.5,
        animation: {
          duration: 500,
          easingFunction: 'easeInOutQuad'
        }
      })
    }
  })

  // 布局稳定后隐藏加载状态
  network.on('stabilizationIterationsDone', () => {
    loading.value = false
  })
}

// 获取节点默认头像
const getDefaultAvatar = (type: string): string => {
  const avatars: Record<string, string> = {
    'USER': '👤',
    'PROJECT': '📁',
    'TECHNOLOGY': '⚙️',
    'CONCEPT': '💡',
    'TASK': '📋',
    'ERROR': '❌',
    'SOLUTION': '✅',
    'PREFERENCE': '⭐'
  }
  return avatars[type] || '📌'
}

// 获取节点有效头像
const getEffectiveAvatar = (node: GraphNode): string => {
  return node.avatar || getDefaultAvatar(node.type)
}

// 判断字符串是否为图片URL
const isImageUrl = (str: string): boolean => {
  if (!str) return false
  return str.startsWith('http://') || str.startsWith('https://') || str.startsWith('data:image')
}

// 将emoji转换为图片URL（使用开源emoji图片服务）
const emojiToImageUrl = (emoji: string): string | null => {
  if (!emoji) return null
  // 移除变体选择符（fe0f），这是 emoji 显示样式的修饰符，Twemoji 路径中不需要
  const cleanEmoji = emoji.replace(/\uFE0F/g, '')
  // 使用 Twemoji CDN 服务将 emoji 转为图片
  const codePoints = [...cleanEmoji].map(char => char.codePointAt(0)?.toString(16)).join('-')
  if (!codePoints) return null
  return `https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/${codePoints}.png`
}

// 更新图谱数据到 vis-network
const updateNetworkData = () => {
  if (!nodesDataSet || !edgesDataSet) return

  const themeColors = getThemeColors()

  // 更新节点
  const nodes: Node[] = graphData.value.nodes.map(node => {
    const avatar = getEffectiveAvatar(node)
    const hasImage = node.image && node.image.length > 0
    const hasDetailedDesc = node.detailedDescription && node.detailedDescription.length > 0
    // 根据 importance 动态调整节点大小
    const nodeSize = 20 + (node.importance || 5) * 2

    // 确定节点图片：优先使用avatar中的URL，其次将emoji转为图片URL
    let nodeImageUrl: string | null = null
    let isCustomImage = false // 是否为用户自定义图片

    if (isImageUrl(node.avatar || '')) {
      nodeImageUrl = node.avatar!
      isCustomImage = true
    } else if (avatar) {
      // avatar是emoji，转换为图片URL
      nodeImageUrl = emojiToImageUrl(avatar)
    }

    // 使用图片形状渲染节点
    if (nodeImageUrl) {
      return {
        id: node.id,
        label: node.name.length > 12 ? node.name.slice(0, 12) + '...' : node.name,
        shape: 'circularImage',
        image: nodeImageUrl,
        size: nodeSize,
        // 图片在圆形内的缩放比例
        imagePadding: isCustomImage ? 4 : 2,
        title: `<div style="padding:8px;max-width:280px;">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;">
            <span style="font-size:24px;">${avatar}</span>
            <strong style="color:${themeColors.textColor};font-size:14px;">${node.name}</strong>
          </div>
          <div style="font-size:11px;color:${themeColors.edgeFontColor};">
            类型: ${node.typeName}
          </div>
          ${node.description ? `<div style="font-size:11px;color:${themeColors.edgeFontColor};margin-top:4px;">${node.description.slice(0, 100)}${node.description.length > 100 ? '...' : ''}</div>` : ''}
          ${hasImage || hasDetailedDesc ? `<div style="font-size:10px;color:var(--chat-accent-purple);margin-top:6px;">📎 点击查看更多</div>` : ''}
        </div>`,
        color: {
          background: 'rgba(255,255,255,0)',
          border: 'rgba(255,255,255,0)',
          highlight: {
            background: 'rgba(255,255,255,0)',
            border: themeColors.highlightColor
          },
          hover: {
            background: 'rgba(255,255,255,0)',
            border: themeColors.highlightColor
          }
        },
        font: {
          color: isDarkTheme() ? '#ffffff' : '#1a1a2e',
          size: 12,
          face: 'Plus Jakarta Sans, system-ui, sans-serif'
        },
        borderWidth: 3,
        shadow: {
          enabled: false
        }
      }
    }

    // 回退：使用默认的 dot 形状
    return {
      id: node.id,
      label: node.name.length > 12 ? node.name.slice(0, 12) + '...' : node.name,
      shape: 'dot',
      title: `<div style="padding:8px;max-width:280px;">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;">
          <span style="font-size:24px;">${avatar}</span>
          <strong style="color:${themeColors.textColor};font-size:14px;">${node.name}</strong>
        </div>
        <div style="font-size:11px;color:${themeColors.edgeFontColor};">
          类型: ${node.typeName}
        </div>
        ${node.description ? `<div style="font-size:11px;color:${themeColors.edgeFontColor};margin-top:4px;">${node.description.slice(0, 100)}${node.description.length > 100 ? '...' : ''}</div>` : ''}
        ${hasImage || hasDetailedDesc ? `<div style="font-size:10px;color:var(--chat-accent-purple);margin-top:6px;">📎 点击查看更多</div>` : ''}
      </div>`,
      color: {
        background: getTypeColor(node.type),
        border: themeColors.nodeBorderColor,
        highlight: {
          background: themeColors.highlightColor,
          border: '#fff'
        },
        hover: {
          background: getTypeColor(node.type),
          border: themeColors.highlightColor
        }
      },
      size: nodeSize,
      font: {
        color: isDarkTheme() ? '#ffffff' : '#1a1a2e',
        size: 12,
        face: 'Plus Jakarta Sans, system-ui, sans-serif'
      },
      borderWidth: 2,
      shadow: {
        enabled: true,
        color: 'rgba(0,0,0,0.3)',
        size: 8
      }
    }
  })

  // 更新边
  const edges: Edge[] = graphData.value.relations.map(rel => ({
    id: rel.id,
    from: rel.sourceId,
    to: rel.targetId,
    label: rel.typeName,
    width: 1 + (rel.weight || 1) * 0.3,
    color: {
      color: themeColors.edgeColor,
      highlight: '#8b5cf6'
    },
    font: {
      size: 10,
      color: themeColors.edgeFontColor,
      strokeWidth: 0
    },
    smooth: {
      enabled: true,
      type: 'curvedCW',
      roundness: 0.2
    },
    arrows: {
      to: { enabled: true, scaleFactor: 0.5 }
    }
  }))

  // 清空并重新添加数据
  nodesDataSet.clear()
  nodesDataSet.add(nodes)
  edgesDataSet.clear()
  edgesDataSet.add(edges)
}

// 更新主题颜色
const updateTheme = () => {
  if (!network) return

  const themeColors = getThemeColors()

  network.setOptions({
    nodes: {
      font: { color: themeColors.textColor }
    },
    edges: {
      font: { color: themeColors.edgeFontColor },
      color: { color: themeColors.edgeColor }
    }
  })
}

// 监听主题变化
const observeThemeChange = () => {
  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.attributeName === 'class') {
        updateTheme()
        updateNetworkData()
      }
    })
  })

  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class']
  })

  return observer
}

const loadGraphData = async () => {
  const userId = userStore.user?.id
  if (!userId) return

  loading.value = true
  try {
    // 根据是否有 sessionId 决定加载全局图谱还是对话级图谱
    const graphResult = props.sessionId
      ? await getConversationGraph(Number(userId), props.sessionId)
      : await getFullGraph(Number(userId))
    graphData.value = graphResult

    await nextTick()

    if (!network) {
      initNetwork()
    }
    updateNetworkData()

    // 直接隐藏加载状态，不依赖 stabilizationIterationsDone 事件
    // 因为该事件只会在首次初始化时触发一次，后续刷新或空数据时不会触发
    loading.value = false
  } catch (error: any) {
    console.error('加载图谱失败:', error)
    ElMessage.error('加载图谱失败: ' + (error.message || '未知错误'))
    loading.value = false
  }
}

const handleFitView = () => {
  if (network) {
    network.fit({
      animation: {
        duration: 500,
        easingFunction: 'easeInOutQuad'
      }
    })
  }
}

const focusNode = (node: GraphNode) => {
  if (network) {
    network.focus(node.id, {
      scale: 1.5,
      animation: {
        duration: 500,
        easingFunction: 'easeInOutQuad'
      }
    })
  }
  // 打开编辑弹窗
  openEditDialog(node)
}

const openEditDialog = (node: GraphNode) => {
  selectedNode.value = node
  editingNode.value = {
    id: node.id,
    name: node.name,
    nodeType: node.type,
    importance: node.importance || 3,
    keywords: node.keywords || '',
    description: node.description || '',
    avatar: node.avatar || '',
    image: node.image || '',
    detailedDescription: node.detailedDescription || ''
  }
  nodeDetailVisible.value = true
}

// 重新丰富化节点
const handleEnrichNode = async () => {
  if (!previewNode.value) return

  // 防止重复点击
  if (enrichedNodeIds.value.has(previewNode.value.id)) {
    return
  }

  enrichingNode.value = true
  try {
    const result = await enrichNode(previewNode.value.id)
    // 标记该节点已触发丰富化
    enrichedNodeIds.value.add(previewNode.value.id)
    ElMessage.success(`节点「${result.nodeName}」已触发丰富化，请稍后刷新查看结果`)

    // 延迟刷新数据
    setTimeout(() => {
      loadGraphData()
    }, 3000)
  } catch (error: any) {
    // 如果是"正在丰富化中"的错误，也标记为已触发
    const errorMsg = error.message || '未知错误'
    if (errorMsg.includes('正在丰富化中')) {
      if (previewNode.value) {
        enrichedNodeIds.value.add(previewNode.value.id)
      }
      ElMessage.warning('节点正在丰富化中，请稍后再试')
    } else {
      ElMessage.error('丰富化失败: ' + errorMsg)
    }
  } finally {
    enrichingNode.value = false
  }
}

const handleSearch = async () => {
  if (!searchKeyword.value.trim()) {
    await loadGraphData()
    return
  }

  const userId = userStore.user?.id
  if (!userId) return

  try {
    // 根据当前图谱类型调用不同的搜索
    // 全局图谱页面只搜索 GLOBAL 节点，对话图谱页面只搜索该对话的节点
    const nodes = props.sessionId
      ? await searchNodes(Number(userId), searchKeyword.value, undefined, props.sessionId)
      : await searchNodes(Number(userId), searchKeyword.value, 'GLOBAL')

    // 转换 KnowledgeNode 到 GraphNode 格式
    graphData.value.nodes = nodes.map(node => ({
      id: node.id,
      type: node.nodeType,
      typeName: node.nodeTypeName,
      name: node.name,
      description: node.description,
      avatar: node.avatar,
      image: node.image,
      detailedDescription: node.detailedDescription,
      keywords: node.keywords,
      importance: node.importance ?? 5,
      properties: node.properties,
      confidence: node.confidence,
      accessCount: node.accessCount
    }))
    updateNetworkData()
  } catch (error) {
    console.error('搜索失败:', error)
  }
}

const handleRefresh = () => {
  searchKeyword.value = ''
  loadGraphData()
}

const handleDeleteNode = async (node: GraphNode) => {
  try {
    await ElMessageBox.confirm(
      `确定删除节点「${node.name}」吗？删除后相关的知识关系也会被清除。`,
      '删除节点',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteNode(node.id)
    ElMessage.success(`节点「${node.name}」已删除`)
    loadGraphData()
  } catch {
    // 用户取消
  }
}

const handleAddNode = () => {
  isAddingNode.value = true
  editingNode.value = {
    id: 0,
    name: '',
    nodeType: 'CONCEPT',
    importance: 3,
    keywords: '',
    description: '',
    avatar: '',
    image: '',
    detailedDescription: ''
  }
  nodeDetailVisible.value = true
}

// 渲染详细描述（已清理过百度百科链接）
const renderDescription = (text: string): string => {
  if (!text) return ''

  // 转义 HTML 特殊字符
  let result = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 将 Markdown 链接 [文本](URL) 转换为 HTML <a> 标签
  result = result.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')

  // 将裸露的 URL 转换为可点击链接
  result = result.replace(/(?<!href=")(https?:\/\/[^\s<>\[\]()]+)/g, '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>')

  // 将换行符转换为 <br>
  result = result.replace(/\n/g, '<br>')

  // 将 **文本** 转换为 <strong>文本</strong>
  result = result.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')

  return result
}

const handleSaveNode = async () => {
  if (!editingNode.value) return
  try {
    if (isAddingNode.value) {
      // 新增节点
      await addNode({
        userId: Number(userStore.user?.id),
        name: editingNode.value.name,
        nodeType: editingNode.value.nodeType,
        importance: editingNode.value.importance,
        keywords: editingNode.value.keywords,
        description: editingNode.value.description,
        avatar: editingNode.value.avatar,
        image: editingNode.value.image,
        detailedDescription: editingNode.value.detailedDescription
      })
      ElMessage.success('节点已添加')
    } else {
      // 更新节点
      await updateNode(editingNode.value.id, {
        name: editingNode.value.name,
        nodeType: editingNode.value.nodeType,
        importance: editingNode.value.importance,
        keywords: editingNode.value.keywords,
        description: editingNode.value.description,
        avatar: editingNode.value.avatar,
        image: editingNode.value.image,
        detailedDescription: editingNode.value.detailedDescription
      })
      ElMessage.success('节点已更新')
    }
    nodeDetailVisible.value = false
    loadGraphData()
  } catch (error: any) {
    ElMessage.error((isAddingNode.value ? '添加失败: ' : '更新失败: ') + (error.message || '未知错误'))
  }
}

// 关系管理
const handleAddRelation = () => {
  isEditingRelation.value = false
  editingRelationId.value = null
  editingRelation.value = {
    sourceNodeId: null,
    targetNodeId: null,
    relationType: '相关',
    weight: 5
  }
  relationDialogVisible.value = true
}

const handleEditRelation = (rel: { id: number; sourceId: number; targetId: number; type: string; weight: number }) => {
  isEditingRelation.value = true
  editingRelationId.value = rel.id
  editingRelation.value = {
    sourceNodeId: rel.sourceId,
    targetNodeId: rel.targetId,
    relationType: rel.type,
    weight: rel.weight ?? 5
  }
  relationDialogVisible.value = true
}

const handleSaveRelation = async () => {
  if (!editingRelation.value || !editingRelation.value.sourceNodeId || !editingRelation.value.targetNodeId) {
    ElMessage.warning('请选择源节点和目标节点')
    return
  }
  if (editingRelation.value.sourceNodeId === editingRelation.value.targetNodeId) {
    ElMessage.warning('源节点和目标节点不能相同')
    return
  }
  if (!editingRelation.value.relationType || editingRelation.value.relationType.trim() === '') {
    ElMessage.warning('请输入关系类型')
    return
  }
  try {
    if (isEditingRelation.value && editingRelationId.value) {
      await updateRelation(editingRelationId.value, {
        sourceNodeId: editingRelation.value.sourceNodeId,
        targetNodeId: editingRelation.value.targetNodeId,
        relationType: editingRelation.value.relationType,
        weight: editingRelation.value.weight
      })
      ElMessage.success('关系已更新')
    } else {
      await addRelation({
        userId: Number(userStore.user?.id),
        sourceNodeId: editingRelation.value.sourceNodeId,
        targetNodeId: editingRelation.value.targetNodeId,
        relationType: editingRelation.value.relationType,
        weight: editingRelation.value.weight
      })
      ElMessage.success('关系已添加')
    }
    relationDialogVisible.value = false
    loadGraphData()
  } catch (error: any) {
    ElMessage.error((isEditingRelation.value ? '更新失败: ' : '添加失败: ') + (error.message || '未知错误'))
  }
}

const handleDeleteRelation = async (rel: { id: number; sourceName: string; typeName: string; targetName: string }) => {
  try {
    await ElMessageBox.confirm(
      `确定删除关系「${rel.sourceName} → ${rel.typeName} → ${rel.targetName}」吗？`,
      '删除关系',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteRelation(rel.id)
    ElMessage.success('关系已删除')
    loadGraphData()
  } catch {
    // 用户取消
  }
}

const handleClearAll = async () => {
  const userId = userStore.user?.id
  if (!userId) return

  try {
    // 根据是否有 sessionId 决定删除范围
    if (props.sessionId) {
      // 对话级图谱：只删除该对话的节点
      await ElMessageBox.confirm(
        `确定清除该对话的知识图谱吗？此操作将删除该对话的所有节点和关系，且不可恢复！全局知识图谱不受影响。`,
        '清除对话图谱',
        { confirmButtonText: '确认清除', cancelButtonText: '取消', type: 'warning', confirmButtonClass: 'el-button--danger' }
      )
      const result = await clearConversationGraph(Number(userId), props.sessionId)
      ElMessage.success(`已清除该对话的 ${result.deletedNodes} 个节点`)
    } else {
      // 全局图谱：让用户选择删除范围
      await ElMessageBox.confirm(
        '请选择要清除的范围：点击"清除全部"将删除所有知识图谱（全局+对话级）；点击"仅全局"只删除全局图谱，保留对话级图谱。',
        '清除图谱数据',
        {
          distinguishCancelAndClose: true,
          confirmButtonText: '清除全部',
          cancelButtonText: '仅全局',
          type: 'warning'
        }
      )

      // 用户点击"清除全部"
      const result = await clearAll(Number(userId))
      ElMessage.success(`已清除全部图谱 ${result.deletedNodes} 个节点`)
    }
    loadGraphData()
  } catch (action: unknown) {
    // 用户点击"仅全局"按钮（cancel）或关闭弹窗
    if (action === 'cancel' && !props.sessionId) {
      // 用户选择仅清除全局图谱
      const result = await clearGlobalGraph(Number(userId))
      ElMessage.success(`已清除全局图谱 ${result.deletedNodes} 个节点，对话图谱已保留`)
      loadGraphData()
    }
    // 其他情况（关闭弹窗）不做任何操作
  }
}

let themeObserver: MutationObserver | null = null

// 监听 sessionId 变化，重新加载图谱
watch(() => props.sessionId, () => {
  loadGraphData()
})

onMounted(async () => {
  await nextTick()

  // 监听主题变化
  themeObserver = observeThemeChange()

  loadGraphData()
})

onUnmounted(() => {
  if (network) {
    network.destroy()
    network = null
  }
  if (themeObserver) {
    themeObserver.disconnect()
    themeObserver = null
  }
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.knowledge-graph-page {
  padding: 12px;
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--chat-page-bg);
  color: var(--chat-text-primary);
  box-sizing: border-box;

  // 背景层
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

  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-image:
      linear-gradient(var(--chat-border-subtle) 1px, transparent 1px),
      linear-gradient(90deg, var(--chat-border-subtle) 1px, transparent 1px);
    background-size: 60px 60px;
    mask-image: radial-gradient(ellipse 80% 60% at 50% 50%, black, transparent);
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
  margin-bottom: 8px;
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

  .search-input {
    width: 140px;
  }
}

.stats-cards {
  display: flex;
  gap: 8px;

  .stat-card {
    background: var(--chat-surface-glass);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
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
  gap: 8px;
  min-height: 0;
}

.graph-container {
  flex: 1;
  background: transparent;
  border: 1px solid var(--chat-border-subtle);
  border-radius: var(--chat-radius-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;

  .graph-toolbar {
    padding: 6px 8px;
    display: flex;
    gap: 6px;
    border-bottom: 1px solid var(--chat-border-subtle);
    flex-shrink: 0;
    background: var(--chat-surface-glass);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
  }

  .graph-canvas-wrapper {
    flex: 1;
    position: relative;
    min-height: 0;

    .canvas-bg {
      position: absolute;
      inset: 0;
      z-index: 0;
      overflow: hidden;
      pointer-events: none;

      .bg-gradient {
        position: absolute;
        inset: 0;
        background: var(--chat-page-gradient);
      }

      .bg-grid {
        position: absolute;
        inset: 0;
        background-image:
          linear-gradient(var(--chat-border-subtle) 1px, transparent 1px),
          linear-gradient(90deg, var(--chat-border-subtle) 1px, transparent 1px);
        background-size: 60px 60px;
        mask-image: radial-gradient(ellipse 80% 60% at 50% 50%, black, transparent);
      }

      .bg-noise {
        position: absolute;
        inset: 0;
        opacity: 0.15;
        background-image: var(--chat-page-noise);
      }
    }

    .network-container {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 1;
    }

    .loading-overlay,
    .empty-overlay {
      position: absolute;
      inset: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      background: var(--chat-bg-primary);
      color: var(--chat-text-tertiary);
      gap: 12px;
      z-index: 10;
    }
  }

  .graph-legend {
    padding: 4px 8px;
    border-top: 1px solid var(--chat-border-subtle);
    flex-shrink: 0;
    background: var(--chat-surface-glass);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);

    .legend-title {
      display: none;
    }

    .legend-items {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      .legend-item {
        display: flex;
        align-items: center;
        gap: 4px;

        .legend-color {
          width: 8px;
          height: 8px;
          border-radius: 50%;
        }

        .legend-label {
          font-size: 10px;
          color: var(--chat-text-primary);
        }
      }
    }
  }
}

.sidebar {
  width: 200px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;

  .sidebar-section {
    background: var(--chat-surface-glass);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border: 1px solid var(--chat-border-subtle);
    border-radius: var(--chat-radius-md);
    overflow: hidden;

    .section-header {
      padding: 6px 8px;
      display: flex;
      align-items: center;
      gap: 6px;
      border-bottom: 1px solid var(--chat-border-subtle);
      font-weight: 600;
      font-size: 12px;
      color: var(--chat-text-primary);

      .el-button {
        margin-left: auto;
      }

      .el-icon {
        width: 14px;
        height: 14px;
      }
    }

    .node-list,
    .relation-list {
      max-height: 140px;
      overflow-y: auto;
    }

    .node-item {
      padding: 5px 8px;
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      transition: background 0.2s;

      &:hover {
        background: var(--chat-surface-glass-hover);
      }

      .node-type-badge {
        font-size: 9px;
        padding: 1px 4px;
        border-radius: var(--chat-radius-sm);
        color: #fff;
      }

      .node-name {
        flex: 1;
        font-size: 11px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        color: var(--chat-text-primary);
      }

      .node-delete-btn {
        opacity: 0;
        font-size: 12px;
        color: var(--chat-text-tertiary);
        transition: all 0.2s;
        flex-shrink: 0;

        &:hover {
          color: #ef4444;
        }
      }

      &:hover .node-delete-btn {
        opacity: 1;
      }
    }

    .relation-item {
      padding: 5px 8px;
      display: flex;
      align-items: center;
      gap: 4px;
      transition: background 0.2s;

      &:hover {
        background: var(--chat-surface-glass-hover);

        .relation-delete-btn {
          opacity: 1;
        }
      }

      .relation-visual {
        flex: 1;
        display: flex;
        align-items: center;
        gap: 3px;
        font-size: 10px;
        min-width: 0;
        cursor: pointer;
        padding: 2px 4px;
        border-radius: var(--chat-radius-sm);
        transition: background 0.2s;

        &:hover {
          background: var(--chat-surface-glass-hover);
        }
      }

      .relation-node {
        color: var(--chat-text-primary);
        font-weight: 500;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 50px;
      }

      .relation-line {
        flex: 1;
        height: 1px;
        background: linear-gradient(90deg, var(--chat-border-subtle), var(--chat-accent-purple), var(--chat-border-subtle));
        border-radius: 1px;
        min-width: 8px;
      }

      .relation-type-badge {
        font-size: 9px;
        padding: 1px 4px;
        border-radius: 6px;
        background: var(--chat-accent-purple);
        color: #fff;
        white-space: nowrap;
      }

      .relation-delete-btn {
        opacity: 0;
        font-size: 12px;
        color: var(--chat-text-tertiary);
        transition: all 0.2s;
        flex-shrink: 0;

        &:hover {
          color: #ef4444;
        }
      }
    }
  }
}

.node-detail {
  .detail-row {
    display: flex;
    padding: 8px 0;
    border-bottom: 1px solid var(--chat-border-subtle);

    &:last-child {
      border-bottom: none;
    }

    .detail-label {
      width: 80px;
      color: var(--chat-text-tertiary);
    }

    .detail-value {
      flex: 1;
      font-weight: 500;
      color: var(--chat-text-primary);
    }
  }
}

// ==================== Panel (Dialog) Styles ====================

.panel-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

:root.dark .panel-overlay {
  background: rgba(0, 0, 0, 0.6);
}

.panel-card {
  width: 400px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  background: var(--chat-bg-primary);
  border: 1px solid var(--chat-border-default);
  border-radius: var(--chat-radius-xl);
  box-shadow: var(--chat-shadow-lg);
  overflow: hidden;

  &--preview {
    width: 480px;
    max-height: 90vh;
  }
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--chat-border-subtle);

  .panel-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 600;
    color: var(--chat-text-primary);

    svg {
      width: 18px;
      height: 18px;
      color: var(--chat-accent-purple);
    }

    .preview-avatar {
      font-size: 24px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      flex-shrink: 0;
    }

    .preview-avatar-img {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      object-fit: cover;
    }
  }

  .panel-header-actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .panel-action-btn {
    width: 28px;
    height: 28px;
    border-radius: var(--chat-radius-sm);
    border: none;
    background: transparent;
    color: var(--chat-text-tertiary);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all var(--chat-transition-fast);

    svg {
      width: 16px;
      height: 16px;

      &.is-loading {
        animation: spin 1s linear infinite;
      }
    }

    &:disabled {
      cursor: not-allowed;
      opacity: 0.5;
    }

    &:hover {
      background: var(--chat-bg-hover);
      color: var(--chat-accent-purple);
    }
  }
}

.panel-close {
  width: 28px;
  height: 28px;
  border-radius: var(--chat-radius-sm);
  border: none;
  background: var(--chat-bg-elevated);
  color: var(--chat-text-tertiary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--chat-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: var(--chat-bg-hover);
    color: var(--chat-text-primary);
  }
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--chat-border-default);
    border-radius: 2px;
  }
}

.panel-body--preview {
  padding: 16px 20px;
  overflow-y: auto;
  max-height: calc(90vh - 120px);
}

.panel-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--chat-border-subtle);
}

// Fields
.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--chat-text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;

  .field-value {
    font-weight: 400;
    color: var(--chat-accent-purple);
    font-family: var(--chat-font-mono);
    font-size: 11px;
  }

  .field-optional {
    font-weight: 400;
    color: var(--chat-text-muted);
    font-size: 11px;
  }
}

.field-hint {
  font-size: 11px;
  color: var(--chat-text-muted);
  margin-top: 2px;
}

.field-input,
.field-select,
.field-textarea {
  width: 100%;
  background: var(--chat-bg-elevated);
  border: 1px solid var(--chat-border-subtle);
  border-radius: var(--chat-radius-md);
  padding: 8px 12px;
  font-family: var(--chat-font-body);
  font-size: 13px;
  color: var(--chat-text-primary);
  outline: none;
  transition: all var(--chat-transition-fast);
  box-sizing: border-box;

  &::placeholder {
    color: var(--chat-text-muted);
  }

  &:focus {
    border-color: var(--chat-accent-purple);
    box-shadow: 0 0 0 2px rgba(139, 92, 246, 0.15);
  }
}

.field-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23989898' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  padding-right: 30px;
  cursor: pointer;

  option {
    background: var(--chat-bg-elevated);
    color: var(--chat-text-primary);
  }
}

.field-textarea {
  resize: vertical;
  min-height: 60px;
  line-height: 1.5;
}

// Type chips
.type-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.type-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--chat-radius-full);
  border: 1px solid var(--chat-border-subtle);
  background: var(--chat-bg-elevated);
  font-size: 12px;
  font-weight: 500;
  color: var(--chat-text-secondary);
  cursor: pointer;
  transition: all var(--chat-transition-fast);
  font-family: var(--chat-font-body);

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  &:hover {
    border-color: var(--chat-border-default);
    background: var(--chat-bg-hover);
  }

  &--active {
    border-color: var(--chat-accent-purple);
    background: rgba(139, 92, 246, 0.1);
    color: var(--chat-accent-purple);

    &.type-chip--rel {
      border-color: var(--chat-accent-cyan);
      background: rgba(6, 182, 212, 0.1);
      color: var(--chat-accent-cyan);
    }
  }
}

// Importance bar
.importance-bar {
  display: flex;
  gap: 4px;
}

.importance-dot {
  flex: 1;
  height: 32px;
  border-radius: var(--chat-radius-sm);
  border: 1px solid var(--chat-border-subtle);
  background: var(--chat-bg-elevated);
  font-size: 12px;
  font-weight: 600;
  color: var(--chat-text-muted);
  cursor: pointer;
  transition: all var(--chat-transition-fast);
  font-family: var(--chat-font-body);

  &:hover {
    border-color: var(--chat-border-default);
    background: var(--chat-bg-hover);
  }

  &--active {
    background: var(--chat-gradient-primary);
    border-color: transparent;
    color: #fff;

    &:hover {
      opacity: 0.9;
    }
  }

  .importance-bar--10 & {
    height: 28px;
    font-size: 10px;
  }
}

// Buttons
.btn {
  padding: 8px 18px;
  border-radius: var(--chat-radius-md);
  font-size: 13px;
  font-weight: 600;
  font-family: var(--chat-font-body);
  cursor: pointer;
  transition: all var(--chat-transition-fast);
  border: 1px solid var(--chat-border-subtle);
  background: var(--chat-bg-elevated);
  color: var(--chat-text-secondary);

  &--ghost {
    &:hover {
      background: var(--chat-bg-hover);
      color: var(--chat-text-primary);
    }
  }

  &--primary {
    background: var(--chat-gradient-primary);
    border: none;
    color: var(--chat-accent-text);

    &:hover {
      opacity: 0.9;
      box-shadow: var(--chat-shadow-glow-purple);
    }
  }
}

// ==================== 节点预览弹窗样式 ====================

.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

:root.dark .preview-overlay {
  background: rgba(0, 0, 0, 0.7);
}

.preview-modal {
  width: 100%;
  max-width: 520px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  background: var(--chat-bg-primary);
  border: 1px solid var(--chat-border-default);
  border-radius: var(--chat-radius-xl);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}

// 头部
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--chat-border-subtle);
  flex-shrink: 0;
}

.preview-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.preview-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--chat-bg-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.preview-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-avatar__emoji {
  font-size: 22px;
  line-height: 1;
}

.preview-title-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.preview-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--chat-text-primary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-type-badge {
  display: inline-flex;
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: var(--chat-radius-full);
  font-size: 10px;
  font-weight: 500;
  color: #fff;
}

.preview-header__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.preview-action-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--chat-radius-sm);
  border: none;
  background: transparent;
  color: var(--chat-text-tertiary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--chat-transition-fast);

  svg {
    width: 16px;
    height: 16px;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }

  &:hover:not(:disabled) {
    background: var(--chat-bg-hover);
    color: var(--chat-accent-purple);
  }
}

.preview-close-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--chat-radius-sm);
  border: none;
  background: var(--chat-bg-elevated);
  color: var(--chat-text-tertiary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--chat-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: var(--chat-bg-hover);
    color: var(--chat-text-primary);
  }
}

// 内容区
.preview-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--chat-border-default);
    border-radius: 3px;
  }
}

// 图片轮播
.preview-gallery {
  border-radius: var(--chat-radius-lg);
  overflow: hidden;
  background: transparent;
  min-height: 280px;
}

.gallery-viewport {
  position: relative;
  width: 100%;
  height: 280px;
  overflow: hidden;
}

.gallery-track {
  display: flex;
  width: 100%;
  height: 100%;
  transition: transform 0.3s ease-out;
}

.gallery-slide {
  flex: 0 0 100%;
  width: 100%;
  height: 100%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
}

.gallery-image {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  object-fit: contain;

  &.gallery-image--loading {
    opacity: 0;
  }
}

.gallery-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--chat-bg-elevated);
  color: var(--chat-text-muted);
  font-size: 12px;

  svg {
    width: 32px;
    height: 32px;
    stroke: var(--chat-text-muted);
  }

  &--error svg {
    stroke: var(--chat-error, #ef4444);
  }
}

.gallery-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.95);
  color: var(--chat-text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--chat-transition-fast);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);

  svg {
    width: 18px;
    height: 18px;
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  &:hover:not(:disabled) {
    background: #fff;
    transform: translateY(-50%) scale(1.05);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  }

  &--prev {
    left: 12px;
  }

  &--next {
    right: 12px;
  }
}

.gallery-indicators {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  background: var(--chat-bg-elevated);
  border-top: 1px solid var(--chat-border-subtle);
}

.gallery-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: none;
  background: var(--chat-border-default);
  cursor: pointer;
  transition: all var(--chat-transition-fast);
  padding: 0;

  &:hover {
    background: var(--chat-text-muted);
  }

  &--active {
    width: 24px;
    border-radius: 4px;
    background: var(--chat-accent-purple);
  }
}

// 信息区块
.preview-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-section__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--chat-accent-purple);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.preview-section__content {
  font-size: 13px;
  line-height: 1.7;
  color: var(--chat-text-primary);
  white-space: pre-wrap;
  word-break: break-word;

  a {
    color: var(--sf-accent, #0066ff);
    text-decoration: none;
    font-weight: 500;

    &:hover {
      text-decoration: underline;
    }
  }

  strong {
    font-weight: 600;
  }
}

// 参考资料
.preview-references {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-reference-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--chat-bg-elevated);
  border: 1px solid var(--chat-border-subtle);
  border-radius: var(--chat-radius-md);
  font-size: 12px;
  color: var(--chat-text-secondary);
  text-decoration: none;
  transition: all var(--chat-transition-fast);
  word-break: break-all;

  svg {
    width: 14px;
    height: 14px;
    flex-shrink: 0;
    color: var(--chat-accent-purple);
  }

  &:hover {
    border-color: var(--chat-accent-purple);
    background: rgba(139, 92, 246, 0.05);
    color: var(--chat-text-primary);
  }
}

.preview-info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.preview-info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;

  &--full {
    grid-column: 1 / -1;
  }
}

.preview-info-label {
  font-size: 11px;
  color: var(--chat-text-muted);
}

.preview-info-value {
  font-size: 13px;
  color: var(--chat-text-primary);
  line-height: 1.4;
}

.preview-keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.preview-keyword {
  padding: 3px 8px;
  border-radius: var(--chat-radius-full);
  background: var(--chat-bg-elevated);
  border: 1px solid var(--chat-border-subtle);
  font-size: 11px;
  color: var(--chat-text-secondary);
}

.preview-importance {
  display: flex;
  gap: 2px;
}

.importance-star {
  font-size: 14px;
  color: var(--chat-border-default);

  &--active {
    color: #f59e0b;
  }
}

// Transition
.panel-fade-enter-active {
  transition: opacity 0.2s ease;

  .panel-card,
  .preview-modal {
    animation: panelSlideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  }
}

.panel-fade-leave-active {
  transition: opacity 0.15s ease;
}

.panel-fade-enter-from,
.panel-fade-leave-to {
  opacity: 0;
}

// 动画
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.is-spinning {
  animation: spin 1s linear infinite;
}

@keyframes panelSlideUp {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

// Mobile styles
@media (max-width: 768px) {
  .knowledge-graph-page {
    &__header {
      padding: 16px;
      flex-wrap: wrap;
      gap: 12px;

      &__title {
        font-size: 18px;
        width: 100%;
      }
    }

    &__stats {
      padding: 12px 16px;
      gap: 8px;
      overflow-x: auto;

      &__item {
        white-space: nowrap;
        font-size: 12px;
      }
    }

    &__actions {
      padding: 12px 16px;
      gap: 8px;
      flex-wrap: wrap;
    }
  }

  .search-bar {
    flex: 1;
    min-width: 150px;

    &__input {
      padding: 8px 12px 8px 32px;
      font-size: 13px;
    }
  }

  .action-btn {
    padding: 6px 10px;
    font-size: 12px;

    svg {
      width: 14px;
      height: 14px;
    }
  }

  .graph-container {
    height: calc(100vh - 280px);
  }

  .side-panel {
    width: 100%;
    max-width: 100%;
    right: 0;
    border-radius: 16px 16px 0 0;
    max-height: 60vh;
  }

  .node-detail {
    padding: 16px;

    &__title {
      font-size: 16px;
    }

    &__section {
      margin-top: 12px;
    }
  }

  .panel-card {
    width: calc(100% - 32px);
    max-width: 100%;
    margin: 16px auto;
    border-radius: 12px;

    .panel-header {
      padding: 12px 16px;

      .panel-title {
        font-size: 15px;
      }
    }

    .panel-body {
      padding: 12px 16px;
      max-height: calc(100vh - 250px);
      overflow-y: auto;
    }

    .panel-footer {
      padding: 12px 16px;
    }
  }

  .field-group {
    .field-label {
      font-size: 12px;
    }

    .field-input,
    .field-select,
    .field-textarea {
      padding: 8px 12px;
      font-size: 14px;
    }
  }

  .form-actions {
    flex-direction: column;

    .btn {
      width: 100%;
      justify-content: center;
    }
  }

  .preview-overlay {
    padding: 10px;
  }

  .preview-modal {
    width: calc(100% - 20px);
    max-width: calc(100% - 20px);
    max-height: 90vh;
    border-radius: 12px;

    .preview-header {
      padding: 12px 16px;
      flex-wrap: wrap;
      gap: 8px;

      &__left {
        width: 100%;
        gap: 10px;
      }

      &__actions {
        width: 100%;
        justify-content: flex-end;
      }
    }

    .preview-avatar {
      width: 36px;
      height: 36px;
    }

    .preview-title-group {
      flex-wrap: wrap;

      .preview-title {
        font-size: 16px;
        width: 100%;
      }

      .preview-type-badge {
        font-size: 10px;
        padding: 2px 8px;
      }
    }

    .preview-action-btn,
    .preview-close-btn {
      width: 32px;
      height: 32px;

      svg {
        width: 16px;
        height: 16px;
      }
    }

    .preview-content {
      padding: 16px;
      max-height: calc(90vh - 120px);
      overflow-y: auto;
    }

    .preview-gallery {
      .gallery-viewport {
        height: 200px;
      }

      .gallery-nav {
        width: 32px;
        height: 32px;

        svg {
          width: 16px;
          height: 16px;
        }
      }
    }

    .preview-section {
      padding: 12px 0;

      &__title {
        font-size: 13px;
        margin-bottom: 8px;
      }
    }

    .preview-info-grid {
      gap: 8px;

      .preview-info-item {
        padding: 8px;

        .preview-info-label {
          font-size: 11px;
        }

        .preview-info-value {
          font-size: 13px;
        }
      }
    }

    .preview-reference-link {
      padding: 8px 10px;
      font-size: 12px;
    }
  }
}
</style>
