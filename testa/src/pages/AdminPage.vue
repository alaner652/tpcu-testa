<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Ban, Building2, DatabaseBackup, Gauge, KeyRound, Lock, Plus, RotateCw, ScrollText, Search, Settings, Shield, Trash2, UserCog, UserPlus, Users, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import AdminOverview from '@/components/app/AdminOverview.vue'
import AuditLog from '@/components/app/AuditLog.vue'
import BackupPanel from '@/components/app/BackupPanel.vue'
import BatchCreate from '@/components/app/BatchCreate.vue'
import EmergencyView from '@/components/app/EmergencyView.vue'
import StaffUnits from '@/components/app/StaffUnits.vue'
import SystemSettings from '@/components/app/SystemSettings.vue'
import TempPasswords, { type IssuedPassword } from '@/components/app/TempPasswords.vue'
import UnitMembers from '@/components/app/UnitMembers.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import MoreMenu from '@/components/ui/MoreMenu.vue'
import SelectionBar, { barButton } from '@/components/ui/SelectionBar.vue'
import SelectMark from '@/components/ui/SelectMark.vue'
import { ROLE_CHOICES, UNIT_ROLE_LABEL, rolesOf, useAuth, type UnitRole } from '@/composables/useAuth'
import { useConfirm } from '@/composables/useConfirm'
import { useSelection } from '@/composables/useSelection'
import { useShortcuts } from '@/composables/useShortcuts'
import { api } from '@/lib/api'

/**
 * Admin pages (system administrators): overview, staff accounts, units and roles, audit log, backups, settings.
 * Administrators manage accounts and units only; they cannot see banks, papers or scores.
 */
interface Staff {
  id: number
  email: string
  name: string
  systemAdmin: boolean
  active: boolean
  mustChangePassword: boolean
  locked: boolean
  lastLoginAt: string | null
  roles: { id: number; unitId: number; unitName: string; role: UnitRole }[]
}
interface Unit {
  id: number
  name: string
  type: 'ADMISSION' | 'CLASS'
  active: boolean
}
interface DeletionImpact {
  deletable: boolean
  banks: number
  exams: number
  graderAssignments: number
  gradings: number
  emergencyAccesses: number
}

const { user } = useAuth()
const { confirm } = useConfirm()

const TABS = [
  { value: 'overview', label: '總覽', icon: Gauge },
  { value: 'staff', label: '使用者', icon: UserCog },
  { value: 'units', label: '單位', icon: Building2 },
  { value: 'audit', label: '操作紀錄', icon: ScrollText },
  { value: 'backups', label: '備份', icon: DatabaseBackup },
  { value: 'emergency', label: '緊急檢視', icon: Shield },
  { value: 'settings', label: '系統設定', icon: Settings },
] as const
const tabs = TABS
type Tab = (typeof TABS)[number]['value']
const tab = ref<Tab>('overview')
/** System settings with unsaved changes: ask before leaving the tab */
const settingsDirty = ref(false)
async function switchTab(next: Tab) {
  if (next === tab.value) return
  if (tab.value === 'settings' && settingsDirty.value) {
    const ok = await confirm({ title: '設定還沒儲存', description: '離開這一頁，剛才的變更會消失。', confirmLabel: '不儲存，離開' })
    if (!ok) return
    settingsDirty.value = false
  }
  tab.value = next
}

const loadError = ref('')
const fail = (e: unknown) => toast.error((e as Error).message)

// ── Users ────────────────────────────────────────────
const staff = ref<Staff[]>([])
const staffQuery = ref('')
const shownStaff = computed(() => {
  const q = staffQuery.value.trim().toLowerCase()
  if (!q) return staff.value
  return staff.value.filter((s) => [s.name, s.email, ...s.roles.map((r) => r.unitName)].some((t) => t.toLowerCase().includes(q)))
})
const creating = ref(false)
const draft = reactive({ email: '', name: '', systemAdmin: false, unitId: null as number | null, choice: 'teacher' })
const createError = ref('')
const saving = ref(false)
const batching = ref(false)
/** Staff row whose units and roles are open */
const openStaff = ref<number | null>(null)
/** Temporary passwords just generated, shown only this once */
const issued = ref<IssuedPassword[]>([])

async function loadStaff() {
  try {
    staff.value = await api<Staff[]>('/admin/staff')
    loadError.value = ''
  } catch (e) {
    loadError.value = (e as Error).message
  }
}

async function createStaff() {
  if (saving.value) return
  if (!draft.email.trim() || !draft.name.trim()) return void (createError.value = '請輸入 Email 與姓名。')
  saving.value = true
  try {
    const res = await api<{ staff: Staff; temporaryPassword: string }>('/admin/staff', {
      method: 'POST',
      body: {
        email: draft.email,
        name: draft.name,
        systemAdmin: draft.systemAdmin,
        memberships: draft.unitId === null ? [] : [{ unitId: draft.unitId, roles: rolesOf(draft.choice) }],
      },
    })
    issued.value = [{ name: res.staff.name, email: res.staff.email, password: res.temporaryPassword }]
    Object.assign(draft, { email: '', name: '', systemAdmin: false, unitId: null })
    createError.value = ''
    creating.value = false
    await loadStaff()
  } catch (e) {
    createError.value = (e as Error).message
  } finally {
    saving.value = false
  }
}

async function toggleActive(s: Staff) {
  if (s.active) {
    const ok = await confirm({
      title: `停用 ${s.name} 的帳號？`,
      description: '停用後對方會立刻被登出，也無法再登入。題庫與考試紀錄會保留，之後可以重新啟用。',
      confirmLabel: '停用',
      tone: 'danger',
    })
    if (!ok) return
  }
  try {
    await api(`/admin/staff/${s.id}`, { method: 'PATCH', body: { active: !s.active } })
    toast.success(s.active ? `已停用 ${s.name} 的帳號` : `已啟用 ${s.name} 的帳號`)
    await loadStaff()
  } catch (e) {
    fail(e)
  }
}

async function resetPassword(s: Staff) {
  const ok = await confirm({
    title: `重設 ${s.name} 的密碼？`,
    description: '系統會產生一組臨時密碼。對方會立刻被登出，下次登入後必須換成自己的密碼。',
    confirmLabel: '重設密碼',
    tone: 'danger',
  })
  if (!ok) return
  try {
    const res = await api<{ temporaryPassword: string }>(`/admin/staff/${s.id}/reset-password`, { method: 'POST' })
    issued.value = [{ name: s.name, email: s.email, password: res.temporaryPassword }]
    await loadStaff()
  } catch (e) {
    fail(e)
  }
}

async function deleteStaff(s: Staff) {
  try {
    const impact = await api<DeletionImpact>(`/admin/staff/${s.id}/deletion-impact`)
    if (!impact.deletable) {
      const uses = [
        impact.banks && `${impact.banks} 份題庫`,
        impact.exams && `${impact.exams} 場考試`,
        impact.graderAssignments && `${impact.graderAssignments} 筆評分指派`,
        impact.gradings && `${impact.gradings} 筆評分`,
        impact.emergencyAccesses && `${impact.emergencyAccesses} 筆緊急授權`,
      ].filter(Boolean)
      toast.error('無法永久刪除這個帳號', { description: `仍有${uses.join('、')}。為了保留歷史資料，請維持停用。` })
      return
    }
    const ok = await confirm({
      title: `永久刪除 ${s.name}？`,
      description: `將刪除 ${s.email} 的帳號與單位角色，之後無法復原。操作紀錄仍會保留。`,
      confirmLabel: '永久刪除',
      tone: 'danger',
    })
    if (!ok) return
    await api(`/admin/staff/${s.id}`, { method: 'DELETE' })
    issued.value = issued.value.filter((i) => i.email !== s.email)
    toast.success(`已永久刪除 ${s.name} 的帳號`)
    await loadStaff()
  } catch (e) {
    fail(e)
  }
}

function batchDone(items: IssuedPassword[]) {
  issued.value = items
  batching.value = false
  toast.success(`已建立 ${items.length} 個帳號`)
  loadStaff()
}

// ── Several accounts at once (long press or Ctrl click a row) ──
const selection = useSelection(() => (tab.value === 'staff' ? shownStaff.value : []), (s) => s.id)
/** Your own account is never part of a batch: you cannot disable yourself or reset your password here */
const others = computed(() => selection.selected.value.filter((s) => s.email !== user.value?.email))
const toDisable = computed(() => others.value.filter((s) => s.active))
const toEnable = computed(() => others.value.filter((s) => !s.active))
const bulkJoin = reactive({ open: false, unitId: null as number | null, choice: 'teacher' })
const bulkBusy = ref(false)
watch(selection.active, (on) => !on && (bulkJoin.open = false))
const skipSelf = () => (others.value.length < selection.count.value ? '你自己的帳號不包含在內。' : '')

async function bulk(run: () => Promise<unknown>, done: string) {
  bulkBusy.value = true
  try {
    await run()
    toast.success(done)
    selection.clear()
  } catch (e) {
    fail(e)
  } finally {
    bulkBusy.value = false
    await loadStaff()
  }
}

function startBulkJoin() {
  bulkJoin.unitId = units.value.find((u) => u.active)?.id ?? null
  bulkJoin.open = true
}

async function bulkAddToUnit() {
  const ids = selection.selected.value.map((s) => s.id)
  const unit = units.value.find((u) => u.id === bulkJoin.unitId)
  if (!unit || !ids.length) return
  await bulk(
    () => api('/admin/staff/batch/memberships', { method: 'POST', body: { ids, unitId: unit.id, roles: rolesOf(bulkJoin.choice) } }),
    `已把 ${ids.length} 人加入「${unit.name}」`,
  )
}

async function bulkActive(active: boolean) {
  const list = active ? toEnable.value : toDisable.value
  if (!list.length) return
  if (!active) {
    const ok = await confirm({
      title: `停用 ${list.length} 個帳號？`,
      description: `停用後這些人會立刻被登出，也無法再登入。題庫與考試紀錄會保留，之後可以重新啟用。${skipSelf()}`,
      confirmLabel: '停用',
      tone: 'danger',
    })
    if (!ok) return
  }
  await bulk(
    () => api('/admin/staff/batch/active', { method: 'POST', body: { ids: list.map((s) => s.id), active } }),
    `已${active ? '啟用' : '停用'} ${list.length} 個帳號`,
  )
}

async function bulkResetPasswords() {
  const list = others.value
  if (!list.length) return
  const ok = await confirm({
    title: `重設 ${list.length} 人的密碼？`,
    description: `系統會為每個人產生臨時密碼。他們會立刻被登出，下次登入後必須換成自己的密碼。${skipSelf()}`,
    confirmLabel: '重設密碼',
    tone: 'danger',
  })
  if (!ok) return
  await bulk(async () => {
    const res = await api<{ staff: Staff; temporaryPassword: string }[]>('/admin/staff/batch/reset-password', {
      method: 'POST',
      body: { ids: list.map((s) => s.id) },
    })
    issued.value = res.map((r) => ({ name: r.staff.name, email: r.staff.email, password: r.temporaryPassword }))
  }, `已重設 ${list.length} 人的密碼`)
}

const searchInput = ref<HTMLInputElement | null>(null)
useShortcuts('使用者', () => [
  { combo: 'n', label: '新增帳號', run: () => (creating.value = true), when: () => tab.value === 'staff' && !creating.value && !batching.value },
  { combo: '/', label: '搜尋', run: () => searchInput.value?.focus(), when: () => tab.value === 'staff' && !!searchInput.value },
  { combo: '', label: '展開這個人的單位與角色', keys: 'Enter', when: () => tab.value === 'staff' },
  ...selection.shortcuts(() => tab.value === 'staff'),
])

const rolesText = (s: Staff) => {
  const byUnit = new Map<string, string[]>()
  for (const r of s.roles) byUnit.set(r.unitName, [...(byUnit.get(r.unitName) ?? []), UNIT_ROLE_LABEL[r.role]])
  return [...byUnit].map(([unit, roles]) => `${unit}（${roles.join('、')}）`).join('，')
}

// ── Units ────────────────────────────────────────────
const units = ref<Unit[]>([])
const newUnit = ref('')
const openUnit = ref<number | null>(null)
async function loadUnits() {
  try {
    units.value = await api<Unit[]>('/units')
    loadError.value = ''
  } catch (e) {
    loadError.value = (e as Error).message
  }
}

async function addUnit() {
  const name = newUnit.value.trim()
  if (!name) return
  try {
    await api('/units', { method: 'POST', body: { name, type: 'ADMISSION' } })
    newUnit.value = ''
    toast.success(`已新增單位「${name}」`)
    await loadUnits()
  } catch (e) {
    fail(e)
  }
}

async function toggleUnit(u: Unit) {
  if (u.active) {
    const ok = await confirm({
      title: `停用單位「${u.name}」？`,
      description: '停用後，這個單位的成員會暫時失去在此單位的所有權限。資料會保留，之後可以重新啟用。',
      confirmLabel: '停用',
      tone: 'danger',
    })
    if (!ok) return
  }
  try {
    await api(`/units/${u.id}`, { method: 'PATCH', body: { active: !u.active } })
    toast.success(u.active ? `已停用單位「${u.name}」` : `已啟用單位「${u.name}」`)
    await loadUnits()
  } catch (e) {
    fail(e)
  }
}

const toggleMembers = (u: Unit) => (openUnit.value = openUnit.value === u.id ? null : u.id)

function load() {
  loadError.value = ''
  if (tab.value === 'staff') {
    loadStaff()
    loadUnits()
  } else if (tab.value === 'units') loadUnits()
  else if (tab.value === 'audit' || tab.value === 'overview') {
    // Map ids in the audit log back to names and unit names
    if (!staff.value.length) loadStaff()
    if (!units.value.length) loadUnits()
  }
}
onMounted(load)
watch(tab, load)
</script>

<template>
  <div>
    <h1 class="mb-4 px-4 text-[26px] font-bold sm:text-[30px] md:px-0">後台管理</h1>

    <div role="tablist" class="mb-4 flex gap-1 overflow-x-auto px-4 md:px-0">
      <button
        v-for="t in tabs"
        :key="t.value"
        type="button"
        role="tab"
        :aria-selected="tab === t.value"
        class="inline-flex h-10 shrink-0 items-center gap-1.5 rounded-full px-4 text-[15px] transition-colors"
        :class="tab === t.value ? 'bg-brand font-medium text-brand-fg' : 'text-ink-2 hover:bg-ink/[0.06] hover:text-ink'"
        @click="switchTab(t.value)"
      >
        <component :is="t.icon" class="size-4" aria-hidden="true" />{{ t.label }}
      </button>
    </div>

    <p v-if="loadError" class="card mb-4 flex items-center gap-3 px-5 py-4 text-[15px] text-grade sm:px-6" role="alert">
      <span class="min-w-0 flex-1">{{ loadError }}</span>
      <Button variant="outline" @click="load"><RotateCw />重試</Button>
    </p>

    <AdminOverview v-if="tab === 'overview'" :staff="staff" @go="switchTab" />

    <!-- Users -->
    <section v-else-if="tab === 'staff'" class="card">
      <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-6">
        <p class="text-[15px] text-ink-2">{{ staff.length }} 位教職員</p>
        <label v-if="staff.length > 8" class="relative ml-auto w-full max-w-64 max-sm:order-last max-sm:max-w-none">
          <Search class="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-ink-3" aria-hidden="true" />
          <input ref="searchInput" v-model="staffQuery" type="search" class="field h-10 pl-10" placeholder="搜尋姓名、Email 或單位" aria-label="搜尋教職員" />
        </label>
        <div v-if="!creating && !batching" class="flex gap-2">
          <Button variant="outline" @click="batching = true"><Users />批次建立</Button>
          <Button variant="primary" @click="creating = true"><Plus />新增帳號</Button>
        </div>
      </div>

      <TempPasswords v-if="issued.length" class="border-t border-line" :items="issued" @close="issued = []" />

      <div v-if="batching" class="border-t border-line bg-sunken/60">
        <BatchCreate :units="units" :existing="staff.map((s) => s.email)" @done="batchDone" @cancel="batching = false" />
      </div>

      <form v-if="creating" class="border-t border-line bg-sunken/60 px-5 py-4 sm:px-6" novalidate @submit.prevent="createStaff">
        <div class="grid gap-2 sm:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_auto_auto]">
          <input v-model="draft.email" type="email" class="field bg-paper" placeholder="Email（登入帳號）" aria-label="Email" />
          <input v-model="draft.name" class="field bg-paper" placeholder="姓名" aria-label="姓名" />
          <Button size="md" @click="((creating = false), (createError = ''))"><X />取消</Button>
          <Button variant="primary" size="md" type="submit" :disabled="saving"><UserPlus />建立帳號</Button>
        </div>
        <div class="mt-2 grid gap-2 sm:grid-cols-2">
          <select v-model="draft.unitId" class="field bg-paper" aria-label="加入單位">
            <option :value="null">先不加入單位</option>
            <option v-for="u in units.filter((x) => x.active)" :key="u.id" :value="u.id">加入「{{ u.name }}」</option>
          </select>
          <select v-model="draft.choice" class="field bg-paper" aria-label="角色" :disabled="draft.unitId === null">
            <option v-for="c in ROLE_CHOICES" :key="c.value" :value="c.value">{{ c.label }}</option>
          </select>
        </div>
        <label class="mt-3 flex items-center gap-2 text-[15px]">
          <input v-model="draft.systemAdmin" type="checkbox" class="size-4 accent-brand" />
          設為系統管理員（可以管理所有帳號與單位）
        </label>
        <p v-if="createError" class="mt-2 text-sm text-grade" role="alert">{{ createError }}</p>
      </form>

      <ul class="divide-y divide-line border-t border-line">
        <li
          v-for="s in shownStaff"
          :key="s.id"
          v-bind="selection.row(s, () => (openStaff = openStaff === s.id ? null : s.id))"
          class="group/row outline-none focus-visible:bg-sunken pointer-coarse:select-none"
          :class="selection.has(s) && 'bg-brand-soft/50 focus-visible:bg-brand-soft'"
        >
          <div class="flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3.5 sm:px-6">
            <SelectMark :checked="selection.has(s)" :picking="selection.active.value" :label="`選取 ${s.name}`" @toggle="(e) => selection.mark(s, e)">
              <span
                class="flex size-10 items-center justify-center rounded-full bg-brand-soft text-[15px] font-semibold text-brand-strong"
                :class="!s.active && 'opacity-60'"
              >
                {{ s.name.slice(0, 1).toUpperCase() }}
              </span>
            </SelectMark>
            <div class="min-w-0 flex-1 basis-56" :class="!s.active && 'opacity-60'">
              <p class="flex flex-wrap items-center gap-x-2 gap-y-1 text-base font-medium">
                <span class="truncate">{{ s.name }}</span>
                <Badge v-if="s.systemAdmin" tone="done"><Shield />系統管理員</Badge>
                <Badge v-if="s.email === user?.email">你自己</Badge>
                <Badge v-if="!s.active">已停用</Badge>
                <Badge v-if="s.locked" tone="danger"><Lock />密碼輸錯，已鎖定</Badge>
                <Badge v-if="s.mustChangePassword" tone="warn">尚未換掉臨時密碼</Badge>
              </p>
              <p class="truncate text-sm text-ink-2">{{ s.email }}</p>
              <p class="text-sm text-ink-2">{{ rolesText(s) || (s.systemAdmin ? '' : '尚未加入任何單位') }}</p>
            </div>
            <Button variant="outline" :aria-expanded="openStaff === s.id" @click="openStaff = openStaff === s.id ? null : s.id">
              <Building2 />{{ openStaff === s.id ? '收合' : '單位與角色' }}
            </Button>
            <MoreMenu
              :label="`${s.name} 的更多動作`"
              :actions="s.email === user?.email ? [] : [
                { label: '重設密碼', icon: KeyRound, run: () => resetPassword(s) },
                s.active
                  ? { label: '停用帳號', icon: Ban, run: () => toggleActive(s), danger: true }
                  : { label: '重新啟用', icon: RotateCw, run: () => toggleActive(s) },
                ...(!s.active ? [{ label: '永久刪除', icon: Trash2, run: () => deleteStaff(s), danger: true }] : []),
              ]"
            />
          </div>
          <div v-if="openStaff === s.id" class="cursor-auto border-t border-line bg-sunken/60 px-5 py-4 select-text sm:px-6" @pointerdown.stop @click.stop @keydown.stop>
            <StaffUnits :staff="s" :units="units" @changed="loadStaff" />
          </div>
        </li>
        <li v-if="staffQuery.trim() && !shownStaff.length" class="px-5 py-6 text-[15px] text-ink-2 sm:px-6">找不到符合「{{ staffQuery.trim() }}」的教職員。</li>
      </ul>
    </section>

    <!-- Units -->
    <section v-else-if="tab === 'units'" class="card">
      <form class="flex gap-2 px-5 py-4 sm:px-6" @submit.prevent="addUnit">
        <input v-model="newUnit" class="field" placeholder="新單位名稱，例如：招生組" aria-label="新單位名稱" />
        <Button variant="primary" size="md" type="submit" :disabled="!newUnit.trim()"><Plus />新增</Button>
      </form>
      <ul class="divide-y divide-line border-t border-line">
        <li v-for="u in units" :key="u.id">
          <div class="flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3.5 sm:px-6" :class="!u.active && 'opacity-60'">
            <p class="flex min-w-0 flex-1 basis-40 items-center gap-2 text-base font-medium">
              <span class="truncate">{{ u.name }}</span>
              <Badge v-if="!u.active">已停用</Badge>
            </p>
            <Button variant="outline" :aria-expanded="openUnit === u.id" @click="toggleMembers(u)"><Users />{{ openUnit === u.id ? '收合' : '成員與角色' }}</Button>
            <MoreMenu
              :label="`${u.name} 的更多動作`"
              :actions="[
                u.active
                  ? { label: '停用單位', icon: Ban, run: () => toggleUnit(u), danger: true }
                  : { label: '重新啟用', icon: RotateCw, run: () => toggleUnit(u) },
              ]"
            />
          </div>

          <div v-if="openUnit === u.id" class="border-t border-line bg-sunken/60 px-5 py-4 sm:px-6">
            <UnitMembers :unit="u" @changed="loadStaff" />
          </div>
        </li>
        <li v-if="!units.length && !loadError" class="px-5 py-6 text-[15px] text-ink-2 sm:px-6">還沒有任何單位。先新增一個，例如「招生組」。</li>
      </ul>
    </section>

    <AuditLog v-else-if="tab === 'audit'" :staff="staff" :units="units" />

    <BackupPanel v-else-if="tab === 'backups'" />

    <EmergencyView v-else-if="tab === 'emergency'" />

    <SystemSettings v-else-if="tab === 'settings'" v-model:dirty="settingsDirty" />

    <SelectionBar :count="selection.count.value" :total="shownStaff.length" unit="人" @all="selection.all" @clear="selection.clear">
      <form v-if="bulkJoin.open" class="flex flex-wrap items-center gap-1.5" @submit.prevent="bulkAddToUnit">
        <select v-model="bulkJoin.unitId" class="field h-9 w-44 bg-paper text-ink" aria-label="加入哪個單位">
          <option v-for="u in units.filter((x) => x.active)" :key="u.id" :value="u.id">{{ u.name }}</option>
        </select>
        <select v-model="bulkJoin.choice" class="field h-9 w-40 bg-paper text-ink" aria-label="角色">
          <option v-for="c in ROLE_CHOICES" :key="c.value" :value="c.value">{{ c.label }}</option>
        </select>
        <button type="button" :class="barButton" @click="bulkJoin.open = false">取消</button>
        <button type="submit" :class="[barButton, 'bg-brand text-brand-fg hover:bg-brand-strong']" :disabled="bulkJoin.unitId === null || bulkBusy">
          <Building2 />加入
        </button>
      </form>
      <template v-else>
        <button v-if="units.some((u) => u.active)" type="button" :class="barButton" @click="startBulkJoin"><Building2 />加入單位</button>
        <button v-if="others.length" type="button" :class="barButton" :disabled="bulkBusy" @click="bulkResetPasswords"><KeyRound />重設密碼</button>
        <button v-if="toEnable.length" type="button" :class="barButton" :disabled="bulkBusy" @click="bulkActive(true)"><RotateCw />啟用</button>
        <button v-if="toDisable.length" type="button" :class="barButton" :disabled="bulkBusy" @click="bulkActive(false)"><Ban />停用</button>
      </template>
    </SelectionBar>
  </div>
</template>
