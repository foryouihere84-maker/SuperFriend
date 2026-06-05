<template>
  <el-dialog
    v-model="visible"
    :title="fileName"
    :width="dialogWidth"
    destroy-on-close
    @close="handleClose"
  >
    <!-- 加载中 -->
    <div v-if="loading" class="preview-loading">
      <el-icon class="is-loading" :size="48">
        <Loading />
      </el-icon>
      <p>加载中...</p>
    </div>

    <!-- 错误提示 -->
    <div v-else-if="error" class="preview-error">
      <el-icon :size="48" color="#f56c6c">
        <WarningFilled />
      </el-icon>
      <p>{{ error }}</p>
      <el-button type="primary" @click="loadFile">重试</el-button>
    </div>

    <!-- PDF 预览 -->
    <div v-else-if="fileType === 'pdf'" class="preview-pdf">
      <iframe v-if="blobUrl" :src="blobUrl" class="pdf-viewer" />
    </div>

    <!-- 图片预览 -->
    <div v-else-if="isImage" class="preview-image">
      <el-image-viewer
        v-if="blobUrl"
        :url-list="[blobUrl]"
        :initial-index="0"
        @close="visible = false"
      />
    </div>

    <!-- 视频预览 -->
    <div v-else-if="isVideo" class="preview-video">
      <video
        v-if="blobUrl"
        ref="videoRef"
        :src="blobUrl"
        controls
        preload="metadata"
        playsinline
        class="video-player"
      />
    </div>

    <!-- 音频预览 -->
    <div v-else-if="isAudio" class="preview-audio">
      <div class="audio-cover">
        <el-icon :size="80" color="#409eff">
          <Headset />
        </el-icon>
      </div>
      <div class="audio-info">
        <h3>{{ fileName }}</h3>
        <p v-if="fileSize">{{ formatFileSize(fileSize) }}</p>
      </div>
      <audio
        ref="audioRef"
        :src="blobUrl"
        controls
        preload="metadata"
        class="audio-player"
      />
    </div>

    <!-- DOCX 预览 -->
    <div v-else-if="fileType === 'docx'" class="preview-document">
      <div v-if="documentLoading" class="document-loading">
        <el-icon class="is-loading" :size="32">
          <Loading />
        </el-icon>
        <p>正在解析文档...</p>
      </div>
      <div v-else-if="documentError" class="document-fallback">
        <p>{{ documentError }}</p>
        <el-button type="primary" @click="downloadFile">下载文件</el-button>
      </div>
      <div v-else ref="docxContainerRef" class="docx-preview-container" />
    </div>

    <!-- PPTX 预览 -->
    <div v-else-if="fileType === 'pptx'" class="preview-document">
      <div v-if="documentLoading" class="document-loading">
        <el-icon class="is-loading" :size="32">
          <Loading />
        </el-icon>
        <p>正在解析演示文稿...</p>
      </div>
      <div v-else-if="documentError" class="document-fallback">
        <p>{{ documentError }}</p>
        <el-button type="primary" @click="downloadFile">下载文件</el-button>
      </div>
      <template v-else>
        <div class="pptx-nav" v-if="pptxSlideCount > 1">
          <el-button :disabled="pptxCurrentSlide <= 1" @click="switchPptxSlide(pptxCurrentSlide - 1)" size="small">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <span class="pptx-page-info">{{ pptxCurrentSlide }} / {{ pptxSlideCount }}</span>
          <el-button :disabled="pptxCurrentSlide >= pptxSlideCount" @click="switchPptxSlide(pptxCurrentSlide + 1)" size="small">
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div ref="pptxContainerRef" class="pptx-preview-container" />
      </template>
    </div>

    <!-- XLSX 预览 -->
    <div v-else-if="isSpreadsheet" class="preview-document">
      <div v-if="documentLoading" class="document-loading">
        <el-icon class="is-loading" :size="32">
          <Loading />
        </el-icon>
        <p>正在解析表格...</p>
      </div>
      <div v-else-if="documentError" class="document-fallback">
        <p>{{ documentError }}</p>
        <el-button type="primary" @click="downloadFile">下载文件</el-button>
      </div>
      <template v-else>
        <el-tabs v-if="xlsxSheetNames.length > 1" v-model="xlsxActiveSheet" class="xlsx-tabs" @tab-change="onXlsxSheetChange">
          <el-tab-pane v-for="name in xlsxSheetNames" :key="name" :label="name" :name="name" />
        </el-tabs>
        <div class="xlsx-preview-container" v-html="xlsxHtml" />
      </template>
    </div>

    <!-- 旧版 Office 格式 (doc/xls/ppt) -->
    <div v-else-if="isLegacyOffice" class="preview-unsupported">
      <el-icon :size="64" color="#e6a23c">
        <WarningFilled />
      </el-icon>
      <p>此文件为旧版 Office 格式，暂不支持在线预览</p>
      <p class="file-meta">建议使用 Microsoft Office 或 WPS 打开</p>
      <el-button type="primary" @click="downloadFile">
        <el-icon><Download /></el-icon>
        下载文件
      </el-button>
    </div>

    <!-- 代码/文本预览 -->
    <div v-else-if="isText" class="preview-text">
      <div v-if="codeLanguage" class="code-lang-badge">{{ codeLanguage }}</div>
      <pre v-if="highlightedContent"><code class="hljs" v-html="highlightedContent"></code></pre>
      <div v-else class="text-loading">
        <el-icon class="is-loading" :size="32">
          <Loading />
        </el-icon>
      </div>
    </div>

    <!-- 不支持预览 -->
    <div v-else class="preview-unsupported">
      <el-icon :size="64" color="#909399">
        <Document />
      </el-icon>
      <p>此文件类型不支持在线预览</p>
      <p class="file-meta">
        {{ fileName }} · {{ formatFileSize(fileSize) }}
      </p>
      <el-button type="primary" @click="downloadFile">
        <el-icon><Download /></el-icon>
        下载文件
      </el-button>
    </div>

    <!-- 底部操作栏 -->
    <template #footer>
      <div class="preview-footer">
        <div class="file-info">
          <span class="file-type">{{ displayFileType }}</span>
          <span v-if="fileSize" class="file-size">{{ formatFileSize(fileSize) }}</span>
        </div>
        <div class="actions">
          <el-button v-if="canPreview" @click="downloadFile">
            <el-icon><Download /></el-icon>
            下载
          </el-button>
          <el-button type="primary" @click="visible = false">关闭</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { Loading, WarningFilled, Download, Headset, Document, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { fileDB, type FileType } from '@/utils/fileDB'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css'

const props = defineProps<{
  modelValue: boolean
  fileId: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

// 状态
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const error = ref<string>('')
const blobUrl = ref<string>('')
const fileName = ref<string>('')
const fileType = ref<FileType | null>(null)
const fileSize = ref<number>(0)

// 文档预览
const documentLoading = ref(false)
const documentError = ref<string>('')

// DOCX
const docxContainerRef = ref<HTMLElement>()

// PPTX
const pptxContainerRef = ref<HTMLElement>()
const pptxCurrentSlide = ref(1)
const pptxSlideCount = ref(0)
let pptxViewerInstance: any = null

// XLSX — 缓存每个 sheet 的 HTML
const xlsxSheetNames = ref<string[]>([])
const xlsxActiveSheet = ref('')
const xlsxHtml = ref('')
const xlsxSheetHtmlMap = ref<Record<string, string>>({})

// 文本/代码预览
const highlightedContent = ref<string>('')
const codeLanguage = ref<string>('')

// 扩展名到 hljs 语言名的映射
const EXT_TO_HLJS: Record<string, string> = {
  py: 'python', python: 'python',
  js: 'javascript', jsx: 'javascript',
  ts: 'typescript', tsx: 'typescript',
  vue: 'xml', svelte: 'xml',
  java: 'java', kotlin: 'kotlin', kt: 'kotlin', kts: 'kotlin',
  c: 'c', h: 'c', cpp: 'cpp', cc: 'cpp', cxx: 'cpp', hpp: 'cpp',
  cs: 'csharp',
  go: 'go', rs: 'rust', rb: 'ruby', php: 'php',
  swift: 'swift', scala: 'scala', clj: 'clojure', lua: 'lua',
  sql: 'sql', graphql: 'graphql', proto: 'protobuf',
  sh: 'bash', bash: 'bash', zsh: 'bash', fish: 'bash',
  ps1: 'powershell', bat: 'dos', cmd: 'dos',
  html: 'xml', htm: 'xml',
  css: 'css', scss: 'scss', sass: 'scss', less: 'less',
  json: 'json', xml: 'xml', yaml: 'yaml', yml: 'yaml', toml: 'ini',
  md: 'markdown', markdown: 'markdown',
  ini: 'ini', properties: 'properties', conf: 'nginx', cfg: 'nginx',
  csv: 'plaintext', tsv: 'plaintext', log: 'plaintext',
  dockerfile: 'dockerfile', makefile: 'makefile',
}

// 代码文件扩展名集合
const CODE_EXTENSIONS = new Set([
  'py', 'python', 'js', 'jsx', 'ts', 'tsx', 'vue', 'svelte',
  'java', 'kt', 'kts', 'c', 'h', 'cpp', 'cc', 'cxx', 'hpp', 'cs',
  'go', 'rs', 'rb', 'php', 'swift', 'scala', 'clj', 'lua',
  'sql', 'graphql', 'proto',
  'sh', 'bash', 'zsh', 'fish', 'ps1', 'bat', 'cmd',
  'css', 'scss', 'sass', 'less',
  'dockerfile', 'makefile',
])

// 文本文件扩展名集合（包含代码 + 纯文本）
const TEXT_EXTENSIONS = new Set([
  // 纯文本
  'txt', 'text', 'log', 'md', 'markdown',
  // 配置/数据
  'json', 'xml', 'yaml', 'yml', 'toml', 'ini', 'properties', 'conf', 'cfg',
  // Web
  'html', 'htm', 'css', 'scss', 'sass', 'less', 'js', 'jsx', 'ts', 'tsx', 'vue', 'svelte',
  // 编程语言
  'py', 'python', 'java', 'c', 'cpp', 'cc', 'cxx', 'h', 'hpp', 'cs',
  'go', 'rs', 'rb', 'php', 'swift', 'kt', 'kts', 'scala', 'clj', 'lua',
  'pl', 'pm', 'r', 'm', 'mm',
  // Shell
  'sh', 'bash', 'zsh', 'fish', 'ps1', 'bat', 'cmd',
  // 数据
  'csv', 'tsv', 'sql', 'graphql', 'proto',
  // 特殊文件名
  'dockerfile', 'makefile', 'cmakelists', 'readme', 'changelog', 'license', 'env',
])

// 计算属性
const dialogWidth = computed(() => {
  if (isImage.value) return '80%'
  if (isVideo.value) return '70%'
  if (fileType.value === 'pdf') return '80%'
  if (fileType.value === 'pptx') return '80%'
  if (fileType.value === 'docx') return '75%'
  if (isSpreadsheet.value) return '85%'
  return '600px'
})

const isImage = computed(() =>
  fileType.value === 'image' || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(fileType.value || '')
)

const isVideo = computed(() =>
  fileType.value === 'video' || ['mp4', 'webm', 'ogg', 'mov', 'avi'].includes(fileType.value || '')
)

const isAudio = computed(() =>
  fileType.value === 'audio' || ['mp3', 'wav', 'ogg', 'aac', 'flac', 'm4a'].includes(fileType.value || '')
)

const isSpreadsheet = computed(() =>
  ['xlsx', 'xls'].includes(fileType.value || '')
)

const isLegacyOffice = computed(() =>
  ['doc', 'ppt'].includes(fileType.value || '')
)

const isText = computed(() =>
  fileType.value === 'text' || TEXT_EXTENSIONS.has(fileType.value || '')
)

const canPreview = computed(() =>
  blobUrl.value && !error.value
)

const displayFileType = computed(() => {
  const ft = fileType.value
  if (!ft) return ''
  const map: Record<string, string> = {
    docx: 'DOCX', doc: 'DOC', xlsx: 'XLSX', xls: 'XLS',
    pptx: 'PPTX', ppt: 'PPT', pdf: 'PDF',
    image: 'IMAGE', audio: 'AUDIO', video: 'VIDEO',
    text: 'TEXT', archive: 'ARCHIVE', other: 'OTHER',
  }
  return map[ft] || ft.toUpperCase()
})

// 加载文件
const loadFile = async () => {
  if (!props.fileId) return

  loading.value = true
  error.value = ''
  blobUrl.value = ''
  documentError.value = ''
  highlightedContent.value = ''
  codeLanguage.value = ''
  xlsxSheetNames.value = []
  xlsxHtml.value = ''
  xlsxSheetHtmlMap.value = {}
  pptxSlideCount.value = 0
  pptxCurrentSlide.value = 1
  pptxViewerInstance = null

  try {
    const file = await fileDB.get(props.fileId)
    if (!file) {
      error.value = '文件不存在或已过期'
      loading.value = false
      return
    }

    fileName.value = file.fileName
    fileType.value = file.fileType
    fileSize.value = file.size

    // 获取 Blob URL
    const url = await fileDB.getBlobUrl(props.fileId)
    if (!url) {
      error.value = '无法读取文件数据'
      loading.value = false
      return
    }
    blobUrl.value = url

    // 根据文件类型加载预览
    // 先关闭 loading，让模板渲染出对应类型的容器
    loading.value = false
    await nextTick()

    if (fileType.value === 'docx') {
      await loadDocxPreview()
    } else if (fileType.value === 'pptx') {
      await loadPptxPreview()
    } else if (isSpreadsheet.value) {
      await loadXlsxPreview()
    } else if (isText.value) {
      await loadTextPreview()
    }

  } catch (err: any) {
    console.error('[FilePreview] 加载文件失败:', err)
    error.value = err.message || '加载文件失败'
    loading.value = false
  }
}

// DOCX 预览
const loadDocxPreview = async () => {
  documentLoading.value = true
  documentError.value = ''

  try {
    const blob = await fileDB.getBlob(props.fileId!)
    if (!blob) {
      documentLoading.value = false
      return
    }

    const arrayBuffer = await blob.arrayBuffer()
    const { renderAsync } = await import('docx-preview')

    // 先让容器渲染到 DOM
    documentLoading.value = false
    await nextTick()

    const container = docxContainerRef.value
    if (!container) {
      documentError.value = '渲染容器未就绪'
      return
    }

    container.innerHTML = ''
    await renderAsync(arrayBuffer, container, undefined, {
      className: 'docx-preview',
      inWrapper: true,
      ignoreWidth: false,
      ignoreHeight: false,
    })
  } catch (err: any) {
    console.warn('[FilePreview] DOCX 预览失败:', err)
    documentError.value = '文档解析失败，请下载后查看'
  }
}

// PPTX 预览 — 使用 PptxViewer
const loadPptxPreview = async () => {
  documentLoading.value = true
  documentError.value = ''

  try {
    const blob = await fileDB.getBlob(props.fileId!)
    if (!blob) {
      documentLoading.value = false
      return
    }

    const arrayBuffer = await blob.arrayBuffer()
    const { PptxViewer } = await import('@aiden0z/pptx-renderer')

    // 先让容器渲染到 DOM
    documentLoading.value = false
    await nextTick()

    const container = pptxContainerRef.value
    if (!container) {
      documentError.value = '渲染容器未就绪'
      return
    }

    container.innerHTML = ''
    pptxViewerInstance = new PptxViewer(container, {
      width: 960,
    })
    await pptxViewerInstance.open(arrayBuffer)
    pptxSlideCount.value = pptxViewerInstance.getSlideCount?.() || 1
    pptxCurrentSlide.value = 1
  } catch (err: any) {
    console.warn('[FilePreview] PPTX 预览失败:', err)
    documentError.value = '演示文稿解析失败，请下载后查看'
  }
}

// PPTX 翻页
const switchPptxSlide = async (slideNum: number) => {
  if (!pptxViewerInstance || slideNum < 1 || slideNum > pptxSlideCount.value) return
  pptxCurrentSlide.value = slideNum
  try {
    await pptxViewerInstance.preview(slideNum)
  } catch (err) {
    console.warn('[FilePreview] PPTX 翻页失败:', err)
  }
}

// XLSX 预览 — 一次性解析所有 sheet 并缓存 HTML
const loadXlsxPreview = async () => {
  documentLoading.value = true
  documentError.value = ''

  try {
    const blob = await fileDB.getBlob(props.fileId!)
    if (!blob) {
      documentLoading.value = false
      return
    }

    const arrayBuffer = await blob.arrayBuffer()
    const XLSX = await import('xlsx-js-style')

    const workbook = XLSX.read(arrayBuffer, { type: 'array' })
    xlsxSheetNames.value = workbook.SheetNames
    xlsxActiveSheet.value = workbook.SheetNames[0] || ''

    // 一次性渲染所有 sheet 的 HTML 并缓存
    const htmlMap: Record<string, string> = {}
    for (const name of workbook.SheetNames) {
      const sheet = workbook.Sheets[name]
      if (sheet) {
        htmlMap[name] = XLSX.utils.sheet_to_html(sheet, { editable: false })
      } else {
        htmlMap[name] = '<p style="text-align:center;color:#909399;">工作表为空</p>'
      }
    }
    xlsxSheetHtmlMap.value = htmlMap
    xlsxHtml.value = htmlMap[xlsxActiveSheet.value] || ''

    // 数据已就绪，关闭 loading 让模板渲染
    documentLoading.value = false
  } catch (err: any) {
    console.warn('[FilePreview] XLSX 预览失败:', err)
    documentError.value = '表格解析失败，请下载后查看'
  }
}

const onXlsxSheetChange = (name: string | number) => {
  const sheetName = String(name)
  xlsxHtml.value = xlsxSheetHtmlMap.value[sheetName] || '<p style="text-align:center;color:#909399;">工作表为空</p>'
}

// 文本/代码预览
const loadTextPreview = async () => {
  try {
    const blob = await fileDB.getBlob(props.fileId!)
    if (!blob) return

    const text = await blob.text()
    const truncated = text.length > 50000
    const content = truncated ? text.slice(0, 50000) : text

    const ext = fileName.value.split('.').pop()?.toLowerCase() || ''
    const lang = EXT_TO_HLJS[ext]

    if (lang && hljs.getLanguage(lang)) {
      codeLanguage.value = lang
      try {
        const result = hljs.highlight(content, { language: lang })
        highlightedContent.value = result.value
      } catch {
        highlightedContent.value = escapeHtml(content)
      }
    } else if (CODE_EXTENSIONS.has(ext)) {
      codeLanguage.value = ext
      try {
        const result = hljs.highlightAuto(content)
        highlightedContent.value = result.value
        if (result.language) codeLanguage.value = result.language
      } catch {
        highlightedContent.value = escapeHtml(content)
      }
    } else {
      codeLanguage.value = ''
      highlightedContent.value = escapeHtml(content)
    }

    if (truncated) {
      highlightedContent.value += '\n\n<span style="color:#e6a23c">... (内容过长，已截断显示)</span>'
    }
  } catch (err: any) {
    console.warn('[FilePreview] 文本预览失败:', err)
    highlightedContent.value = ''
  }
}

// 下载文件
const downloadFile = async () => {
  if (!props.fileId) return
  try {
    await fileDB.download(props.fileId)
  } catch (err: any) {
    console.error('[FilePreview] 下载失败:', err)
  }
}

// 关闭时清理
const handleClose = () => {
  if (blobUrl.value) {
    URL.revokeObjectURL(blobUrl.value)
    blobUrl.value = ''
  }
  documentError.value = ''
  highlightedContent.value = ''
  codeLanguage.value = ''
  xlsxSheetNames.value = []
  xlsxHtml.value = ''
  xlsxSheetHtmlMap.value = {}
  pptxViewerInstance = null
  pptxSlideCount.value = 0
}

// 工具函数
const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
}

const escapeHtml = (text: string): string => {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

// 监听文件 ID 变化
watch(() => props.fileId, (newId) => {
  if (newId && visible.value) {
    loadFile()
  }
})

// 监听对话框打开
watch(visible, (val) => {
  if (val && props.fileId) {
    loadFile()
  } else if (!val) {
    handleClose()
  }
})
</script>

<style scoped>
.preview-loading,
.preview-error,
.preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
  gap: 16px;
}

.preview-loading p,
.preview-error p,
.preview-unsupported p {
  color: #606266;
  margin: 0;
}

.file-meta {
  color: #909399;
  font-size: 14px;
}

.preview-pdf {
  height: 70vh;
}

.pdf-viewer {
  width: 100%;
  height: 100%;
  border: none;
}

.preview-image {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.preview-video {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}

.video-player {
  max-width: 100%;
  max-height: 60vh;
}

.preview-audio {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  gap: 20px;
}

.audio-cover {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.audio-info {
  text-align: center;
}

.audio-info h3 {
  margin: 0 0 8px;
  font-size: 18px;
}

.audio-info p {
  margin: 0;
  color: #909399;
}

.audio-player {
  width: 100%;
  max-width: 400px;
}

.preview-document {
  min-height: 200px;
  max-height: 70vh;
  overflow: auto;
}

.document-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  gap: 12px;
}

.document-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px;
  gap: 16px;
}

/* DOCX 预览样式 */
.docx-preview-container {
  background: #fff;
  border-radius: 8px;
  overflow: auto;
}

.docx-preview-container :deep(.docx-wrapper) {
  background: #f5f7fa !important;
  padding: 16px !important;
}

.docx-preview-container :deep(.docx-wrapper > section.docx) {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1) !important;
  margin-bottom: 16px !important;
}

/* PPTX 预览样式 */
.pptx-preview-container {
  display: flex;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 8px;
  overflow: auto;
  min-height: 400px;
}

.pptx-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 0 12px;
}

.pptx-page-info {
  font-size: 14px;
  color: #606266;
  min-width: 60px;
  text-align: center;
}

/* XLSX 预览样式 */
.xlsx-tabs {
  margin-bottom: 8px;
}

.xlsx-preview-container {
  overflow: auto;
  max-height: 60vh;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.xlsx-preview-container :deep(table) {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
}

.xlsx-preview-container :deep(th),
.xlsx-preview-container :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
  text-align: left;
  white-space: nowrap;
}

.xlsx-preview-container :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

.xlsx-preview-container :deep(tr:nth-child(even)) {
  background: #fafafa;
}

.xlsx-preview-container :deep(tr:hover) {
  background: #ecf5ff;
}

/* 代码/文本预览样式 */
.preview-text {
  max-height: 60vh;
  overflow: auto;
  background: #282c34;
  border-radius: 8px;
  padding: 16px;
  position: relative;
}

.code-lang-badge {
  position: sticky;
  top: 0;
  right: 0;
  display: inline-block;
  background: rgba(255, 255, 255, 0.15);
  color: #abb2bf;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 8px;
  text-transform: uppercase;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
}

.preview-text pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.preview-text pre code {
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.text-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.preview-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.file-info {
  display: flex;
  gap: 12px;
  color: #909399;
  font-size: 14px;
}

.file-type {
  background: #f0f2f5;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.actions {
  display: flex;
  gap: 8px;
}
</style>
