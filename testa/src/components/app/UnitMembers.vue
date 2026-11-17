<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, UserPlus, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import StaffPicker from '@/components/app/StaffPicker.vue'
import TempPasswords, { type IssuedPassword } from '@/components/app/TempPasswords.vue'
import ErrorNote from '@/components/ui/ErrorNote.vue'
import Button from '@/components/ui/Button.vue'
import { ROLE_CHOICES, UNIT_ROLE_LABEL, rolesOf, useAuth, type UnitRole } from '@/composables/useAuth'
import { useConfirm } from '@/composables/useConfirm'
import { ApiError, api } from '@/lib/api'

/** A unit's members and roles. Used by system administrators and the unit's owners. */
const props = defineProps<{ unit: { id: number; name: string } }>()
const emit = defineEmits<{ changed: [] }>()

interface UnitMember {
  id: number
  staffId: number
  email: string
  name: string
  active: boolean
  role: UnitRole
}

const { confirm } = useConfirm()
const { user } = useAuth()
const members = ref<UnitMember[]>([])
const loaded = ref(false)
const error = ref('')
const draft = reactive({ email: '', choice: 'teacher' })
/** The email has no account yet: administrators can create it here, owners are told whom to ask */
const missingAccount = ref('')
const newName = ref('')
const issued = ref<IssuedPassword[]>([])
const busy = ref(false)
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
  if (!email || busy.value) return
  busy.value = true
  missingAccount.value = ''
  try {
    await api(url(), { method: 'POST', body: { email, roles: rolesOf(draft.choice) } })
    draft.email = ''
    toast.success(`已將 ${email} 加入「${props.unit.name}」`)
    emit('changed')
  } catch (e) {
    if (e instanceof ApiError && e.code === 'unknown_staff' && email.includes('@')) missingAccount.value = email
    else toast.error((e as Error).message)
  } finally {
    busy.value = false
  }
  await load()
}

async function createAndAdd() {
  const name = newName.value.trim()
  if (!name || busy.value) return
  busy.value = true
  try {
    const res = await api<{ staff: { name: string; email: string }; temporaryPassword: string }>('/admin/staff', {
      method: 'POST',
      body: { email: missingAccount.value, name, memberships: [{ unitId: props.unit.id, roles: rolesOf(draft.choice) }] },
    })
    issued.value = [{ name: res.staff.name, email: res.staff.email, password: res.temporaryPassword }]
    missingAccount.value = ''
    newName.value = ''
    draft.email = ''
    emit('changed')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    busy.value = false
  }
  await load()
}

/** Add a role right on the member's row: "+" lists the roles they lack, one click adds it */
const ALL_ROLES = Object.keys(UNIT_ROLE_LABEL) as UnitRole[]
const addingFor = ref<number | null>(null)
const missing = (roles: UnitMember[]) => ALL_ROLES.filter((r) => !roles.some((m) => m.role === r))

async function grant(row: { name: string; email: string; roles: UnitMember[] }, role: UnitRole) {
  try {
    await api(url(), { method: 'POST', body: { email: row.email, roles: [role] } })
    toast.success(`已給 ${row.name}「${UNIT_ROLE_LABEL[role]}」角色`)
    emit('changed')
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
    emit('changed')
  } catch (e) {
    toast.error((e as Error).message)
  }
}
</script>

<template>
  <div>
    <ErrorNote v-if="error" :message="error" :retry="load" plain />
    <p v-else-if="!loaded" class="text-[15px] text-ink-2">載入中…</p>
    <template v-else>
      <p v-if="!members.length" class="text-[15px] text-ink-2">這個單位還沒有成員。先加入一位承辦人。</p>
      <ul v-else class="divide-y divide-line">
        <li v-for="row in grouped()" :key="row.email" class="flex flex-wrap items-center gap-x-2 gap-y-1.5 py-3 first:pt-0 last:pb-0">
          <div class="min-w-0 flex-1 basis-full sm:basis-56">
            <p class="truncate text-[15px] font-medium">
              {{ row.name }}<span v-if="!row.active" class="ml-2 text-sm font-normal text-grade">帳號已停用</span>
            </p>
            <p class="truncate text-sm text-ink-2">{{ row.email }}</p>
          </div>
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
        <StaffPicker v-model="draft.email" :unit-id="unit.id" :exclude="members.map((m) => m.email)" />
        <select v-model="draft.choice" class="field" aria-label="角色">
          <option v-for="c in ROLE_CHOICES" :key="c.value" :value="c.value">{{ c.label }}</option>
        </select>
        <Button variant="primary" size="md" type="submit" :disabled="!draft.email.trim() || busy"><Plus />加入</Button>
      </form>

      <div v-if="missingAccount" class="mt-3 rounded-control bg-paper px-4 py-3 shadow-card" role="alert">
        <template v-if="user?.role === 'admin'">
          <p class="text-[15px]"><span class="font-medium">{{ missingAccount }}</span> 還沒有帳號。輸入姓名，直接建立帳號並加入「{{ unit.name }}」。</p>
          <form class="mt-2 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto_auto]" novalidate @submit.prevent="createAndAdd">
            <input v-model="newName" class="field" placeholder="姓名" aria-label="姓名" />
            <Button size="md" @click="missingAccount = ''"><X />取消</Button>
            <Button variant="primary" size="md" type="submit" :disabled="!newName.trim() || busy"><UserPlus />建立並加入</Button>
          </form>
        </template>
        <p v-else class="text-[15px]">
          <span class="font-medium">{{ missingAccount }}</span> 還沒有帳號。請系統管理員先建立帳號，之後就能在這裡加入。
        </p>
      </div>
      <TempPasswords v-if="issued.length" class="mt-3 rounded-control" :items="issued" @close="issued = []" />
    </template>
  </div>
</template>
