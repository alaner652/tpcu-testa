<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Ban, Building2, ChevronDown, Copy, DatabaseBackup, Download, KeyRound, Lock, Plus, RotateCw, ScrollText, Search, Settings, Shield, Trash2, UserCog, UserPlus, Users, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import EmergencyView from '@/components/app/EmergencyView.vue'
import SystemSettings from '@/components/app/SystemSettings.vue'
import UnitMembers from '@/components/app/UnitMembers.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import MoreMenu from '@/components/ui/MoreMenu.vue'
import { UNIT_ROLE_LABEL, useAuth, type UnitRole } from '@/composables/useAuth'
import { useConfirm } from '@/composables/useConfirm'
import { api, apiBlob } from '@/lib/api'

/**
 * Admin pages (system administrators): staff accounts, units and roles, audit log.
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
  roles: { unitId: number; unitName: string; role: UnitRole }[]
}
interface Unit {
  id: number
  name: string
  type: 'ADMISSION' | 'CLASS'
  active: boolean
}
interface AuditEntry {
  id: number
  at: string
  actorType: string
  actorLabel: string | null
  action: string
  targetType: string | null
  targetId: string | null
  unitId: number | null
  reason: string | null
  detail: string | null
  ip: string | null
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
  { value: 'staff', label: '使用者', icon: UserCog },
  { value: 'units', label: '單位', icon: Building2 },
  { value: 'audit', label: '操作紀錄', icon: ScrollText },
  { value: 'backups', label: '備份', icon: DatabaseBackup },
  { value: 'emergency', label: '緊急檢視', icon: Shield },
  { value: 'settings', label: '系統設定', icon: Settings },
] as const
const tabs = TABS
const tab = ref<(typeof TABS)[number]['value']>('staff')

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
const draft = reactive({ email: '', name: '', systemAdmin: false })
const createError = ref('')
const saving = ref(false)
/** The temporary password just generated, shown only this once */
const issued = ref<{ name: string; email: string; password: string } | null>(null)
const passwordCopied = ref(false)
watch(issued, () => (passwordCopied.value = false))

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
    const res = await api<{ staff: Staff; temporaryPassword: string }>('/admin/staff', { method: 'POST', body: draft })
    issued.value = { name: res.staff.name, email: res.staff.email, password: res.temporaryPassword }
    Object.assign(draft, { email: '', name: '', systemAdmin: false })
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
    issued.value = { name: s.name, email: s.email, password: res.temporaryPassword }
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
    if (issued.value?.email === s.email) issued.value = null
    toast.success(`已永久刪除 ${s.name} 的帳號`)
    await loadStaff()
  } catch (e) {
    fail(e)
  }
}

async function copyPassword() {
  if (!issued.value) return
  try {
    await navigator.clipboard.writeText(issued.value.password)
    passwordCopied.value = true
    toast.success('已複製臨時密碼')
  } catch {
    passwordCopied.value = false
    toast.error('無法複製，請手動抄寫')
  }
}

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

// ── Audit log ────────────────────────────────────────
const ACTION_LABEL: Record<string, string> = {
  SYSTEM_SETUP: '初始設定',
  LOGIN: '登入',
  LOGIN_FAILED: '登入失敗',
  LOGOUT: '登出',
  PASSWORD_CHANGED: '變更密碼',
  PASSWORD_RESET: '重設密碼',
  STAFF_CREATED: '建立帳號',
  STAFF_ENABLED: '啟用帳號',
  STAFF_DISABLED: '停用帳號',
  STAFF_DELETED: '永久刪除帳號',
  BACKUP_REQUESTED: '建立備份',
  BACKUP_DOWNLOADED: '下載備份',
  SYSTEM_ADMIN_GRANTED: '設為系統管理員',
  SYSTEM_ADMIN_REVOKED: '取消系統管理員',
  UNIT_CREATED: '建立單位',
  UNIT_RENAMED: '單位改名',
  UNIT_ENABLED: '啟用單位',
  UNIT_DISABLED: '停用單位',
  UNIT_ROLE_GRANTED: '指派角色',
  UNIT_ROLE_REVOKED: '移除角色',
  BANK_CREATED: '建立題庫',
  BANK_DELETED: '刪除題庫',
  EXAM_CREATED: '建立考試',
  EXAM_PUBLISHED: '發布考試',
  EXAM_WITHDRAWN: '取消發布',
  EXAM_ENDED: '結束考試',
  EXAM_ARCHIVED: '封存考試',
  EXAM_UNARCHIVED: '取消封存',
  EXAM_PURGED: '刪除考試資料',
  EMERGENCY_ACCESS_GRANTED: '授權緊急檢視',
  EMERGENCY_ACCESS_REVOKED: '撤銷緊急檢視',
  EMERGENCY_ACCESS_VIEWED: '緊急檢視考試',
  EXAM_DELETED: '刪除考試',
  CANDIDATES_IMPORTED: '匯入考生名單',
  CANDIDATE_CODE_RESET: '重設登入碼',
  CANDIDATE_REMOVED: '移除考生',
  CANDIDATE_LOGIN: '考生登入',
  CANDIDATE_LOGIN_FAILED: '考生登入失敗',
  CANDIDATE_LOGOUT: '考生登出',
  ATTEMPT_STARTED: '開始作答',
  ATTEMPT_SUBMITTED: '交卷',
  ATTEMPT_EXTENDED: '補時',
  GRADER_ASSIGNED: '指派評分者',
  GRADER_UNASSIGNED: '取消指派評分者',
  GRADING_REOPENED: '重開評分',
  RESULTS_PUBLISHED: '公布成績',
  CANDIDATE_RESULTS_CHANGED: '考生查詢成績設定',
  SETTINGS_CHANGED: '變更系統設定',
  RESULTS_EXPORTED: '匯出成績',
}
const DETAIL_LABEL: Record<string, string> = {
  bad_password: '密碼錯誤',
  unknown_account: '帳號不存在',
  locked: '帳號鎖定中',
  inactive: '帳號已停用',
  system_admin: '系統管理員',
  unknown_ticket: '准考證號不在名單上',
  bad_code: '登入碼錯誤',
  replaced_session: '在別處重新登入，舊連線已登出',
  auto: '系統自動收卷',
  ...UNIT_ROLE_LABEL,
}
const audit = ref<AuditEntry[]>([])
const auditTotal = ref(0)
const auditPage = ref(0)

const auditAction = ref('')
watch(auditAction, () => loadAudit())

async function loadAudit(page = 0) {
  try {
    const action = auditAction.value ? `&action=${auditAction.value}` : ''
    const res = await api<{ items: AuditEntry[]; total: number }>(`/admin/audit-logs?page=${page}&size=50${action}`)
    audit.value = page === 0 ? res.items : [...audit.value, ...res.items]
    auditTotal.value = res.total
    auditPage.value = page
    loadError.value = ''
  } catch (e) {
    loadError.value = (e as Error).message
  }
}

/** The backend can list and download dumps, and signal the isolated backup container to create one. */
interface BackupFile {
  name: string
  size: number
  at: string
}
interface BackupState {
  configured: boolean
  triggerSupported: boolean
  running: boolean
  lastFailed: boolean
  keepDays: number
  items: BackupFile[]
}
const backups = ref<BackupState | null>(null)
const backupsLoading = ref(false)
const backupStarting = ref(false)
const showAllBackups = ref(false)
const shownBackups = computed(() => (showAllBackups.value ? backups.value?.items : backups.value?.items.slice(0, 7)) ?? [])
async function loadBackups(silent = false) {
  if (!silent) backupsLoading.value = true
  try {
    backups.value = await api<BackupState>('/admin/backups')
    loadError.value = ''
  } catch (e) {
    loadError.value = (e as Error).message
  } finally {
    if (!silent) backupsLoading.value = false
  }
}

async function startBackup() {
  if (backupStarting.value || backups.value?.running) return
  const previous = backups.value?.items[0]?.name
  backupStarting.value = true
  try {
    await api('/admin/backups/run', { method: 'POST' })
    toast('正在建立備份', { description: '完成後會自動更新清單。' })
    for (let i = 0; i < 60; i++) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      await loadBackups(true)
      if (!backups.value?.running) {
        if (backups.value?.lastFailed) throw new Error('備份建立失敗，請聯絡系統維護人員。')
        if (backups.value?.items[0]?.name !== previous) {
          toast.success('備份已建立')
          return
        }
      }
    }
    toast('備份仍在背景執行', { description: '稍後按「重新整理」查看結果。' })
  } catch (e) {
    fail(e)
  } finally {
    backupStarting.value = false
  }
}

async function downloadBackup(b: BackupFile) {
  try {
    const blob = await apiBlob(`/admin/backups/${encodeURIComponent(b.name)}`)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = b.name
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    toast.success('已開始下載備份')
  } catch (e) {
    fail(e)
  }
}
/** Daily backups: warn when there has been none for 26 hours */
const backupStale = (b: BackupFile) => Date.now() - new Date(b.at).getTime() > 26 * 3600 * 1000
const fileSize = (bytes: number) => (bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`)

const when = (iso: string) =>
  new Date(iso).toLocaleString('zh-TW', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })

const auditTarget = (a: AuditEntry) => {
  const parts: string[] = []
  if (a.targetType === 'staff') {
    const s = staff.value.find((x) => String(x.id) === a.targetId)
    parts.push(s ? s.name : `帳號 #${a.targetId}`)
  }
  if (a.unitId) parts.push(units.value.find((u) => u.id === a.unitId)?.name ?? `單位 #${a.unitId}`)
  if (a.detail) parts.push(DETAIL_LABEL[a.detail] ?? a.detail)
  if (a.reason) parts.push(`理由：${a.reason}`)
  return parts.join('，')
}

function load() {
  loadError.value = ''
  if (tab.value === 'staff') loadStaff()
  else if (tab.value === 'units') loadUnits()
  else if (tab.value === 'audit') {
    loadAudit()
    // Map ids in the audit log back to names and unit names
    if (!staff.value.length) loadStaff()
    if (!units.value.length) loadUnits()
  } else if (tab.value === 'backups') loadBackups()
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
        @click="tab = t.value"
      >
        <component :is="t.icon" class="size-4" aria-hidden="true" />{{ t.label }}
      </button>
    </div>

    <p v-if="loadError" class="card mb-4 flex items-center gap-3 px-5 py-4 text-[15px] text-grade sm:px-6" role="alert">
      <span class="min-w-0 flex-1">{{ loadError }}</span>
      <Button variant="outline" @click="load"><RotateCw />重試</Button>
    </p>

    <!-- Users -->
    <section v-if="tab === 'staff'" class="card">
      <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-6">
        <p class="text-[15px] text-ink-2">{{ staff.length }} 位教職員</p>
        <label v-if="staff.length > 8" class="relative ml-auto w-full max-w-64 max-sm:order-last max-sm:max-w-none">
          <Search class="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-ink-3" aria-hidden="true" />
          <input v-model="staffQuery" type="search" class="field h-10 pl-10" placeholder="搜尋姓名、Email 或單位" aria-label="搜尋教職員" />
        </label>
        <Button v-if="!creating" variant="primary" @click="creating = true"><Plus />新增帳號</Button>
      </div>

      <div v-if="issued" class="border-t border-line bg-marker/40 px-5 py-4 sm:px-6" role="status">
        <p class="text-[15px] font-medium">{{ issued.name }}（{{ issued.email }}）的臨時密碼</p>
        <div class="mt-2 flex flex-wrap items-center gap-2">
          <code class="rounded-control bg-paper px-3 py-1.5 text-lg font-semibold tracking-wider select-all">{{ issued.password }}</code>
          <Button variant="outline" @click="copyPassword"><Copy />{{ passwordCopied ? '已複製' : '複製' }}</Button>
        </div>
        <p class="mt-2 text-sm text-ink-2">這組密碼只會顯示這一次，請親自交給對方。對方登入後必須先換成自己的密碼。</p>
      </div>

      <form v-if="creating" class="border-t border-line bg-sunken/60 px-5 py-4 sm:px-6" novalidate @submit.prevent="createStaff">
        <div class="grid gap-2 sm:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_auto_auto]">
          <input v-model="draft.email" type="email" class="field bg-paper" placeholder="Email（登入帳號）" aria-label="Email" />
          <input v-model="draft.name" class="field bg-paper" placeholder="姓名" aria-label="姓名" />
          <Button size="md" @click="((creating = false), (createError = ''))"><X />取消</Button>
          <Button variant="primary" size="md" type="submit" :disabled="saving"><UserPlus />建立帳號</Button>
        </div>
        <label class="mt-3 flex items-center gap-2 text-[15px]">
          <input v-model="draft.systemAdmin" type="checkbox" class="size-4 accent-brand" />
          設為系統管理員（可以管理所有帳號與單位）
        </label>
        <p class="mt-2 text-sm text-ink-2">建立後到「單位」分頁，把這位教職員加入單位並指派角色。</p>
        <p v-if="createError" class="mt-2 text-sm text-grade" role="alert">{{ createError }}</p>
      </form>

      <ul class="divide-y divide-line border-t border-line">
        <li v-for="s in shownStaff" :key="s.id" class="flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3.5 sm:px-6" :class="!s.active && 'opacity-60'">
          <span class="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-soft text-[15px] font-semibold text-brand-strong">
            {{ s.name.slice(0, 1).toUpperCase() }}
          </span>
          <div class="min-w-0 flex-1 basis-56">
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
            <UnitMembers :unit="u" />
          </div>
        </li>
        <li v-if="!units.length && !loadError" class="px-5 py-6 text-[15px] text-ink-2 sm:px-6">還沒有任何單位。先新增一個，例如「招生組」。</li>
      </ul>
    </section>

    <!-- Audit log -->
    <section v-else-if="tab === 'audit'" class="card">
      <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-6">
        <p class="text-[15px] text-ink-2">共 {{ auditTotal }} 筆。紀錄只會增加，任何人都不能修改或刪除。</p>
        <select v-model="auditAction" class="field h-10 w-full sm:w-56" aria-label="依動作篩選">
          <option value="">全部動作</option>
          <option v-for="(label, action) in ACTION_LABEL" :key="action" :value="action">{{ label }}</option>
        </select>
      </div>
      <ul class="divide-y divide-line border-t border-line">
        <li v-if="auditAction && !audit.length" class="px-5 py-6 text-[15px] text-ink-2 sm:px-6">沒有這個動作的紀錄。</li>
        <li v-for="a in audit" :key="a.id" class="flex flex-wrap items-baseline gap-x-4 gap-y-0.5 px-5 py-3 text-[15px] sm:px-6">
          <span class="w-36 shrink-0 text-sm text-ink-2 tabular-nums">{{ when(a.at) }}</span>
          <span class="w-28 shrink-0 font-medium" :class="a.action.endsWith('LOGIN_FAILED') && 'text-grade'">{{ ACTION_LABEL[a.action] ?? a.action }}</span>
          <span class="min-w-0 flex-1 basis-48 truncate">{{ a.actorLabel ?? '系統' }}<template v-if="a.actorType === 'CANDIDATE'">（考生）</template></span>
          <span class="min-w-0 flex-1 basis-48 text-sm text-ink-2">{{ auditTarget(a) }}</span>
        </li>
      </ul>
      <div v-if="audit.length < auditTotal" class="border-t border-line px-5 py-3 text-center sm:px-6">
        <Button variant="outline" @click="loadAudit(auditPage + 1)"><ChevronDown />載入更早的紀錄</Button>
      </div>
    </section>

    <!-- Backups -->
    <section v-else-if="tab === 'backups'" class="card">
      <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-6">
        <div>
          <h2 class="text-[17px] font-semibold">資料庫備份</h2>
          <p class="mt-0.5 text-sm text-ink-2">系統每天自動備份一次。</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <Button variant="outline" :disabled="backupsLoading" @click="loadBackups()">
            <RotateCw :class="backupsLoading && 'animate-spin'" />重新整理
          </Button>
          <Button
            v-if="backups?.triggerSupported"
            variant="primary"
            :disabled="backupStarting || backups.running"
            @click="startBackup"
          >
            <RotateCw v-if="backupStarting || backups.running" class="animate-spin" /><DatabaseBackup v-else />
            {{ backupStarting || backups.running ? '建立中…' : '立即備份' }}
          </Button>
        </div>
      </div>

      <p v-if="backupsLoading && !backups" class="border-t border-line px-5 py-6 text-[15px] text-ink-2 sm:px-6" role="status">正在讀取備份狀態…</p>
      <template v-else-if="backups">
        <p v-if="!backups.configured" class="border-t border-line px-5 py-5 text-[15px] sm:px-6">
          <Badge tone="danger">無法讀取備份狀態</Badge>
          <span class="mt-2 block text-ink-2">請聯絡系統維護人員檢查備份設定。</span>
        </p>
        <p v-else-if="!backups.items.length" class="border-t border-line px-5 py-5 text-[15px] sm:px-6">
          <Badge tone="warn">{{ backups.running ? '正在建立第一份備份' : backups.lastFailed ? '備份建立失敗' : '還沒有任何備份' }}</Badge>
          <span class="mt-2 block text-ink-2">
            {{ backups.running ? '完成後會自動出現在這裡。' : backups.lastFailed ? '請再試一次；如果仍然失敗，請聯絡系統維護人員。' : '如果系統已運作超過一天，請聯絡系統維護人員。' }}
          </span>
        </p>
        <template v-else>
          <div class="border-t border-line px-5 py-4 text-[15px] sm:px-6">
            <div class="flex flex-wrap items-center gap-2">
              <Badge :tone="backups.running ? 'warn' : backups.lastFailed || backupStale(backups.items[0]) ? 'danger' : 'done'">
                {{ backups.running ? '正在建立備份' : backups.lastFailed ? '上次備份失敗' : backupStale(backups.items[0]) ? '需要注意' : '備份正常' }}
              </Badge>
              <span>最近一次：<span class="font-medium tabular-nums">{{ when(backups.items[0].at) }}</span></span>
              <span class="text-sm text-ink-2">{{ fileSize(backups.items[0].size) }}</span>
            </div>
            <p v-if="backups.lastFailed" class="mt-2 text-sm text-grade">上次建立備份失敗，請再試一次；如果仍然失敗，請聯絡系統維護人員。</p>
            <p v-else-if="backupStale(backups.items[0])" class="mt-2 text-sm text-grade">已超過一天沒有新的備份，請聯絡系統維護人員。</p>
            <p v-else class="mt-2 text-sm text-ink-2">保留 {{ backups.keepDays }} 天，目前可查看 {{ backups.items.length }} 份。還原需由系統維護人員操作。</p>
          </div>
          <ul class="divide-y divide-line border-t border-line">
            <li v-for="b in shownBackups" :key="b.name" class="flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3 text-[15px] sm:px-6">
              <span class="w-36 shrink-0 text-sm text-ink-2 tabular-nums">{{ when(b.at) }}</span>
              <span class="min-w-0 flex-1 basis-48 truncate font-mono text-sm">{{ b.name }}</span>
              <span class="text-sm text-ink-2 tabular-nums">{{ fileSize(b.size) }}</span>
              <Button variant="outline" @click="downloadBackup(b)"><Download />下載</Button>
            </li>
          </ul>
          <div v-if="backups.items.length > 7" class="border-t border-line px-5 py-3 text-center sm:px-6">
            <Button @click="showAllBackups = !showAllBackups">
              <ChevronDown :class="showAllBackups && 'rotate-180'" />{{ showAllBackups ? '收合' : `查看全部 ${backups.items.length} 份` }}
            </Button>
          </div>
        </template>
      </template>
    </section>

    <EmergencyView v-else-if="tab === 'emergency'" />

    <SystemSettings v-else />
  </div>
</template>
