import { get, post } from './request'
import type { ApiResponse } from '@/types/api'
import type { MCPServersResponse, MCPServerConfig, MCPServerStats } from '@/types/mcp'
import { API_PATHS } from '@/config/api'

const API_BASE = API_PATHS.MCP
const AGENT_API_BASE = API_PATHS.AGENT

export const getAvailableServers = async (): Promise<MCPServersResponse> => {
  const response = await get<ApiResponse<MCPServersResponse>>(`${API_BASE}/servers/available`)
  return response.data
}

export const selectServer = async (serverName: string): Promise<ApiResponse<void>> => {
  return post(`${API_BASE}/servers/${serverName}/select`)
}

export const deselectServer = async (serverName: string): Promise<ApiResponse<void>> => {
  return post(`${API_BASE}/servers/${serverName}/deselect`)
}

export const saveServerSelection = async (serverNames: string[]): Promise<ApiResponse<{ configFilePath: string }>> => {
  return post(`${API_BASE}/servers/selection`, serverNames)
}

export const getSelectedConfig = async (): Promise<ApiResponse<MCPServerConfig>> => {
  return get(`${API_BASE}/servers/selected/config`)
}

export const reloadConfig = async (): Promise<ApiResponse<void>> => {
  return post(`${API_BASE}/reload-config`)
}

export const startSelectedServers = async (): Promise<ApiResponse<string>> => {
  return post(`${API_BASE}/servers/start-selected`)
}

export const getServerStats = async (): Promise<MCPServerStats> => {
  const response = await get<ApiResponse<MCPServerStats>>(`${API_BASE}/stats/tool-usage`)
  return response.data
}

export interface AgentInterruptRequest {
  mode: 'cancel' | 'append'
  context?: string
}

export interface SessionStatus {
  sessionId: string
  state: string
  isExecuting: boolean
}

export const interruptSession = async (sessionId: string, request: AgentInterruptRequest): Promise<ApiResponse<string>> => {
  return post(`${AGENT_API_BASE}/sessions/${sessionId}/interrupt`, request)
}

export const getSessionStatus = async (sessionId: string): Promise<ApiResponse<SessionStatus>> => {
  return get(`${AGENT_API_BASE}/sessions/${sessionId}/status`)
}
