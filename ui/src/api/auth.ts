import { post } from './request'
import type { LoginRequest, RegisterRequest, AuthResponse, AuthData } from '@/types/auth'
import { API_PATHS } from '@/config/api'

const API_BASE = API_PATHS.AUTH

export const login = async (data: LoginRequest): Promise<AuthData> => {
  const response = await post<AuthResponse>(`${API_BASE}/login`, data)
  return response.data
}

export const register = async (data: RegisterRequest): Promise<AuthData> => {
  const response = await post<AuthResponse>(`${API_BASE}/regist`, data)
  return response.data
}

export const logout = async (): Promise<string> => {
  const response = await post<{ success: boolean; data: string }>(`${API_BASE}/logout`)
  return response.data
}
