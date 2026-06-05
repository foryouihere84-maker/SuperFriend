import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import EmptyLayout from '@/layouts/EmptyLayout.vue'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: EmptyLayout,
    children: [
      {
        path: '',
        name: 'Login',
        component: () => import('@/views/Login/index.vue'),
        meta: {
            title: '用户登录',
            requiresAuth: false
        }
      }
    ]
  },
  {
    path: '/register',
    component: EmptyLayout,
    children: [
      {
        path: '',
        name: 'Register',
        component: () => import('@/views/Register/index.vue'),
        meta: {
            title: '用户注册',
            requiresAuth: false
        }
      }
    ]
  },
  {
    path: '/',
    component: DefaultLayout,
    children: [
      {
        path: '',
        redirect: '/chat'
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/Chat/index.vue'),
        meta: {
          title: 'AI 对话',
          requiresAuth: true
        }
      },
      {
        path: 'mcp',
        name: 'MCPManager',
        component: () => import('@/views/MCPManager/index.vue'),
        meta: {
          title: 'MCP 服务器管理',
          requiresAuth: true
        }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/Settings/index.vue'),
        meta: {
          title: '设置',
          requiresAuth: true
        }
      },
      {
        path: 'settings/models',
        name: 'ModelConfig',
        component: () => import('@/views/ModelConfig/index.vue'),
        meta: {
          title: '模型配置',
          requiresAuth: true
        }
      },
      {
        path: 'settings/token-stats',
        name: 'TokenStats',
        component: () => import('@/views/TokenStats/index.vue'),
        meta: {
          title: 'Token 统计',
          requiresAuth: true
        }
      },
      {
        path: 'knowledge-graph',
        name: 'KnowledgeGraph',
        component: () => import('@/views/KnowledgeGraph/index.vue'),
        meta: {
          title: '知识图谱',
          requiresAuth: true
        }
      },
      {
        path: 'profile',
        name: 'UserProfile',
        component: () => import('@/views/UserProfile/index.vue'),
        meta: {
          title: '用户画像',
          requiresAuth: true
        }
      },

      {
        path: 'api-test',
        name: 'ApiTest',
        component: () => import('@/views/ApiTest/index.vue'),
        meta: {
          title: 'API连接测试',
          requiresAuth: false
        }
      },
      {
        path: 'about',
        name: 'About',
        component: () => import('@/views/About/index.vue'),
        meta: {
          title: '关于',
          requiresAuth: false
        }
      },
      {
        path: 'skills',
        name: 'Skills',
        component: () => import('@/views/SkillManager/index.vue'),
        meta: {
          title: 'Skills 技能管理',
          requiresAuth: true
        }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound/index.vue'),
    meta: {
      title: '页面不存在'
    }
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

let authInitialized = false

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || 'Super Friend'} - Super Friend`
  
  const userStore = useUserStore()
  
  if (!authInitialized) {
    userStore.initAuth()
    authInitialized = true
  }
  
  const requiresAuth = to.meta.requiresAuth !== false
  
  if (requiresAuth && !userStore.isLoggedIn) {
    next('/login')
  } else if (!requiresAuth && userStore.isLoggedIn && (to.path === '/login' || to.path === '/register')) {
    next('/chat')
  } else {
    next()
  }
})

export default router
