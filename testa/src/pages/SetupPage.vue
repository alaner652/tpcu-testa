<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { CircleAlert, KeyRound, LoaderCircle } from 'lucide-vue-next'
import Logo from '@/components/app/Logo.vue'
import { useAuth } from '@/composables/useAuth'
import { api } from '@/lib/api'

/** First-run setup: only while there is no system administrator, with the setup token from the server log. */
const { setupFirstAdmin } = useAuth()
const router = useRouter()

const token = ref('')
const email = ref('')
const name = ref('')
const password = ref('')
const again = ref('')
const error = ref('')
const loading = ref(false)

api<{ required: boolean }>('/auth/setup', { quiet401: true })
  .then((s) => void (!s.required && router.replace({ name: 'login' })))
  .catch(() => {})

async function submit() {
  if (loading.value) return
  if (!token.value.trim() || !email.value.trim() || !name.value.trim() || !password.value) {
    return void (error.value = '每一欄都要填。')
  }
  if (password.value.length < 8) return void (error.value = '密碼至少需要 8 個字元。')
  if (password.value !== again.value) return void (error.value = '兩次輸入的密碼不一樣。')
  error.value = ''
  loading.value = true
  try {
    await setupFirstAdmin(token.value.trim(), email.value.trim(), name.value.trim(), password.value)
    await router.push({ name: 'admin' })
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-dvh flex-col items-center justify-center px-4 py-10">
    <div class="w-full max-w-[25rem]">
      <div class="mb-6 flex justify-center"><Logo /></div>

      <form class="rounded-card bg-paper p-6 shadow-card sm:p-8" novalidate @submit.prevent="submit">
        <h1 class="flex items-center gap-2 text-lg font-semibold"><KeyRound class="size-5 text-brand" />初始設定</h1>
        <p class="mt-1 text-[15px] text-ink-2">建立第一位系統管理員。設定碼在伺服器的啟動紀錄裡，完成後這個頁面就會關閉。</p>

        <div class="mt-5 space-y-4">
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">設定碼</span>
            <input v-model="token" autocomplete="off" spellcheck="false" class="field font-mono" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">Email（登入帳號）</span>
            <input v-model="email" type="email" autocomplete="username" class="field" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">姓名</span>
            <input v-model="name" autocomplete="name" class="field" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">密碼</span>
            <input v-model="password" type="password" autocomplete="new-password" class="field" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">再輸入一次密碼</span>
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
          {{ loading ? '建立中…' : '建立管理員' }}
        </button>
      </form>
    </div>
  </div>
</template>
