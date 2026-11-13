<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { CircleAlert, LoaderCircle } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Logo from '@/components/app/Logo.vue'
import { useAuth } from '@/composables/useAuth'

/** 變更密碼。用臨時密碼登入的人會被帶到這裡，換完才能繼續。 */
const { user, changePassword, logout } = useAuth()
const router = useRouter()

const forced = !!user.value?.mustChangePassword
const current = ref('')
const next = ref('')
const again = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  if (loading.value) return
  if (!current.value || !next.value) return void (error.value = '請輸入目前的密碼與新密碼。')
  if (next.value.length < 8) return void (error.value = '新密碼至少需要 8 個字元。')
  if (next.value !== again.value) return void (error.value = '兩次輸入的新密碼不一樣。')
  error.value = ''
  loading.value = true
  try {
    await changePassword(current.value, next.value)
    toast.success('密碼已變更，其他裝置上的登入已失效')
    await router.push({ name: 'home' })
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

async function leave() {
  if (forced) {
    await logout()
    await router.push({ name: 'login' })
  } else {
    router.back()
  }
}
</script>

<template>
  <div class="flex min-h-dvh flex-col items-center justify-center px-4 py-10">
    <div class="w-full max-w-[25rem]">
      <div class="mb-6 flex justify-center"><Logo /></div>
      <form class="rounded-card bg-paper p-6 shadow-card sm:p-8" novalidate @submit.prevent="submit">
        <h1 class="text-2xl font-bold">{{ forced ? '請先設定新密碼' : '變更密碼' }}</h1>
        <p class="mt-1 text-[15px] text-ink-2">
          {{ forced ? '你目前使用的是臨時密碼，換成自己的密碼後才能繼續。' : '變更後，其他裝置上的登入會失效。' }}
        </p>
        <div class="mt-6 space-y-4">
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">{{ forced ? '臨時密碼' : '目前的密碼' }}</span>
            <input v-model="current" type="password" autocomplete="current-password" class="field" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">新密碼（至少 8 個字元）</span>
            <input v-model="next" type="password" autocomplete="new-password" class="field" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">再輸入一次新密碼</span>
            <input v-model="again" type="password" autocomplete="new-password" class="field" />
          </label>
        </div>
        <p v-if="error" class="mt-4 flex items-start gap-1.5 text-sm text-grade" role="alert">
          <CircleAlert class="mt-0.5 size-4 shrink-0" />{{ error }}
        </p>
        <button
          type="submit"
          class="mt-6 flex h-12 w-full items-center justify-center gap-2 rounded-full bg-brand text-base font-semibold text-brand-fg transition-colors hover:bg-brand-strong disabled:opacity-70"
          :disabled="loading"
        >
          <LoaderCircle v-if="loading" class="size-4 animate-spin" />
          {{ loading ? '儲存中…' : '儲存新密碼' }}
        </button>
        <button type="button" class="mt-3 h-10 w-full rounded-full text-[15px] text-ink-2 hover:text-ink" @click="leave">
          {{ forced ? '登出' : '取消' }}
        </button>
      </form>
    </div>
  </div>
</template>
