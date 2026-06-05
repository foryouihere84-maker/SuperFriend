export interface Skill {
  name: string
  description: string
  category: string
  version: string
  author?: string
  tags: string[]
  allowedTools: string[]
  instructions?: string
  parameters?: Record<string, any>
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  scope: 'SYSTEM' | 'USER' | 'PROJECT'
  isSelected?: boolean
  executionCount: number
  successRate: number
  averageExecutionTime: number
  scripts?: SkillScript[]
  resources?: SkillResource[]
  lastUsedTime?: string
  createdTime?: string
  updatedTime?: string
}

export interface SkillExecutionRequest {
  skillName: string
  sessionId: string
  userId?: string
  userRequest: string
  parameters?: Record<string, any>
  variables?: Record<string, any>
}

export interface SkillExecutionResponse {
  success: boolean
  data?: any
  error?: string
  executionTime: number
  status: 'SUCCESS' | 'FAILED' | 'PARTIAL' | 'INTERRUPTED' | 'TIMEOUT'
  metadata?: Record<string, any>
}

export interface SkillScript {
  scriptType: 'python' | 'bash' | 'javascript' | 'typescript' | 'ruby' | 'powershell'
  scriptName: string
  scriptContent: string
  isMain?: boolean
  executionOrder?: number
  isModified?: boolean
}

export interface SkillResource {
  resourceName: string
  resourceType: 'document' | 'image' | 'template' | 'example' | 'config' | 'other'
  storageType: 'inline' | 'oss'
  resourceContent?: string
  resourcePath?: string
  resourceUrl?: string
  fileSize?: number
  mimeType?: string
  description?: string
  isDeleted?: boolean
  isNew?: boolean
}

export interface SkillConfig {
  name: string
  description: string
  category: string
  version?: string
  author?: string
  tags?: string[]
  allowedTools?: string[]
  instructions: string
  parameters?: Record<string, any>
  priority?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  scripts?: SkillScript[]
  resources?: SkillResource[]
}

export interface SkillStats {
  totalSkills: number
  categories: number
  tags: number
  totalExecutions: number
  averageSuccessRate: number
}

export interface SkillSuggestionRequest {
  userRequest: string
}

export interface SkillsPathConfig {
  systemPath: string
  projectPath: string
}

export interface SkillRecommendation {
  skillName: string
  description: string
  category: string
  score: number
  confidence: string
  autoExecute: boolean
  suggestedParameters?: Record<string, any>
}

export interface AIIntentAnalysis {
  userMessage: string
  intent: string
  keywords: string[]
  confidence: number
  reasoning?: string
  suggestedSkillType?: string
  recommendedSkills: SkillRecommendation[]
}

export interface SkillImportResult {
  success: boolean
  message: string
  skillId?: number
  skillName?: string
  extractedFiles?: number
  resourceCount?: number
  aiAnalysisPerformed?: boolean
}
