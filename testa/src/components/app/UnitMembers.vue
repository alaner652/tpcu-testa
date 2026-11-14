<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, RotateCw, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import { UNIT_ROLE_LABEL, type UnitRole } from '@/composables/useAuth'
import { useConfirm } from '@/composables/useConfirm'
import { api } from '@/lib/api'

/** A unit's members and roles. Used by system administrators and the unit's owners. */
const props = defineProps<{ unit: { id: number; name: string } }>()

interface UnitMember {
  id: number
  staffId: number
  email: string
  name: string
  active: boolean
  role: UnitRole
}

const { confirm } = useConfirm()
const members = ref<UnitMember[]>([])
const loaded = ref(false)
const error = ref('')
/** The MVP combines the four roles into two common sets; single roles can still be assigned */
const ROLE_CHOICES: { value: string; label: string; roles: UnitRole[] }[] = [
  { value: 'officer', label: '承辦人（負責人＋監考）', roles: ['OWNER', 'PROCTOR'] },
  { value: 'teacher', label: '教師（出題＋評分）', roles: ['AUTHOR', 'GRADER'] },
  { value: 'OWNER', label: '只有負責人', roles: ['OWNER'] },
  { value: 'AUTHOR', label: '只有出題', roles: ['AUTHOR'] },
  { value: 'PROCTOR', label: '只有監考', roles: ['PROCTOR'] },
  { value: 'GRADER', label: '只有評分', roles: ['GRADER'] },
]
const draft = reactive({ email: '', choice: 'teacher' })
const url = () => `/units/${props.unit.id}/members`

async function load() {
  try {
    members.value = await api<UnitMember[]>(url())
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loaded.value = true
  }
}
onMounted(load)

/** All roles of one staff member on one row */
const grouped = () => {
  const map = new Map<number, { staffId: number; name: string; email: string; active: boolean; roles: UnitMember[] }>()
  for (const m of members.value) {
    const row = map.get(m.staffId) ?? { staffId: m.staffId, name: m.name, email: m.email, active: m.active, roles: [] }
    row.roles.push(m)
    map.set(m.staffId, row)
  }
  return [...map.values()]
}

async function add() {
  const email = draft.email.trim()
  if (!email) return
  const choice = ROLE_CHOICES.find((c) => c.value === draft.choice)!
  const have = new Set(members.value.filter((m) => m.email === email.toLowerCase()).map((m) => m.role))
  try {
    for (const role of choice.roles) {
      if (!have.has(role)) await api(url(), { method: 'POST', body: { email, role } })
    }
    draft.email = ''
    toast.success(`已將 ${email} 加入「${props.unit.name}」`)
  } catch (e) {
    toast.error((e as Error).message)
  }
  await load()
}

/** Add a role right on the member's row: "+" lists the roles they lack, one click adds it */
const ALL_ROLES = Object.keys(UNIT_ROLE_LABEL) as UnitRole[]
const addingFor = ref<number | null>(null)
const missing = (roles: UnitMember[]) => ALL_ROLES.filter((r) => !roles.some((m) => m.role === r))

async function grant(row: { name: string; email: string; roles: UnitMember[] }, role: UnitRole) {
  try {
    await api(url(), { method: 'POST', body: { email: row.email, role } })
    toast.success(`已給 ${row.name}「${UNIT_ROLE_LABEL[role]}」角色`)
    if (missing(row.roles).length <= 1) addingFor.value = null
  } catch (e) {
    toast.error((e as Error).message)
  }
  await load()
}

async function remove(m: UnitMember) {
  const ok = await confirm({
    title: `移除 ${m.name} 的「${UNIT_ROLE_LABEL[m.role]}」角色？`,
    description: `移除後立即生效，${m.name} 在「${props.unit.name}」不能再做這個角色的工作。`,
    confirmLabel: '移除',
    tone: 'danger',
  })
  if (!ok) return
  try {
    await api(`${url()}/${m.id}`, { method: 'DELETE' })
    members.value = members.value.filter((x) => x.id !== m.id)
    toast.success(`已移除 ${m.name} 的「${UNIT_ROLE_LABEL[m.role]}」角色`)
  } catch (e) {
    toast.error((e as Error).message)
  }
}
</script>

<template>
  <div>
    <p v-if="error" class="flex items-center gap-3 text-[15px] text-grade" role="alert">
      <span class="min-w-0 flex-1">{{ error }}</span>
      <Button variant="outline" class="rounded-full" @click="load"><RotateCw />重試</Button>
    </p>
    <p v-else-if="!loaded" class="text-[15px] text-ink-2">載入中…</p>
    <template v-else>
      <p v-if="!members.length" class="text-[15px] text-ink-2">這個單位還沒有成員。先加入一位承辦人。</p>
      <ul v-else class="space-y-2">
        <li v-for="row in grouped()" :key="row.email" class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
          <p class="min-w-0 basis-52 text-[15px]">
            <span class="font-medium">{{ row.name }}</span>
            <span class="ml-2 text-sm text-ink-2">{{ row.email }}</span>
            <span v-if="!row.active" class="ml-2 text-sm text-grade">帳號已停用</span>
          </p>
          <span v-for="m in row.roles" :key="m.id" class="inline-flex h-8 items-center gap-1 rounded-full bg-paper pr-1 pl-3 text-sm shadow-card">
            {{ UNIT_ROLE_LABEL[m.role] }}
            <button
              type="button"
              class="flex size-6 items-center justify-center rounded-full text-ink-3 hover:bg-grade-soft hover:text-grade"
              :aria-label="`移除 ${row.name} 的${UNIT_ROLE_LABEL[m.role]}角色`"
              @click="remove(m)"
            >
              <X class="size-3.5" />
            </button>
          </span>
          <template v-if="missing(row.roles).length">
            <button
              type="button"
              class="flex size-8 items-center justify-center rounded-full border border-dashed border-line text-ink-2 hover:border-brand hover:text-brand-strong"
              :aria-label="`給 ${row.name} 加角色`"
              :aria-expanded="addingFor === row.staffId"
              @click="addingFor = addingFor === row.staffId ? null : row.staffId"
            >
              <Plus class="size-4 transition-transform" :class="addingFor === row.staffId && 'rotate-45'" />
            </button>
            <template v-if="addingFor === row.staffId">
              <button
                v-for="role in missing(row.roles)"
                :key="role"
                type="button"
                class="inline-flex h-8 items-center gap-1 rounded-full border border-dashed border-line px-3 text-sm text-ink-2 hover:border-brand hover:text-brand-strong"
                @click="grant(row, role)"
              >
                <Plus class="size-3.5" />{{ UNIT_ROLE_LABEL[role] }}
              </button>
            </template>
          </template>
        </li>
      </ul>
      <form class="mt-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_14rem_auto]" novalidate @submit.prevent="add">
        <input v-model="draft.email" type="email" class="field bg-paper" placeholder="教職員的 Email" aria-label="教職員的 Email" />
        <select v-model="draft.choice" class="field bg-paper" aria-label="角色">
          <option v-for="c in ROLE_CHOICES" :key="c.value" :value="c.value">{{ c.label }}</option>
        </select>
        <Button variant="primary" size="md" class="h-11 rounded-full" type="submit" :disabled="!draft.email.trim()"><Plus />加入</Button>
      </form>
    </template>
  </div>
</template>
