import { get, post, put, del } from './request'
import { API_PATHS } from '@/config/api'

const API_BASE = API_PATHS.KNOWLEDGE_GRAPH

export interface KnowledgeNode {
  id: number
  userId: number
  nodeType: string
  nodeTypeName: string
  name: string
  description: string | null
  avatar: string | null           // 头像URL或emoji
  image: string | null            // 图片URL，点击头像展示
  detailedDescription: string | null  // 详细描述，点击头像展示
  keywords: string | null
  importance: number
  properties: string
  sourceSessionId: string
  confidence: number
  accessCount: number
  lastAccessedTime: string
  createdTime: string
  updatedTime: string
}

export interface KnowledgeRelation {
  id: number
  sourceId: number
  targetId: number
  type: string
  typeName: string
  weight: number
  sourceName: string
  targetName: string
}

export interface GraphStats {
  totalNodes: number
  totalRelations: number
  nodeTypeCount: number
  relationTypeCount: number
}

export interface GraphContext {
  nodes: GraphNode[]
  relations: GraphRelation[]
  stats: GraphStats
}

export interface GraphNode {
  id: number
  type: string
  typeName: string
  name: string
  description: string | null
  avatar: string | null           // 头像URL或emoji
  image: string | null            // 图片URL，点击头像展示
  detailedDescription: string | null  // 详细描述，点击头像展示
  keywords: string | null
  importance: number
  properties: string
  confidence: number
  accessCount: number
}

export interface GraphRelation {
  id: number
  sourceId: number
  targetId: number
  type: string
  typeName: string
  weight: number
  sourceName: string
  targetName: string
}

// API 响应包装类型
interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

// ==================== 节点管理 ====================

// 获取所有节点
export const getAllNodes = async (userId: number): Promise<KnowledgeNode[]> => {
  const response = await get<ApiResponse<KnowledgeNode[]>>(`${API_BASE}/nodes?userId=${userId}`)
  return response?.data || []
}

// 按类型获取节点
export const getNodesByType = async (userId: number, type: string): Promise<KnowledgeNode[]> => {
  const response = await get<ApiResponse<KnowledgeNode[]>>(`${API_BASE}/nodes/type/${type}?userId=${userId}`)
  return response?.data || []
}

// 按会话获取节点
export const getNodesBySession = async (userId: number, sessionId: string): Promise<KnowledgeNode[]> => {
  const response = await get<ApiResponse<KnowledgeNode[]>>(`${API_BASE}/nodes/session/${sessionId}?userId=${userId}`)
  return response?.data || []
}

// 添加节点
export const addNode = async (node: Partial<KnowledgeNode>): Promise<KnowledgeNode> => {
  const response = await post<ApiResponse<KnowledgeNode>>(`${API_BASE}/nodes`, node)
  return response?.data
}

// 更新节点
export const updateNode = async (id: number, node: Partial<KnowledgeNode>): Promise<KnowledgeNode> => {
  const response = await put<ApiResponse<KnowledgeNode>>(`${API_BASE}/nodes/${id}`, node)
  return response?.data
}

// 删除节点
export const deleteNode = async (id: number): Promise<void> => {
  return del(`${API_BASE}/nodes/${id}`)
}

// ==================== 关系管理 ====================

// 获取所有关系
export const getAllRelations = async (userId: number): Promise<KnowledgeRelation[]> => {
  const response = await get<ApiResponse<KnowledgeRelation[]>>(`${API_BASE}/relations?userId=${userId}`)
  return response?.data || []
}

// 添加关系
export const addRelation = async (relation: {
  userId: number
  sourceNodeId: number
  targetNodeId: number
  relationType: string
  properties?: string
  weight?: number
}): Promise<KnowledgeRelation> => {
  const response = await post<ApiResponse<KnowledgeRelation>>(`${API_BASE}/relations`, relation)
  return response?.data
}

// 删除关系
export const deleteRelation = async (id: number): Promise<void> => {
  return del(`${API_BASE}/relations/${id}`)
}

// 更新关系
export const updateRelation = async (id: number, relation: {
  sourceNodeId: number
  targetNodeId: number
  relationType: string
  weight?: number
}): Promise<void> => {
  return put(`${API_BASE}/relations/${id}`, relation)
}

// ==================== 图谱查询 ====================

// 获取完整图谱
export const getFullGraph = async (userId: number): Promise<GraphContext> => {
  const response = await get<ApiResponse<GraphContext>>(`${API_BASE}/graph?userId=${userId}`)
  return response?.data || { nodes: [], relations: [], stats: { totalNodes: 0, totalRelations: 0, nodeTypeCount: 0, relationTypeCount: 0 } }
}

// 获取对话级图谱
export const getConversationGraph = async (userId: number, sessionId: string): Promise<GraphContext> => {
  const response = await get<ApiResponse<GraphContext>>(`${API_BASE}/graph/conversation/${sessionId}?userId=${userId}`)
  return response?.data || { nodes: [], relations: [], stats: { totalNodes: 0, totalRelations: 0, nodeTypeCount: 0, relationTypeCount: 0 } }
}

// 搜索节点
export const searchNodes = async (
  userId: number,
  keyword: string,
  scope?: string,
  sessionId?: string
): Promise<KnowledgeNode[]> => {
  let url = `${API_BASE}/search?userId=${userId}&keyword=${encodeURIComponent(keyword)}`
  if (scope) {
    url += `&scope=${scope}`
  }
  if (sessionId) {
    url += `&sessionId=${sessionId}`
  }
  const response = await get<ApiResponse<KnowledgeNode[]>>(url)
  return response?.data || []
}

// 获取统计信息
export const getStats = async (userId: number): Promise<{ totalNodes: number; totalRelations: number }> => {
  const response = await get<ApiResponse<{ total: number; totalRelations: number }>>(`${API_BASE}/stats?userId=${userId}`)
  return { totalNodes: response?.data?.total || 0, totalRelations: response?.data?.totalRelations || 0 }
}

// 清除全部图谱数据（全局+对话级）
export const clearAll = async (userId: number): Promise<{ deletedNodes: number }> => {
  const response = await del<ApiResponse<{ deletedNodes: number }>>(`${API_BASE}/clear?userId=${userId}`)
  return response?.data || { deletedNodes: 0 }
}

// 清除全局图谱（保留对话级图谱）
export const clearGlobalGraph = async (userId: number): Promise<{ deletedNodes: number; scope: string }> => {
  const response = await del<ApiResponse<{ deletedNodes: number; scope: string }>>(`${API_BASE}/clear/global?userId=${userId}`)
  return response?.data || { deletedNodes: 0, scope: 'GLOBAL' }
}

// 清除所有对话级图谱（保留全局图谱）
export const clearAllConversationGraphs = async (userId: number): Promise<{ deletedNodes: number; scope: string }> => {
  const response = await del<ApiResponse<{ deletedNodes: number; scope: string }>>(`${API_BASE}/clear/conversation?userId=${userId}`)
  return response?.data || { deletedNodes: 0, scope: 'CONVERSATION' }
}

// 清除单个对话的图谱（保留全局图谱和其他对话图谱）
export const clearConversationGraph = async (userId: number, sessionId: string): Promise<{ deletedNodes: number }> => {
  const response = await del<ApiResponse<{ deletedNodes: number }>>(`${API_BASE}/clear/conversation/${sessionId}?userId=${userId}`)
  return response?.data || { deletedNodes: 0 }
}

// ==================== 节点丰富化 ====================

// 手动触发单个节点的丰富化
export const enrichNode = async (nodeId: number): Promise<{ nodeId: number; nodeName: string; message: string }> => {
  const response = await post<ApiResponse<{ nodeId: number; nodeName: string; message: string }>>(`${API_BASE}/nodes/${nodeId}/enrich`)
  // 检查是否成功
  if (!response?.success) {
    throw new Error(response?.message || '丰富化请求失败')
  }
  return response?.data || { nodeId: 0, nodeName: '', message: '' }
}

// 批量丰富化用户所有缺少信息的节点
export const enrichAllNodes = async (userId: number): Promise<{ totalNodes: number; triggered: number; skipped: number; message: string }> => {
  const response = await post<ApiResponse<{ totalNodes: number; triggered: number; skipped: number; message: string }>>(`${API_BASE}/nodes/enrich-all?userId=${userId}`)
  return response?.data || { totalNodes: 0, triggered: 0, skipped: 0, message: '' }
}
