import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { API_BASE_URL, API_TIMEOUT } from '@/config/api'
import { API_CONFIG } from '@/config/app'

const instance: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT.DEFAULT,
  headers: {
    'Content-Type': 'application/json'
  }
})

instance.interceptors.request.use(
  (config) => {
    // 使用统一配置获取 token
    const token = API_CONFIG.getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    const userStore = useUserStore()
    const userId = userStore.user?.id
    if (userId) {
      config.headers['X-User-Id'] = String(userId)
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

instance.interceptors.response.use(
  (response: AxiosResponse) => {
    // 检查业务层面的错误（success: false）
    const data = response.data
    if (data && data.success === false) {
      // 业务错误，抛出异常让调用方处理
      const error = new Error(data.message || '操作失败') as Error & { response?: { data: unknown } }
      error.response = { data }
      return Promise.reject(error)
    }
    return response.data
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default instance

// 导出类型：由于拦截器返回 response.data，实际返回类型是 T 而不是 AxiosResponse<T>
export type ApiResponse<T> = T

export const request = <T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> => {
  return instance.request(config)
}

export const get = <T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> => {
  return instance.get(url, config)
}

export const post = <T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> => {
  return instance.post(url, data, config)
}

export const put = <T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> => {
  return instance.put(url, data, config)
}

export const del = <T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> => {
  return instance.delete(url, config)
}
