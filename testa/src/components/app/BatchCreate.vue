<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { UserPlus, X } from 'lucide-vue-next'
import FormError from '@/components/ui/FormError.vue'
import Button from '@/components/ui/Button.vue'
import { ROLE_CHOICES, rolesOf } from '@/composables/useAuth'
import { ApiError, api } from '@/lib/api'
import type { IssuedPassword } from '@/components/app/TempPasswords.vue'

/**
 * Creates many staff accounts at once from pasted lines ("name, email", straight from Excel works too).
 * Rows are checked here first; the server creates all of them or none.
 */
const props = defineProps<{
  units: { id: number; name: string; active: boolean }[]
  existing: string[]
}>()
const emit = defineEmits<{ done: [items: IssuedPassword[]]; cancel: [] }>()

const text = ref('')
const draft = reactive({ unitId: null as number | null, choice: 'teacher' })
const serverErrors = ref<Record<number, string>>({})
const error = ref('')
const saving = ref(false)

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

interface Row {
  line: number
  name: string
  email: string
  problem: string
}

const rows = computed<Row[]>(() => {
  const seen = new Set<string>()
  const taken = new Set(props.existing.map((e) => e.toLowerCase()))
  const out: Row[] = []
  text.value.split(/\r?\n/).forEach((raw, i) => {
    const cells = raw
      .split(/[\t,，;；]/)
      .map((c) => c.trim())
      .filter(Boolean)
    if (!cells.length) return
    // Skip a header row copied from a spreadsheet
    if (!out.length && !cells.some((c) => c.includes('@')) && cells.some((c) => /姓名|email|信箱/i.test(c))) return
    const email = (cells.find((c) => c.includes('@')) ?? '').toLowerCase()
    const name = cells.filter((c) => !c.includes('@')).join(' ')
    let problem = ''
    if (!email) problem = '找不到 Email'
    else if (!EMAIL.test(email)) problem = 'Email 格式不正確'
    else if (!name) problem = '缺少姓名'
    else if (name.length > 100) problem = '姓名太長了'
    else if (seen.has(email)) problem = '名單裡重複了'
    else if (taken.has(email)) problem = '已經有帳號了'
    if (email) seen.add(email)
    out.push({
      line: i + 1,
      name,
      email,
      problem: serverErrors.value[out.length] ?? problem,
    })
  })
  return out
})
const problems = computed(() => rows.value.filter((r) => r.problem).length)

function edited() {
  serverErrors.value = {}
  error.value = ''
}

async function submit() {
  if (saving.value || !rows.value.length || problems.value) return
  if (rows.value.length > 200) return void (error.value = '一次最多建立 200 個帳號，請分批建立。')
  saving.value = true
  error.value = ''
  try {
    const created = await api<{ staff: { name: string; email: string }; temporaryPassword: string }[]>('/admin/staff/batch', {
      method: 'POST',
      body: {
        people: rows.value.map((r) => ({ name: r.name, email: r.email })),
        memberships: draft.unitId === null ? [] : [{ unitId: draft.unitId, roles: rolesOf(draft.choice) }],
      },
    })
    emit(
      'done',
      created.map((c) => ({
        name: c.staff.name,
        email: c.staff.email,
        password: c.temporaryPassword,
      })),
    )
  } catch (e) {
    error.value = (e as Error).message
    if (e instanceof ApiError && e.errors) {
      const map: Record<number, string> = {}
      for (const [key, message] of Object.entries(e.errors)) {
        const m = key.match(/^people\[(\d+)]/)
        if (m) map[Number(m[1])] = message
      }
      serverErrors.value = map
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="px-5 py-4 sm:px-6" novalidate @submit.prevent="submit">
    <label for="batch-lines" class="text-[15px] font-medium">每行一位：姓名、Email</label>
    <p class="mt-0.5 text-sm text-ink-2">可以直接從 Excel 選取兩欄後貼上，順序不拘，第一列的欄位標題會自動略過。</p>
    <textarea
      id="batch-lines"
      v-model="text"
      rows="6"
      class="field mt-2 h-auto bg-paper py-2 font-mono text-sm"
      placeholder="林小明, ming@tpcu.edu.tw&#10;陳美華, mei@tpcu.edu.tw"
      @input="edited"
    />

    <div v-if="rows.length" class="mt-3 max-h-72 overflow-auto rounded-control bg-paper">
      <table class="w-full text-sm">
        <thead class="sticky top-0 bg-paper text-left text-ink-2">
          <tr>
            <th class="px-3 py-2 font-medium">行</th>
            <th class="px-3 py-2 font-medium">姓名</th>
            <th class="px-3 py-2 font-medium">Email</th>
            <th class="px-3 py-2 font-medium">檢查</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-line">
          <tr v-for="r in rows" :key="r.line" :class="r.problem && 'bg-grade-soft/60'">
            <td class="px-3 py-1.5 text-ink-3">{{ r.line }}</td>
            <td class="px-3 py-1.5">{{ r.name }}</td>
            <td class="px-3 py-1.5 break-all">{{ r.email }}</td>
            <td class="px-3 py-1.5" :class="r.problem ? 'text-grade' : 'text-brand-strong'">
              {{ r.problem || '可以建立' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="mt-3 grid gap-2 sm:grid-cols-2">
      <select v-model="draft.unitId" class="field bg-paper" aria-label="加入單位">
        <option :value="null">先不加入單位</option>
        <option v-for="u in units.filter((x) => x.active)" :key="u.id" :value="u.id">加入「{{ u.name }}」</option>
      </select>
      <select v-model="draft.choice" class="field bg-paper" aria-label="角色" :disabled="draft.unitId === null">
        <option v-for="c in ROLE_CHOICES" :key="c.value" :value="c.value">
          {{ c.label }}
        </option>
      </select>
    </div>

    <FormError v-if="problems" class="mt-2">有 {{ problems }} 行需要修正，修正後才能建立。</FormError>
    <FormError v-else-if="error" class="mt-2">{{ error }}</FormError>
    <div class="mt-3 flex justify-end gap-2">
      <Button size="md" @click="emit('cancel')"><X />取消</Button>
      <Button variant="primary" size="md" type="submit" :disabled="!rows.length || !!problems || saving">
        <UserPlus />建立 {{ rows.length || '' }} 個帳號
      </Button>
    </div>
  </form>
</template>
