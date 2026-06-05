<template>
  <div ref="containerRef" class="stream-text">
    <div class="stream-text__content" :style="contentStyle">
      <span
        v-for="(line, idx) in renderedLines"
        :key="idx"
        class="stream-text__line"
        :style="{ lineHeight: `${lineHeight}px` }"
      >
        {{ line }}
      </span>
      <span v-if="!done" class="stream-text__cursor"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, shallowRef } from 'vue'
import { prepare, layout, type PreparedText } from '@chenglou/pretext'

const props = withDefaults(
  defineProps<{
    content: string
    done?: boolean
    maxWidth?: number
    lineHeight?: number
    fontSize?: number
    fontFamily?: string
    fontWeight?: string
  }>(),
  {
    done: false,
    maxWidth: 800,
    lineHeight: 24,
    fontSize: 14,
    fontFamily: '"Plus Jakarta Sans", system-ui, sans-serif',
    fontWeight: '400'
  }
)

const containerRef = ref<HTMLElement>()
const preparedText = shallowRef<PreparedText | null>(null)
const containerWidth = ref(props.maxWidth)
const measuredHeight = ref(0)
const lineCount = ref(0)
const resizeObserver = ref<ResizeObserver | null>(null)

const fontConfig = computed(() => {
  return `${props.fontWeight} ${props.fontSize}px ${props.fontFamily}`
})

const contentStyle = computed(() => ({
  fontSize: `${props.fontSize}px`,
  fontFamily: props.fontFamily,
  fontWeight: props.fontWeight,
  lineHeight: `${props.lineHeight}px`
}))

const prepareText = () => {
  if (!props.content) {
    preparedText.value = null
    measuredHeight.value = 0
    lineCount.value = 0
    return
  }

  try {
    preparedText.value = prepare(props.content, fontConfig.value, {
      whiteSpace: 'pre-wrap'
    })
    updateLayout()
  } catch (e) {
    console.warn('Pretext prepare failed:', e)
    preparedText.value = null
  }
}

const updateLayout = () => {
  if (!preparedText.value) {
    measuredHeight.value = 0
    lineCount.value = 0
    return
  }

  try {
    const result = layout(preparedText.value, containerWidth.value, props.lineHeight)
    measuredHeight.value = result.height
    lineCount.value = result.lineCount
  } catch (e) {
    console.warn('Pretext layout failed:', e)
  }
}

const renderedLines = computed(() => {
  if (!props.content) return []
  
  const lines: string[] = []
  const text = props.content
  const maxWidth = containerWidth.value
  const avgCharWidth = props.fontSize * 0.6
  const charsPerLine = Math.floor(maxWidth / avgCharWidth)
  
  if (charsPerLine <= 0) return [text]
  
  let remaining = text
  while (remaining.length > 0) {
    let breakPoint = Math.min(charsPerLine, remaining.length)
    
    if (breakPoint < remaining.length) {
      const searchStart = Math.max(0, breakPoint - 20)
      const searchEnd = Math.min(remaining.length, breakPoint + 10)
      const searchText = remaining.slice(searchStart, searchEnd)
      
      const spaceIdx = searchText.lastIndexOf(' ')
      const newlineIdx = searchText.indexOf('\n')
      
      if (newlineIdx !== -1 && (spaceIdx === -1 || newlineIdx < spaceIdx)) {
        breakPoint = searchStart + newlineIdx + 1
      } else if (spaceIdx !== -1) {
        breakPoint = searchStart + spaceIdx + 1
      }
    }
    
    lines.push(remaining.slice(0, breakPoint))
    remaining = remaining.slice(breakPoint)
  }
  
  return lines
})

const updateContainerWidth = () => {
  if (containerRef.value) {
    const padding = 32
    containerWidth.value = Math.min(containerRef.value.clientWidth - padding, props.maxWidth)
    if (preparedText.value) {
      updateLayout()
    }
  }
}

watch(() => props.content, () => {
  prepareText()
}, { immediate: true })

watch(() => [props.fontSize, props.fontFamily, props.fontWeight], () => {
  prepareText()
})

watch(() => props.maxWidth, () => {
  containerWidth.value = props.maxWidth
  updateLayout()
})

onMounted(() => {
  updateContainerWidth()
  
  resizeObserver.value = new ResizeObserver(() => {
    updateContainerWidth()
  })
  
  if (containerRef.value) {
    resizeObserver.value.observe(containerRef.value)
  }
})

onUnmounted(() => {
  resizeObserver.value?.disconnect()
})

defineExpose({
  measuredHeight,
  lineCount
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.stream-text {
  width: 100%;
  position: relative;
  
  &__content {
    white-space: pre-wrap;
    word-wrap: break-word;
    overflow-wrap: break-word;
    color: var(--chat-text-primary);
  }
  
  &__line {
    display: block;
  }
  
  &__cursor {
    display: inline-block;
    width: 2px;
    height: 1em;
    background: var(--chat-gradient-primary);
    margin-left: 2px;
    vertical-align: text-bottom;
    animation: cursorBlink 0.8s ease-in-out infinite;
    border-radius: 1px;
  }
}

@keyframes cursorBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
