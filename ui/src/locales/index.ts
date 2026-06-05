/**
 * i18n 国际化配置
 * 简单的国际化解决方案，支持中文和英文
 */

import { ref, computed } from 'vue'
import zhCN from './zh-CN'
import enUS from './en-US'

export type Locale = 'zh-CN' | 'en-US'

export const locales = {
  'zh-CN': zhCN,
  'en-US': enUS,
} as const

export type LocaleMessages = typeof zhCN

// 当前语言
const currentLocale = ref<Locale>('zh-CN')

// 存储语言偏好
const LOCALE_STORAGE_KEY = 'sf_locale'

// 初始化语言
const initLocale = () => {
  const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
  if (stored && (stored === 'zh-CN' || stored === 'en-US')) {
    currentLocale.value = stored
  } else {
    // 根据浏览器语言设置
    const browserLang = navigator.language
    if (browserLang.startsWith('zh')) {
      currentLocale.value = 'zh-CN'
    } else {
      currentLocale.value = 'en-US'
    }
  }
}

// 获取翻译文本
function getNestedValue(obj: unknown, path: string): string | undefined {
  return path.split('.').reduce((acc: unknown, part) => acc && (acc as Record<string, unknown>)[part], obj) as string | undefined
}

// 翻译函数
function translate(key: string, params?: Record<string, string | number>): string {
  const messages = locales[currentLocale.value]
  let text = getNestedValue(messages, key) || getNestedValue(locales['en-US'], key) || key

  // 替换参数
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      text = text.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v))
    })
  }

  return text
}

// composable
export function useI18n() {
  // 初始化
  if (typeof window !== 'undefined' && !localStorage.getItem(LOCALE_STORAGE_KEY)) {
    initLocale()
  }

  const locale = computed({
    get: () => currentLocale.value,
    set: (val: Locale) => {
      currentLocale.value = val
      localStorage.setItem(LOCALE_STORAGE_KEY, val)
    },
  })

  const isZhCN = computed(() => currentLocale.value === 'zh-CN')
  const isEnUS = computed(() => currentLocale.value === 'en-US')

  const setLocale = (newLocale: Locale) => {
    locale.value = newLocale
  }

  const toggleLocale = () => {
    setLocale(currentLocale.value === 'zh-CN' ? 'en-US' : 'zh-CN')
  }

  return {
    locale,
    isZhCN,
    isEnUS,
    setLocale,
    toggleLocale,
    t: translate,
  }
}

// 导出翻译函数
export const t = translate

export default {
  useI18n,
  t: translate,
}
