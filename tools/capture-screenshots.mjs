#!/usr/bin/env node

/**
 * Rebuild docs/images (drives Chrome over the DevTools protocol, see lib/chrome.mjs).
 *
 * Prerequisites:
 *   backend in the dev profile on :8080 (demo accounts), with TESTA_BACKUP_PATH and TESTA_BACKUP_CONTROL_PATH set
 *   so the backup tab has content; the production build served on :4173 (cd testa && npm run build && npx vite preview);
 *   Chrome or Chromium (set CHROMIUM to its path if it is not in /Applications).
 *
 * Run from the repository root:
 *   node tools/capture-screenshots.mjs
 */
import { mkdir, writeFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { DESKTOP, PHONE, api, candidateLogin, clickText, evaluate, pause, staffLogin, viewport, visit, withChrome } from './lib/chrome.mjs'

const OUTPUT = resolve('docs/images')

async function capture(page, name, phone = false) {
  await viewport(page, phone ? PHONE : DESKTOP)
  await evaluate(page, 'window.scrollTo(0, 0); document.fonts.ready.then(() => true)')
  await pause(250)
  const { data } = await page.send('Page.captureScreenshot', { format: 'png', fromSurface: true })
  await writeFile(join(OUTPUT, name), Buffer.from(data, 'base64'))
  process.stdout.write(`saved ${name}\n`)
}

async function main() {
  await mkdir(OUTPUT, { recursive: true })
  await withChrome(async (browser) => {
    const page = await browser.newPage()
    await viewport(page)
    await visit(page, '/login', '准考證號')
    await capture(page, 'login.png')
    await capture(page, 'login-phone.png', true)

    const teacher = await staffLogin(page, 'teacher@testa.tw', 'testa-teacher')
    const unitId = teacher.units[0].id
    let banks = await api(page, '/banks')
    let bank = banks.find((item) => item.name === '文件截圖示範題庫')
    if (!bank) {
      bank = await api(page, '/banks', 'POST', {
        unitId,
        name: '文件截圖示範題庫',
        subject: '數學',
        duration: 120,
        questions: [
          { id: 'doc-q1', type: 'single', stem: '若 $f(x)=x^2-4x+3$，則最小值為何？', points: 5, choices: [{ id: 'a', text: '$-1$' }, { id: 'b', text: '$0$' }, { id: 'c', text: '$1$' }], correct: ['a'], explanation: '配方得 $f(x)=(x-2)^2-1$。' },
          { id: 'doc-q2', type: 'blank', stem: '等差數列首項為 3、公差為 4，則第十項為＿＿。', points: 5, blanks: [{ id: 'b1', answers: ['39'] }] },
          { id: 'doc-q3', type: 'essay', stem: '請以數學歸納法證明 $1+2+\\cdots+n=\\dfrac{n(n+1)}{2}$。', points: 10, rubric: '驗證起始值、寫出歸納假設、完成推導與結論。', wordLimit: 600 },
        ],
      })
    }
    await viewport(page)
    await visit(page, '/', '林老師')
    await capture(page, 'teacher-home.png')
    await visit(page, '/banks', '題庫')
    await capture(page, 'banks.png')
    await visit(page, `/banks/${bank.id}`, '文件截圖示範題庫')
    await capture(page, 'editor.png')
    await clickText(page, '模擬作答')
    await capture(page, 'editor-preview.png')

    await staffLogin(page, 'officer@testa.tw', 'testa-officer')
    const stamp = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Taipei' }).format(new Date()).replaceAll('-', '')
    const examName = `文件截圖示範考試 ${stamp}`
    let exams = await api(page, '/exams')
    let exam = exams.find((item) => item.name === examName && item.status === 'live')
    if (!exam) {
      exam = await api(page, '/exams', 'POST', {
        name: examName,
        bankId: bank.id,
        startAt: new Date(Date.now() - 60_000).toISOString(),
        duration: 180,
        mode: 'CLASSROOM',
      })
      await api(page, `/exams/${exam.id}/candidates`, 'POST', {
        rows: [
          { ticket: `DOC-${stamp}-01`, name: '陳怡安', dept: '資訊工程科' },
          { ticket: `DOC-${stamp}-02`, name: '林柏宇', dept: '電機工程科' },
          { ticket: `DOC-${stamp}-03`, name: '張雅婷', dept: '企業管理科' },
        ],
      })
      exam = await api(page, `/exams/${exam.id}/publish`, 'POST')
    }
    const roster = await api(page, `/exams/${exam.id}/candidates`)
    const candidate = roster[0]

    await viewport(page)
    await visit(page, '/', '王承辦')
    await capture(page, 'officer-home.png')
    await visit(page, '/exams', '考試')
    await capture(page, 'exams.png')
    await clickText(page, '建立考試')
    await capture(page, 'exam-new.png')
    await visit(page, `/exams/${exam.id}`, examName)
    await capture(page, 'exam-proctor.png')
    await capture(page, 'exam-proctor-phone.png', true)
    await viewport(page)
    await clickText(page, '登入單')
    await capture(page, 'exam-slips.png')

    await candidateLogin(page, candidate.ticket, candidate.code)
    await viewport(page)
    await visit(page, '/student', examName)
    await capture(page, 'candidate-home.png')
    await visit(page, '/student/exam/now', '請確認這是你本人')
    await capture(page, 'candidate-rules.png')
    await evaluate(page, `(() => { const box = document.querySelector('input[type="checkbox"]'); box.click(); return true })()`)
    await clickText(page, '開始作答')
    await capture(page, 'candidate-exam.png')
    await capture(page, 'candidate-exam-phone.png', true)
    await api(page, '/candidate/answers/doc-q1', 'PUT', { choices: ['a'] })
    await api(page, '/candidate/answers/doc-q2', 'PUT', { blanks: { b1: '39' } })
    await api(page, '/candidate/answers/doc-q3', 'PUT', { essay: '先驗證 n=1 成立。假設 n=k 時成立，再證明 n=k+1 時也成立，因此命題對所有正整數成立。' })
    await api(page, '/candidate/submit', 'POST')

    const teacherMe = await staffLogin(page, 'teacher@testa.tw', 'testa-teacher')
    await staffLogin(page, 'officer@testa.tw', 'testa-officer')
    await api(page, `/exams/${exam.id}/graders`, 'PUT', { staffIds: [teacherMe.id] })
    await api(page, `/exams/${exam.id}/end`, 'POST')
    await visit(page, `/exams/${exam.id}`, examName)
    await clickText(page, '評分')
    await capture(page, 'exam-grading.png')

    await staffLogin(page, 'teacher@testa.tw', 'testa-teacher')
    await viewport(page)
    await visit(page, '/grading', '匿名')
    await capture(page, 'grading.png')
    await capture(page, 'grading-phone.png', true)
    const items = await api(page, `/grading/exams/${exam.id}/items`)
    for (const item of items) {
      await api(page, `/grading/exams/${exam.id}/items/${item.anonId}/${item.questionId}`, 'PUT', { score: 8, comment: '推導方向正確，結論可再寫完整。' })
    }

    await staffLogin(page, 'officer@testa.tw', 'testa-officer')
    await api(page, `/exams/${exam.id}/results/publish`, 'POST')
    await viewport(page)
    await visit(page, `/exams/${exam.id}`, examName)
    await clickText(page, '成績')
    await capture(page, 'exam-results.png')

    await staffLogin(page, 'admin@testa.tw', 'testa-admin')
    await viewport(page)
    await visit(page, '/admin', '需要處理')
    await capture(page, 'admin-overview.png')
    await clickText(page, '使用者')
    await capture(page, 'admin-users.png')
    await clickText(page, '操作紀錄')
    await capture(page, 'admin-audit.png')
    await clickText(page, '備份')
    await capture(page, 'admin-backups.png')
    await clickText(page, '系統設定')
    await capture(page, 'admin-settings.png')
  })
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
