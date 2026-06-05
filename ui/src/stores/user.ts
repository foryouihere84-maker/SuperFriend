import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserDTO } from '@/types/auth'
import { login as loginApi, logout as logoutApi } from '@/api/auth'
import { storage } from '@/utils/storage'
import { STORAGE_KEYS } from '@/config/app'

const TOKEN_KEY = STORAGE_KEYS.TOKEN
const USER_KEY = STORAGE_KEYS.USER || 'sf_user'
const THEME_KEY = STORAGE_KEYS.THEME || 'sf_theme'

const getInitialTheme = (): boolean => {
  const savedTheme = storage.get<string>(THEME_KEY)
  if (savedTheme) {
    return savedTheme === 'dark'
  }
  if (typeof window !== 'undefined') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  return true
}

const initialDarkMode = getInitialTheme()

if (initialDarkMode) {
  document.documentElement.classList.add('dark')
} else {
  document.documentElement.classList.remove('dark')
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const user = ref<UserDTO | null>(null)
  const isLoading = ref(false)
  const isDarkMode = ref(initialDarkMode)

  const isLoggedIn = computed(() => !!token.value && !!user.value)

  const setToken = (newToken: string) => {
    token.value = newToken
    // Token 直接存 localStorage，不使用带 TTL 的缓存
    localStorage.setItem(TOKEN_KEY, newToken)
  }

  const setUser = (newUser: UserDTO) => {
    user.value = newUser
    // 用户信息直接存 localStorage，不使用带 TTL 的缓存
    localStorage.setItem(USER_KEY, JSON.stringify(newUser))
  }

  const clearAuth = () => {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  const login = async (username: string, password: string) => {
    try {
      isLoading.value = true
      const authData = await loginApi({ username, password })
      setToken(authData.token)
      setUser(authData.user)
      return authData
    } catch (error) {
      clearAuth()
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const logout = async () => {
    try {
      await logoutApi()
    } catch (error) {
      console.error('Logout error:', error)
    } finally {
      clearAuth()
    }
  }

  const initAuth = () => {
    // 直接从 localStorage 读取，不使用带 TTL 的缓存
    const savedToken = localStorage.getItem(TOKEN_KEY)
    const savedUserStr = localStorage.getItem(USER_KEY)
    if (savedToken && savedUserStr) {
      token.value = savedToken
      try {
        user.value = JSON.parse(savedUserStr)
      } catch {
        user.value = null
      }
    }
  }

  const initTheme = () => {
    const savedTheme = storage.get<string>(THEME_KEY)
    if (savedTheme) {
      isDarkMode.value = savedTheme === 'dark'
    } else {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
      isDarkMode.value = prefersDark
    }
    applyTheme()
  }

  const toggleTheme = () => {
    isDarkMode.value = !isDarkMode.value
    storage.set(THEME_KEY, isDarkMode.value ? 'dark' : 'light')
    applyTheme()
  }

  const applyTheme = () => {
    if (isDarkMode.value) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  return {
    token,
    user,
    isLoading,
    isDarkMode,
    isLoggedIn,
    setToken,
    setUser,
    clearAuth,
    login,
    logout,
    initAuth,
    initTheme,
    toggleTheme
  }
})
