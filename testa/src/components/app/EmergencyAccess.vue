<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import { useConfirm } from '@/composables/useConfirm'
import { when, type Exam } from '@/composables/useExams'
import { api } from '@/lib/api'

/**
 * 單位負責人：授權一位系統管理員在限定時間內檢視這場考試。
 * 系統管理員平常看不到考試內容；授權、撤銷、每一次檢視都會留在操作紀錄。
 */
const props = defineProps<{ exam: Exam }>()

interface Grant {
  id: number
  staffName: string
  grantedByName: string
  reason: string
  grantedAt: string
  expiresAt: string
  revokedAt: string | null
  viewCount: number
  lastViewedAt: string | null
  active: boolean
}
interface Overview {
  admins: { staffId: number; name: string; email: string }[]
  grants: Grant[]
}

const { confirm } = useConfirm()
const data = ref<Overview | null>(null)
const error = ref('')
const form = reactive({ staffId: '', reason: '', hours: 24 })
const sending = ref(false)
const HOURS = [
  { value: 2, label: '2 小時' },
  { value: 24, label: '1 天' },
  { value: 72, label: '3 天' },
]

async function load() {
  try {
    data.value = await api<Overview>(`/exams/${props.exam.id}/emergency-access`)
    if (!form.staffId && data.value.admins.length === 1) form.staffId = String(data.value.admins[0].staffId)
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  }
}
onMounted(load)

async function grant() {
  if (sending.value || !form.staffId || !form.reason.trim()) return
  const admin = data.value?.admins.find((a) => String(a.staffId) === form.staffId)
  const ok = await confirm({
    title: `授權 ${admin?.name ?? '系統管理員'} 檢視這場考試？`,
    description: '對方在時效內可以看到考生名單、作答狀態與成績（看不到登入碼，也不能修改）。授權與每一次檢視都會留在操作紀錄。',
    confirmLabel: '授權',
  })
  if (!ok) return
  sending.value = true
  try {
    await api(`/exams/${props.exam.id}/emergency-access`, {
      method: 'POST',
      body: { staffId: Number(form.staffId), reason: form.reason, hours: form.hours },
    })
    form.reason = ''
    toast.success('已授權')
    await load()
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    sending.value = false
  }
}

async function revoke(g: Grant) {
  if (!(await confirm({ title: `撤銷 ${g.staffName} 的檢視授權？`, description: '撤銷後對方立刻看不到這場考試。', confirmLabel: '撤銷', tone: 'danger' }))) return
  try {
    await api(`/exams/${props.exam.id}/emergency-access/${g.id}`, { method: 'DELETE' })
    toast.success('已撤銷授權')
    await load()
  } catch (e) {
    toast.error((e as Error).message)
  }
}

const state = (g: Grant) => (g.active ? `有效到 ${when(g.expiresAt)}` : g.revokedAt ? `已於 ${when(g.revokedAt)} 撤銷` : `已於 ${when(g.expiresAt)} 到期`)
</script>

<template>
  <p v-if="error" class="card flex items-center gap-3 px-5 py-4 text-[15px] text-grade sm:px-6" role="alert">
    <span class="min-w-0 flex-1">{{ error }}</span>
    <Button variant="outline" class="rounded-full" @click="load">重試</Button>
  </p>

  <div v-else-if="data" class="space-y-3">
    <section class="card">
      <div class="px-5 py-4 sm:px-6">
        <h2 class="text-[17px] font-semibold">授權系統管理員檢視</h2>
        <p class="mt-1 text-[15px] text-ink-2">
          系統管理員平常看不到考試內容。遇到需要協助查證的狀況（例如考生申訴、系統異常），可以在這裡授權一位系統管理員限時檢視。對方只能看名單、作答狀態與成績，看不到登入碼，也不能修改。
        </p>
      </div>
      <p v-if="!data.admins.length" class="border-t border-line px-5 py-5 text-[15px] text-ink-2 sm:px-6">目前沒有啟用中的系統管理員。</p>
      <form v-else class="space-y-4 border-t border-line px-5 py-5 sm:px-6" novalidate @submit.prevent="grant">
        <div class="flex flex-wrap gap-4">
          <label class="block min-w-0 flex-1 basis-56">
            <span class="mb-1.5 block text-sm font-medium">授權給</span>
            <select v-model="form.staffId" class="field">
              <option value="" disabled>請選擇</option>
              <option v-for="a in data.admins" :key="a.staffId" :value="String(a.staffId)">{{ a.name }}（{{ a.email }}）</option>
            </select>
          </label>
          <label class="block w-36">
            <span class="mb-1.5 block text-sm font-medium">時效</span>
            <select v-model.number="form.hours" class="field">
              <option v-for="h in HOURS" :key="h.value" :value="h.value">{{ h.label }}</option>
            </select>
          </label>
        </div>
        <label class="block">
          <span class="mb-1.5 block text-sm font-medium">理由（必填，會留在操作紀錄）</span>
          <input v-model="form.reason" class="field" maxlength="500" placeholder="例如：考生申訴成績，需要協助查證" />
        </label>
        <Button variant="primary" size="md" class="rounded-full" type="submit" :disabled="sending || !form.staffId || !form.reason.trim()">授權</Button>
      </form>
    </section>

    <section v-if="data.grants.length" class="card">
      <h2 class="px-5 py-4 text-[17px] font-semibold sm:px-6">授權紀錄</h2>
      <ul class="divide-y divide-line border-t border-line">
        <li v-for="g in data.grants" :key="g.id" class="flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3 sm:px-6">
          <div class="min-w-0 flex-1 basis-64 text-[15px]">
            <p>
              <span class="font-medium">{{ g.staffName }}</span>
              <span class="ml-2 rounded-full px-2.5 py-0.5 text-[13px] font-medium" :class="g.active ? 'bg-marker text-marker-ink' : 'bg-sunken text-ink-2'">
                {{ state(g) }}
              </span>
            </p>
            <p class="mt-1 text-sm text-ink-2">
              {{ g.grantedByName }} 於 {{ when(g.grantedAt) }} 授權。理由：{{ g.reason }}
            </p>
            <p class="text-sm text-ink-2 tabular-nums">
              <template v-if="g.viewCount">已檢視 {{ g.viewCount }} 次，最近一次 {{ when(g.lastViewedAt) }}</template>
              <template v-else>還沒有檢視過</template>
            </p>
          </div>
          <Button v-if="g.active" variant="danger" class="rounded-full" @click="revoke(g)">撤銷</Button>
        </li>
      </ul>
    </section>
  </div>
</template>
