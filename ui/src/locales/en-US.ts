/**
 * English Language Pack
 */
export default {
  // App
  app: {
    name: 'Super Friend',
    title: 'Super Friend - AI Assistant Platform',
  },

  // Common
  common: {
    confirm: 'Confirm',
    cancel: 'Cancel',
    save: 'Save',
    delete: 'Delete',
    edit: 'Edit',
    add: 'Add',
    search: 'Search',
    reset: 'Reset',
    loading: 'Loading...',
    success: 'Success',
    failed: 'Failed',
    error: 'Error',
    warning: 'Warning',
    info: 'Info',
    yes: 'Yes',
    no: 'No',
    ok: 'OK',
    close: 'Close',
    back: 'Back',
    next: 'Next',
    previous: 'Previous',
    submit: 'Submit',
    retry: 'Retry',
    refresh: 'Refresh',
  },

  // Auth
  auth: {
    login: 'Login',
    logout: 'Logout',
    register: 'Register',
    username: 'Username',
    password: 'Password',
    confirmPassword: 'Confirm Password',
    email: 'Email',
    loginSuccess: 'Login successful',
    logoutSuccess: 'Logged out successfully',
    loginFailed: 'Login failed',
    registerSuccess: 'Registration successful',
    confirmLogout: 'Are you sure you want to logout?',
    placeholder: {
      username: 'Please enter username',
      password: 'Please enter password',
      email: 'Please enter email',
      confirmPassword: 'Please confirm password',
    },
  },

  // Chat
  chat: {
    title: 'Chat',
    placeholder: 'Type a message...',
    thinking: 'Thinking...',
    agentExecuting: 'Agent is executing...',
    howCanIHelp: 'How can I help you Today?',
    send: 'Send',
    newChat: 'New Chat',
    clearHistory: 'Clear History',
    searchHistory: 'Search conversations...',
    noMessages: 'No messages yet',
    inputInstruction: 'Input additional instructions...',
  },

  // Search
  search: {
    placeholder: 'Search...',
    noResults: 'No results found',
    results: 'Search Results',
  },

  // Skills
  skill: {
    title: 'Skill Management',
    searchPlaceholder: 'Search skills...',
    name: 'Skill Name',
    description: 'Description',
    category: 'Category',
    tags: 'Tags',
    create: 'Create Skill',
    execute: 'Execute',
    reload: 'Reload',
  },

  // MCP
  mcp: {
    title: 'MCP Server Management',
    serverName: 'Server Name',
    status: 'Status',
    enabled: 'Enabled',
    disabled: 'Disabled',
    start: 'Start',
    stop: 'Stop',
    restart: 'Restart',
  },

  // Model Config
  model: {
    title: 'Model Configuration',
    provider: 'Provider',
    apiUrl: 'API URL',
    apiKey: 'API Key',
    modelId: 'Model ID',
    test: 'Test Connection',
    save: 'Save',
    cancel: 'Cancel',
    placeholder: {
      apiUrl: 'https://api.openai.com/v1/chat/completions',
      apiKey: 'sk-...',
      modelId: 'e.g., gpt-4, deepseek-chat, llama3',
      name: 'e.g., My GPT-4',
      selectProvider: 'Select Provider',
    },
  },

  // Knowledge Graph
  knowledgeGraph: {
    title: 'Knowledge Graph',
    nodes: 'Nodes',
    relations: 'Relations',
    addNode: 'Add Node',
    addRelation: 'Add Relation',
    search: 'Search...',
  },

  // User
  user: {
    profile: 'Profile',
    settings: 'Settings',
    preferences: 'Preferences',
    theme: 'Theme',
    lightMode: 'Light Mode',
    darkMode: 'Dark Mode',
    summary: 'User Profile Summary',
    name: 'Name',
    placeholder: {
      summary: 'Please enter user profile summary...',
      name: 'Please enter name',
    },
  },

  // About
  about: {
    title: 'About',
    description: 'AI Assistant Platform',
    features: 'Features',
    differentFrom: 'What makes Super Friend different from other AI chatbots?',
    copyright: '© 2025 Super Friend. Making AI your work partner.',
  },

  // Error Messages
  errors: {
    operationFailed: 'Operation failed',
    requestFailed: 'Request failed',
    networkError: 'Network error',
    timeout: 'Request timeout',
    serverError: 'Server error',
    unauthorized: 'Unauthorized, please login again',
    notFound: 'Not found',
    unknown: 'Unknown error',
  },

  // File Upload
  upload: {
    dragHint: 'Drag & drop or paste',
    maxSize: 'Max {maxSize}MB',
    unsupportedType: 'File type not supported',
    uploadFailed: 'Upload failed',
    uploading: 'Uploading...',
  },
}
