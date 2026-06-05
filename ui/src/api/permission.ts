import { get, post, put, del } from './request'
import type { ApiResponse } from '@/types/api'

export interface ApprovalRequest {
  requestId: string
  sessionId: string
  toolName: string
  serverName: string
  operation: string
  arguments: string
  permissionLevel: string
  description: string
  userId: number
}

export interface ApprovalDecision {
  requestId: string
  decision: 'approved' | 'denied'
  reason: string
  alwaysAllow: boolean
}

export interface PermissionPolicy {
  id: number
  userId: number
  toolName: string | null
  operationType: string | null
  resourcePattern: string | null
  permissionLevel: 'auto_allow' | 'confirm' | 'deny'
  description: string | null
}

const APPROVAL_BASE = '/api/v16/approval'
const PERMISSION_BASE = '/api/v16/permission'

export const getPendingApproval = async (requestId: string): Promise<ApiResponse<ApprovalRequest>> => {
  return get(`${APPROVAL_BASE}/pending/${requestId}`)
}

export const getPendingApprovalsForSession = async (sessionId: string): Promise<ApiResponse<ApprovalRequest[]>> => {
  return get(`${APPROVAL_BASE}/pending/session/${sessionId}`)
}

export const submitApprovalDecision = async (decision: ApprovalDecision): Promise<ApiResponse<string>> => {
  return post(`${APPROVAL_BASE}/decide`, decision)
}

export const getPolicies = async (userId: number): Promise<ApiResponse<PermissionPolicy[]>> => {
  return get(`${PERMISSION_BASE}/policies?userId=${userId}`)
}

export const addPolicy = async (policy: Partial<PermissionPolicy>): Promise<ApiResponse<PermissionPolicy>> => {
  return post(`${PERMISSION_BASE}/policies`, policy)
}

export const updatePolicy = async (id: number, policy: Partial<PermissionPolicy>): Promise<ApiResponse<PermissionPolicy>> => {
  return put(`${PERMISSION_BASE}/policies/${id}`, policy)
}

export const deletePolicy = async (id: number): Promise<ApiResponse<string>> => {
  return del(`${PERMISSION_BASE}/policies/${id}`)
}
