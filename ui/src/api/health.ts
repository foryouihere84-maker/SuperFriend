import { get } from './request'

export interface HealthResponse {
  status: string
  timestamp: number
  services: {
    database: boolean
    ai: boolean
    mcp: boolean
  }
}

export const checkHealth = async (): Promise<HealthResponse> => {
  return get<HealthResponse>('/api/health')
}
