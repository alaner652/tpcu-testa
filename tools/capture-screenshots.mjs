#!/usr/bin/env node

/**
 * Rebuild docs/images without adding a browser automation dependency (it drives Chrome over the DevTools protocol).
 *
 * Prerequisites:
 *   backend in the dev profile on :8080 (demo accounts), with TESTA_BACKUP_PATH and TESTA_BACKUP_CONTROL_PATH set
 *   so the backup tab has content; the production build served on :4173 (cd testa && npm run build && npx vite preview);
 *   Chrome or Chromium (set CHROMIUM to its path if it is not in /Applications).
 *
 * Run from the repository root:
 *   node tools/capture-screenshots.mjs
 */
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { spawn } from 'node:child_process'

const APP = process.env.TESTA_APP || 'http://localhost:4173/'
const DEBUG = 'http://127.0.0.1:9222'
const OUTPUT = resolve('docs/images')
const pause = (ms) => new Promise((done) => setTimeout(done, ms))

class Cdp {
  constructor(url) {
    this.nextId = 1
    this.pending = new Map()
    this.socket = new WebSocket(url)
  }

  async open() {
    await new Promise((resolveOpen, reject) => {
      this.socket.addEventListener('open', resolveOpen, { once: true })
      this.socket.addEventListener('error', reject, { once: true })
    })
    this.socket.addEventListener('message', ({ data }) => {
      const message = JSON.parse(data)
      if (!message.id || !this.pending.has(message.id)) return
      const { resolveResult, reject } = this.pending.get(message.id)
      this.pending.delete(message.id)
      if (message.error) reject(new Error(message.error.message))
      else resolveResult(message.result)
    })
  }

  send(method, params = {}) {
    const id = this.nextId++
    this.socket.send(JSON.stringify({ id, method, params }))
    return new Promise((resolveResult, reject) => this.pending.set(id, { resolveResult, reject }))
  }

  close() {
    this.socket.close()
  }
}

async function newPage() {
  const target = await fetch(`${DEBUG}/json/new?about:blank`, { method: 'PUT' }).then((r) => r.json())
  const page = new Cdp(target.webSocketDebuggerUrl)
  await page.open()
  await Promise.all([page.send('Page.enable'), page.send('Runtime.enable'), page.send('Network.enable')])
  // The theme is chosen when the app first loads, so emulate light mode before opening it.
  await viewport(page)
  await page.send('Page.navigate', { url: APP })
  await pause(1000)
  return page
}

async function evaluate(page, expression) {
  const result = await page.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
  return result.result.value
}

async function viewport(page, phone = false) {
  await page.send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: 'light' }] })
  await page.send('Emulation.setDeviceMetricsOverride', {
    width: phone ? 390 : 1440,
    height: phone ? 844 : 900,
    deviceScaleFactor: phone ? 2 : 1,
    mobile: phone,
    screenWidth: phone ? 390 : 1440,
    screenHeight: phone ? 844 : 900,
  })
}

async function visit(page, path, text = '') {
  await page.send('Page.navigate', { url: `${APP}#${path}` })
  for (let i = 0; i < 80; i++) {
    const ready = await evaluate(
      page,
      `document.readyState === 'complete' && ${text ? `document.body?.innerText.includes(${JSON.stringify(text)})` : '!!document.body'}`,
    ).catch(() => false)
    if (ready) break
    await pause(100)
  }
  await evaluate(page, 'document.fonts.ready.then(() => true)')
  await pause(500)
}

async function clickText(page, text) {
  const clicked = await evaluate(
    page,
    `(() => {
      const wanted = ${JSON.stringify(text)};
      const el = [...document.querySelectorAll('button,[role="tab"],a')]
        .find((node) => node.textContent.trim() === wanted || node.getAttribute('aria-label') === wanted);
      if (!el) return false;
      el.click();
      return true;
    })()`,
  )
  if (!clicked) throw new Error(`Nothing clickable labelled: ${text}`)
  await pause(700)
}

async function capture(page, name, phone = false) {
  await viewport(page, phone)
  await evaluate(page, 'window.scrollTo(0, 0); document.fonts.ready.then(() => true)')
  await pause(250)
  const { data } = await page.send('Page.captureScreenshot', { format: 'png', fromSurface: true })
  await writeFile(join(OUTPUT, name), Buffer.from(data, 'base64'))
  process.stdout.write(`saved ${name}\n`)
}

const apiSource = `async (path, method = 'GET', body) => {
  if (method !== 'GET') await fetch('/api/auth/csrf');
  const token = decodeURIComponent((document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/) || [,''])[1]);
  const response = await fetch('/api' + path, {
    method,
    credentials: 'include',
    headers: { 'Accept': 'application/json', ...(method === 'GET' ? {} : { 'X-XSRF-TOKEN': token }), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const raw = await response.text();
  if (!response.ok) throw new Error(method + ' ' + path + ': ' + response.status + ' ' + raw);
  return raw ? JSON.parse(raw) : null;
}`

async function api(page, path, method = 'GET', body) {
  return evaluate(page, `(${apiSource})(${JSON.stringify(path)}, ${JSON.stringify(method)}, ${JSON.stringify(body)})`)
}

async function clearSession(page) {
  await page.send('Network.clearBrowserCookies')
  await visit(page, '/login', '登入')
}

async function staffLogin(page, email, password) {
  await clearSession(page)
  const me = await api(page, '/auth/login', 'POST', { email, password })
  await page.send('Page.navigate', { url: `${APP}?session=${Date.now()}#/` })
  await pause(800)
  return me
}

async function candidateLogin(page, ticket, code) {
  await clearSession(page)
  const me = await api(page, '/candidate/login', 'POST', { ticket, code })
  await page.send('Page.navigate', { url: `${APP}?session=${Date.now()}#/student` })
  await pause(800)
  return me
}

async function main() {
  await mkdir(OUTPUT, { recursive: true })
  const profile = await mkdtemp(join(tmpdir(), 'testa-capture-'))
  const chromium = spawn(
    process.env.CHROMIUM || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--remote-debugging-port=9222', `--user-data-dir=${profile}`, 'about:blank'],
    { stdio: 'ignore' },
  )
  let page
  try {
    let browserReady = false
    for (let i = 0; i < 200; i++) {
      if (await fetch(`${DEBUG}/json/version`).then((r) => r.ok).catch(() => false)) {
        browserReady = true
        break
      }
      await pause(100)
    }
    if (!browserReady) throw new Error('Chrome did not open its remote debugging port')
    page = await newPage()

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
    await visit(page, '/admin', '使用者')
    await capture(page, 'admin-users.png')
    await clickText(page, '操作紀錄')
    await capture(page, 'admin-audit.png')
    await clickText(page, '備份')
    await capture(page, 'admin-backups.png')
    await clickText(page, '系統設定')
    await capture(page, 'admin-settings.png')
  } finally {
    page?.close()
    // Wait for Chrome to exit before removing its profile, or a cleanup error hides the real one
    const exited = new Promise((done) => chromium.once('exit', done))
    chromium.kill('SIGTERM')
    await exited
    await rm(profile, { recursive: true, force: true, maxRetries: 5 }).catch(() => {})
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
