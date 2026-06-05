import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { MCPServer, MCPServerStats } from '@/types/mcp'
import {
  getAvailableServers,
  selectServer,
  deselectServer,
  saveServerSelection,
  getSelectedConfig,
  reloadConfig,
  getServerStats
} from '@/api/mcp'
import { cache, CacheKeys, CacheTTL } from '@/utils/cache'

export const useMCPStore = defineStore('mcp', () => {
  const servers = ref<MCPServer[]>([])
  const selectedServerNames = ref<Set<string>>(new Set())
  const isLoading = ref(false)
  const stats = ref<MCPServerStats>({
    totalServers: 0,
    selectedServers: 0,
    runningServers: 0,
    totalTools: 0
  })
  const lastFetchTime = ref<number>(0) // 上次获取时间

  const selectedServers = computed(() =>
    servers.value.filter(server => selectedServerNames.value.has(server.name))
  )

  const runningServers = computed(() =>
    servers.value.filter(server => server.running)
  )

  const loadServers = async (forceRefresh = false) => {
    // 如果不是强制刷新，先尝试从缓存加载
    if (!forceRefresh) {
      const cachedServers = cache.get<{ servers: MCPServer[], stats: MCPServerStats }>(CacheKeys.MCP_SERVERS)
      if (cachedServers) {
        servers.value = cachedServers.servers
        stats.value = cachedServers.stats
        selectedServerNames.value = new Set(
          servers.value.filter(s => s.selected).map(s => s.name)
        )
        lastFetchTime.value = Date.now()
        return
      }
    }

    try {
      isLoading.value = true
      const response = await getAvailableServers()
      servers.value = response.servers
      stats.value = response.stats
      selectedServerNames.value = new Set(
        servers.value.filter(s => s.selected).map(s => s.name)
      )
      lastFetchTime.value = Date.now()

      // 缓存服务器列表（5分钟）
      cache.set(CacheKeys.MCP_SERVERS, {
        servers: response.servers,
        stats: response.stats
      }, { ttl: CacheTTL.MEDIUM })
    } catch (error) {
      console.error('Failed to load servers:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const selectServerByName = async (serverName: string) => {
    try {
      await selectServer(serverName)
      selectedServerNames.value.add(serverName)
      const server = servers.value.find(s => s.name === serverName)
      if (server) server.selected = true
      // 清除缓存，因为选择状态改变了
      cache.remove(CacheKeys.MCP_SERVERS)
    } catch (error) {
      console.error('Failed to select server:', error)
      throw error
    }
  }

  const deselectServerByName = async (serverName: string) => {
    try {
      await deselectServer(serverName)
      selectedServerNames.value.delete(serverName)
      const server = servers.value.find(s => s.name === serverName)
      if (server) server.selected = false
      // 清除缓存
      cache.remove(CacheKeys.MCP_SERVERS)
    } catch (error) {
      console.error('Failed to deselect server:', error)
      throw error
    }
  }

  const toggleServer = async (serverName: string) => {
    if (selectedServerNames.value.has(serverName)) {
      await deselectServerByName(serverName)
    } else {
      await selectServerByName(serverName)
    }
  }

  const saveSelection = async () => {
    try {
      const serverNames = Array.from(selectedServerNames.value)
      await saveServerSelection(serverNames)
      // 清除缓存
      cache.remove(CacheKeys.MCP_SERVERS)
    } catch (error) {
      console.error('Failed to save selection:', error)
      throw error
    }
  }

  const loadSelectedConfig = async () => {
    // 尝试从缓存加载
    const cachedConfig = cache.get(CacheKeys.MCP_SELECTED_CONFIG)
    if (cachedConfig) {
      return cachedConfig
    }

    try {
      const config = await getSelectedConfig()
      // 缓存配置（30分钟）
      cache.set(CacheKeys.MCP_SELECTED_CONFIG, config, { ttl: CacheTTL.LONG })
      return config
    } catch (error) {
      console.error('Failed to load selected config:', error)
      throw error
    }
  }

  const reloadServerConfig = async () => {
    try {
      await reloadConfig()
      // 清除所有 MCP 相关缓存
      cache.clearByPrefix('mcp_')
      await loadServers(true)
    } catch (error) {
      console.error('Failed to reload config:', error)
      throw error
    }
  }

  const loadStats = async (useCache = true): Promise<MCPServerStats> => {
    // 如果使用缓存且存在有效缓存
    if (useCache) {
      const cachedStats = cache.get<MCPServerStats>(CacheKeys.MCP_STATS)
      if (cachedStats) {
        stats.value = cachedStats
        return stats.value
      }
    }

    try {
      stats.value = await getServerStats()
      // 缓存统计（1分钟，统计数据变化较快）
      cache.set(CacheKeys.MCP_STATS, stats.value, { ttl: CacheTTL.SHORT })
      return stats.value
    } catch (error) {
      console.error('Failed to load stats:', error)
      return stats.value
    }
  }

  const selectAll = () => {
    servers.value.forEach(server => {
      selectedServerNames.value.add(server.name)
      server.selected = true
    })
    // 清除缓存
    cache.remove(CacheKeys.MCP_SERVERS)
  }

  const deselectAll = () => {
    selectedServerNames.value.clear()
    servers.value.forEach(server => {
      server.selected = false
    })
    // 清除缓存
    cache.remove(CacheKeys.MCP_SERVERS)
  }

  /**
   * 清除所有 MCP 相关缓存
   */
  const clearCache = () => {
    cache.clearByPrefix('mcp_')
  }

  return {
    servers,
    selectedServerNames,
    selectedServers,
    runningServers,
    isLoading,
    stats,
    lastFetchTime,
    loadServers,
    selectServerByName,
    deselectServerByName,
    toggleServer,
    saveSelection,
    loadSelectedConfig,
    reloadServerConfig,
    loadStats,
    selectAll,
    deselectAll,
    clearCache
  }
})
