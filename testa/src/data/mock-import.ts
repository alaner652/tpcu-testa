import type { Question } from '@/types/question'
import { createQuestion as q } from '@/lib/question'

export interface DetectedQuestion {
  question: Question
  /** 0–1，模擬的辨識信心 */
  confidence: number
  note?: string
}

export const IMPORT_STEPS = ['上傳檔案', '解析版面與題號', '辨識題型與選項', '轉換數學公式'] as const

export const SAMPLE_FILE = { name: '高二數學第二次段考.docx', size: '184 KB' }

/** 模擬 AI 解析 Word / PDF 後的結果。 */
export function createDetectedQuestions(): DetectedQuestion[] {
  return [
    {
      confidence: 0.98,
      question: q('single', {
        stem: '若 $2^{x}=8$，則 $x$ 的值為何？',
        choices: ['$2$', '$3$', '$4$', '$8$'],
        correct: [1],
        review: true,
      }),
    },
    {
      confidence: 0.96,
      question: q('single', {
        stem: '直線 $3x-4y+5=0$ 到原點的距離為何？',
        choices: ['$1$', '$\\dfrac{5}{4}$', '$\\dfrac{5}{3}$', '$5$'],
        correct: [0],
        review: true,
      }),
    },
    {
      confidence: 0.93,
      question: q('multiple', {
        stem: '下列哪些函數為偶函數？',
        choices: ['$f(x)=x^2$', '$f(x)=\\sin x$', '$f(x)=\\cos x$', '$f(x)=x^3$'],
        correct: [0, 2],
        review: true,
      }),
    },
    {
      confidence: 0.71,
      note: '公式辨識信心較低，請核對題目',
      question: q('blank', {
        stem: '計算 $\\displaystyle\\sum_{k=1}^{5} k^2=$ ＿＿。',
        blanks: [['55']],
        review: true,
      }),
    },
    {
      confidence: 0.9,
      question: q('essay', {
        stem: '試證明 $\\sqrt{2}$ 為無理數。',
        rubric: '',
        review: true,
      }),
    },
  ]
}
