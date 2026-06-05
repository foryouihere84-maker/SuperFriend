/**
 * 历史消息图片修复工具
 * 从 IndexedDB 查找图片/文件，用实际 dataUrl 替换消息中的引用
 * 如果消息中没有引用但 IndexedDB 中有该会话的图片，则添加保底展示
 *
 * 引用格式:
 * - 图片: ![alt](image:id) 或 ![alt](file:id:filename)
 * - 文件: [file:id:filename]
 */

import { imageDB, type StoredImage } from './imageDB'
import { fileDB, type StoredFile } from './fileDB'
import { debug } from './debug'

/**
 * 解析图片引用
 * 格式: ![alt](image:id) 或 ![alt](file:id:filename)
 */
function parseImageReference(content: string): Array<{
  fullMatch: string
  reference: string  // 例如 "image:abc123" 或 "file:abc123:filename.pdf"
  alt: string
  index: number
}> {
  const results: Array<{
    fullMatch: string
    reference: string
    alt: string
    index: number
  }> = []

  // 匹配 ![alt](image:id) 或 ![alt](file:id) 格式
  const regex = /!\[([^\]]*)\]\((image:[^)]+|file:[^)]+)\)/g
  let match

  while ((match = regex.exec(content)) !== null) {
    const alt = match[1]
    const reference = match[2]
    results.push({
      fullMatch: match[0],
      reference: reference,
      alt: alt,
      index: match.index
    })
  }

  return results
}

/**
 * 解析文件引用
 * 格式: [file:id:filename]
 */
function parseFileReference(content: string): Array<{
  fullMatch: string
  reference: string  // 例如 "file:abc123:filename.pdf"
  filename: string
  index: number
}> {
  const results: Array<{
    fullMatch: string
    reference: string
    filename: string
    index: number
  }> = []

  // 匹配 [file:id:filename] 格式
  const regex = /\[file:([^:]+):([^\]]+)\]/g
  let match

  while ((match = regex.exec(content)) !== null) {
    const id = match[1]
    const filename = match[2]
    results.push({
      fullMatch: match[0],
      reference: `file:${id}:${filename}`,
      filename: filename,
      index: match.index
    })
  }

  return results
}

/**
 * 从引用中提取 ID 和类型
 * 返回: { type: 'image' | 'file', id: string }
 */
function parseReference(ref: string): { type: 'image' | 'file'; id: string } | null {
  if (ref.startsWith('image:')) {
    return { type: 'image', id: ref.substring(6) }
  }
  if (ref.startsWith('file:')) {
    // file:abc123:filename.pdf 格式
    const parts = ref.substring(5).split(':')
    return { type: 'file', id: parts[0] }
  }
  return null
}

/**
 * 根据 ID 从 IndexedDB 查找图片
 */
async function findImageById(id: string): Promise<StoredImage | null> {
  try {
    return await imageDB.get(id)
  } catch (error) {
    console.warn('[ImageFix] 查找图片失败:', error)
    return null
  }
}

/**
 * 根据 ID 从 IndexedDB 查找文件
 */
async function findFileById(id: string): Promise<StoredFile | null> {
  try {
    return await fileDB.get(id)
  } catch (error) {
    console.warn('[ImageFix] 查找文件失败:', error)
    return null
  }
}

/**
 * 修复单条消息中的引用
 */
async function fixMessageReferences(
  content: string,
  _sessionId: string
): Promise<string> {
  if (!content) return content

  let fixedContent = content

  // 处理图片引用
  const imageRefs = parseImageReference(content)
  for (const ref of imageRefs) {
    const parsed = parseReference(ref.reference)
    if (!parsed) continue

    let dataUrl: string | null = null

    if (parsed.type === 'image') {
      const image = await findImageById(parsed.id)
      dataUrl = image?.dataUrl || null
    } else if (parsed.type === 'file') {
      const file = await findFileById(parsed.id)
      dataUrl = file?.dataUrl || null
    }

    if (dataUrl) {
      // 替换引用为实际 dataUrl
      fixedContent = fixedContent.replace(
        ref.fullMatch,
        `![${ref.alt}](${dataUrl})`
      )
      debug.log(`[ImageFix] 替换图片引用 ${ref.reference} -> dataUrl (长度: ${dataUrl.length})`)
    } else {
      // 无法找到，替换为提示
      debug.log(`[ImageFix] 未找到图片 ${ref.reference}，显示提示`)
      fixedContent = fixedContent.replace(
        ref.fullMatch,
        `![${ref.alt}](图片已失效，请刷新页面重新生成)`
      )
    }
  }

  // 处理文件引用
  const fileRefs = parseFileReference(content)
  for (const ref of fileRefs) {
    const parsed = parseReference(ref.reference)
    if (!parsed) continue

    const file = await findFileById(parsed.id)

    if (file) {
      // 根据文件类型选择图标
      const fileIcon = getFileIcon(file.fileType)
      fixedContent = fixedContent.replace(
        ref.fullMatch,
        `\n\n${fileIcon} **[${file.fileName}](${file.dataUrl})**\n\n`
      )
      debug.log(`[ImageFix] 替换文件引用 ${ref.reference} -> dataUrl`)
    } else {
      // 无法找到，替换为提示
      debug.log(`[ImageFix] 未找到文件 ${ref.reference}，显示提示`)
      fixedContent = fixedContent.replace(
        ref.fullMatch,
        `\n\n📎 **[${ref.filename}](文件已失效，缓存可能已清除)**\n\n`
      )
    }
  }

  return fixedContent
}

/**
 * 为消息添加保底图片展示
 * 如果消息中没有图片引用但 IndexedDB 中有该会话的图片，则添加展示
 */
async function addFallbackImagesForMessage(
  msg: { content: string; role: string; id: string },
  _sessionId: string,
  sessionImages: StoredImage[]
): Promise<{ content: string; hasFallback: boolean }> {
  // 只对 assistant 角色的消息添加保底图片
  if (msg.role !== 'assistant') {
    return { content: msg.content, hasFallback: false }
  }

  // 检查是否已经有图片引用
  const existingImageRefs = parseImageReference(msg.content)
  const existingFileRefs = parseFileReference(msg.content)

  // 如果已经有图片/文件引用，不添加保底
  if (existingImageRefs.length > 0 || existingFileRefs.length > 0) {
    return { content: msg.content, hasFallback: false }
  }

  // 如果该会话没有图片，不添加保底
  if (sessionImages.length === 0) {
    return { content: msg.content, hasFallback: false }
  }

  debug.log(`[ImageFix] 消息 ${msg.id} 没有图片引用，但 IndexedDB 中有 ${sessionImages.length} 张图片，添加保底展示`)

  // 构建保底图片展示
  const fallbackImages = sessionImages.map(img => {
    const timestamp = new Date(img.createdAt).toLocaleTimeString()
    return `![图片 ${timestamp}](${img.dataUrl})`
  }).join('\n\n')

  const warningText = `\n\n---\n⚠️ **以下图片来自浏览器缓存，如果显示异常请刷新页面重新生成**\n\n`

  return {
    content: msg.content + warningText + fallbackImages,
    hasFallback: true
  }
}

/**
 * 修复历史消息中的图片/文件引用
 * 从 IndexedDB 查找实际的 dataUrl 并替换引用
 * 如果消息中没有引用但有保底图片，则添加展示
 */
export async function fixHistoryImages(
  messages: Array<{ content: string; role: string; id: string }>,
  sessionId: string
): Promise<void> {
  debug.log('[ImageFix] ========== 开始修复 ==========')
  debug.log('[ImageFix] 会话ID:', sessionId, '消息数:', messages.length)

  // 从 IndexedDB 获取该会话的所有图片
  let sessionImages: StoredImage[] = []
  let sessionFiles: StoredFile[] = []

  try {
    sessionImages = await imageDB.getBySession(sessionId)
    sessionFiles = await fileDB.getBySession(sessionId)
    debug.log(`[ImageFix] IndexedDB 查询结果: 图片 ${sessionImages.length}, 文件 ${sessionFiles.length}`)
  } catch (error) {
    console.warn('[ImageFix] IndexedDB 查询失败:', error)
  }

  // 检查是否有包含引用的消息
  let hasReferences = false
  let imageRefCount = 0
  let fileRefCount = 0

  for (let i = 0; i < messages.length; i++) {
    const msg = messages[i]
    if (!msg.content) continue

    const imageRefs = parseImageReference(msg.content)
    const fileRefs = parseFileReference(msg.content)

    imageRefCount += imageRefs.length
    fileRefCount += fileRefs.length

    if (imageRefs.length > 0 || fileRefs.length > 0) {
      hasReferences = true
    }
  }

  // 如果有引用，先修复引用
  if (hasReferences) {
    for (let i = 0; i < messages.length; i++) {
      const msg = messages[i]
      if (!msg.content) continue

      const imageRefs = parseImageReference(msg.content)
      const fileRefs = parseFileReference(msg.content)

      if (imageRefs.length === 0 && fileRefs.length === 0) continue

      const fixedContent = await fixMessageReferences(msg.content, sessionId)

      if (fixedContent !== msg.content) {
        messages[i] = { ...msg, content: fixedContent }
      }
    }
  }

  // 保底机制：如果没有引用但有图片，添加保底展示
  if (!hasReferences && sessionImages.length > 0) {
    for (let i = 0; i < messages.length; i++) {
      const msg = messages[i]

      const { content: fixedContent, hasFallback } = await addFallbackImagesForMessage(
        msg,
        sessionId,
        sessionImages
      )

      if (hasFallback) {
        messages[i] = { ...msg, content: fixedContent }
      }
    }
  }
}

/**
 * 检查消息中是否有未解析的引用
 */
export function hasUnresolvedReferences(content: string): boolean {
  if (!content) return false

  const imageRefs = parseImageReference(content)
  const fileRefs = parseFileReference(content)

  return imageRefs.length > 0 || fileRefs.length > 0
}

/**
 * 根据文件类型获取图标
 */
function getFileIcon(fileType: string): string {
  switch (fileType) {
    // Office文档
    case 'pdf': return '📄'
    case 'docx': return '📝'
    case 'xlsx': return '📊'
    case 'pptx': return '📽️'
    // 媒体文件
    case 'image': return '🖼️'
    case 'audio': return '🎵'
    case 'video': return '🎬'
    // 其他类型
    case 'archive': return '📦'
    case 'text': return '📃'
    default: return '📎'
  }
}
