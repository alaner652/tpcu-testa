<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { LoaderCircle, PlugZap, Save, TriangleAlert, Wifi } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import Switch from '@/components/ui/Switch.vue'
import { api } from '@/lib/api'

/**
 * System settings (system administrators): AI question import and the exam rules that used to need a config file.
 * Written for people who do not run servers: every field says what it does, and the AI address can be tested before saving.
 */
interface Settings {
  ai: { enabled: boolean; baseUrl: string; model: string; apiKeySet: boolean }
  flagPercent: number
  retentionYears: number
  loginMaxFailures: number
  loginLockMinutes: number
  backupKeepDays: number
}
interface Probe {
  ok: boolean
  message: string
  models: string[]
}

const saved = ref<Settings | null>(null)
const form = reactive({
  aiEnabled: false,
  aiBaseUrl: '',
  aiModel: '',
  /** Empty means "keep the stored key" */
  aiApiKey: '',
  removeKey: false,
  flagPercent: 20,
  retentionYears: 1,
  loginMaxFailures: 5,
  loginLockMinutes: 15,
  backupKeepDays: 30,
})
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
    flagPercent: s.flagPercent,
    retentionYears: s.retentionYears,
    loginMaxFailures: s.loginMaxFailures,
    loginLockMinutes: s.loginLockMinutes,
    backupKeepDays: s.backupKeepDays,
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
    form.flagPercent !== s.flagPercent ||
    form.retentionYears !== s.retentionYears ||
    form.loginMaxFailures !== s.loginMaxFailures ||
    form.loginLockMinutes !== s.loginLockMinutes
    || form.backupKeepDays !== s.backupKeepDays
  )
})

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
          flagPercent: form.flagPercent,
          retentionYears: form.retentionYears,
          loginMaxFailures: form.loginMaxFailures,
          loginLockMinutes: form.loginLockMinutes,
          backupKeepDays: form.backupKeepDays,
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

  <form v-else class="space-y-4" @submit.prevent>
    <!-- AI question import -->
    <section class="card divide-y divide-line">
      <label class="flex items-center gap-4 px-5 py-5 sm:px-6">
        <span class="min-w-0 flex-1">
          <span class="block text-[17px] font-semibold">AI 匯入題目</span>
          <span class="mt-0.5 block text-sm text-ink-2">
            開啟後，出題老師可以上傳現有的 Word 或 PDF 考卷，讓 AI 拆成題目。每一題都要老師核對後才算數。
          </span>
        </span>
        <Switch v-model="form.aiEnabled" />
      </label>

      <div class="space-y-5 px-5 py-5 sm:px-6">
        <div>
          <label for="ai-url" class="mb-1.5 block text-[15px] font-medium">AI 服務網址</label>
          <div class="flex flex-wrap items-center gap-2">
            <input
              id="ai-url"
              v-model="form.aiBaseUrl"
              class="field max-w-md flex-1 basis-64"
              inputmode="url"
              autocomplete="off"
              placeholder="http://10.0.0.5:8090"
              @input="probe = null"
            />
            <Button variant="outline" size="md" :disabled="testing || !form.aiBaseUrl.trim()" @click="test">
              <LoaderCircle v-if="testing" class="animate-spin" /><PlugZap v-else />測試連線
            </Button>
          </div>
          <p class="mt-1.5 text-sm text-ink-3">
            架設 AI 主機的人會提供這個網址。考卷文字只會送到這裡，考生的資料不會。
          </p>
          <p
            v-if="probe"
            class="mt-2 flex items-start gap-1.5 text-sm"
            :class="probe.ok ? 'text-brand-strong' : 'text-grade'"
            role="status"
          >
            <Wifi v-if="probe.ok" class="mt-0.5 size-4 shrink-0" />
            <TriangleAlert v-else class="mt-0.5 size-4 shrink-0" />{{ probe.message }}
          </p>
        </div>

        <div>
          <label for="ai-model" class="mb-1.5 block text-[15px] font-medium">模型</label>
          <select v-if="models.length" id="ai-model" v-model="form.aiModel" class="field max-w-md">
            <option value="">自動（使用 AI 服務預設的模型）</option>
            <option v-for="m in models" :key="m" :value="m">{{ m.split('/').pop() }}</option>
          </select>
          <p v-else class="text-sm text-ink-2">自動（使用 AI 服務預設的模型）。按「測試連線」後可以從清單挑選。</p>
        </div>

        <div>
          <label for="ai-key" class="mb-1.5 block text-[15px] font-medium">API 金鑰（通常不用填）</label>
          <input
            id="ai-key"
            v-model="form.aiApiKey"
            type="password"
            class="field max-w-md"
            autocomplete="new-password"
            :disabled="form.removeKey"
            :placeholder="saved.ai.apiKeySet ? '已經設定。要更換才需要輸入' : '學校自己架的 AI 主機通常不需要'"
          />
          <label v-if="saved.ai.apiKeySet" class="mt-2 flex items-center gap-2 text-sm text-ink-2">
            <input v-model="form.removeKey" type="checkbox" class="size-4 accent-(--brand)" />移除目前的金鑰
          </label>
        </div>
      </div>
    </section>

    <!-- Exam rules -->
    <section class="card divide-y divide-line">
      <h2 class="px-5 py-4 text-[17px] font-semibold sm:px-6">考試規則</h2>

      <div class="flex flex-wrap items-center gap-x-6 gap-y-3 px-5 py-5 sm:px-6">
        <label for="flag" class="min-w-0 flex-1 basis-72">
          <span class="block text-[15px] font-medium">評分差距提醒</span>
          <span class="mt-0.5 block text-sm text-ink-2">
            同一份申論答案，評分老師之間的分數差距超過配分的這個比例時，公布成績前會提醒承辦人。
          </span>
        </label>
        <span class="flex items-center gap-2">
          <input id="flag" v-model.number="form.flagPercent" type="number" min="5" max="100" required class="field w-24 text-right tabular-nums" />
          <span class="text-ink-2">%</span>
        </span>
      </div>

      <div class="flex flex-wrap items-center gap-x-6 gap-y-3 px-5 py-5 sm:px-6">
        <label for="backup-days" class="min-w-0 flex-1 basis-72">
          <span class="block text-[15px] font-medium">備份保留天數</span>
          <span class="mt-0.5 block text-sm text-ink-2">
            系統每天清理超過這個天數的資料庫備份。建議至少保留 30 天，並另外保留 VM 層級備份。
          </span>
        </label>
        <span class="flex items-center gap-2">
          <input id="backup-days" v-model.number="form.backupKeepDays" type="number" min="1" max="365" required class="field w-24 text-right tabular-nums" />
          <span class="text-ink-2">天</span>
        </span>
      </div>

      <div class="flex flex-wrap items-center gap-x-6 gap-y-3 px-5 py-5 sm:px-6">
        <label for="years" class="min-w-0 flex-1 basis-72">
          <span class="block text-[15px] font-medium">資料保存年數</span>
          <span class="mt-0.5 block text-sm text-ink-2">
            考試資料保存到「考試結束那一年，再加這個年數」的年底。過了這一天，封存的考試才能刪除。
          </span>
        </label>
        <span class="flex items-center gap-2">
          <input id="years" v-model.number="form.retentionYears" type="number" min="1" max="10" required class="field w-24 text-right tabular-nums" />
          <span class="text-ink-2">年</span>
        </span>
      </div>

      <div class="flex flex-wrap items-center gap-x-6 gap-y-3 px-5 py-5 sm:px-6">
        <div class="min-w-0 flex-1 basis-72">
          <span class="block text-[15px] font-medium">登入保護</span>
          <span class="mt-0.5 block text-sm text-ink-2">
            密碼或登入碼連續輸錯太多次，帳號會暫時鎖定。教職員和考生都適用；考生可以由監考重設。
          </span>
        </div>
        <span class="flex flex-wrap items-center gap-2 text-ink-2">
          輸錯
          <input v-model.number="form.loginMaxFailures" type="number" min="3" max="20" required aria-label="輸錯幾次後鎖定" class="field w-20 text-right tabular-nums" />
          次，鎖定
          <input v-model.number="form.loginLockMinutes" type="number" min="1" max="1440" required aria-label="鎖定幾分鐘" class="field w-24 text-right tabular-nums" />
          分鐘
        </span>
      </div>
    </section>

    <div class="flex items-center justify-end gap-3 px-5 sm:px-0">
      <span v-if="dirty" class="text-sm text-ink-2">有還沒儲存的變更</span>
      <Button variant="primary" size="md" :disabled="!dirty || saving" @click="save"><Save />儲存設定</Button>
    </div>
  </form>
</template>
