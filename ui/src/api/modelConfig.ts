import { get, post, put, del } from './request'

const API_BASE = '/api/v16/model-config'

export interface AIModelConfigVO {
  configId: string
  name: string
  provider: string
  apiUrl: string
  apiKeyMasked: string
  modelId: string
  maxTokens: number | null
  temperature: number | null
  isDefault: boolean
  isEnabled: boolean
  sortOrder: number
  isSystem: boolean
}

export interface AIModelConfigDTO {
  configId?: string
  name: string
  provider: string
  apiUrl: string
  apiKey: string
  modelId: string
  maxTokens?: number
  temperature?: number
  isDefault?: boolean
  isEnabled?: boolean
  sortOrder?: number
  extraParams?: string
}

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export const getAvailableModels = async (userId?: number): Promise<AIModelConfigVO[]> => {
  const params = userId ? `?userId=${userId}` : ''
  const response = await get<ApiResponse<AIModelConfigVO[]>>(`${API_BASE}/available${params}`)
  return response.data || []
}

export const getUserModels = async (userId: number): Promise<AIModelConfigVO[]> => {
  const response = await get<ApiResponse<AIModelConfigVO[]>>(`${API_BASE}/user?userId=${userId}`)
  return response.data || []
}

export const getModel = async (configId: string, userId?: number): Promise<AIModelConfigVO> => {
  const params = userId ? `?userId=${userId}` : ''
  const response = await get<ApiResponse<AIModelConfigVO>>(`${API_BASE}/${configId}${params}`)
  return response.data
}

export const createModel = async (data: AIModelConfigDTO, userId: number): Promise<AIModelConfigVO> => {
  const response = await post<ApiResponse<AIModelConfigVO>>(`${API_BASE}?userId=${userId}`, data)
  return response.data
}

export const updateModel = async (configId: string, data: AIModelConfigDTO, userId: number): Promise<AIModelConfigVO> => {
  const response = await put<ApiResponse<AIModelConfigVO>>(`${API_BASE}/${configId}?userId=${userId}`, data)
  return response.data
}

export const deleteModel = async (configId: string, userId: number): Promise<void> => {
  await del(`${API_BASE}/${configId}?userId=${userId}`)
}

export const setDefaultModel = async (configId: string, userId: number): Promise<void> => {
  await put(`${API_BASE}/${configId}/default?userId=${userId}`)
}

export const testConnection = async (data: AIModelConfigDTO): Promise<{ success: boolean; message: string }> => {
  const response = await post<ApiResponse<{ success: boolean; message: string }>>(`${API_BASE}/test`, data)
  return response.data
}

export const testConnectionByConfigId = async (configId: string): Promise<{ success: boolean; message: string }> => {
  const response = await get<ApiResponse<{ success: boolean; message: string }>>(`${API_BASE}/test/${configId}`)
  return response.data
}
