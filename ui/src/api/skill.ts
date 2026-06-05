import { get, post, put, del } from './request'
import type { ApiResponse } from '@/types/api'
import type {
  Skill,
  SkillExecutionRequest,
  SkillExecutionResponse,
  SkillConfig,
  SkillStats,
  SkillSuggestionRequest,
  SkillsPathConfig,
  SkillRecommendation,
  AIIntentAnalysis,
  SkillImportResult
} from '@/types/skill'
import { API_PATHS } from '@/config/api'

const API_BASE = API_PATHS.SKILL

export const getAllSkills = async (): Promise<ApiResponse<Skill[]>> => {
  return get(`${API_BASE}`)
}

export const getSkill = async (name: string): Promise<ApiResponse<Skill>> => {
  return get(`${API_BASE}/${name}`)
}

export const getCategories = async (): Promise<ApiResponse<string[]>> => {
  return get(`${API_BASE}/categories`)
}

export const getSkillsByCategory = async (category: string): Promise<ApiResponse<Skill[]>> => {
  return get(`${API_BASE}/category/${category}`)
}

export const getTags = async (): Promise<ApiResponse<string[]>> => {
  return get(`${API_BASE}/tags`)
}

export const getSkillsByTag = async (tag: string): Promise<ApiResponse<Skill[]>> => {
  return get(`${API_BASE}/tag/${tag}`)
}

export const searchSkills = async (keyword: string): Promise<ApiResponse<Skill[]>> => {
  return get(`${API_BASE}/search?keyword=${encodeURIComponent(keyword)}`)
}

export const suggestSkills = async (userRequest: string): Promise<ApiResponse<Skill[]>> => {
  const request: SkillSuggestionRequest = { userRequest }
  return post(`${API_BASE}/suggest`, request)
}

export const executeSkill = async (
  request: SkillExecutionRequest
): Promise<ApiResponse<SkillExecutionResponse>> => {
  return post(`${API_BASE}/execute`, request)
}

export const createSkill = async (
  config: SkillConfig,
  userId?: number
): Promise<ApiResponse<string>> => {
  const params = new URLSearchParams()
  if (userId) {
    params.append('userId', userId.toString())
  }
  return post(`${API_BASE}/create?${params.toString()}`, config)
}

export const deleteSkill = async (name: string, userId?: number): Promise<ApiResponse<string>> => {
  const params = new URLSearchParams()
  if (userId) {
    params.append('userId', userId.toString())
  }
  const queryString = params.toString()
  return del(`${API_BASE}/${name}${queryString ? '?' + queryString : ''}`)
}

export const updateSkill = async (
  config: SkillConfig,
  skillName: string,
  userId?: number
): Promise<ApiResponse<string>> => {
  const params = new URLSearchParams()
  params.append('skillName', skillName)
  if (userId) {
    params.append('userId', userId.toString())
  }
  return post(`${API_BASE}/update?${params.toString()}`, config)
}

export const updateSkillScripts = async (
  skillName: string,
  scriptType: string,
  scriptContent: string,
  userId?: number
): Promise<ApiResponse<string>> => {
  const params = new URLSearchParams()
  params.append('skillName', skillName)
  params.append('scriptType', scriptType)
  if (userId) {
    params.append('userId', userId.toString())
  }
  return post(`${API_BASE}/update/scripts?${params.toString()}`, scriptContent, {
    headers: { 'Content-Type': 'text/plain' }
  })
}

export const reloadSkills = async (): Promise<ApiResponse<string>> => {
  return post(`${API_BASE}/reload`)
}

export const getSkillStats = async (): Promise<ApiResponse<SkillStats>> => {
  return get(`${API_BASE}/stats`)
}

export const getSkillsPaths = async (): Promise<ApiResponse<SkillsPathConfig>> => {
  return get(`${API_BASE}/paths`)
}

export const updateSkillsPaths = async (
  config: SkillsPathConfig
): Promise<ApiResponse<string>> => {
  return post(`${API_BASE}/paths`, config)
}

export const recommendSkills = async (message: string): Promise<ApiResponse<SkillRecommendation[]>> => {
  return post(`${API_BASE}/recommend`, { message })
}

export const analyzeIntent = async (message: string): Promise<ApiResponse<AIIntentAnalysis>> => {
  return post(`${API_BASE}/analyze-intent`, { message })
}

export const updateSkillSelection = async (
  name: string,
  isSelected: boolean,
  userId?: number
): Promise<ApiResponse<void>> => {
  const body: Record<string, unknown> = { isSelected }
  if (userId) {
    body.userId = userId
  }
  return put(`${API_BASE}/${encodeURIComponent(name)}/selection`, body)
}

export const getUserSelectedSkills = async (userId?: number): Promise<ApiResponse<Skill[]>> => {
  const params = new URLSearchParams()
  if (userId) {
    params.append('userId', userId.toString())
  }
  const queryString = params.toString()
  return get(`${API_BASE}/user/selected${queryString ? '?' + queryString : ''}`)
}

export interface ResourceUploadResult {
  resourceName: string
  resourceType: string
  storageType: 'inline' | 'oss'
  resourcePath: string
  resourceUrl: string
  fileSize: number
  mimeType: string
  description?: string
  ownerId: number
}

export const uploadResource = async (
  file: File,
  skillName: string,
  resourceType: string = 'document',
  description?: string,
  userId?: number
): Promise<ApiResponse<ResourceUploadResult>> => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('skillName', skillName)
  formData.append('resourceType', resourceType)
  if (description) {
    formData.append('description', description)
  }
  if (userId) {
    formData.append('userId', userId.toString())
  }
  return post(`${API_BASE}/resources/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const downloadResource = async (
  skillName: string,
  resourcePath: string,
  expirationMinutes: number = 60,
  userId?: number
): Promise<ApiResponse<string>> => {
  const params = new URLSearchParams()
  params.append('skillName', skillName)
  params.append('resourcePath', resourcePath)
  params.append('expirationMinutes', expirationMinutes.toString())
  if (userId) {
    params.append('userId', userId.toString())
  }
  return get(`${API_BASE}/resources/download?${params.toString()}`)
}

export const deleteResource = async (
  skillName: string,
  resourcePath: string,
  userId?: number
): Promise<ApiResponse<string>> => {
  const params = new URLSearchParams()
  params.append('skillName', skillName)
  params.append('resourcePath', resourcePath)
  if (userId) {
    params.append('userId', userId.toString())
  }
  return del(`${API_BASE}/resources?${params.toString()}`)
}

export const importSkillPackage = async (
  file: File,
  modelConfigId?: string
): Promise<ApiResponse<SkillImportResult>> => {
  const formData = new FormData()
  formData.append('file', file)
  if (modelConfigId) {
    formData.append('modelConfigId', modelConfigId)
  }
  return post(`${API_BASE}/package/import`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
