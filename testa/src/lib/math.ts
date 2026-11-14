import katex from 'katex'

const MATH_PATTERN = /(\$\$[\s\S]+?\$\$|\$[^$\n]+?\$)/g

function escapeHtml(text: string) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export function hasMath(text: string) {
  return /\$[^$]+\$/.test(text)
}

/** Turns text with $...$ / $$...$$ into safe HTML (all other text is escaped). */
export function renderMathText(text: string): string {
  return text
    .split(MATH_PATTERN)
    .map((part, i) => {
      if (i % 2 === 0) return escapeHtml(part).replace(/\n/g, '<br>')
      const display = part.startsWith('$$')
      const source = display ? part.slice(2, -2) : part.slice(1, -1)
      try {
        return katex.renderToString(source, { displayMode: display, throwOnError: true, output: 'html' })
      } catch {
        return `<span class="rounded bg-grade-soft px-1 font-mono text-[0.85em] text-grade" title="公式語法有誤">${escapeHtml(part)}</span>`
      }
    })
    .join('')
}

/** For narrow places such as the nav: drop newlines, keep formulas. */
export function oneLine(text: string) {
  return text
    .replace(/\$\$/g, '$')
    .replace(/\\displaystyle/g, '')
    .replace(/\\dfrac/g, '\\frac')
    .replace(/\\begin\{(\w+)\}[\s\S]*?\\end\{\1\}/g, '[\\,\\cdots]')
    .replace(/\s*\n\s*/g, ' ')
}
