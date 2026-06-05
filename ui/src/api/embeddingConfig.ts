import { get, post, put, del } from './request'

const API_BASE = '/api/v1/embedding-config'

export interface EmbeddingModelConfigVO {
  configId: string
  name: string
  provider: string
  apiUrl: string
  apiKeyMasked: string
  modelId: string
  dimensions: number
  maxInputTokens: number
  isDefault: boolean
  isEnabled: boolean
  sortOrder: number
}

export interface EmbeddingModelConfigDTO {
  configId?: string
  name: string
  provider: string
  apiUrl: string
  apiKey: string
  modelId: string
  dimensions?: number
  maxInputTokens?: number
  isDefault?: boolean
  isEnabled?: boolean
  sortOrder?: number
}

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export const getAllEmbeddingConfigs = async (): Promise<EmbeddingModelConfigVO[]> => {
  const response = await get<ApiResponse<EmbeddingModelConfigVO[]>>(`${API_BASE}/all`)
  return response.data || []
}

export const getEnabledEmbeddingConfigs = async (): Promise<EmbeddingModelConfigVO[]> => {
  const response = await get<ApiResponse<EmbeddingModelConfigVO[]>>(`${API_BASE}/enabled`)
  return response.data || []
}

export const getEmbeddingConfig = async (configId: string): Promise<EmbeddingModelConfigVO> => {
  const response = await get<ApiResponse<EmbeddingModelConfigVO>>(`${API_BASE}/${configId}`)
  return response.data
}

export const createEmbeddingConfig = async (data: EmbeddingModelConfigDTO): Promise<EmbeddingModelConfigVO> => {
  const response = await post<ApiResponse<EmbeddingModelConfigVO>>(`${API_BASE}`, data)
  return response.data
}

export const updateEmbeddingConfig = async (configId: string, data: EmbeddingModelConfigDTO): Promise<EmbeddingModelConfigVO> => {
  const response = await put<ApiResponse<EmbeddingModelConfigVO>>(`${API_BASE}/${configId}`, data)
  return response.data
}

export const deleteEmbeddingConfig = async (configId: string): Promise<void> => {
  await del(`${API_BASE}/${configId}`)
}

export const setDefaultEmbeddingConfig = async (configId: string): Promise<void> => {
  await put(`${API_BASE}/${configId}/default`)
}

export const refreshEmbeddingCache = async (): Promise<{ success: boolean; message: string }> => {
  const response = await post<ApiResponse<{ success: boolean; message: string }>>(`${API_BASE}/refresh`)
  return response.data
}
