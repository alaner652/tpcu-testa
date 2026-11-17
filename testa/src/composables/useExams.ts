import { ref } from 'vue'
import type { BadgeTone } from '@/components/ui/Badge.vue'
import { api } from '@/lib/api'
import { useAuth } from './useAuth'

/** Exams and rosters for staff (backend API). */
export type ExamStatus = 'draft' | 'scheduled' | 'live' | 'ended'
export type ExamMode = 'ONLINE' | 'CLASSROOM'
export interface Exam {
  id: number
  unitId: number
  unitName: string
  name: string
  bankId: number | null
  bankName: string | null
  /** ISO time; drafts may not have one yet */
  startAt: string | null
  duration: number
  mode: ExamMode
  status: ExamStatus
  /** Only after publishing (questions frozen at publish time) */
  questionCount: number
  totalPoints: number
  takers: number
  inProgress: number
  submitted: number
  /** Whether I am an owner (edit, publish, import rosters); proctors can only view and reset login codes */
  canManage: boolean
  resultsPublishedAt: string | null
  /** Read-only once archived; candidates can no longer sign in */
  archivedAt: string | null
  /** How long data is kept; an archived exam can be deleted after it (set once the exam ends) */
  retainUntil: string | null
}
export interface IssuedCode {
  id: number
  ticket: string
  code: string
}
export type CandidateStatus = 'IDLE' | 'IN' | 'DONE'
export interface Candidate {
  id: number
  ticket: string
  name: string
  dept: string
  seat: number
  /** Whether a login code has been issued; the code itself is shown only once, when issued */
  hasCode: boolean
  status: CandidateStatus
  /** Temporarily locked after too many wrong login codes */
  locked: boolean
  /** Times the candidate left the exam screen while answering */
  leaveCount: number
  /** Only while answering: this candidate's deadline (including extra time) */
  deadlineAt: string | null
  /** Total extra minutes given by proctors */
  extraMinutes: number
}
export interface RosterRow {
  ticket: string
  name: string
  dept: string
}
export interface RosterPreview {
  file: string
  rows: RosterRow[]
  problems: { row: string; who: string; reason: string }[]
}
export interface ExamInput {
  name: string
  bankId: number
  startAt: string | null
  duration: number
  mode: ExamMode
}

export const EXAM_STATUS: Record<ExamStatus, { label: string; tone: BadgeTone }> = {
  draft: { label: '草稿', tone: 'neutral' },
  scheduled: { label: '已排程', tone: 'warn' },
  live: { label: '進行中', tone: 'live' },
  ended: { label: '已結束', tone: 'neutral' },
}
export const CANDIDATE_STATUS: Record<CandidateStatus, { label: string; tone: BadgeTone }> = {
  IDLE: { label: '尚未登入', tone: 'neutral' },
  IN: { label: '作答中', tone: 'live' },
  DONE: { label: '已交卷', tone: 'done' },
}
/** "11 月 20 日 10:00" */
export function when(iso: string | null) {
  if (!iso) return '尚未設定開始時間'
  const d = new Date(iso)
  const time = d.toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit', hour12: false })
  const year = d.getFullYear() === new Date().getFullYear() ? '' : `${d.getFullYear()} 年 `
  return `${year}${d.getMonth() + 1} 月 ${d.getDate()} 日 ${time}`
}

/** Retention shown as "2027 年 12 月 31 日" (retainUntil is midnight the next day) */
export function retainLabel(iso: string | null) {
  if (!iso) return ''
  const d = new Date(new Date(iso).getTime() - 1000)
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`
}
export const canPurge = (exam: Exam) => !!exam.archivedAt && !!exam.retainUntil && Date.now() >= new Date(exam.retainUntil).getTime()

/** ISO time to an <input type="datetime-local"> value (local time) */
export function toLocalInput(iso: string | null) {
  if (!iso) return ''
  const d = new Date(iso)
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

const exams = ref<Exam[]>([])
const loading = ref(true)
const loadError = ref('')

function put(exam: Exam) {
  const i = exams.value.findIndex((e) => e.id === exam.id)
  if (i >= 0) exams.value[i] = exam
  else exams.value.unshift(exam)
  return exam
}

async function load() {
  const { user, ready } = useAuth()
  await ready
  loadError.value = ''
  if (!user.value || user.value.role === 'student') {
    exams.value = []
    loading.value = false
    return
  }
  try {
    exams.value = await api<Exam[]>('/exams')
  } catch (e) {
    loadError.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

export function useExams() {
  return {
    exams,
    loading,
    loadError,
    load,
    refresh: async (id: number) => put(await api<Exam>(`/exams/${id}`)),
    create: async (input: ExamInput) => put(await api<Exam>('/exams', { method: 'POST', body: input })),
    update: async (id: number, input: ExamInput) =>
      put(await api<Exam>(`/exams/${id}`, { method: 'PATCH', body: { ...input, clearStartAt: !input.startAt } })),
    publish: async (id: number) => put(await api<Exam>(`/exams/${id}/publish`, { method: 'POST' })),
    withdraw: async (id: number) => put(await api<Exam>(`/exams/${id}/withdraw`, { method: 'POST' })),
    end: async (id: number) => put(await api<Exam>(`/exams/${id}/end`, { method: 'POST' })),
    archive: async (id: number) => put(await api<Exam>(`/exams/${id}/archive`, { method: 'POST' })),
    unarchive: async (id: number) => put(await api<Exam>(`/exams/${id}/unarchive`, { method: 'POST' })),
    /** Drafts can be deleted right away; archived exams only after retention, with a reason */
    remove: async (id: number, reason?: string) => {
      await api(`/exams/${id}`, { method: 'DELETE', body: reason ? { reason } : undefined })
      exams.value = exams.value.filter((e) => e.id !== id)
    },
    candidates: (examId: number) => api<Candidate[]>(`/exams/${examId}/candidates`),
    preview: (examId: number, file: File) => {
      const form = new FormData()
      form.append('file', file)
      return api<RosterPreview>(`/exams/${examId}/candidates/preview`, { method: 'POST', body: form })
    },
    importRows: (examId: number, rows: RosterRow[]) =>
      api<{ added: number; total: number }>(`/exams/${examId}/candidates`, { method: 'POST', body: { rows } }),
    resetCode: (examId: number, candidateId: number) =>
      api<{ code: string }>(`/exams/${examId}/candidates/${candidateId}/reset-code`, { method: 'POST' }),
    extendTime: (examId: number, candidateId: number, minutes: number, reason: string) =>
      api<Candidate>(`/exams/${examId}/candidates/${candidateId}/extend`, { method: 'POST', body: { minutes, reason } }),
    removeCandidate: (examId: number, candidateId: number) =>
      api(`/exams/${examId}/candidates/${candidateId}`, { method: 'DELETE' }),
    // Several candidates at once: all succeed or none do
    extendTimes: (examId: number, ids: number[], minutes: number, reason: string) =>
      api(`/exams/${examId}/candidates/extend`, { method: 'POST', body: { ids, minutes, reason } }),
    /** New login codes for printing slips: the response is the only place they appear */
    issueCodes: (examId: number, ids: number[]) =>
      api<IssuedCode[]>(`/exams/${examId}/candidates/codes`, { method: 'POST', body: { ids } }),
    removeCandidates: (examId: number, ids: number[]) => api(`/exams/${examId}/candidates/remove`, { method: 'POST', body: { ids } }),
  }
}
