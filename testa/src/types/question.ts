export type QuestionType = 'single' | 'multiple' | 'blank' | 'essay'

export interface Choice {
  id: string
  text: string
}

export interface Blank {
  id: string
  /** Accepted answers; any match scores */
  answers: string[]
}

/**
 * All question types share one structure, so switching type keeps what was entered.
 */
export interface Question {
  id: string
  type: QuestionType
  stem: string
  points: number
  explanation: string
  // single, multiple
  choices: Choice[]
  correct: string[]
  // blank
  blanks: Blank[]
  caseSensitive: boolean
  // essay
  rubric: string
  wordLimit: number | null
  aiGrading: boolean
  /** Imported by AI and not yet confirmed by the teacher */
  review?: boolean
}

export interface Bank {
  id: string
  name: string
  subject: string
  /** Time limit (minutes) */
  duration: number
  questions: Question[]
  // Provided by the backend (not in the candidate demo data)
  unitId?: number
  unitName?: string
  /** Version on the server, sent back on save; a mismatch means someone else saved first */
  version?: number
  /** Whether I can edit this bank (authors can, unit owners only view) */
  editable?: boolean
}

export type SaveState = 'saved' | 'dirty' | 'saving' | 'error'
