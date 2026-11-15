import { ref } from 'vue'
import { api } from '@/lib/api'
import type { Attempt, RemoteExam } from '@/lib/grading'
import type { Question, QuestionType } from '@/types/question'

/** Candidate side: own exam state, the paper (without answers), saving answers, submitting. */
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
    /** Whether the score will be shown here once published */
    candidateResults: boolean
  }
  attempt: { startedAt: string; deadlineAt: string; submittedAt: string | null; remainingSeconds: number } | null
  /** Present once results are published, if the exam lets candidates see them. total is null for absentees. */
  result: {
    publishedAt: string
    fullMarks: number
    autoScore: number | null
    essayScore: number | null
    total: number | null
  } | null
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

  /** Starts (or resumes) the attempt and loads the paper. */
  async function open(): Promise<{ questions: Question[]; remote: RemoteExam }> {
    state.value = await api<CandidateState>('/candidate/start', { method: 'POST' })
    const paper = await api<Paper>('/candidate/paper')
    // Add empty answer fields so answer components can share the question type
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
        sync: async () => {
          const s = await api<CandidateState>('/candidate/me', { quiet401: true })
          return s.attempt && !s.attempt.submittedAt ? s.attempt.remainingSeconds : 0
        },
        submit: async () => {
          state.value = await api<CandidateState>('/candidate/submit', { method: 'POST' })
        },
      },
    }
  }

  /** Left the exam screen while answering: just tell the server to record it. */
  const reportLeave = () => api('/candidate/leave', { method: 'POST', quiet401: true }).catch(() => {})

  return { state, load, open, reportLeave }
}
