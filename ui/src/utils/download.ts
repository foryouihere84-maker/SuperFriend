/**
 * 文件下载工具
 */

export const downloadDataUrl = (dataUrl: string, filename: string): void => {
  if (!dataUrl) {
    console.error('[Download] dataUrl 为空')
    return
  }

  const link = document.createElement('a')
  link.href = dataUrl
  link.download = filename || `file-${Date.now()}`
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

export const downloadBlob = (blob: Blob, filename: string): void => {
  if (!blob) {
    console.error('[Download] blob 为空')
    return
  }

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || `file-${Date.now()}`
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

export const downloadImage = (dataUrl: string, prompt?: string): void => {
  if (!dataUrl) {
    console.error('[Download] 图片 dataUrl 为空')
    return
  }

  const timestamp = Date.now()
  let filename: string

  if (prompt && prompt.trim()) {
    const safePrompt = prompt
      .slice(0, 30)
      .replace(/[<>:"/\\|?*\x00-\x1F]/g, '_')
      .replace(/\s+/g, '-')
      .trim()
    filename = `ai-${safePrompt}-${timestamp}.png`
  } else {
    filename = `ai-image-${timestamp}.png`
  }

  downloadDataUrl(dataUrl, filename)
}

export const downloadBase64 = (base64: string, filename: string, mimeType: string = 'image/png'): void => {
  if (!base64) {
    console.error('[Download] base64 为空')
    return
  }

  const dataUrl = base64.startsWith('data:')
    ? base64
    : `data:${mimeType};base64,${base64}`

  downloadDataUrl(dataUrl, filename)
}
