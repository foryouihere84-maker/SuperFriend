import { get, post, del } from './request'

export interface UserFileInfo {
  fileId: string
  fileName: string
  fileSize: number
  fileType: string
  mimeType: string
  sessionId: string
  uploadTime: string
  lastAccessTime: string
  isSensitive: boolean
}

export interface StorageStats {
  userId: number
  totalSizeBytes: number
  totalSizeMB: number
  fileCount: number
  maxSizeMB: number
  usedPercent: number
}

export interface FileUploadResult {
  fileName: string
  fileId: string
  fileUrl: string
  fileSize: number
  contentType: string
  type: string
  persistentFileId?: string
}

interface BackendResponse<T> {
  success: boolean
  message?: string
  data: T
}

export const fileApi = {
  getUserFiles: async (userId: number): Promise<UserFileInfo[]> => {
    const response = await get<BackendResponse<UserFileInfo[]>>(`/api/v16/files/user/${userId}`)
    if (!response || !response.success) {
      throw new Error(response?.message || '获取文件列表失败')
    }
    return response.data
  },

  getSessionFiles: async (userId: number, sessionId: string): Promise<UserFileInfo[]> => {
    const response = await get<BackendResponse<UserFileInfo[]>>(`/api/v16/files/user/${userId}/session/${sessionId}`)
    if (!response || !response.success) {
      throw new Error(response?.message || '获取会话文件列表失败')
    }
    return response.data
  },

  deleteFile: async (userId: number, fileId: string): Promise<boolean> => {
    const response = await del<BackendResponse<boolean>>(`/api/v16/files/user/${userId}/file/${fileId}`)
    if (!response || !response.success) {
      throw new Error(response?.message || '删除文件失败')
    }
    return response.data
  },

  getStorageStats: async (userId: number): Promise<StorageStats> => {
    const response = await get<BackendResponse<StorageStats>>(`/api/v16/files/user/${userId}/stats`)
    if (!response || !response.success || !response.data) {
      throw new Error(response?.message || '获取存储统计失败')
    }
    return response.data
  },

  uploadFile: async (
    file: File,
    options?: {
      type?: string
      userId?: number
      sessionId?: string
      isSensitive?: boolean
    }
  ): Promise<FileUploadResult> => {
    const formData = new FormData()
    formData.append('file', file)
    if (options?.type) formData.append('type', options.type)
    if (options?.userId) formData.append('userId', String(options.userId))
    if (options?.sessionId) formData.append('sessionId', options.sessionId)
    if (options?.isSensitive) formData.append('isSensitive', 'true')

    const response = await post<BackendResponse<FileUploadResult>>('/api/v16/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    if (!response || !response.success) {
      throw new Error(response?.message || '文件上传失败')
    }
    return response.data
  }
}
