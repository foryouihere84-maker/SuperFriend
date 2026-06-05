import { ref, onUnmounted } from 'vue'

export interface RealtimeOptions {
  interval?: number
  enabled?: boolean
  onError?: (error: Error) => void
}

export function useRealtimeUpdate<T>(
  fetchFn: () => Promise<T>,
  options: RealtimeOptions = {}
) {
  const {
    interval = 5000,
    enabled = true,
    onError
  } = options

  const data = ref<T | null>(null)
  const isLoading = ref(false)
  const error = ref<Error | null>(null)
  const lastUpdate = ref<number>(0)

  let timer: number | null = null

  const fetchData = async () => {
    if (!enabled) return

    try {
      isLoading.value = true
      error.value = null
      const result = await fetchFn()
      data.value = result
      lastUpdate.value = Date.now()
    } catch (err) {
      error.value = err as Error
      onError?.(err as Error)
    } finally {
      isLoading.value = false
    }
  }

  const start = () => {
    if (timer) return
    fetchData()
    timer = window.setInterval(fetchData, interval)
  }

  const stop = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  const restart = () => {
    stop()
    start()
  }

  const updateInterval = (newInterval: number) => {
    stop()
    timer = window.setInterval(fetchData, newInterval)
  }

  onUnmounted(() => {
    stop()
  })

  return {
    data,
    isLoading,
    error,
    lastUpdate,
    start,
    stop,
    restart,
    updateInterval,
    fetchData
  }
}

export function useDebouncedUpdate<T>(
  fetchFn: () => Promise<T>,
  delay: number = 300
) {
  const data = ref<T | null>(null)
  const isLoading = ref(false)
  let timeout: number | null = null

  const debouncedFetch = async () => {
    if (timeout) {
      clearTimeout(timeout)
    }

    timeout = window.setTimeout(async () => {
      try {
        isLoading.value = true
        const result = await fetchFn()
        data.value = result
      } catch (err) {
        console.error('Debounced fetch error:', err)
      } finally {
        isLoading.value = false
      }
    }, delay)
  }

  const cancel = () => {
    if (timeout) {
      clearTimeout(timeout)
      timeout = null
    }
  }

  onUnmounted(() => {
    cancel()
  })

  return {
    data,
    isLoading,
    fetch: debouncedFetch,
    cancel
  }
}
