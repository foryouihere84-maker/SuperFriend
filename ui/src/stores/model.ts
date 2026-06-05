import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getAvailableModels,
  createModel,
  updateModel,
  deleteModel,
  setDefaultModel,
  testConnection,
  testConnectionByConfigId,
  type AIModelConfigVO,
  type AIModelConfigDTO
} from '@/api/modelConfig'
import { cache, CacheKeys, CacheTTL } from '@/utils/cache'

export const useModelStore = defineStore('model', () => {
  const models = ref<AIModelConfigVO[]>([])
  const isLoading = ref(false)
  const defaultModel = computed(() => models.value.find(m => m.isDefault && m.isEnabled))

  /**
   * 加载可用模型列表
   */
  const loadModels = async (userId?: number, forceRefresh = false) => {
    // 如果不是强制刷新，先尝试从缓存加载
    if (!forceRefresh) {
      const cachedModels = cache.get<AIModelConfigVO[]>(CacheKeys.MODEL_LIST)
      if (cachedModels) {
        models.value = cachedModels
        return
      }
    }

    try {
      isLoading.value = true
      models.value = await getAvailableModels(userId)
      // 缓存模型列表（30分钟）
      cache.set(CacheKeys.MODEL_LIST, models.value, { ttl: CacheTTL.LONG })
    } catch (error) {
      console.error('Failed to load models:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取单个模型详情
   */
  const getModel = async (configId: string, userId?: number, useCache = true) => {
    const cacheKey = `${CacheKeys.MODEL_DETAIL}${configId}`

    if (useCache) {
      const cachedModel = cache.get<AIModelConfigVO>(cacheKey)
      if (cachedModel) {
        return cachedModel
      }
    }

    try {
      const { getModel: getModelApi } = await import('@/api/modelConfig')
      const model = await getModelApi(configId, userId)
      // 缓存模型详情（30分钟）
      cache.set(cacheKey, model, { ttl: CacheTTL.LONG })
      return model
    } catch (error) {
      console.error('Failed to get model:', error)
      throw error
    }
  }

  /**
   * 创建模型配置
   */
  const createModelConfig = async (data: AIModelConfigDTO, userId: number) => {
    try {
      isLoading.value = true
      const model = await createModel(data, userId)
      // 清除缓存并重新加载
      clearModelCache()
      await loadModels(userId, true)
      return model
    } catch (error) {
      console.error('Failed to create model:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 更新模型配置
   */
  const updateModelConfig = async (configId: string, data: AIModelConfigDTO, userId: number) => {
    try {
      isLoading.value = true
      const model = await updateModel(configId, data, userId)
      // 清除缓存并重新加载
      clearModelCache()
      await loadModels(userId, true)
      return model
    } catch (error) {
      console.error('Failed to update model:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 删除模型配置
   */
  const deleteModelConfig = async (configId: string, userId: number) => {
    try {
      isLoading.value = true
      await deleteModel(configId, userId)
      // 清除缓存并重新加载
      clearModelCache()
      await loadModels(userId, true)
    } catch (error) {
      console.error('Failed to delete model:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 设置默认模型
   */
  const setAsDefaultModel = async (configId: string, userId: number) => {
    try {
      await setDefaultModel(configId, userId)
      // 更新本地状态
      models.value.forEach(m => {
        m.isDefault = m.configId === configId
      })
      // 更新缓存
      cache.set(CacheKeys.MODEL_LIST, models.value, { ttl: CacheTTL.LONG })
    } catch (error) {
      console.error('Failed to set default model:', error)
      throw error
    }
  }

  /**
   * 测试连接
   */
  const testModelConnection = async (data: AIModelConfigDTO) => {
    return testConnection(data)
  }

  /**
   * 通过配置ID测试连接
   */
  const testModelConnectionById = async (configId: string) => {
    return testConnectionByConfigId(configId)
  }

  /**
   * 清除所有模型相关缓存
   */
  const clearModelCache = () => {
    cache.clearByPrefix('model_')
  }

  /**
   * 获取启用的模型列表
   */
  const enabledModels = computed(() => models.value.filter(m => m.isEnabled))

  return {
    models,
    isLoading,
    defaultModel,
    enabledModels,
    loadModels,
    getModel,
    createModelConfig,
    updateModelConfig,
    deleteModelConfig,
    setAsDefaultModel,
    testModelConnection,
    testModelConnectionById,
    clearModelCache
  }
})
