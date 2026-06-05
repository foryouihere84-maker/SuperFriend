import { onMounted, onUnmounted, type Ref } from 'vue'

interface SwipeOptions {
  onSwipeLeft?: () => void
  onSwipeRight?: () => void
  threshold?: number
  maxTime?: number
}

export function useSwipeGesture(elementRef: Ref<HTMLElement | null>, options: SwipeOptions = {}) {
  const { onSwipeLeft, onSwipeRight, threshold = 50, maxTime = 500 } = options

  let startX = 0
  let startY = 0
  let startTime = 0

  const handleTouchStart = (e: TouchEvent) => {
    startX = e.touches[0].clientX
    startY = e.touches[0].clientY
    startTime = Date.now()
  }

  const handleTouchEnd = (e: TouchEvent) => {
    const deltaX = e.changedTouches[0].clientX - startX
    const deltaY = e.changedTouches[0].clientY - startY
    const deltaTime = Date.now() - startTime

    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > threshold) {
      if (deltaTime < maxTime) {
        if (deltaX > 0 && onSwipeRight) {
          onSwipeRight()
        } else if (deltaX < 0 && onSwipeLeft) {
          onSwipeLeft()
        }
      }
    }
  }

  onMounted(() => {
    elementRef.value?.addEventListener('touchstart', handleTouchStart, { passive: true })
    elementRef.value?.addEventListener('touchend', handleTouchEnd, { passive: true })
  })

  onUnmounted(() => {
    elementRef.value?.removeEventListener('touchstart', handleTouchStart)
    elementRef.value?.removeEventListener('touchend', handleTouchEnd)
  })
}
