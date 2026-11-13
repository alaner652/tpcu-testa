import type { Bank } from '@/types/question'
import { createQuestion as q } from '@/lib/question'

export function createMockBanks(): Bank[] {
  return [
    {
      id: 'bank_math',
      name: '115 學年度數學科入學測驗',
      subject: '數學',
      duration: 80,
      questions: [
        q('single', {
          stem: '若 $f(x)=x^2-4x+3$，則 $f(x)$ 的最小值為何？',
          choices: ['$-1$', '$0$', '$1$', '$3$'],
          correct: [0],
          explanation: '配方得 $f(x)=(x-2)^2-1$，故最小值為 $-1$。',
        }),
        q('single', {
          stem: '求極限值：\n$$\\lim_{x\\to 0}\\frac{\\sin 3x}{x}$$',
          choices: ['$0$', '$1$', '$3$', '不存在'],
          correct: [2],
        }),
        q('multiple', {
          stem: '下列哪些數是無理數？',
          choices: ['$\\sqrt{2}$', '$\\dfrac{22}{7}$', '$\\pi$', '$\\sqrt{16}$'],
          correct: [0, 2],
          points: 6,
        }),
        q('blank', {
          stem: '已知等差數列 $\\langle a_n\\rangle$ 的首項 $a_1=3$、公差 $d=4$，則 $a_{10}=$ ＿＿，前 10 項的和 $S_{10}=$ ＿＿。',
          blanks: [['39'], ['210']],
          points: 6,
        }),
        q('single', {
          stem: '計算定積分 $\\displaystyle\\int_0^1 3x^2\\,dx$ 的值。',
          choices: ['$\\dfrac{1}{3}$', '$1$', '$\\dfrac{3}{2}$', '$3$'],
          correct: [1],
        }),
        q('multiple', {
          stem: '設矩陣 $A=\\begin{bmatrix}1&2\\\\3&4\\end{bmatrix}$，下列敘述哪些正確？',
          choices: ['$\\det A=-2$', '$A$ 為可逆矩陣', '$A^{T}=A$', '$\\operatorname{tr}A=5$'],
          correct: [0, 1, 3],
          points: 6,
        }),
        q('blank', {
          stem: '擲一顆公正骰子兩次，點數和為 7 的機率為 ＿＿。',
          blanks: [['1/6', '\\frac{1}{6}']],
        }),
        q('single', {
          stem: '設 $\\log_2 x=5$，則 $x$ 的值為何？',
          choices: ['$10$', '$25$', '$32$', '$64$'],
          correct: [],
        }),
        q('essay', {
          stem: '試以數學歸納法證明：對任意正整數 $n$，\n$$1+2+\\cdots+n=\\frac{n(n+1)}{2}$$',
          rubric: '驗證 n = 1 成立（2 分）\n寫出歸納假設（3 分）\n完成 n = k + 1 的推導（4 分）\n寫出結論（1 分）',
          aiGrading: true,
        }),
      ],
    },
    {
      id: 'bank_chinese',
      name: '國文科閱讀測驗',
      subject: '國文',
      duration: 50,
      questions: [
        q('single', {
          stem: '「先天下之憂而憂，後天下之樂而樂」出自下列哪一篇文章？',
          choices: ['〈醉翁亭記〉', '〈岳陽樓記〉', '〈赤壁賦〉', '〈師說〉'],
          correct: [1],
        }),
        q('multiple', {
          stem: '下列哪些作品屬於「唐宋八大家」的作者所寫？',
          choices: ['〈師說〉', '〈出師表〉', '〈赤壁賦〉', '〈桃花源記〉'],
          correct: [0, 2],
        }),
        q('essay', {
          stem: '閱讀〈岳陽樓記〉後，說明作者如何藉由景物的描寫帶出「不以物喜，不以己悲」的主張。',
          rubric: '能指出晴、雨兩段景物描寫的對比（4 分）\n能說明情隨景遷與古仁人之心的差別（4 分）\n文句通順、結構完整（2 分）',
          wordLimit: 300,
        }),
      ],
    },
    {
      id: 'bank_english',
      name: '英文科模擬試題',
      subject: '英文',
      duration: 60,
      questions: [],
    },
  ]
}
