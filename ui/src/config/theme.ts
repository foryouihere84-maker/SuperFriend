/**
 * 主题配置 - TypeScript 定义
 * 用于动态主题切换和样式配置
 */

// ============================================
// 基础类型定义
// ============================================

export interface ThemeColors {
  bgWhite: string
  bgGray50: string
  bgGray100: string
  bgGray200: string
  bgGray300: string
  textPrimary: string
  textSecondary: string
  textTertiary: string
  textMuted: string
  textPlaceholder: string
  borderLight: string
  borderDefault: string
  borderStrong: string
}

export interface AccentColors {
  default: string
  hover: string
  light: string
  subtle: string
  text: string
}

export interface FunctionalColors {
  success: string
  successLight: string
  warning: string
  warningLight: string
  error: string
  errorLight: string
}

export interface ShadowConfig {
  sm: string
  md: string
  lg: string
}

export interface SpacingConfig {
  space1: string
  space2: string
  space3: string
  space4: string
  space5: string
  space6: string
  space8: string
  space10: string
  space12: string
}

export interface BorderRadiusConfig {
  sm: string
  md: string
  lg: string
  xl: string
  full: string
}

export interface TransitionConfig {
  fast: string
  normal: string
  slow: string
}

export interface FontConfig {
  sans: string
  mono: string
}

// ============================================
// Chat 主题类型定义
// ============================================

export interface ChatThemeColors {
  bgDeep: string
  bgPrimary: string
  bgSecondary: string
  bgElevated: string
  bgHover: string
  surfaceGlass: string
  surfaceGlassHover: string
  surfaceGlassActive: string
  borderSubtle: string
  borderDefault: string
  borderStrong: string
  textPrimary: string
  textSecondary: string
  textTertiary: string
  textMuted: string
  accentPurple: string
  accentPurpleLight: string
  accentPurpleDark: string
  accentCyan: string
  accentCyanLight: string
  accentPink: string
  accentGreen: string
  accentOrange: string
  accentRed: string
}

export interface ChatPageTheme {
  bg: string
  gradient: string
  grid: string
  noise: string
}

export interface ChatShadowConfig {
  sm: string
  md: string
  lg: string
  glowPurple: string
  glowCyan: string
}

export interface ChatRadiusConfig {
  sm: string
  md: string
  lg: string
  xl: string
  full: string
}

export interface ChatFontConfig {
  display: string
  body: string
  mono: string
}

export interface ChatTransitionConfig {
  fast: string
  normal: string
  slow: string
  spring: string
}

export interface ChatTheme {
  colors: ChatThemeColors
  page: ChatPageTheme
  shadows: ChatShadowConfig
  radius: ChatRadiusConfig
  fonts: ChatFontConfig
  transitions: ChatTransitionConfig
}

// ============================================
// 完整主题配置
// ============================================

export interface ThemeConfig {
  colors: ThemeColors
  accent: AccentColors
  functional: FunctionalColors
  shadows: ShadowConfig
  spacing: SpacingConfig
  borderRadius: BorderRadiusConfig
  transitions: TransitionConfig
  fonts: FontConfig
  chat: ChatTheme
}

// ============================================
// 亮色主题
// ============================================

export const lightColors: ThemeColors = {
  bgWhite: '#ffffff',
  bgGray50: '#fafafa',
  bgGray100: '#f5f5f5',
  bgGray200: '#eeeeee',
  bgGray300: '#e0e0e0',
  textPrimary: '#1a1a1a',
  textSecondary: '#4a4a4a',
  textTertiary: '#6b6b6b',
  textMuted: '#9a9a9a',
  textPlaceholder: '#b0b0b0',
  borderLight: '#f0f0f0',
  borderDefault: '#e5e5e5',
  borderStrong: '#d0d0d0',
}

export const lightAccent: AccentColors = {
  default: '#1a1a1a',
  hover: '#333333',
  light: '#f5f5f5',
  subtle: '#fafafa',
  text: '#ffffff',
}

export const lightFunctional: FunctionalColors = {
  success: '#10b981',
  successLight: '#d1fae5',
  warning: '#f59e0b',
  warningLight: '#fef3c7',
  error: '#ef4444',
  errorLight: '#fee2e2',
}

export const lightShadows: ShadowConfig = {
  sm: '0 1px 2px rgba(0, 0, 0, 0.04)',
  md: '0 2px 8px rgba(0, 0, 0, 0.06)',
  lg: '0 4px 16px rgba(0, 0, 0, 0.08)',
}

export const lightChatColors: ChatThemeColors = {
  bgDeep: '#f8f9fc',
  bgPrimary: '#ffffff',
  bgSecondary: '#f1f3f8',
  bgElevated: '#ffffff',
  bgHover: '#e8ebf2',
  surfaceGlass: 'rgba(0, 0, 0, 0.02)',
  surfaceGlassHover: 'rgba(0, 0, 0, 0.04)',
  surfaceGlassActive: 'rgba(0, 0, 0, 0.06)',
  borderSubtle: 'rgba(0, 0, 0, 0.06)',
  borderDefault: 'rgba(0, 0, 0, 0.1)',
  borderStrong: 'rgba(0, 0, 0, 0.15)',
  textPrimary: '#1a1a2e',
  textSecondary: '#4a4a68',
  textTertiary: '#6b6b88',
  textMuted: '#9898a8',
  accentPurple: '#7c3aed',
  accentPurpleLight: '#8b5cf6',
  accentPurpleDark: '#6d28d9',
  accentCyan: '#0891b2',
  accentCyanLight: '#06b6d4',
  accentPink: '#db2777',
  accentGreen: '#059669',
  accentOrange: '#d97706',
  accentRed: '#dc2626',
}

export const lightChatPage: ChatPageTheme = {
  bg: '#f5f7fc',
  gradient:
    'radial-gradient(ellipse 80% 50% at 50% -20%, rgba(120, 119, 198, 0.08), transparent), ' +
    'radial-gradient(ellipse 60% 40% at 100% 100%, rgba(139, 92, 246, 0.05), transparent), ' +
    'radial-gradient(ellipse 50% 30% at 0% 80%, rgba(59, 130, 246, 0.03), transparent)',
  grid:
    'linear-gradient(rgba(0, 0, 0, 0.015) 1px, transparent 1px), ' +
    'linear-gradient(90deg, rgba(0, 0, 0, 0.015) 1px, transparent 1px)',
  noise:
    "url(\"data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E\")",
}

export const lightChatShadows: ChatShadowConfig = {
  sm: '0 2px 8px rgba(0, 0, 0, 0.08)',
  md: '0 4px 16px rgba(0, 0, 0, 0.1)',
  lg: '0 8px 32px rgba(0, 0, 0, 0.12)',
  glowPurple: '0 0 20px rgba(124, 58, 237, 0.2)',
  glowCyan: '0 0 20px rgba(8, 145, 178, 0.2)',
}

// ============================================
// 深色主题
// ============================================

export const darkColors: ThemeColors = {
  bgWhite: '#0a0a0a',
  bgGray50: '#141414',
  bgGray100: '#1c1c1c',
  bgGray200: '#262626',
  bgGray300: '#333333',
  textPrimary: '#f5f5f5',
  textSecondary: '#a0a0a0',
  textTertiary: '#737373',
  textMuted: '#525252',
  textPlaceholder: '#404040',
  borderLight: 'rgba(255, 255, 255, 0.06)',
  borderDefault: 'rgba(255, 255, 255, 0.1)',
  borderStrong: 'rgba(255, 255, 255, 0.15)',
}

export const darkAccent: AccentColors = {
  default: '#ffffff',
  hover: '#f5f5f5',
  light: 'rgba(255, 255, 255, 0.1)',
  subtle: 'rgba(255, 255, 255, 0.05)',
  text: '#1a1a1a',
}

export const darkFunctional: FunctionalColors = {
  success: '#22c55e',
  successLight: 'rgba(34, 197, 94, 0.15)',
  warning: '#f59e0b',
  warningLight: 'rgba(245, 158, 11, 0.15)',
  error: '#f87171',
  errorLight: 'rgba(248, 113, 113, 0.15)',
}

export const darkShadows: ShadowConfig = {
  sm: '0 1px 2px rgba(0, 0, 0, 0.2)',
  md: '0 2px 8px rgba(0, 0, 0, 0.3)',
  lg: '0 4px 16px rgba(0, 0, 0, 0.4)',
}

export const darkChatColors: ChatThemeColors = {
  bgDeep: '#06060a',
  bgPrimary: '#0c0c12',
  bgSecondary: '#12121a',
  bgElevated: '#1a1a24',
  bgHover: '#22222e',
  surfaceGlass: 'rgba(255, 255, 255, 0.03)',
  surfaceGlassHover: 'rgba(255, 255, 255, 0.06)',
  surfaceGlassActive: 'rgba(255, 255, 255, 0.08)',
  borderSubtle: 'rgba(255, 255, 255, 0.06)',
  borderDefault: 'rgba(255, 255, 255, 0.1)',
  borderStrong: 'rgba(255, 255, 255, 0.15)',
  textPrimary: '#f5f5fa',
  textSecondary: '#c8c8d8',
  textTertiary: '#9898a8',
  textMuted: '#787888',
  accentPurple: '#8b5cf6',
  accentPurpleLight: '#a78bfa',
  accentPurpleDark: '#7c3aed',
  accentCyan: '#06b6d4',
  accentCyanLight: '#22d3ee',
  accentPink: '#ec4899',
  accentGreen: '#10b981',
  accentOrange: '#f59e0b',
  accentRed: '#ef4444',
}

export const darkChatPage: ChatPageTheme = {
  bg: '#0d0d14',
  gradient:
    'radial-gradient(ellipse 80% 50% at 50% -20%, rgba(120, 119, 198, 0.12), transparent), ' +
    'radial-gradient(ellipse 60% 40% at 100% 100%, rgba(139, 92, 246, 0.06), transparent), ' +
    'radial-gradient(ellipse 50% 30% at 0% 80%, rgba(59, 130, 246, 0.04), transparent)',
  grid:
    'linear-gradient(rgba(255, 255, 255, 0.015) 1px, transparent 1px), ' +
    'linear-gradient(90deg, rgba(255, 255, 255, 0.015) 1px, transparent 1px)',
  noise:
    "url(\"data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E\")",
}

export const darkChatShadows: ChatShadowConfig = {
  sm: '0 2px 8px rgba(0, 0, 0, 0.3)',
  md: '0 4px 16px rgba(0, 0, 0, 0.4)',
  lg: '0 8px 32px rgba(0, 0, 0, 0.5)',
  glowPurple: '0 0 20px rgba(139, 92, 246, 0.3)',
  glowCyan: '0 0 20px rgba(6, 182, 212, 0.3)',
}

// ============================================
// 共享配置
// ============================================

export const spacing: SpacingConfig = {
  space1: '4px',
  space2: '8px',
  space3: '12px',
  space4: '16px',
  space5: '20px',
  space6: '24px',
  space8: '32px',
  space10: '40px',
  space12: '48px',
}

export const borderRadius: BorderRadiusConfig = {
  sm: '4px',
  md: '8px',
  lg: '12px',
  xl: '16px',
  full: '9999px',
}

export const transitions: TransitionConfig = {
  fast: '150ms ease',
  normal: '200ms ease',
  slow: '300ms ease',
}

export const fonts: FontConfig = {
  sans: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
  mono: "'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, monospace",
}

export const chatRadius: ChatRadiusConfig = {
  sm: '6px',
  md: '10px',
  lg: '14px',
  xl: '20px',
  full: '9999px',
}

export const chatFonts: ChatFontConfig = {
  display: "'Sora', system-ui, sans-serif",
  body: "'Plus Jakarta Sans', system-ui, sans-serif",
  mono: "'JetBrains Mono', 'Fira Code', monospace",
}

export const chatTransitions: ChatTransitionConfig = {
  fast: '150ms cubic-bezier(0.4, 0, 0.2, 1)',
  normal: '250ms cubic-bezier(0.4, 0, 0.2, 1)',
  slow: '400ms cubic-bezier(0.4, 0, 0.2, 1)',
  spring: '500ms cubic-bezier(0.34, 1.56, 0.64, 1)',
}

// ============================================
// 导出完整主题配置
// ============================================

export const lightTheme: ThemeConfig = {
  colors: lightColors,
  accent: lightAccent,
  functional: lightFunctional,
  shadows: lightShadows,
  spacing,
  borderRadius,
  transitions,
  fonts,
  chat: {
    colors: lightChatColors,
    page: lightChatPage,
    shadows: lightChatShadows,
    radius: chatRadius,
    fonts: chatFonts,
    transitions: chatTransitions,
  },
}

export const darkTheme: ThemeConfig = {
  colors: darkColors,
  accent: darkAccent,
  functional: darkFunctional,
  shadows: darkShadows,
  spacing,
  borderRadius,
  transitions,
  fonts,
  chat: {
    colors: darkChatColors,
    page: darkChatPage,
    shadows: darkChatShadows,
    radius: chatRadius,
    fonts: chatFonts,
    transitions: chatTransitions,
  },
}

// ============================================
// 主题状态管理
// ============================================

import { ref, computed } from 'vue'

export type ThemeMode = 'light' | 'dark'

const isDarkMode = ref(false)

export const useTheme = () => {
  const themeMode = computed(() => (isDarkMode.value ? 'dark' : 'light'))

  const theme = computed(() => (isDarkMode.value ? darkTheme : lightTheme))

  const toggleTheme = () => {
    isDarkMode.value = !isDarkMode.value
  }

  const setTheme = (mode: ThemeMode) => {
    isDarkMode.value = mode === 'dark'
  }

  return {
    isDarkMode: computed(() => isDarkMode.value),
    themeMode,
    theme,
    toggleTheme,
    setTheme,
  }
}

export { isDarkMode }
