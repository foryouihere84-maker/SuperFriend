export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  confirmPassword: string
}

export interface UserDTO {
  id: string
  username: string
  email: string
  displayName?: string
  avatar?: string
  createdTime?: number
  lastLoginTime?: number
}

export interface AuthData {
  user: UserDTO
  token: string
}

export interface AuthResponse {
  success: boolean
  data: AuthData
  message?: string
  code?: number
}
