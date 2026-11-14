<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { CircleAlert, LoaderCircle } from 'lucide-vue-next'
import Logo from '@/components/app/Logo.vue'
import { DEMO_ACCOUNTS, signedOutNotice, useAuth } from '@/composables/useAuth'
import { DEMO_CANDIDATE } from '@/composables/useWorkspace'
import { api } from '@/lib/api'

/** One login page, two identities: candidates use candidate number + login code, staff use account and password. */
const { login, loginCandidate } = useAuth()
const router = useRouter()

const who = ref<'candidate' | 'staff'>('candidate')
const ticket = ref('')
const code = ref('')
const email = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
// Back here after being signed out: explain why and keep the same identity tab
if (signedOutNotice.value) {
  who.value = signedOutNotice.value.role === 'student' ? 'candidate' : 'staff'
  error.value = signedOutNotice.value.text
  signedOutNotice.value = null
}
// A fresh installation has no administrator yet: go to first-run setup
api<{ required: boolean }>('/auth/setup', { quiet401: true })
  .then((s) => void (s.required && router.replace({ name: 'setup' })))
  .catch(() => {})

/** Demo identities only in development */
const showDemo = import.meta.env.DEV

async function submit() {
  if (loading.value) return
  const candidate = who.value === 'candidate'
  if (candidate ? !ticket.value.trim() || !code.value.trim() : !email.value.trim() || !password.value) {
    error.value = candidate ? '請輸入准考證號與登入碼。' : '請輸入帳號與密碼。'
    return
  }
  error.value = ''
  loading.value = true
  try {
    const user = candidate ? await loginCandidate(ticket.value, code.value) : await login(email.value, password.value)
    await router.push({ name: user.role === 'student' ? 'student' : 'home' })
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

function demoCandidate() {
  who.value = 'candidate'
  ticket.value = DEMO_CANDIDATE.ticket
  code.value = DEMO_CANDIDATE.code
  submit()
}

function demoStaff(account: (typeof DEMO_ACCOUNTS)[number]) {
  who.value = 'staff'
  email.value = account.email
  password.value = account.password
  submit()
}

function switchTo(next: 'candidate' | 'staff') {
  who.value = next
  error.value = ''
}
</script>

<template>
  <div class="flex min-h-dvh flex-col items-center justify-center px-4 py-10">
    <div class="w-full max-w-[25rem]">
      <div class="mb-6 flex justify-center"><Logo /></div>

      <form class="rounded-card bg-paper p-6 shadow-card sm:p-8" novalidate @submit.prevent="submit">
        <div role="tablist" aria-label="登入身分" class="mb-6 grid grid-cols-2 rounded-full bg-sunken p-1">
          <button
            v-for="t in [
              { value: 'candidate', label: '我是考生' },
              { value: 'staff', label: '教職員' },
            ] as const"
            :key="t.value"
            type="button"
            role="tab"
            :aria-selected="who === t.value"
            class="h-10 rounded-full text-[15px] transition-colors"
            :class="who === t.value ? 'bg-brand font-medium text-brand-fg' : 'text-ink-2 hover:text-ink'"
            @click="switchTo(t.value)"
          >
            {{ t.label }}
          </button>
        </div>

        <template v-if="who === 'candidate'">
          <h1 class="sr-only">考生登入</h1>
          <p class="text-[15px] text-ink-2">輸入登入單上的准考證號和登入碼。</p>
          <div class="mt-5 space-y-4">
            <label class="block">
              <span class="mb-1.5 block text-sm font-medium">准考證號</span>
              <input v-model="ticket" autocomplete="off" autocapitalize="characters" placeholder="例如 A115-0001" class="field h-12 text-lg" />
            </label>
            <label class="block">
              <span class="mb-1.5 block text-sm font-medium">登入碼</span>
              <input
                v-model="code"
                inputmode="numeric"
                autocomplete="one-time-code"
                maxlength="7"
                class="field h-12 text-2xl font-semibold tracking-[0.2em]"
              />
            </label>
          </div>
        </template>

        <template v-else>
          <h1 class="sr-only">教職員登入</h1>
          <div class="space-y-4">
            <label class="block">
              <span class="mb-1.5 block text-sm font-medium">帳號</span>
              <input v-model="email" type="email" autocomplete="username" placeholder="name@school.edu.tw" class="field" />
            </label>
            <label class="block">
              <span class="mb-1.5 block text-sm font-medium">密碼</span>
              <input v-model="password" type="password" autocomplete="current-password" class="field" />
            </label>
          </div>
        </template>

        <p v-if="error" class="mt-4 flex items-start gap-1.5 text-sm text-grade" role="alert">
          <CircleAlert class="mt-0.5 size-4 shrink-0" />{{ error }}
        </p>

        <button
          type="submit"
          class="mt-6 flex h-12 w-full items-center justify-center gap-2 rounded-full bg-brand text-base font-semibold text-brand-fg transition-colors hover:bg-brand-strong disabled:opacity-70"
          :disabled="loading"
        >
          <LoaderCircle v-if="loading" class="size-4 animate-spin" />
          {{ loading ? '登入中…' : '登入' }}
        </button>
      </form>

      <div v-if="showDemo" class="mt-4 rounded-card border-[1.5px] border-dashed border-line-strong p-4">
        <p class="text-sm text-ink-2">開發模式，點一下用示範身分登入：</p>
        <div class="mt-3 grid grid-cols-2 gap-2">
          <button
            type="button"
            class="rounded-control bg-paper px-3 py-2.5 text-left shadow-card transition-shadow hover:shadow-lift disabled:opacity-60"
            :disabled="loading"
            @click="demoCandidate"
          >
            <span class="block text-[15px] font-medium">考生</span>
            <span class="block text-[13px] text-ink-2">王小明</span>
          </button>
          <button
            v-for="account in DEMO_ACCOUNTS"
            :key="account.email"
            type="button"
            class="rounded-control bg-paper px-3 py-2.5 text-left shadow-card transition-shadow hover:shadow-lift disabled:opacity-60"
            :disabled="loading"
            @click="demoStaff(account)"
          >
            <span class="block text-[15px] font-medium">{{ account.label }}</span>
            <span class="block text-[13px] text-ink-2">{{ account.name }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
