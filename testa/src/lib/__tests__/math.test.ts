import { describe, expect, it } from 'vitest'
import { oneLine, renderMathText } from '@/lib/math'

describe('question text to HTML', () => {
  it('escapes all text outside formulas, so HTML cannot be injected', () => {
    const html = renderMathText('<img src=x onerror="alert(1)"> & <script>')
    expect(html).not.toContain('<img')
    expect(html).not.toContain('<script')
    expect(html).toContain('&lt;img src=x onerror=&quot;alert(1)&quot;&gt; &amp; &lt;script&gt;')
  })

  it('turns newlines into <br> and passes $...$ to KaTeX', () => {
    const html = renderMathText('第一行\n$x^2$')
    expect(html).toContain('第一行<br>')
    expect(html).toContain('class="katex"')
  })

  it('shows the escaped source on a formula syntax error instead of throwing', () => {
    const html = renderMathText('$\\frac{1}{$')
    expect(html).toContain('公式語法有誤')
    expect(html).toContain('$\\frac{1}{$')
  })

  it('oneLine drops newlines and makes block formulas inline', () => {
    expect(oneLine('求\n$$\\dfrac{1}{2}$$')).toBe('求 $\\frac{1}{2}$')
  })
})
