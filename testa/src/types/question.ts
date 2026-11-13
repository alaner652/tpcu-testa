export type QuestionType = 'single' | 'multiple' | 'blank' | 'essay'

export interface Choice {
  id: string
  text: string
}

export interface Blank {
  id: string
  /** 可接受的答案，任一相符即得分 */
  answers: string[]
}

/**
 * 所有題型共用同一個結構，切換題型時不會遺失已輸入的內容。
 */
export interface Question {
  id: string
  type: QuestionType
  stem: string
  points: number
  explanation: string
  // 單選、多選
  choices: Choice[]
  correct: string[]
  // 填空
  blanks: Blank[]
  caseSensitive: boolean
  // 申論
  rubric: string
  wordLimit: number | null
  aiGrading: boolean
  /** AI 匯入後尚未由教師確認 */
  review?: boolean
}

export interface Bank {
  id: string
  name: string
  subject: string
  /** 作答時間（分鐘） */
  duration: number
  questions: Question[]
  // 以下由後端提供（考生端的示範資料沒有）
  unitId?: number
  unitName?: string
  /** 伺服器上的版本，儲存時送回去，對不上代表別人先改過了 */
  version?: number
  /** 我能不能修改這份題庫（出題者可以，單位負責人只能看） */
  editable?: boolean
}

export type SaveState = 'saved' | 'dirty' | 'saving' | 'error'
