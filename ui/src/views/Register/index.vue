<template>
  <div class="register-page">
    <div class="register-page__container">
      <div class="register-card">
        <div class="register-card__header">
          <Logo />
          <h1 class="register-card__title">创建账户</h1>
          <p class="register-card__subtitle">注册一个新账户开始使用</p>
        </div>

        <el-form
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          class="register-form"
          @submit.prevent="handleRegister"
        >
          <el-form-item prop="username">
            <div class="form-group">
              <label class="form-label">用户名</label>
              <el-input
                v-model="registerForm.username"
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

          <el-form-item prop="email">
            <div class="form-group">
              <label class="form-label">邮箱</label>
              <el-input
                v-model="registerForm.email"
                placeholder="请输入邮箱"
                size="large"
                clearable
              >
                <template #prefix>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
                    <polyline points="22,6 12,13 2,6" />
                  </svg>
                </template>
              </el-input>
            </div>
          </el-form-item>

          <el-form-item prop="password">
            <div class="form-group">
              <label class="form-label">密码</label>
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                clearable
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

          <el-form-item prop="confirmPassword">
            <div class="form-group">
              <label class="form-label">确认密码</label>
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请确认密码"
                size="large"
                show-password
                clearable
                @keyup.enter="handleRegister"
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

          <div class="terms-checkbox">
            <label class="checkbox-label">
              <input type="checkbox" v-model="agreeTerms" />
              <span class="checkbox-custom"></span>
              <span class="checkbox-text">
                我已阅读并同意
                <a href="#" class="terms-link">服务条款</a>
                和
                <a href="#" class="terms-link">隐私政策</a>
              </span>
            </label>
          </div>

          <el-form-item>
            <button
              type="submit"
              class="submit-btn"
              :disabled="userStore.isLoading || !agreeTerms"
            >
              <span v-if="!userStore.isLoading">注册</span>
              <span v-else class="loading-content">
                <span class="spinner"></span>
                注册中...
              </span>
            </button>
          </el-form-item>

          <div class="register-footer">
            <span>已有账号？</span>
            <router-link to="/login" class="auth-link">立即登录</router-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { register } from '@/api/auth'
import { validateEmail } from '@/utils/validate'
import type { FormInstance, FormRules } from 'element-plus'
import Logo from '@/components/Logo/index.vue'

const router = useRouter()
const userStore = useUserStore()

const registerFormRef = ref<FormInstance>()
const agreeTerms = ref(false)

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (!validateEmail(value)) {
          callback(new Error('请输入正确的邮箱格式'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  if (!registerFormRef.value) return

  try {
    await registerFormRef.value.validate()
    if (!agreeTerms.value) {
      ElMessage.warning('请先阅读并同意服务条款和隐私政策')
      return
    }

    await register(registerForm)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error: any) {
    const message = error.response?.data?.message || error.message || '注册失败'
    ElMessage.error(message)
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.register-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--sf-page-bg);
}

.register-page__container {
  width: 100%;
  max-width: 400px;
  padding: 20px;
}

.register-card {
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

.register-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
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

.terms-checkbox {
  margin-bottom: 20px;
}

.checkbox-label {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--sf-text-secondary);
  line-height: 1.5;

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
    flex-shrink: 0;
    margin-top: 2px;

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

  .checkbox-text {
    flex: 1;
  }
}

.terms-link {
  color: var(--sf-text-primary);
  text-decoration: none;
  transition: color var(--sf-transition-fast);

  &:hover {
    color: var(--sf-text-secondary);
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

.register-footer {
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
  .register-page {
    padding: 16px;
  }

  .register-page__container {
    max-width: 100%;
    padding: 16px;
  }

  .register-card {
    padding: 32px 24px;
    border-radius: var(--sf-radius-lg);
  }

  .register-card__header {
    margin-bottom: 24px;
  }

  .register-card__title {
    font-size: 22px;
  }

  .register-card__subtitle {
    font-size: 13px;
  }

  .register-form {
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

  .terms-checkbox {
    margin-bottom: 16px;
  }

  .checkbox-label {
    font-size: 12px;
    gap: 6px;
  }

  .submit-btn {
    height: 48px;
    font-size: 15px;
  }
}
</style>
