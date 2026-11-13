import { ref } from 'vue'
import { api } from '@/lib/api'
import type { Attempt, RemoteExam } from '@/lib/grading'
import type { Question, QuestionType } from '@/types/question'

/** 考生端：自己的考試狀態、試卷（不含答案）、存答案、交卷。 */
export interface CandidateState {
  name: string
  ticket: string
  dept: string
  seat: number
  exam: {
    name: string
    startAt: string | null
    duration: number
    mode: 'ONLINE' | 'CLASSROOM'
    status: 'scheduled' | 'live' | 'ended'
    questionCount: number
    totalPoints: number
  }
  attempt: { startedAt: string; deadlineAt: string; submittedAt: string | null; remainingSeconds: number } | null
}

interface Paper {
  examName: string
  questions: {
    id: string
    type: QuestionType
    stem: string
    points: number
    choices: { id: string; text: string }[]
    blanks: { id: string }[]
    wordLimit: number | null
  }[]
  answers: Record<string, Partial<Attempt>>
  remainingSeconds: number
}

const state = ref<CandidateState | null>(null)

export function useCandidate() {
  async function load() {
    state.value = await api<CandidateState>('/candidate/me')
    return state.value
  }

  /** 開始作答（或回到進行中的作答），並取得試卷。 */
  async function open(): Promise<{ questions: Question[]; remote: RemoteExam }> {
    state.value = await api<CandidateState>('/candidate/start', { method: 'POST' })
    const paper = await api<Paper>('/candidate/paper')
    // 補上答案欄位（都是空的），讓作答元件可以共用題目的型別
    const questions: Question[] = paper.questions.map((q) => ({
      ...q,
      explanation: '',
      correct: [],
      blanks: q.blanks.map((b) => ({ id: b.id, answers: [] })),
      caseSensitive: false,
      rubric: '',
      aiGrading: false,
    }))
    return {
      questions,
      remote: {
        initial: paper.answers,
        remainingSeconds: paper.remainingSeconds,
        save: async (questionId, answer) =>
          (await api<{ remainingSeconds: number }>(`/candidate/answers/${encodeURIComponent(questionId)}`, { method: 'PUT', body: answer }))
            .remainingSeconds,
        submit: async () => {
          state.value = await api<CandidateState>('/candidate/submit', { method: 'POST' })
        },
      },
    }
  }

  /** 作答中離開考試畫面：只通知伺服器記一筆。 */
  const reportLeave = () => api('/candidate/leave', { method: 'POST', quiet401: true }).catch(() => {})

  return { state, load, open, reportLeave }
}
