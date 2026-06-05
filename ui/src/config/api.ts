/**
 * API 配置文件
 * 集中管理所有后端 API 地址和路径配置
 */

// 后端服务地址（用于 axios baseURL）
// 生产环境使用空字符串，通过 Nginx 代理访问后端
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

// 搜索服务地址
export const SEARCH_SERVICE_URL = import.meta.env.VITE_SEARCH_SERVICE_URL || 'http://localhost:8088'

// Ollama 服务地址
export const OLLAMA_URL = import.meta.env.VITE_OLLAMA_URL || 'http://localhost:11434'

// API 版本路径
export const API_PATHS = {
  AI: '/api/v16/ai',
  AUTH: '/api/auth',
  MODEL_CONFIG: '/api/v16/model-config',
  SKILL: '/api/v16/skills',
  MCP: '/api/v16/mcp',
  AGENT: '/api/v16/agent',
  KNOWLEDGE_GRAPH: '/api/v16/knowledge-graph',
  FILE: '/api/v16/files',
  CHAT: '/api/v16/chat',
} as const

// Provider URLs（AI 模型提供商端点）
export const PROVIDER_URLS: Record<string, string> = {
  // 国际主流
  openai: 'https://api.openai.com/v1/chat/completions',
  anthropic: 'https://api.anthropic.com/v1/messages',
  gemini: 'https://generativelanguage.googleapis.com/v1beta/models',
  azure: 'https://{resource}.openai.azure.com/openai/deployments/{deployment}/chat/completions',
  azureai: 'https://{resource}.cognitiveservices.azure.com/openai/deployments/{deployment}/chat/completions',
  cohere: 'https://api.cohere.ai/v1/chat',
  mistral: 'https://api.mistral.ai/v1/chat/completions',
  groq: 'https://api.groq.com/openai/v1/chat/completions',
  perplexity: 'https://api.perplexity.ai/chat/completions',
  ai21: 'https://api.ai21.com/studio/v1/chat/completions',
  
  // 中国主流
  deepseek: 'https://api.deepseek.com/chat/completions',
  dashscope: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
  qwen: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
  moonshot: 'https://api.moonshot.cn/v1/chat/completions',
  zhipu: 'https://open.bigmodel.cn/api/paas/v4/chat/completions',
  baichuan: 'https://api.baichuan-ai.com/v1/chat/completions',
  ernie: 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions',
  hunyuan: 'https://hunyuan.tencentcloudapi.com/v1/chat/completions',
  spark: 'https://spark-api-open.xf-yun.com/v1/chat/completions',
  minimax: 'https://api.minimax.chat/v1/text/chatcompletion_v2',
  stepfun: 'https://api.stepfun.com/v1/chat/completions',
  
  // 本地/开源
  ollama: `${OLLAMA_URL}/api/chat`,
  lmstudio: 'http://localhost:1234/v1/chat/completions',
  textgenerationwebui: 'http://localhost:5000/v1/chat/completions',
  koboldcpp: 'http://localhost:5001/api/v1/generate',
  llamacpp: 'http://localhost:8080/completion',
  vllm: 'http://localhost:8000/v1/chat/completions',
  tensorrtllm: 'http://localhost:8000/v2/models/ensemble/generate',
  sglang: 'http://localhost:30000/v1/chat/completions',
  
  // 其他云服务
  awsbedrock: 'https://bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke',
  googlevertexai: 'https://{region}-aiplatform.googleapis.com/v1/projects/{project}/locations/{region}/publishers/google/models/{model}:predict',
  cloudflare: 'https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/run/{model}',
  replicate: 'https://api.replicate.com/v1/predictions',
  fireworks: 'https://api.fireworks.ai/inference/v1/chat/completions',
  together: 'https://api.together.xyz/v1/chat/completions',
  anyscale: 'https://api.endpoints.anyscale.com/v1/chat/completions',
  octoai: 'https://text.octoai.run/v1/chat/completions',
  deepinfra: 'https://api.deepinfra.com/v1/inference/{model}',
  nvidia: 'https://integrate.api.nvidia.com/v1/chat/completions',
  
  // 自定义
  custom: '',
} as const

// 超时配置 (ms)
export const API_TIMEOUT = {
  DEFAULT: 30000,
  FILE_UPLOAD: 60000,
  FILE_DOWNLOAD: 120000,
} as const
