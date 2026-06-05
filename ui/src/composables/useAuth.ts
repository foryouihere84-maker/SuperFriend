import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

export const useAuth = () => {
  const userStore = useUserStore()

  onMounted(() => {
    userStore.initAuth()
  })

  return {
    userStore
  }
}
