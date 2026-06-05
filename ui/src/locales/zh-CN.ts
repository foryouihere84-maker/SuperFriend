/**
 * 中文语言包
 */
export default {
  // 应用
  app: {
    name: 'Super Friend',
    title: 'Super Friend - 智能AI助手平台',
  },

  // 通用
  common: {
    confirm: '确定',
    cancel: '取消',
    save: '保存',
    delete: '删除',
    edit: '编辑',
    add: '添加',
    search: '搜索',
    reset: '重置',
    loading: '加载中...',
    success: '操作成功',
    failed: '操作失败',
    error: '错误',
    warning: '警告',
    info: '提示',
    yes: '是',
    no: '否',
    ok: '好的',
    close: '关闭',
    back: '返回',
    next: '下一步',
    previous: '上一步',
    submit: '提交',
    retry: '重试',
    refresh: '刷新',
  },

  // 认证
  auth: {
    login: '登录',
    logout: '退出登录',
    register: '注册',
    username: '用户名',
    password: '密码',
    confirmPassword: '确认密码',
    email: '邮箱',
    loginSuccess: '登录成功',
    logoutSuccess: '已退出登录',
    loginFailed: '登录失败',
    registerSuccess: '注册成功',
    confirmLogout: '确定要退出登录吗？',
    placeholder: {
      username: '请输入用户名',
      password: '请输入密码',
      email: '请输入邮箱',
      confirmPassword: '请确认密码',
    },
  },

  // 聊天
  chat: {
    title: '聊天',
    placeholder: '输入消息...',
    thinking: '思考中...',
    agentExecuting: 'Agent 正在执行中...',
    howCanIHelp: 'How can I help you Today?',
    send: '发送',
    newChat: '新建对话',
    clearHistory: '清空历史',
    searchHistory: '搜索对话...',
    noMessages: '暂无消息',
    inputInstruction: '输入追加的指令...',
  },

  // 搜索
  search: {
    placeholder: '搜索你想搜索的内容...',
    noResults: '未找到结果',
    results: '搜索结果',
  },

  // 技能
  skill: {
    title: '技能管理',
    searchPlaceholder: '搜索技能...',
    name: '技能名称',
    description: '描述',
    category: '分类',
    tags: '标签',
    create: '创建技能',
    execute: '执行',
    reload: '重新加载',
  },

  // MCP
  mcp: {
    title: 'MCP 服务器管理',
    serverName: '服务器名称',
    status: '状态',
    enabled: '已启用',
    disabled: '已禁用',
    start: '启动',
    stop: '停止',
    restart: '重启',
  },

  // 模型配置
  model: {
    title: '模型配置',
    provider: '提供商',
    apiUrl: 'API 地址',
    apiKey: 'API Key',
    modelId: '模型标识',
    test: '测试连接',
    save: '保存',
    cancel: '取消',
    placeholder: {
      apiUrl: 'https://api.openai.com/v1/chat/completions',
      apiKey: 'sk-...',
      modelId: '如：gpt-4、deepseek-chat、llama3',
      name: '如：My GPT-4',
      selectProvider: '选择提供商',
    },
  },

  // 知识图谱
  knowledgeGraph: {
    title: '知识图谱',
    nodes: '节点',
    relations: '关系',
    addNode: '添加节点',
    addRelation: '添加关系',
    search: '搜索...',
  },

  // 用户
  user: {
    profile: '个人中心',
    settings: '设置',
    preferences: '偏好设置',
    theme: '主题',
    lightMode: '浅色模式',
    darkMode: '深色模式',
    summary: '用户画像摘要',
    name: '名称',
    placeholder: {
      summary: '请输入用户画像摘要...',
      name: '请输入名称',
    },
  },

  // 关于
  about: {
    title: '关于',
    description: '智能AI助手平台',
    features: '功能特点',
    differentFrom: 'Super Friend和其他AI聊天机器人有什么不同？',
    copyright: '© 2025 Super Friend. 让AI成为您的工作伙伴',
  },

  // 错误消息
  errors: {
    operationFailed: '操作失败',
    requestFailed: '请求失败',
    networkError: '网络错误',
    timeout: '请求超时',
    serverError: '服务器错误',
    unauthorized: '未授权，请重新登录',
    notFound: '未找到',
    unknown: '未知错误',
  },

  // 文件上传
  upload: {
    dragHint: '支持拖拽、粘贴',
    maxSize: '最大{maxSize}MB',
    unsupportedType: '文件类型不支持',
    uploadFailed: '上传失败',
    uploading: '上传中...',
  },
}
