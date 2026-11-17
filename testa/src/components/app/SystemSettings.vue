<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { LoaderCircle, PlugZap, Save, TriangleAlert, Undo2, Wifi } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import Switch from '@/components/ui/Switch.vue'
import ChoiceSelect from '@/components/app/ChoiceSelect.vue'
import SettingRow from '@/components/app/SettingRow.vue'
import { api } from '@/lib/api'

/**
 * System settings (system administrators): AI question import and the exam rules that used to need a config file.
 * Written for people who do not run servers: every field says what it does, and the AI address can be tested before saving.
 */
interface Settings {
  ai: { enabled: boolean; baseUrl: string; model: string; apiKeySet: boolean }
  aiTimeoutSeconds: number
  flagPercent: number
  retentionYears: number
  loginMaxFailures: number
  loginLockMinutes: number
  staffIdleMinutes: number
  defaults: Record<NumberKey, number>
  lastChange: { at: string; by: string | null; detail: string | null } | null
}
type NumberKey = 'aiTimeoutSeconds' | 'flagPercent' | 'retentionYears' | 'loginMaxFailures' | 'loginLockMinutes' | 'staffIdleMinutes'
interface Probe {
  ok: boolean
  message: string
  models: string[]
}

/** Whether there are unsaved changes, so the admin page can ask before switching tabs */
const unsaved = defineModel<boolean>('dirty', { default: false })
const saved = ref<Settings | null>(null)
const form = reactive({
  aiEnabled: false,
  aiBaseUrl: '',
  aiModel: '',
  /** Empty means "keep the stored key" */
  aiApiKey: '',
  removeKey: false,
  aiTimeoutSeconds: 300,
  flagPercent: 20,
  retentionYears: 1,
  loginMaxFailures: 5,
  loginLockMinutes: 15,
  staffIdleMinutes: 480,
})
const NUMBERS: NumberKey[] = ['aiTimeoutSeconds', 'flagPercent', 'retentionYears', 'loginMaxFailures', 'loginLockMinutes', 'staffIdleMinutes']

const minutes = (n: number) => (n % 60 ? `${n} 分鐘` : `${n / 60} 小時`)
const CHOICES: Record<NumberKey, [number, string][]> = {
  aiTimeoutSeconds: [60, 120, 300, 600, 1200].map((n) => [n, `${n / 60} 分鐘`]),
  flagPercent: [10, 15, 20, 25, 30, 40, 50].map((n) => [n, `${n}%`]),
  retentionYears: [1, 2, 3, 5, 7, 10].map((n) => [n, `${n} 年`]),
  loginMaxFailures: [3, 5, 10, 20].map((n) => [n, `${n} 次`]),
  loginLockMinutes: [5, 15, 30, 60, 1440].map((n) => [n, n === 1440 ? '1 天' : minutes(n)]),
  staffIdleMinutes: [30, 60, 120, 240, 480].map((n) => [n, minutes(n)]),
}
const FORMAT: Record<NumberKey, (n: number) => string> = {
  aiTimeoutSeconds: (n) => `${n} 秒`,
  flagPercent: (n) => `${n}%`,
  retentionYears: (n) => `${n} 年`,
  loginMaxFailures: (n) => `${n} 次`,
  loginLockMinutes: minutes,
  staffIdleMinutes: minutes,
}
const loadError = ref('')
const saving = ref(false)
const testing = ref(false)
const probe = ref<Probe | null>(null)

function fill(s: Settings) {
  saved.value = s
  Object.assign(form, {
    aiEnabled: s.ai.enabled,
    aiBaseUrl: s.ai.baseUrl,
    aiModel: s.ai.model,
    aiApiKey: '',
    removeKey: false,
    ...Object.fromEntries(NUMBERS.map((key) => [key, s[key]])),
  })
}

onMounted(async () => {
  try {
    fill(await api<Settings>('/admin/settings'))
  } catch (e) {
    loadError.value = (e as Error).message
  }
})

const dirty = computed(() => {
  const s = saved.value
  if (!s) return false
  return (
    form.aiEnabled !== s.ai.enabled ||
    form.aiBaseUrl.trim() !== s.ai.baseUrl ||
    form.aiModel.trim() !== s.ai.model ||
    form.aiApiKey !== '' ||
    form.removeKey ||
    NUMBERS.some((key) => form[key] !== s[key])
  )
})

watch(dirty, (d) => (unsaved.value = d), { immediate: true })

const ROWS: { key: Exclude<NumberKey, 'aiTimeoutSeconds'>; title: string; hint: string }[] = [
  { key: 'flagPercent', title: '評分差距提醒', hint: '兩位老師給同一題的分數差太多，公布成績前會提醒。' },
  { key: 'retentionYears', title: '資料保存年數', hint: '從考試那年算起，到期後封存的考試才能刪除。' },
  { key: 'loginMaxFailures', title: '登入錯誤上限', hint: '密碼或登入碼連續輸錯這麼多次，就暫時鎖定。' },
  { key: 'loginLockMinutes', title: '鎖定時間', hint: '鎖定後要等多久才能再試。' },
  { key: 'staffIdleMinutes', title: '閒置自動登出', hint: '教職員多久沒操作就登出；考生不受影響。' },
]
const discard = () => saved.value && fill(saved.value)

// Leaving with unsaved changes: ask first (other pages, reload, closing the tab)
const LEAVE = '設定還沒儲存，確定要離開嗎？'
onBeforeRouteLeave(() => !dirty.value || window.confirm(LEAVE))
const beforeUnload = (e: BeforeUnloadEvent) => {
  if (dirty.value) e.preventDefault()
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

const changedAt = (iso: string) =>
  new Date(iso).toLocaleString('zh-TW', { year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })

/** Models to offer: what the last test found, plus the one in use. */
const models = computed(() => {
  const list = [...(probe.value?.models ?? [])]
  if (form.aiModel && !list.includes(form.aiModel)) list.unshift(form.aiModel)
  return list
})

async function test() {
  testing.value = true
  probe.value = null
  try {
    probe.value = await api<Probe>('/admin/settings/ai/test', {
      method: 'POST',
      body: { baseUrl: form.aiBaseUrl.trim(), apiKey: form.removeKey ? '' : form.aiApiKey || null },
    })
    // One model on offer: pick it, so the administrator has nothing to type
    if (probe.value.ok && !form.aiModel && probe.value.models.length === 1) form.aiModel = probe.value.models[0]
  } catch (e) {
    probe.value = { ok: false, message: (e as Error).message, models: [] }
  } finally {
    testing.value = false
  }
}

async function save() {
  saving.value = true
  try {
    fill(
      await api<Settings>('/admin/settings', {
        method: 'PUT',
        body: {
          aiEnabled: form.aiEnabled,
          aiBaseUrl: form.aiBaseUrl.trim(),
          aiModel: form.aiModel.trim(),
          aiApiKey: form.removeKey ? '' : form.aiApiKey || null,
          ...Object.fromEntries(NUMBERS.map((key) => [key, form[key]])),
        },
      }),
    )
    toast.success('已儲存設定')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <p v-if="loadError" class="card px-5 py-8 text-center text-ink-2 sm:px-6">{{ loadError }}</p>
  <p v-else-if="!saved" class="card px-5 py-8 text-center text-ink-2 sm:px-6">載入中…</p>

  <form v-else class="card overflow-visible" @submit.prevent>
    <!-- AI question import: the connection fields appear only when it is on -->
    <label class="flex items-center gap-6 px-5 py-4 sm:px-6">
      <span class="min-w-0 flex-1">
        <span class="block text-[15px] font-medium">AI 匯入題目</span>
        <span class="mt-0.5 block text-sm text-ink-2">老師上傳 Word 或 PDF 考卷，AI 拆成題目，再由老師逐題核對。</span>
      </span>
      <Switch v-model="form.aiEnabled" />
    </label>

    <div v-if="form.aiEnabled" class="space-y-5 border-t border-line bg-sunken/50 px-5 py-5 sm:px-6">
      <div>
        <label for="ai-url" class="mb-1.5 block text-[15px] font-medium">AI 服務網址</label>
        <div class="flex flex-wrap items-center gap-2">
          <input
            id="ai-url"
            v-model="form.aiBaseUrl"
            class="field max-w-md flex-1 basis-64 bg-paper"
            inputmode="url"
            autocomplete="off"
            placeholder="http://10.0.0.5:8090"
            @input="probe = null"
          />
          <Button variant="outline" size="md" :disabled="testing || !form.aiBaseUrl.trim()" @click="test">
            <LoaderCircle v-if="testing" class="animate-spin" /><PlugZap v-else />測試連線
          </Button>
        </div>
        <p v-if="probe" class="mt-2 flex items-start gap-1.5 text-sm" :class="probe.ok ? 'text-brand-strong' : 'text-grade'" role="status">
          <Wifi v-if="probe.ok" class="mt-0.5 size-4 shrink-0" />
          <TriangleAlert v-else class="mt-0.5 size-4 shrink-0" />{{ probe.message }}
        </p>
        <p v-else class="mt-1.5 text-sm text-ink-3">架設 AI 主機的人會提供。只會送出考卷文字，不含考生資料。</p>
      </div>

      <div>
        <label for="ai-model" class="mb-1.5 block text-[15px] font-medium">模型</label>
        <select v-if="models.length" id="ai-model" v-model="form.aiModel" class="field max-w-md bg-paper">
          <option value="">自動（AI 服務預設的模型）</option>
          <option v-for="m in models" :key="m" :value="m">{{ m.split('/').pop() }}</option>
        </select>
        <p v-else class="text-sm text-ink-2">自動（AI 服務預設的模型）。按「測試連線」後可以從清單挑選。</p>
      </div>

      <div>
        <label for="ai-key" class="mb-1.5 block text-[15px] font-medium">API 金鑰<span class="font-normal text-ink-3">（通常不用填）</span></label>
        <input
          id="ai-key"
          v-model="form.aiApiKey"
          type="password"
          class="field max-w-md bg-paper"
          autocomplete="new-password"
          :disabled="form.removeKey"
          :placeholder="saved.ai.apiKeySet ? '已設定，要更換才輸入' : ''"
        />
        <label v-if="saved.ai.apiKeySet" class="mt-2 flex items-center gap-2 text-sm text-ink-2">
          <input v-model="form.removeKey" type="checkbox" class="size-4 accent-(--brand)" />移除目前的金鑰
        </label>
      </div>

      <div>
        <label for="aiTimeoutSeconds" class="mb-1.5 block text-[15px] font-medium">等待時間</label>
        <div class="max-w-md">
          <ChoiceSelect id="aiTimeoutSeconds" v-model="form.aiTimeoutSeconds" class="bg-paper" :options="CHOICES.aiTimeoutSeconds" :default-value="saved.defaults.aiTimeoutSeconds" :format="FORMAT.aiTimeoutSeconds" />
        </div>
        <p class="mt-1.5 text-sm text-ink-3">AI 主機比較慢、長考卷常常失敗時再調長。</p>
      </div>
    </div>

    <div class="divide-y divide-line border-t border-line">
      <SettingRow v-for="row in ROWS" :key="row.key" :title="row.title" :hint="row.hint" :for="row.key">
        <ChoiceSelect :id="row.key" v-model="form[row.key]" :options="CHOICES[row.key]" :default-value="saved.defaults[row.key]" :format="FORMAT[row.key]" />
      </SettingRow>
    </div>

    <!-- Footer: last change; while there are changes it stays in view at the bottom -->
    <div
      class="z-20 flex flex-wrap items-center gap-x-4 gap-y-2 md:rounded-b-card border-t border-line bg-paper px-5 py-3 sm:px-6"
      :class="dirty && 'sticky bottom-[calc(4.75rem+env(safe-area-inset-bottom))] shadow-lift md:bottom-0'"
    >
      <p class="min-w-0 flex-1 basis-48 text-sm" :class="dirty ? 'font-medium' : 'text-ink-3'">
        <template v-if="dirty">有還沒儲存的變更</template>
        <template v-else-if="saved.lastChange">
          上次修改：{{ saved.lastChange.by ?? '系統' }}，<span class="tabular-nums">{{ changedAt(saved.lastChange.at) }}</span>
        </template>
      </p>
      <Button v-if="dirty" size="md" :disabled="saving" @click="discard"><Undo2 />放棄</Button>
      <Button variant="primary" size="md" :disabled="!dirty || saving" @click="save"><Save />儲存設定</Button>
    </div>
  </form>
</template>
