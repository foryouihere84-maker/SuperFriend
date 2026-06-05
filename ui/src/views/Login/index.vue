<template>
  <div class="login-page">
    <div class="login-page__container">
      <div class="login-card">
        <div class="login-card__header">
          <Logo />
          <h1 class="login-card__title">欢迎回来</h1>
          <p class="login-card__subtitle">登录您的账户继续使用</p>
        </div>

        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <div class="form-group">
              <label class="form-label">用户名</label>
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                size="large"
                clearable
              >
                <template #prefix>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                    <circle cx="12" cy="7" r="4" />
                  </svg>
                </template>
              </el-input>
            </div>
          </el-form-item>

          <el-form-item prop="password">
            <div class="form-group">
              <label class="form-label">密码</label>
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                clearable
                @keyup.enter="handleLogin"
              >
                <template #prefix>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                  </svg>
                </template>
              </el-input>
            </div>
          </el-form-item>

          <div class="form-options">
            <label class="checkbox-label">
              <input type="checkbox" v-model="rememberMe" />
              <span class="checkbox-custom"></span>
              <span class="checkbox-text">记住我</span>
            </label>
            <a href="#" class="forgot-link">忘记密码？</a>
          </div>

          <el-form-item>
            <button
              type="submit"
              class="submit-btn"
              :disabled="userStore.isLoading"
            >
              <span v-if="!userStore.isLoading">登录</span>
              <span v-else class="loading-content">
                <span class="spinner"></span>
                登录中...
              </span>
            </button>
          </el-form-item>

          <div class="login-footer">
            <span>还没有账号？</span>
            <router-link to="/register" class="auth-link">立即注册</router-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { FormInstance, FormRules } from 'element-plus'
import Logo from '@/components/Logo/index.vue'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance>()
const rememberMe = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  try {
    await loginFormRef.value.validate()
    await userStore.login(loginForm.username, loginForm.password)
    ElMessage.success('登录成功')
    router.push('/chat')
  } catch (error: any) {
    const message = error.response?.data?.message || error.message || '登录失败'
    ElMessage.error(message)
  }
}

onMounted(() => {
  userStore.initAuth()
  if (userStore.isLoggedIn) {
    router.push('/chat')
  }
})
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.login-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--sf-page-bg);
}

.login-page__container {
  width: 100%;
  max-width: 400px;
  padding: 20px;
}

.login-card {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-xl);
  padding: 40px;
  transition: all var(--sf-transition-fast);

  &:hover {
    border-color: var(--sf-border-default);
    box-shadow: var(--sf-shadow-md);
  }

  &__header {
    text-align: center;
    margin-bottom: 32px;
  }

  &__title {
    font-size: 24px;
    font-weight: 600;
    color: var(--sf-text-primary);
    margin: 0 0 8px 0;
    letter-spacing: -0.3px;
  }

  &__subtitle {
    font-size: 14px;
    color: var(--sf-text-tertiary);
    margin: 0;
  }
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 24px;

  &__icon {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--sf-text-primary);
    border-radius: var(--sf-radius-md);

    svg {
      width: 22px;
      height: 22px;
      color: var(--sf-bg-white);
    }
  }

  &__text {
    font-size: 20px;
    font-weight: 700;
    color: var(--sf-text-primary);
    letter-spacing: -0.3px;
  }
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 20px;
  }
}

.form-group {
  width: 100%;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--sf-text-secondary);
  margin-bottom: 8px;
}

:deep(.el-input__wrapper) {
  background: var(--sf-bg-white);
  border: 1px solid var(--sf-border-default);
  border-radius: var(--sf-radius-md);
  padding: 12px 14px;
  box-shadow: none;
  transition: all var(--sf-transition-fast);

  &:hover {
    border-color: var(--sf-border-strong);
  }

  &.is-focus {
    border-color: var(--sf-accent);
    box-shadow: 0 0 0 3px var(--sf-accent-light);
  }
}

:deep(.el-input__inner) {
  color: var(--sf-text-primary);
  font-size: 14px;

  &::placeholder {
    color: var(--sf-text-placeholder);
  }
}

:deep(.el-input__prefix) {
  color: var(--sf-text-tertiary);

  svg {
    width: 18px;
    height: 18px;
  }
}

:deep(.el-input__suffix) {
  color: var(--sf-text-tertiary);
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--sf-text-secondary);

  input {
    display: none;
  }

  .checkbox-custom {
    width: 18px;
    height: 18px;
    border: 1px solid var(--sf-border-default);
    border-radius: var(--sf-radius-sm);
    position: relative;
    transition: all var(--sf-transition-fast);

    &::after {
      content: '';
      position: absolute;
      top: 2px;
      left: 5px;
      width: 5px;
      height: 9px;
      border: solid var(--sf-bg-white);
      border-width: 0 2px 2px 0;
      transform: rotate(45deg);
      opacity: 0;
      transition: opacity var(--sf-transition-fast);
    }
  }

  input:checked + .checkbox-custom {
    background: var(--sf-text-primary);
    border-color: var(--sf-text-primary);

    &::after {
      opacity: 1;
    }
  }
}

.forgot-link {
  font-size: 13px;
  color: var(--sf-text-secondary);
  text-decoration: none;
  transition: color var(--sf-transition-fast);

  &:hover {
    color: var(--sf-text-primary);
  }
}

.submit-btn {
  width: 100%;
  height: 44px;
  background: var(--sf-text-primary);
  border: none;
  border-radius: var(--sf-radius-md);
  color: var(--sf-bg-white);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--sf-transition-fast);

  &:hover:not(:disabled) {
    background: var(--sf-text-secondary);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.loading-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.login-footer {
  text-align: center;
  font-size: 14px;
  color: var(--sf-text-tertiary);

  .auth-link {
    color: var(--sf-text-primary);
    font-weight: 500;
    text-decoration: none;
    margin-left: 4px;
    transition: color var(--sf-transition-fast);

    &:hover {
      color: var(--sf-text-secondary);
    }
  }
}

@media (max-width: 768px) {
  .login-page {
    padding: 16px;
  }

  .login-page__container {
    max-width: 100%;
    padding: 16px;
  }

  .login-card {
    padding: 32px 24px;
    border-radius: var(--sf-radius-lg);
  }

  .login-card__header {
    margin-bottom: 24px;
  }

  .login-card__title {
    font-size: 22px;
  }

  .login-card__subtitle {
    font-size: 13px;
  }

  .login-form {
    :deep(.el-form-item) {
      margin-bottom: 16px;
    }
  }

  .form-label {
    font-size: 12px;
  }

  :deep(.el-input__wrapper) {
    padding: 10px 12px;
    min-height: 44px;
  }

  :deep(.el-input__inner) {
    font-size: 15px;
  }

  .form-options {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .submit-btn {
    height: 48px;
    font-size: 15px;
  }
}
</style>
