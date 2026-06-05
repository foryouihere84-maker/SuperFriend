import { get, post, put, del } from './request'

const API_BASE = '/api/v16/profile'

export interface InterestItem {
  name: string
  confidence: number
  lastSeen?: string
  mentionCount?: number
}

export interface TraitItem {
  name: string
  confidence: number
  contexts?: string[]
  mentionCount?: number
}

export interface PreferenceItem {
  name: string
  confidence: number
  category?: string
  mentionCount?: number
}

export interface SkillItem {
  name: string
  level: string
  confidence: number
  mentionCount?: number
}

export interface UserProfile {
  id: number
  userId: number
  interests: InterestItem[]
  traits: TraitItem[]
  preferences: PreferenceItem[]
  skills: SkillItem[]
  summary: string
  version: number
  createdTime: string
  updatedTime: string
}

export interface ProfileSummary {
  userId: number
  summary: string
  interestCount: number
  traitCount: number
  preferenceCount: number
  skillCount: number
  version: number
  updatedAt: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  error?: string
}

export const getUserProfile = async (userId: number): Promise<UserProfile | null> => {
  const response = await get<ApiResponse<UserProfile>>(`${API_BASE}/${userId}`)
  return response?.data || null
}

export const getOrCreateUserProfile = async (userId: number): Promise<UserProfile> => {
  const response = await get<ApiResponse<UserProfile>>(`${API_BASE}/${userId}/or-create`)
  return response?.data
}

export const getProfileSummary = async (userId: number): Promise<ProfileSummary> => {
  const response = await get<ApiResponse<ProfileSummary>>(`${API_BASE}/${userId}/summary`)
  return response?.data
}

export const clearUserProfile = async (userId: number): Promise<void> => {
  await del<ApiResponse<{ message: string }>>(`${API_BASE}/${userId}`)
}

export const updateFullProfile = async (userId: number, profile: Partial<UserProfile>): Promise<UserProfile> => {
  const response = await put<ApiResponse<UserProfile>>(`${API_BASE}/${userId}`, profile)
  return response?.data
}

export const updateSummary = async (userId: number, summary: string): Promise<void> => {
  await put<ApiResponse<{ message: string }>>(`${API_BASE}/${userId}/summary`, { summary })
}

export const updateInterests = async (userId: number, items: InterestItem[]): Promise<InterestItem[]> => {
  const response = await put<ApiResponse<InterestItem[]>>(`${API_BASE}/${userId}/interests`, items)
  return response?.data
}

export const upsertInterest = async (userId: number, item: InterestItem): Promise<InterestItem> => {
  const response = await post<ApiResponse<InterestItem>>(`${API_BASE}/${userId}/interests`, item)
  return response?.data
}

export const deleteInterest = async (userId: number, name: string): Promise<boolean> => {
  const encodedName = encodeURIComponent(name)
  const response = await del<ApiResponse<{ deleted: boolean }>>(`${API_BASE}/${userId}/interests/${encodedName}`)
  return response?.data?.deleted || false
}

export const updateTraits = async (userId: number, items: TraitItem[]): Promise<TraitItem[]> => {
  const response = await put<ApiResponse<TraitItem[]>>(`${API_BASE}/${userId}/traits`, items)
  return response?.data
}

export const upsertTrait = async (userId: number, item: TraitItem): Promise<TraitItem> => {
  const response = await post<ApiResponse<TraitItem>>(`${API_BASE}/${userId}/traits`, item)
  return response?.data
}

export const deleteTrait = async (userId: number, name: string): Promise<boolean> => {
  const encodedName = encodeURIComponent(name)
  const response = await del<ApiResponse<{ deleted: boolean }>>(`${API_BASE}/${userId}/traits/${encodedName}`)
  return response?.data?.deleted || false
}

export const updatePreferences = async (userId: number, items: PreferenceItem[]): Promise<PreferenceItem[]> => {
  const response = await put<ApiResponse<PreferenceItem[]>>(`${API_BASE}/${userId}/preferences`, items)
  return response?.data
}

export const upsertPreference = async (userId: number, item: PreferenceItem): Promise<PreferenceItem> => {
  const response = await post<ApiResponse<PreferenceItem>>(`${API_BASE}/${userId}/preferences`, item)
  return response?.data
}

export const deletePreference = async (userId: number, name: string): Promise<boolean> => {
  const encodedName = encodeURIComponent(name)
  const response = await del<ApiResponse<{ deleted: boolean }>>(`${API_BASE}/${userId}/preferences/${encodedName}`)
  return response?.data?.deleted || false
}

export const updateSkills = async (userId: number, items: SkillItem[]): Promise<SkillItem[]> => {
  const response = await put<ApiResponse<SkillItem[]>>(`${API_BASE}/${userId}/skills`, items)
  return response?.data
}

export const upsertSkill = async (userId: number, item: SkillItem): Promise<SkillItem> => {
  const response = await post<ApiResponse<SkillItem>>(`${API_BASE}/${userId}/skills`, item)
  return response?.data
}

export const deleteSkill = async (userId: number, name: string): Promise<boolean> => {
  const encodedName = encodeURIComponent(name)
  const response = await del<ApiResponse<{ deleted: boolean }>>(`${API_BASE}/${userId}/skills/${encodedName}`)
  return response?.data?.deleted || false
}
