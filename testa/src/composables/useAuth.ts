import { computed, ref } from 'vue'
import { api, setUnauthorizedHandler } from '@/lib/api'

export type Role = 'admin' | 'teacher' | 'student'
/** 單位內角色（與後端相同）：負責人、出題、監考、評分 */
export type UnitRole = 'OWNER' | 'AUTHOR' | 'PROCTOR' | 'GRADER'
export interface UnitMembership {
  id: number
  name: string
  type: 'ADMISSION' | 'CLASS'
  roles: UnitRole[]
}
export interface User {
  name: string
  email: string
  /** 介面用的粗分類：系統管理員、教職員、考生。細的權限看 units 裡的角色，而且最終由後端判斷。 */
  role: Role
  unit: string
  /** 考生的准考證號 */
  ticket?: string
  /** 教職員：所屬單位與角色 */
  units?: UnitMembership[]
  mustChangePassword?: boolean
}

export const ROLE_LABEL: Record<Role, string> = { admin: '系統管理員', teacher: '教職員', student: '考生' }
export const UNIT_ROLE_LABEL: Record<UnitRole, string> = { OWNER: '負責人', AUTHOR: '出題', PROCTOR: '監考', GRADER: '評分' }

/** 開發用示範帳號，對應後端 dev profile 建立的資料（server/…/Bootstrap.java）。只在開發模式的登入頁顯示。 */
export const DEMO_ACCOUNTS = [
  { label: '教師', name: '林老師', email: 'teacher@testa.tw', password: 'testa-teacher' },
  { label: '承辦人', name: '王承辦', email: 'officer@testa.tw', password: 'testa-officer' },
  { label: '系統管理員', name: '陳主任', email: 'admin@testa.tw', password: 'testa-admin' },
]

interface Me {
  id: number
  email: string
  name: string
  systemAdmin: boolean
  mustChangePassword: boolean
  units: UnitMembership[]
}

function fromMe(me: Me): User {
  return {
    name: me.name,
    email: me.email,
    role: me.systemAdmin ? 'admin' : 'teacher',
    unit: me.units.map((u) => u.name).join('、') || (me.systemAdmin ? '系統管理' : '尚未加入單位'),
    units: me.units,
    mustChangePassword: me.mustChangePassword,
  }
}

/** 考生登入後後端回傳的狀態（細節在 useCandidate） */
interface CandidateMe {
  name: string
  ticket: string
  dept: string
}
const fromCandidate = (c: CandidateMe): User => ({ name: c.name, email: c.ticket, role: 'student', unit: c.dept, ticket: c.ticket })

const user = ref<User | null>(null)

/** 開機時向後端確認登入狀態：先問是不是教職員，不是再問是不是考生。路由守衛會等它完成。 */
const ready: Promise<void> = api<Me>('/auth/me', { quiet401: true })
  .then((me) => void (user.value = fromMe(me)))
  .catch(() =>
    api<CandidateMe>('/candidate/me', { quiet401: true })
      .then((c) => void (user.value = fromCandidate(c)))
      .catch(() => void (user.value = null)),
  )

// Session 過期、帳號被停用、密碼被重設、考生在別台電腦登入：清掉身分，帶回登入頁
setUnauthorizedHandler(() => {
  if (user.value) {
    user.value = null
    location.hash = '#/login'
  }
})

export function useAuth() {
  /** 教職員登入。 */
  async function login(email: string, password: string) {
    const me = await api<Me>('/auth/login', { method: 'POST', body: { email, password }, quiet401: true })
    user.value = fromMe(me)
    return user.value
  }

  /** 重新向後端取得自己的資料（改密碼、角色異動後）。 */
  async function refresh() {
    user.value = fromMe(await api<Me>('/auth/me'))
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    await api('/auth/password', { method: 'POST', body: { currentPassword, newPassword } })
    await refresh()
  }

  /** 考生登入：准考證號 + 登入碼，必須在某場已發布考試的名單上。 */
  async function loginCandidate(ticket: string, code: string) {
    const me = await api<CandidateMe>('/candidate/login', { method: 'POST', body: { ticket, code }, quiet401: true })
    user.value = fromCandidate(me)
    return user.value
  }

  async function logout() {
    user.value = null
    await api('/auth/logout', { method: 'POST', quiet401: true }).catch(() => {})
  }

  return {
    user,
    ready,
    isAdmin: computed(() => user.value?.role === 'admin'),
    isStudent: computed(() => user.value?.role === 'student'),
    login,
    loginCandidate,
    refresh,
    changePassword,
    logout,
  }
}
