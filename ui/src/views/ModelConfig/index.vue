<template>
  <div class="model-config-page">
    <div class="model-config-page__bg">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
      <div class="bg-noise"></div>
    </div>

    <div class="model-config-page__main">
      <div class="model-config-page__header">
        <button class="back-btn" @click="router.push('/settings')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
          <span>返回设置</span>
        </button>
        <div class="header-content">
          <h1>模型配置</h1>
          <p>管理 AI 大模型配置，支持 OpenAI、DeepSeek、Ollama 等多种提供商</p>
        </div>
      </div>

      <div class="model-config-page__content">
        <div class="toolbar">
          <div class="toolbar-left">
            <button class="btn btn--primary" @click="openAddDialog">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              添加模型
            </button>
            <button class="btn btn--secondary" @click="loadModels">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="23 4 23 10 17 10" />
                <polyline points="1 20 1 14 7 14" />
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
              </svg>
              刷新
            </button>
          </div>
          <div class="toolbar-right">
            <span class="model-count">共 {{ models.length }} 个模型</span>
          </div>
        </div>

        <div v-loading="loading" class="model-list">
          <div
            v-for="model in models"
            :key="model.configId"
            :class="['model-card', { 'model-card--disabled': !model.isEnabled, 'model-card--default': model.isDefault }]"
          >
            <div class="model-card__header">
              <div class="model-card__info">
                <div class="model-card__name">
                  <span class="model-card__provider-badge" :class="`provider--${model.provider}`">
                    {{ providerLabel(model.provider) }}
                  </span>
                  {{ model.name }}
                </div>
                <div class="model-card__model-id">{{ model.modelId }}</div>
              </div>
              <div class="model-card__actions">
                <span v-if="model.isDefault" class="badge badge--success">默认</span>
                <span v-if="model.isSystem" class="badge badge--info">系统</span>
                <label class="toggle-switch">
                  <input type="checkbox" v-model="model.isEnabled" @change="handleToggleEnabled(model)" />
                  <span class="toggle-slider"></span>
                </label>
              </div>
            </div>

            <div class="model-card__body">
              <div class="model-card__detail">
                <span class="detail-label">API</span>
                <span class="detail-value detail-value--mono">{{ model.apiUrl }}</span>
              </div>
              <div class="model-card__detail">
                <span class="detail-label">Key</span>
                <span v-if="!model.apiKeyMasked || model.apiKeyMasked === ''" class="detail-value detail-value--warn">未配置</span>
                <span v-else class="detail-value detail-value--mono">{{ model.apiKeyMasked }}</span>
              </div>
              <div class="model-card__detail">
                <span class="detail-label">参数</span>
                <span class="detail-value">
                  max_tokens={{ model.maxTokens ?? 4096 }}, temperature={{ model.temperature ?? 0.7 }}
                </span>
              </div>
            </div>

            <div class="model-card__footer">
              <button
                v-if="!model.isDefault && model.isEnabled"
                class="btn btn--small btn--success"
                @click="handleSetDefault(model)"
              >
                设为默认
              </button>
              <button
                v-if="!model.isSystem"
                class="btn btn--small btn--primary"
                @click="openEditDialog(model)"
              >
                编辑
              </button>
              <button
                v-if="!model.isSystem"
                class="btn btn--small btn--danger"
                @click="handleDelete(model)"
              >
                删除
              </button>
              <button
                class="btn btn--small btn--secondary"
                @click="handleTest(model)"
                :disabled="testingId === model.configId"
              >
                {{ testingId === model.configId ? '测试中...' : '测试连接' }}
              </button>
            </div>
          </div>

          <div v-if="!loading && models.length === 0" class="empty-state">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <circle cx="12" cy="12" r="3" />
              <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
            </svg>
            <p>暂无模型配置</p>
            <button class="btn btn--primary" @click="openAddDialog">添加第一个模型</button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑模型' : '添加模型'"
      :width="dialogWidth"
      :close-on-click-modal="false"
      class="model-dialog"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="formData.name" placeholder="如：My GPT-4" />
        </el-form-item>

        <el-form-item label="提供商" prop="provider">
          <el-select v-model="formData.provider" placeholder="选择提供商" class="input-full" @change="handleProviderChange">
            <el-option label="OpenAI" value="openai" />
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="通义千问 (阿里云)" value="dashscope" />
            <el-option label="Ollama (本地)" value="ollama" />
            <el-option label="Claude (Anthropic)" value="anthropic" />
            <el-option label="Google Gemini" value="gemini" />
            <el-option label="Mistral AI" value="mistral" />
            <el-option label="Groq" value="groq" />
            <el-option label="Azure OpenAI" value="azure" />
            <el-option label="百度文心一言" value="ernie" />
            <el-option label="字节豆包" value="doubao" />
            <el-option label="MiniMax" value="minimax" />
            <el-option label="讯飞星火" value="xfyun" />
            <el-option label="腾讯混元" value="hunyuan" />
            <el-option label="Cohere" value="cohere" />
            <el-option label="Together AI" value="together" />
            <el-option label="Fireworks AI" value="fireworks" />
            <el-option label="Perplexity" value="perplexity" />
            <el-option label="Hugging Face" value="huggingface" />
            <el-option label="AWS Bedrock" value="bedrock" />
            <el-option label="硅基流动" value="siliconflow" />
            <el-option label="其他 (自定义)" value="custom" />
          </el-select>
        </el-form-item>

        <el-form-item label="API 地址" prop="apiUrl">
          <el-input v-model="formData.apiUrl" placeholder="https://api.openai.com/v1/chat/completions" />
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="formData.apiKey"
            type="password"
            show-password
            :placeholder="isEditing ? '留空则保持原有 Key 不变' : 'sk-...'"
          />
        </el-form-item>

        <el-form-item label="模型标识" prop="modelId">
          <el-input v-model="formData.modelId" placeholder="如：gpt-4、deepseek-chat、llama3" />
        </el-form-item>

        <el-row :gutter="isMobile ? 0 : 16">
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="Max Tokens" prop="maxTokens">
              <el-input-number v-model="formData.maxTokens" :min="256" :max="128000" :step="256" class="input-full" />
            </el-form-item>
          </el-col>
          <el-col :span="isMobile ? 24 : 12">
            <el-form-item label="Temperature" prop="temperature">
              <div class="temperature-slider">
                <el-slider v-model="formData.temperature" :min="0" :max="2" :step="0.1" :show-input="!isMobile" />
                <span v-if="isMobile" class="temperature-value">{{ formData.temperature }}</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="设为默认">
          <el-switch v-model="formData.isDefault" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ isEditing ? '保存' : '添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { OLLAMA_URL } from '@/config/api'
import {
  getAvailableModels,
  createModel,
  updateModel,
  deleteModel,
  setDefaultModel,
  testConnectionByConfigId,
  type AIModelConfigVO,
  type AIModelConfigDTO
} from '@/api/modelConfig'

const models = ref<AIModelConfigVO[]>([])
const loading = ref(false)
const saving = ref(false)
const testingId = ref('')
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingConfigId = ref('')
const formRef = ref<FormInstance>()

const router = useRouter()
const userStore = useUserStore()
const userId = computed(() => userStore.user?.id ? Number(userStore.user.id) : 1)
const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)
const dialogWidth = computed(() => isMobile.value ? '95%' : '560px')

const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

const formData = reactive<AIModelConfigDTO>({
  name: '',
  provider: 'openai',
  apiUrl: '',
  apiKey: '',
  modelId: '',
  maxTokens: 4096,
  temperature: 0.7,
  isDefault: false,
  isEnabled: true,
  sortOrder: 0
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择提供商', trigger: 'change' }],
  apiUrl: [{ required: true, message: '请输入 API 地址', trigger: 'blur' }],
  modelId: [{ required: true, message: '请输入模型标识', trigger: 'blur' }]
}

const providerLabel = (provider: string) => {
  const map: Record<string, string> = {
    openai: 'OpenAI',
    deepseek: 'DeepSeek',
    dashscope: '通义千问',
    ollama: 'Ollama',
    anthropic: 'Claude',
    gemini: 'Gemini',
    mistral: 'Mistral',
    groq: 'Groq',
    azure: 'Azure',
    ernie: '文心一言',
    doubao: '豆包',
    minimax: 'MiniMax',
    xfyun: '讯飞星火',
    hunyuan: '腾讯混元',
    cohere: 'Cohere',
    together: 'Together',
    fireworks: 'Fireworks',
    perplexity: 'Perplexity',
    huggingface: 'HuggingFace',
    bedrock: 'Bedrock',
    siliconflow: '硅基流动',
    custom: 'Custom'
  }
  return map[provider] || provider
}

const PROVIDER_URLS: Record<string, string> = {
  openai: 'https://api.openai.com/v1/chat/completions',
  deepseek: 'https://api.deepseek.com/chat/completions',
  dashscope: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
  ollama: `${OLLAMA_URL}/api/chat`,
  anthropic: 'https://api.anthropic.com/v1/messages',
  gemini: 'https://generativelanguage.googleapis.com/v1beta/models',
  mistral: 'https://api.mistral.ai/v1/chat/completions',
  groq: 'https://api.groq.com/openai/v1/chat/completions',
  azure: 'https://YOUR_RESOURCE_NAME.openai.azure.com/openai/deployments/YOUR_DEPLOYMENT_NAME/chat/completions',
  ernie: 'https://qianfan.baidubce.com/v2/chat/completions',
  doubao: 'https://ark.cn-beijing.volces.com/api/v3/chat/completions',
  minimax: 'https://api.minimax.chat/v1/text/chatcompletion_v2',
  xfyun: 'https://spark-api.xf-yun.com/v3.1/chat',
  hunyuan: 'https://api.hunyuan.cloud.tencent.com/v2/chat/completions',
  cohere: 'https://api.cohere.ai/v2/chat',
  together: 'https://api.together.xyz/v1/chat/completions',
  fireworks: 'https://api.fireworks.ai/inference/v1/chat/completions',
  perplexity: 'https://api.perplexity.ai/chat/completions',
  huggingface: 'https://api-inference.huggingface.co/models',
  bedrock: 'https://bedrock-runtime.us-east-1.amazonaws.com/model/',
  siliconflow: 'https://api.siliconflow.cn/v1/chat/completions',
  custom: ''
}

const handleProviderChange = (provider: string) => {
  if (PROVIDER_URLS[provider]) {
    formData.apiUrl = PROVIDER_URLS[provider]
  }
}

const loadModels = async () => {
  loading.value = true
  try {
    models.value = await getAvailableModels(userId.value)
  } catch (e) {
    console.error('加载模型列表失败:', e)
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  formData.name = ''
  formData.provider = 'openai'
  formData.apiUrl = ''
  formData.apiKey = ''
  formData.modelId = ''
  formData.maxTokens = 4096
  formData.temperature = 0.7
  formData.isDefault = false
  formData.isEnabled = true
  formData.sortOrder = 0
}

const openAddDialog = () => {
  isEditing.value = false
  editingConfigId.value = ''
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (model: AIModelConfigVO) => {
  isEditing.value = true
  editingConfigId.value = model.configId
  formData.name = model.name
  formData.provider = model.provider
  formData.apiUrl = model.apiUrl
  formData.apiKey = ''
  formData.modelId = model.modelId
  formData.maxTokens = model.maxTokens ?? 4096
  formData.temperature = model.temperature ?? 0.7
  formData.isDefault = model.isDefault
  formData.isEnabled = model.isEnabled
  formData.sortOrder = model.sortOrder
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEditing.value) {
        await updateModel(editingConfigId.value, formData, userId.value)
        ElMessage.success('模型配置已更新')
      } else {
        await createModel(formData, userId.value)
        ElMessage.success('模型配置已添加')
      }
      dialogVisible.value = false
      await loadModels()
    } catch (e) {
      ElMessage.error('保存失败')
    } finally {
      saving.value = false
    }
  })
}

const handleDelete = async (model: AIModelConfigVO) => {
  try {
    await ElMessageBox.confirm(`确定删除模型「${model.name}」吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteModel(model.configId, userId.value)
    ElMessage.success('已删除')
    await loadModels()
  } catch {
  }
}

const handleSetDefault = async (model: AIModelConfigVO) => {
  try {
    await setDefaultModel(model.configId, userId.value)
    ElMessage.success(`已将「${model.name}」设为默认模型`)
    await loadModels()
  } catch (e) {
    ElMessage.error('设置失败')
  }
}

const handleToggleEnabled = async (model: AIModelConfigVO) => {
  try {
    await updateModel(model.configId, { ...formData, isEnabled: model.isEnabled } as any, userId.value)
    ElMessage.success(model.isEnabled ? '已启用' : '已禁用')
  } catch (e) {
    model.isEnabled = !model.isEnabled
    ElMessage.error('操作失败')
  }
}

const handleTest = async (model: AIModelConfigVO) => {
  testingId.value = model.configId
  try {
    const result = await testConnectionByConfigId(model.configId)
    if (result.success) {
      ElMessage.success(`「${model.name}」连接成功`)
    } else {
      ElMessage.warning(`「${model.name}」连接失败：${result.message}`)
    }
  } catch (e) {
    ElMessage.error('测试失败')
  } finally {
    testingId.value = ''
  }
}

onMounted(() => {
  loadModels()
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.model-config-page {
  @include page-container;
}

.model-config-page__bg {
  @include page-background;
}

.model-config-page__main {
  @include page-main;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.model-config-page__header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  width: 100%;
  max-width: 900px;

  .back-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 16px;
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 10px;
    color: var(--chat-text-secondary);
    font-size: 14px;
    cursor: pointer;
    transition: all 0.2s ease;

    svg {
      width: 18px;
      height: 18px;
    }

    &:hover {
      background: var(--chat-surface-glass-hover);
      color: var(--chat-text-primary);
      border-color: var(--chat-border-default);
    }
  }

  .header-content {
    flex: 1;

    h1 {
      font-size: 24px;
      font-weight: 700;
      color: var(--chat-text-primary);
      margin: 0 0 4px 0;
      letter-spacing: -0.5px;
    }

    p {
      font-size: 14px;
      color: var(--chat-text-tertiary);
      margin: 0;
    }
  }
}

.model-config-page__content {
  width: 100%;
  max-width: 900px;
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 16px;
  overflow: visible;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--chat-border-subtle);
  background: var(--chat-surface-glass);

  &-left {
    display: flex;
    gap: 10px;
  }

  &-right {
    .model-count {
      font-size: 13px;
      color: var(--chat-text-muted);
    }
  }
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;

  svg {
    width: 16px;
    height: 16px;
  }

  &--primary {
    background: var(--chat-gradient-primary);
    color: var(--chat-accent-text);

    &:hover {
      transform: translateY(-1px);
      box-shadow: var(--chat-shadow-glow-purple);
    }
  }

  &--secondary {
    background: var(--chat-surface-glass);
    color: var(--chat-text-secondary);
    border: 1px solid var(--chat-border-subtle);

    &:hover {
      background: var(--chat-surface-glass-hover);
      color: var(--chat-text-primary);
    }
  }

  &--success {
    background: color-mix(in srgb, var(--chat-accent-green) 20%, transparent);
    color: var(--chat-accent-green);
    border: 1px solid color-mix(in srgb, var(--chat-accent-green) 30%, transparent);

    &:hover {
      background: color-mix(in srgb, var(--chat-accent-green) 30%, transparent);
    }
  }

  &--danger {
    background: color-mix(in srgb, var(--chat-accent-red) 20%, transparent);
    color: var(--chat-accent-red);
    border: 1px solid color-mix(in srgb, var(--chat-accent-red) 30%, transparent);

    &:hover {
      background: color-mix(in srgb, var(--chat-accent-red) 30%, transparent);
    }
  }

  &--small {
    padding: 6px 12px;
    font-size: 12px;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none !important;
  }
}

.model-list {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 200px;
}

.model-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 12px;
  padding: 16px;
  transition: all 0.25s ease;

  &:hover {
    background: var(--chat-surface-glass-hover);
    border-color: var(--chat-border-default);
    transform: translateY(-2px);
    box-shadow: var(--chat-shadow-md);
  }

  &--disabled {
    opacity: 0.5;
  }

  &--default {
    border-color: var(--chat-accent-green);
    background: color-mix(in srgb, var(--chat-accent-green) 5%, transparent);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  &__info {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__name {
    font-size: 15px;
    font-weight: 600;
    color: var(--chat-text-primary);
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__model-id {
    font-size: 12px;
    color: var(--chat-text-muted);
    font-family: 'JetBrains Mono', monospace;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-bottom: 14px;
    padding: 12px 14px;
    background: color-mix(in srgb, var(--chat-bg-primary) 20%, transparent);
    border-radius: 8px;
  }

  &__detail {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 12px;

    .detail-label {
      color: var(--chat-text-muted);
      min-width: 50px;
      flex-shrink: 0;
    }

    .detail-value {
      color: var(--chat-text-secondary);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;

      &--mono {
        font-family: 'JetBrains Mono', monospace;
      }

      &--warn {
        color: var(--chat-accent-orange);
        font-weight: 500;
      }
    }
  }

  &__footer {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
}

.model-card__provider-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;

  &.provider--openai {
    background: color-mix(in srgb, #10a37f 20%, transparent);
    color: #10a37f;
  }

  &.provider--deepseek {
    background: color-mix(in srgb, #4d6bfe 20%, transparent);
    color: #4d6bfe;
  }

  &.provider--ollama {
    background: var(--chat-surface-glass);
    color: var(--chat-text-secondary);
  }

  &.provider--anthropic {
    background: color-mix(in srgb, #d4a574 20%, transparent);
    color: #d4a574;
  }

  &.provider--dashscope {
    background: color-mix(in srgb, #ff6b00 20%, transparent);
    color: #ff6b00;
  }

  &.provider--custom {
    background: color-mix(in srgb, #9ca3af 20%, transparent);
    color: #9ca3af;
  }
}

.badge {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 500;

  &--success {
    background: color-mix(in srgb, var(--chat-accent-green) 20%, transparent);
    color: var(--chat-accent-green);
  }

  &--info {
    background: color-mix(in srgb, var(--chat-accent-cyan) 20%, transparent);
    color: var(--chat-accent-cyan);
  }
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;

  input {
    opacity: 0;
    width: 0;
    height: 0;

    &:checked + .toggle-slider {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

      &::before {
        transform: translateX(20px);
      }
    }
  }

  .toggle-slider {
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(255, 255, 255, 0.1);
    border-radius: 24px;
    transition: all 0.3s ease;

    &::before {
      content: '';
      position: absolute;
      width: 18px;
      height: 18px;
      left: 3px;
      bottom: 3px;
      background: white;
      border-radius: 50%;
      transition: all 0.3s ease;
    }
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: rgba(255, 255, 255, 0.4);

  svg {
    width: 64px;
    height: 64px;
    margin-bottom: 16px;
    opacity: 0.4;
  }

  p {
    font-size: 16px;
    margin-bottom: 20px;
  }
}

:deep(.model-dialog) {
  .el-dialog {
    background: var(--chat-bg-elevated);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 16px;

    .el-dialog__header {
      padding: 20px 24px;
      border-bottom: 1px solid var(--chat-border-subtle);

      .el-dialog__title {
        color: var(--chat-text-primary);
        font-weight: 600;
      }
    }

    .el-dialog__body {
      padding: 24px;
    }

    .el-dialog__footer {
      padding: 16px 24px;
      border-top: 1px solid var(--chat-border-subtle);
    }
  }

  .el-form-item__label {
    color: var(--chat-text-secondary);
  }

  .el-input__wrapper,
  .el-select__wrapper {
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    box-shadow: none;

    &:hover {
      border-color: var(--chat-border-default);
    }

    &.is-focus {
      border-color: var(--chat-accent-purple);
    }
  }

  .el-input__inner {
    color: var(--chat-text-primary);

    &::placeholder {
      color: var(--chat-text-muted);
    }
  }
}

@media (max-width: 768px) {
  .model-config-page__main {
    padding: 16px;
  }

  .model-config-page__header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;

    .header-content h1 {
      font-size: 20px;
    }
  }

  .toolbar {
    flex-direction: column;
    gap: 12px;

    &-left {
      width: 100%;
      flex-wrap: wrap;
    }
  }

  .model-card__footer {
    .btn {
      flex: 1;
      justify-content: center;
    }
  }

  :deep(.model-dialog) {
    .el-dialog {
      width: calc(100% - 32px) !important;
      max-width: 560px !important;
      margin: 16px auto !important;
      border-radius: 12px;
      overflow: hidden;

      .el-dialog__header {
        padding: 16px 20px;
        flex-shrink: 0;

        .el-dialog__title {
          font-size: 16px;
        }

        .el-dialog__headerbtn {
          top: 16px;
          right: 16px;

          .el-dialog__close {
            font-size: 18px;
          }
        }
      }

      .el-dialog__body {
        padding: 16px 20px;
        max-height: calc(100vh - 200px);
        overflow-y: auto;
        box-sizing: border-box;
      }

      .el-dialog__footer {
        padding: 12px 20px;
        flex-shrink: 0;
      }
    }

    .el-form {
      label-width: auto !important;

      .el-form-item {
        margin-bottom: 16px;

        .el-form-item__label {
          padding: 0 0 6px 0;
          font-size: 13px;
          width: 100% !important;
        }
      }

      .el-row {
        .el-col {
          max-width: 100%;
          flex: 0 0 100%;
        }
      }
    }

    .el-input,
    .el-select {
      width: 100% !important;

      .el-input__wrapper,
      .el-select__wrapper {
        padding: 4px 12px;
        min-height: 40px;
      }

      .el-input__inner {
        font-size: 15px;
      }
    }

    .el-input-number {
      width: 100% !important;

      .el-input__wrapper {
        padding: 0 30px 0 12px;
      }

      .el-input-number__decrease,
      .el-input-number__increase {
        width: 28px;
      }
    }

    .el-slider {
      width: 100% !important;

      .el-slider__runway {
        height: 4px;
      }

      .el-slider__button {
        width: 16px;
        height: 16px;
      }
    }

    .temperature-slider {
      display: flex;
      align-items: center;
      gap: 12px;

      .el-slider {
        flex: 1;
      }

      .temperature-value {
        min-width: 40px;
        text-align: center;
        font-size: 14px;
        font-weight: 500;
        color: var(--chat-text-primary);
        background: var(--chat-surface-glass);
        padding: 6px 12px;
        border-radius: 6px;
        border: 1px solid var(--chat-border-subtle);
      }
    }
  }
}

// 通用表单样式类
.input-full {
  width: 100%;
}
</style>
