/**
 * Drives headless Chrome over the DevTools protocol, shared by the screenshot and video scripts
 * so neither needs a browser automation dependency.
 */
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawn } from 'node:child_process'

export const APP = process.env.TESTA_APP || 'http://localhost:4173/'
export const DESKTOP = { width: 1440, height: 900, scale: 1, mobile: false }
export const PHONE = { width: 390, height: 844, scale: 2, mobile: true }
const DEBUG = 'http://127.0.0.1:9222'

export const pause = (ms) => new Promise((done) => setTimeout(done, ms))

class Cdp {
  constructor(url) {
    this.nextId = 1
    this.pending = new Map()
    this.listeners = new Map()
    this.socket = new WebSocket(url)
  }

  async open() {
    await new Promise((resolveOpen, reject) => {
      this.socket.addEventListener('open', resolveOpen, { once: true })
      this.socket.addEventListener('error', reject, { once: true })
    })
    this.socket.addEventListener('message', ({ data }) => {
      const message = JSON.parse(data)
      if (message.method) {
        this.listeners.get(message.method)?.(message.params)
        return
      }
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

  /** One listener per event; pass null to remove it. */
  on(method, listener) {
    if (listener) this.listeners.set(method, listener)
    else this.listeners.delete(method)
  }

  close() {
    this.socket.close()
  }
}

/** Starts Chrome, runs work(browser), and always shuts Chrome down and removes its profile. */
export async function withChrome(work) {
  const profile = await mkdtemp(join(tmpdir(), 'testa-chrome-'))
  const chromium = spawn(
    process.env.CHROMIUM || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--remote-debugging-port=9222', `--user-data-dir=${profile}`, 'about:blank'],
    { stdio: 'ignore' },
  )
  const pages = []
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
    await work({
      async newPage(size = DESKTOP, setup) {
        const page = await openPage(size, setup)
        pages.push(page)
        return page
      },
    })
  } finally {
    for (const page of pages) page.close()
    // Wait for Chrome to exit before removing its profile, or a cleanup error hides the real one
    const exited = new Promise((done) => chromium.once('exit', done))
    chromium.kill('SIGTERM')
    await exited
    await rm(profile, { recursive: true, force: true, maxRetries: 5 }).catch(() => {})
  }
}

async function openPage(size, setup) {
  const target = await fetch(`${DEBUG}/json/new?about:blank`, { method: 'PUT' }).then((r) => r.json())
  const page = new Cdp(target.webSocketDebuggerUrl)
  await page.open()
  await Promise.all([page.send('Page.enable'), page.send('Runtime.enable'), page.send('Network.enable')])
  if (setup) await page.send('Page.addScriptToEvaluateOnNewDocument', { source: setup })
  // The theme is chosen when the app first loads, so emulate light mode before opening it.
  await viewport(page, size)
  await page.send('Page.navigate', { url: APP })
  await pause(1000)
  return page
}

export async function evaluate(page, expression) {
  const result = await page.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
  return result.result.value
}

export async function viewport(page, size = DESKTOP) {
  await page.send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: 'light' }] })
  await page.send('Emulation.setDeviceMetricsOverride', {
    width: size.width,
    height: size.height,
    deviceScaleFactor: size.scale,
    mobile: size.mobile,
    screenWidth: size.width,
    screenHeight: size.height,
  })
}

/** Opens a route and waits until the page shows text (or just loads). */
export async function visit(page, path, text = '') {
  await page.send('Page.navigate', { url: `${APP}#${path}` })
  await waitFor(page, text)
}

export async function waitFor(page, text = '', timeout = 8000) {
  for (let i = 0; i < timeout / 100; i++) {
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

export async function clickText(page, text) {
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

/** Calls the backend from inside the page, so it uses the page's session and CSRF cookie. */
export async function api(page, path, method = 'GET', body) {
  return evaluate(page, `(${apiSource})(${JSON.stringify(path)}, ${JSON.stringify(method)}, ${JSON.stringify(body)})`)
}

export async function clearSession(page) {
  await page.send('Network.clearBrowserCookies')
  // The login page remembers the last identity in localStorage; every clip starts from a fresh browser
  await page.send('Storage.clearDataForOrigin', { origin: new URL(APP).origin, storageTypes: 'local_storage' })
  await visit(page, '/login', '登入')
}

export async function staffLogin(page, email, password, path = '/') {
  await clearSession(page)
  const me = await api(page, '/auth/login', 'POST', { email, password })
  await page.send('Page.navigate', { url: `${APP}?session=${Date.now()}#${path}` })
  await pause(800)
  return me
}

export async function candidateLogin(page, ticket, code, path = '/student') {
  await clearSession(page)
  const me = await api(page, '/candidate/login', 'POST', { ticket, code })
  await page.send('Page.navigate', { url: `${APP}?session=${Date.now()}#${path}` })
  await pause(800)
  return me
}
