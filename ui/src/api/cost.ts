import { get } from './request'

export interface CostMetrics {
  totalApiCalls: number
  totalTokens: number
  totalCost: number
  activeSessions: number
  trackedUsers: number
  trackedDays: number
}

export interface SessionCost {
  sessionId: string
  userId: number
  model: string
  startTime: string
  endTime: string | null
  totalInputTokens: number
  totalOutputTokens: number
  totalTokens: number
  totalCost: number
  apiCallCount: number
  toolCallCount: number
  events: CostEvent[]
}

export interface CostEvent {
  timestamp: string
  eventType: string
  model: string
  inputTokens: number
  outputTokens: number
  cost: number
  metadata: Record<string, any>
}

export interface UserCost {
  userId: number
  totalTokens: number
  totalApiCalls: number
  totalCostCents: number
  modelUsage: Record<string, number>
  dailyCostCents: Record<string, number>
}

export interface DailyCost {
  date: string
  totalTokens: number
  totalApiCalls: number
  totalCostCents: number
  modelCosts: Record<string, number>
}

export interface ModelCostSummary {
  modelId: string
  totalCalls: number
  totalInputTokens: number
  totalOutputTokens: number
  totalCost: number
  averageCostPerCall: number
}

export interface DailySummary {
  date: string
  apiCalls: number
  tokens: number
  cost: number
}

export interface CostReport {
  reportTime: string
  totalApiCalls: number
  totalTokens: number
  totalCost: number
  averageCostPerCall: number
  modelSummaries: Record<string, ModelCostSummary>
  topExpensiveSessions: SessionCost[]
  dailyTrend: {
    days: DailySummary[]
  }
}

export interface BudgetStatus {
  userId: number
  dailyBudget: number
  dailySpent: number
  dailyRemaining: number
  monthlyBudget: number
  monthlySpent: number
  monthlyRemaining: number
  usagePercentage: number
  overBudget: boolean
}

export interface ModelPricing {
  modelId: string
  inputPricePer1k: number
  outputPricePer1k: number
  currency: string
}

export const costApi = {
  getMetrics: () => get<CostMetrics>('/api/v16/cost/metrics'),
  
  getSessionCost: (sessionId: string) => get<SessionCost>(`/api/v16/cost/session/${sessionId}`),
  
  getUserCost: (userId: number) => get<UserCost>(`/api/v16/cost/user/${userId}`),
  
  getDailyCost: (date: string) => get<DailyCost>(`/api/v16/cost/daily/${date}`),
  
  getReport: (startDate?: string, endDate?: string) => {
    const params = new URLSearchParams()
    if (startDate) params.append('startDate', startDate)
    if (endDate) params.append('endDate', endDate)
    const query = params.toString() ? `?${params.toString()}` : ''
    return get<CostReport>(`/api/v16/cost/report${query}`)
  },
  
  getBudgetStatus: (userId: number, dailyBudget = 10, monthlyBudget = 100) => 
    get<BudgetStatus>(`/api/v16/cost/budget/${userId}?dailyBudget=${dailyBudget}&monthlyBudget=${monthlyBudget}`),
  
  getModelPricing: (modelId: string) => get<ModelPricing>(`/api/v16/cost/pricing/${modelId}`),
  
  calculateCost: (modelId: string, inputTokens: number, outputTokens: number) => 
    get<{ modelId: string; inputTokens: number; outputTokens: number; totalTokens: number; cost: number }>(
      `/api/v16/cost/calculate?modelId=${modelId}&inputTokens=${inputTokens}&outputTokens=${outputTokens}`
    )
}
