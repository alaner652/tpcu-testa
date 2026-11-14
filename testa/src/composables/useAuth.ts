import { computed, ref } from 'vue'
import { api, setUnauthorizedHandler } from '@/lib/api'

export type Role = 'admin' | 'teacher' | 'student'
/** Roles within a unit (same as the backend): owner, author, proctor, grader */
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
  /** Coarse kind for the UI: administrator, staff, candidate. Fine-grained access comes from unit roles and is decided by the backend. */
  role: Role
  unit: string
  /** Candidate number */
  ticket?: string
  /** Staff: units and roles */
  units?: UnitMembership[]
  mustChangePassword?: boolean
}

export const ROLE_LABEL: Record<Role, string> = { admin: '系統管理員', teacher: '教職員', student: '考生' }
export const UNIT_ROLE_LABEL: Record<UnitRole, string> = { OWNER: '負責人', AUTHOR: '出題', PROCTOR: '監考', GRADER: '評分' }

/** Demo accounts matching the backend dev profile (server/.../Bootstrap.java). Shown on the login page in development only. */
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

/** Candidate state returned after sign-in (details in useCandidate) */
interface CandidateMe {
  name: string
  ticket: string
  dept: string
}
const fromCandidate = (c: CandidateMe): User => ({ name: c.name, email: c.ticket, role: 'student', unit: c.dept, ticket: c.ticket })

const user = ref<User | null>(null)

/** At start-up ask the backend who is signed in: staff first, then candidate. Route guards wait for this. */
const ready: Promise<void> = api<Me>('/auth/me', { quiet401: true })
  .then((me) => void (user.value = fromMe(me)))
  .catch(() =>
    api<CandidateMe>('/candidate/me', { quiet401: true })
      .then((c) => void (user.value = fromCandidate(c)))
      .catch(() => void (user.value = null)),
  )

/** Why the user was signed out, shown once on the login page. */
export const signedOutNotice = ref<{ role: Role; text: string } | null>(null)

// Session expired, account disabled, password reset, candidate signed in elsewhere, backend restarted: clear identity and go to login
setUnauthorizedHandler(() => {
  if (user.value) {
    signedOutNotice.value =
      user.value.role === 'student'
        ? { role: 'student', text: '你的登入已經失效（可能在另一台裝置登入了，或系統剛重新啟動）。請重新登入，已經儲存的答案都還在。' }
        : { role: user.value.role, text: '登入已經失效，請重新登入。' }
    user.value = null
    location.hash = '#/login'
  }
})

export function useAuth() {
  /** Staff sign-in. */
  async function login(email: string, password: string) {
    const me = await api<Me>('/auth/login', { method: 'POST', body: { email, password }, quiet401: true })
    user.value = fromMe(me)
    return user.value
  }

  /** First-run setup: creates the first system administrator with the setup token from the server log and signs in. */
  async function setupFirstAdmin(token: string, email: string, name: string, password: string) {
    const me = await api<Me>('/auth/setup', { method: 'POST', body: { token, email, name, password } })
    user.value = fromMe(me)
    return user.value
  }

  /** Reloads one's own profile (after a password or role change). */
  async function refresh() {
    user.value = fromMe(await api<Me>('/auth/me'))
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    await api('/auth/password', { method: 'POST', body: { currentPassword, newPassword } })
    await refresh()
  }

  /** Candidate sign-in: candidate number + login code, must be on the roster of a published exam. */
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
    setupFirstAdmin,
    loginCandidate,
    refresh,
    changePassword,
    logout,
  }
}
