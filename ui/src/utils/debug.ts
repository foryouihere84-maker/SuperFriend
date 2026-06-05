/**
 * 调试日志模块
 * 通过 VITE_DEBUG 环境变量控制是否输出调试信息
 * 生产环境建议设置 VITE_DEBUG=false 或不设置
 */

const isDebugEnabled = import.meta.env.VITE_DEBUG === 'true'

export const debug = {
  log: (...args: unknown[]) => {
    if (isDebugEnabled) {
      console.log('[Debug]', ...args)
    }
  },
  warn: (...args: unknown[]) => {
    if (isDebugEnabled) {
      console.warn('[Warn]', ...args)
    }
  },
  error: (...args: unknown[]) => {
    console.error('[Error]', ...args)
  },
  info: (...args: unknown[]) => {
    if (isDebugEnabled) {
      console.info('[Info]', ...args)
    }
  },
}

export default debug
