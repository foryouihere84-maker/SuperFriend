import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Skill, SkillStats, SkillExecutionRequest, SkillExecutionResponse, SkillConfig, SkillsPathConfig } from '@/types/skill'
import type { ApiResponse } from '@/types/api'
import { debug } from '@/utils/debug'
import {
  getAllSkills,
  getSkill,
  getCategories,
  getSkillsByCategory,
  getTags,
  getSkillsByTag,
  searchSkills,
  suggestSkills,
  executeSkill as executeSkillApi,
  createSkill as createSkillApi,
  deleteSkill as deleteSkillApi,
  updateSkill as updateSkillApi,
  updateSkillScripts as updateSkillScriptsApi,
  reloadSkills as reloadSkillsApi,
  getSkillStats,
  getSkillsPaths,
  updateSkillsPaths as updateSkillsPathsApi
} from '@/api/skill'
import { cache, CacheKeys, CacheTTL } from '@/utils/cache'

const getErrorMessage = (response: ApiResponse | null | undefined): string => {
  return response?.error || response?.message || 'Operation failed'
}

export const useSkillStore = defineStore('skill', () => {
  const skills = ref<Skill[]>([])
  const categories = ref<string[]>([])
  const tags = ref<string[]>([])
  const stats = ref<SkillStats>({
    totalSkills: 0,
    categories: 0,
    tags: 0,
    totalExecutions:0,
    averageSuccessRate: 0
  })
  const isLoading = ref(false)
  const currentSkill = ref<Skill | null>(null)
  const searchResults = ref<Skill[]>([])
  const suggestedSkills = ref<Skill[]>([])

  const skillNames = computed(() => skills.value.map(s => s.name))
  
  const skillsByCategory = computed(() => {
    const grouped: Record<string, Skill[]> = {}
    skills.value.forEach(skill => {
      if (!grouped[skill.category]) {
        grouped[skill.category] = []
      }
      grouped[skill.category].push(skill)
    })
    return grouped
  })

  const loadAllSkills = async (forceRefresh = false) => {
    // 如果不是强制刷新，先尝试从缓存加载
    if (!forceRefresh) {
      const cachedSkills = cache.get<Skill[]>(CacheKeys.SKILLS_LIST)
      if (cachedSkills) {
        skills.value = cachedSkills
        return
      }
    }

    try {
      isLoading.value = true
      const response = await getAllSkills()
      debug.log('loadAllSkills response:', response)
      if (response.success && response.data) {
        debug.log('loadAllSkills data:', response.data)
        skills.value = response.data
        // 缓存 Skills 列表（30分钟）
        cache.set(CacheKeys.SKILLS_LIST, response.data, { ttl: CacheTTL.LONG })
      }
    } catch (error) {
      console.error('Failed to load skills:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const loadSkill = async (name: string, useCache = true) => {
    const cacheKey = `${CacheKeys.SKILL_DETAIL}${name}`

    // 如果使用缓存，先尝试从缓存加载
    if (useCache) {
      const cachedSkill = cache.get<Skill>(cacheKey)
      if (cachedSkill) {
        currentSkill.value = cachedSkill
        return cachedSkill
      }
    }

    try {
      isLoading.value = true
      const response = await getSkill(name)
      if (response.success && response.data) {
        currentSkill.value = response.data
        // 缓存单个 Skill 详情（30分钟）
        cache.set(cacheKey, response.data, { ttl: CacheTTL.LONG })
        return response.data
      }
      return null
    } catch (error) {
      console.error('Failed to load skill:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const loadCategories = async (useCache = true) => {
    if (useCache) {
      const cachedCategories = cache.get<string[]>(CacheKeys.SKILLS_CATEGORIES)
      if (cachedCategories) {
        categories.value = cachedCategories
        return
      }
    }

    try {
      const response = await getCategories()
      if (response.success && response.data) {
        categories.value = response.data
        // 缓存分类（1小时）
        cache.set(CacheKeys.SKILLS_CATEGORIES, response.data, { ttl: CacheTTL.HOUR })
      }
    } catch (error) {
      console.error('Failed to load categories:', error)
      throw error
    }
  }

  const loadSkillsByCategory = async (category: string) => {
    try {
      isLoading.value = true
      const response = await getSkillsByCategory(category)
      if (response.success && response.data) {
        return response.data
      }
      return []
    } catch (error) {
      console.error('Failed to load skills by category:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const loadTags = async (useCache = true) => {
    if (useCache) {
      const cachedTags = cache.get<string[]>(CacheKeys.SKILLS_TAGS)
      if (cachedTags) {
        tags.value = cachedTags
        return
      }
    }

    try {
      const response = await getTags()
      if (response.success && response.data) {
        tags.value = response.data
        // 缓存标签（1小时）
        cache.set(CacheKeys.SKILLS_TAGS, response.data, { ttl: CacheTTL.HOUR })
      }
    } catch (error) {
      console.error('Failed to load tags:', error)
      throw error
    }
  }

  const loadSkillsByTag = async (tag: string) => {
    try {
      isLoading.value = true
      const response = await getSkillsByTag(tag)
      if (response.success && response.data) {
        return response.data
      }
      return []
    } catch (error) {
      console.error('Failed to load skills by tag:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const search = async (keyword: string) => {
    try {
      isLoading.value = true
      const response = await searchSkills(keyword)
      if (response.success && response.data) {
        searchResults.value = response.data
        return response.data
      }
      return []
    } catch (error) {
      console.error('Failed to search skills:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const suggest = async (userRequest: string) => {
    try {
      isLoading.value = true
      const response = await suggestSkills(userRequest)
      if (response.success && response.data) {
        suggestedSkills.value = response.data
        return response.data
      }
      return []
    } catch (error) {
      console.error('Failed to suggest skills:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const executeSkill = async (request: SkillExecutionRequest): Promise<SkillExecutionResponse> => {
    try {
      isLoading.value = true
      const response = await executeSkillApi(request)
      if (response.success && response.data) {
        return response.data
      }
      throw new Error(getErrorMessage(response))
    } catch (error) {
      console.error('Failed to execute skill:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const createSkill = async (config: SkillConfig) => {
    try {
      isLoading.value = true
      const userStoreModule = await import('./user')
      const userStoreInstance = userStoreModule.useUserStore()
      const userId = userStoreInstance.user?.id ? Number(userStoreInstance.user.id) : undefined
      const response = await createSkillApi(config, userId)
      if (response.success) {
        // 清除 Skills 相关缓存
        clearSkillsCache()
        await loadAllSkills(true)
        return response.data
      }
      throw new Error(getErrorMessage(response))
    } catch (error) {
      console.error('Failed to create skill:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const deleteSkill = async (name: string) => {
    try {
      isLoading.value = true
      const userStoreModule = await import('./user')
      const userStoreInstance = userStoreModule.useUserStore()
      const userId = userStoreInstance.user?.id ? Number(userStoreInstance.user.id) : undefined
      debug.log('Deleting skill:', name, 'userId:', userId, 'user:', userStoreInstance.user)

      const response = await deleteSkillApi(name, userId)
      debug.log('Delete response:', JSON.stringify(response, null, 2))

      if (response && response.success) {
        skills.value = skills.value.filter(s => s.name !== name)
        // 清除相关缓存
        clearSkillsCache()
        return response.data
      }

      throw new Error(getErrorMessage(response))
    } catch (error) {
      console.error('Failed to delete skill:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const updateSkill = async (config: SkillConfig, skillName: string, userId?: number) => {
    try {
      isLoading.value = true
      const response = await updateSkillApi(config, skillName, userId)
      if (response.success) {
        // 清除相关缓存
        clearSkillsCache()
        await loadAllSkills(true)
        return response.data
      }
      throw new Error(getErrorMessage(response))
    } catch (error) {
      console.error('Failed to update skill:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const updateSkillScripts = async (skillName: string, scriptType: string, scriptContent: string, userId?: number) => {
    try {
      isLoading.value = true
      const response = await updateSkillScriptsApi(skillName, scriptType, scriptContent, userId)
      if (response.success) {
        // 清除该 Skill 的缓存
        cache.remove(`${CacheKeys.SKILL_DETAIL}${skillName}`)
        await loadAllSkills(true)
        return response.data
      }
      throw new Error(getErrorMessage(response))
    } catch (error) {
      console.error('Failed to update skill scripts:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const updateSkillSelection = async (skillName: string, isSelected: boolean, userId?: number) => {
    try {
      isLoading.value = true
      const { updateSkillSelection: updateSkillSelectionApi } = await import('@/api/skill')
      const response = await updateSkillSelectionApi(skillName, isSelected, userId)
      if (response.success) {
        const skill = skills.value.find(s => s.name === skillName)
        if (skill) {
          skill.isSelected = isSelected
        }
        // 清除 Skills 缓存
        cache.remove(CacheKeys.SKILLS_LIST)
        return response.data
      }
      throw new Error(getErrorMessage(response))
    } catch (error) {
      console.error('Failed to update skill selection:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const reloadSkills = async () => {
    try {
      isLoading.value = true
      const response = await reloadSkillsApi()
      if (response.success) {
        // 清除所有 Skills 相关缓存
        clearSkillsCache()
        await loadAllSkills(true)
        return response.data
      }
      throw new Error(getErrorMessage(response))
    } catch (error) {
      console.error('Failed to reload skills:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const loadStats = async (useCache = true) => {
    if (useCache) {
      const cachedStats = cache.get<SkillStats>(CacheKeys.SKILLS_STATS)
      if (cachedStats) {
        stats.value = cachedStats
        return
      }
    }

    try {
      const response = await getSkillStats()
      if (response.success && response.data) {
        stats.value = response.data
        // 缓存统计（5分钟）
        cache.set(CacheKeys.SKILLS_STATS, response.data, { ttl: CacheTTL.MEDIUM })
      }
    } catch (error) {
      console.error('Failed to load stats:', error)
    }
  }

  const clearSearchResults = () => {
    searchResults.value = []
  }

  const clearSuggestedSkills = () => {
    suggestedSkills.value = []
  }

  /**
   * 清除所有 Skills 相关缓存
   */
  const clearSkillsCache = () => {
    cache.clearByPrefix('skills_')
    cache.clearByPrefix('skill_detail')
  }

  const loadSkillsPaths = async (): Promise<SkillsPathConfig> => {
    try {
      const response = await getSkillsPaths()
      if (response.success && response.data) {
        return response.data
      }
      throw new Error(getErrorMessage(response))
    } catch (error) {
      console.error('Failed to load skills paths:', error)
      throw error
    }
  }

  const updateSkillsPaths = async (config: SkillsPathConfig): Promise<string> => {
    try {
      const response = await updateSkillsPathsApi(config)
      if (response.success) {
        return response.data
      }
      throw new Error(getErrorMessage(response))
    } catch (error) {
      console.error('Failed to update skills paths:', error)
      throw error
    }
  }

  return {
    skills,
    categories,
    tags,
    stats,
    isLoading,
    currentSkill,
    searchResults,
    suggestedSkills,
    skillNames,
    skillsByCategory,
    loadAllSkills,
    loadSkill,
    loadCategories,
    loadSkillsByCategory,
    loadTags,
    loadSkillsByTag,
    search,
    suggest,
    executeSkill,
    createSkill,
    updateSkill,
    updateSkillScripts,
    updateSkillSelection,
    deleteSkill,
    reloadSkills,
    loadStats,
    loadSkillsPaths,
    updateSkillsPaths,
    clearSearchResults,
    clearSuggestedSkills,
    clearSkillsCache
  }
})
