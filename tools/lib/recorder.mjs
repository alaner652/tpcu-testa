/**
 * Records a browser tab to MP4: Chrome screencast frames, stitched by ffmpeg at their real timing.
 * Actions move a drawn cursor to the element before sending real mouse and keyboard input,
 * so viewers can follow where each click lands.
 */
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawn } from 'node:child_process'
import { evaluate, pause } from './chrome.mjs'

/** Installed in every document: a cursor drawn above everything, with a ring on each click. */
export const CURSOR_SCRIPT = `(() => {
  const install = () => {
    if (document.getElementById('rec-cursor')) return
    const style = document.createElement('style')
    style.textContent = \`
      #rec-cursor { position: fixed; left: 0; top: 0; z-index: 2147483647; pointer-events: none; width: 26px; height: 26px;
        transform: translate(var(--x, -40px), var(--y, -40px)); transition: transform var(--t, 0ms) cubic-bezier(.4, 0, .2, 1); }
      #rec-cursor svg { filter: drop-shadow(0 1px 2px rgb(0 0 0 / .35)); }
      .rec-ring { position: fixed; z-index: 2147483646; pointer-events: none; width: 36px; height: 36px; margin: -18px 0 0 -18px;
        border-radius: 50%; background: rgb(250 204 21 / .45); border: 2px solid rgb(234 179 8); animation: rec-ring 600ms ease-out forwards; }
      @keyframes rec-ring { from { transform: scale(.3); opacity: 1 } to { transform: scale(1.4); opacity: 0 } }\`
    document.head.append(style)
    const cursor = document.createElement('div')
    cursor.id = 'rec-cursor'
    cursor.innerHTML = '<svg width="26" height="26" viewBox="0 0 24 24"><path d="M5 3l14 8.5-6.2 1.3L16 20l-2.6 1.2-3.3-7.3L5 18z" fill="#111" stroke="#fff" stroke-width="1.5" stroke-linejoin="round"/></svg>'
    document.body.append(cursor)
  }
  if (document.body) install()
  else document.addEventListener('DOMContentLoaded', install)
  window.__recCursor = {
    move(x, y, ms) {
      const c = document.getElementById('rec-cursor')
      c.style.setProperty('--t', ms + 'ms')
      c.style.setProperty('--x', x - 5 + 'px')
      c.style.setProperty('--y', y - 3 + 'px')
    },
    ring(x, y) {
      const r = document.createElement('div')
      r.className = 'rec-ring'
      r.style.left = x + 'px'
      r.style.top = y + 'px'
      document.body.append(r)
      setTimeout(() => r.remove(), 700)
    },
  }
})()`

/**
 * Finds an element and scrolls it into view. A target is one of:
 *   'text'             a button, tab, link, menu item or option whose text or aria-label is exactly this
 *   { text, in }       the same, inside the list row or card that contains the text "in"
 *   { field: 'name' }  an input, select or textarea by aria-label, placeholder or the text of its <label>
 *   { css: 'selector', text?: 'contains' }
 *   { row: 'contains' } a list row (li[data-row]) that contains this text
 */
const locateSource = `(target) => {
  const visible = (el) => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden' }
  const clean = (s) => (s || '').replace(/\\s+/g, ' ').trim()
  let candidates = []
  if (typeof target === 'string' || target.text !== undefined && !target.css) {
    const text = typeof target === 'string' ? target : target.text
    candidates = [...document.querySelectorAll('button,[role=tab],[role=menuitem],[role=option],[role=radio],[role=checkbox],a,summary')]
      .filter((el) => clean(el.textContent) === text || el.getAttribute('aria-label') === text)
      .filter((el) => !target.in || el.closest('li,article,[data-row]')?.innerText.includes(target.in))
  } else if (target.field) {
    const fields = [...document.querySelectorAll('input,select,textarea,[contenteditable=true]')]
    candidates = fields.filter((el) => el.getAttribute('aria-label') === target.field || el.getAttribute('placeholder') === target.field
      || clean(el.closest('label')?.querySelector('span')?.textContent) === target.field)
  } else if (target.row) {
    candidates = [...document.querySelectorAll('li[data-row]')].filter((el) => el.innerText.includes(target.row))
  } else {
    candidates = [...document.querySelectorAll(target.css)].filter((el) => !target.text || el.innerText.includes(target.text))
  }
  const el = candidates.filter(visible).at(-1)
  if (!el) return null
  el.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  const r = el.getBoundingClientRect()
  return { x: Math.round(r.left + Math.min(r.width / 2, 160)), y: Math.round(r.top + r.height / 2) }
}`

export class Recorder {
  constructor(page) {
    this.page = page
    this.frames = []
    this.at = { x: 640, y: 400 }
  }

  async start() {
    this.dir = await mkdtemp(join(tmpdir(), 'testa-video-'))
    this.frames = []
    this.writes = []
    this.page.on('Page.screencastFrame', ({ data, metadata, sessionId }) => {
      const file = join(this.dir, `${String(this.frames.length).padStart(5, '0')}.jpg`)
      this.frames.push({ file, time: metadata.timestamp })
      this.writes.push(writeFile(file, Buffer.from(data, 'base64')))
      this.page.send('Page.screencastFrameAck', { sessionId }).catch(() => {})
    })
    await evaluate(this.page, `window.__recCursor.move(${this.at.x}, ${this.at.y}, 0)`)
    await this.page.send('Page.startScreencast', { format: 'jpeg', quality: 90, everyNthFrame: 1 })
    await pause(400)
  }

  /** Stops recording and writes an H.264 MP4 without sound. */
  async stop(output) {
    // Hold the last picture briefly so the result can be read before the clip ends
    await pause(1500)
    await this.page.send('Page.stopScreencast')
    this.page.on('Page.screencastFrame', null)
    await Promise.all(this.writes)
    if (this.frames.length < 2) throw new Error('No frames were recorded')
    const lines = []
    this.frames.forEach((frame, i) => {
      const next = this.frames[i + 1]
      lines.push(`file '${frame.file}'`, `duration ${(next ? next.time - frame.time : 1.5).toFixed(3)}`)
    })
    lines.push(`file '${this.frames.at(-1).file}'`)
    const list = join(this.dir, 'frames.txt')
    await writeFile(list, lines.join('\n'))
    await run('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', list,
      '-vf', 'fps=30,scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p', '-c:v', 'libx264', '-preset', 'slow', '-crf', '28',
      '-movflags', '+faststart', '-an', output])
    await rm(this.dir, { recursive: true, force: true })
    const seconds = this.frames.at(-1).time - this.frames[0].time + 1.5
    return seconds
  }

  async locate(target, timeout = 6000) {
    for (let i = 0; i < timeout / 100; i++) {
      const point = await evaluate(this.page, `(${locateSource})(${JSON.stringify(target)})`)
      if (point) return point
      await pause(100)
    }
    throw new Error(`Not found on screen: ${JSON.stringify(target)}`)
  }

  /** Glides the drawn cursor to the target; the speed depends on the distance. */
  async move(target) {
    const to = await this.locate(target)
    await pause(150)
    const distance = Math.hypot(to.x - this.at.x, to.y - this.at.y)
    const ms = Math.round(Math.min(900, 250 + distance * 0.6))
    await evaluate(this.page, `window.__recCursor.move(${to.x}, ${to.y}, ${ms})`)
    await this.page.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: to.x, y: to.y })
    await pause(ms + 120)
    this.at = to
    return to
  }

  async click(target, { modifiers = 0, after = 700 } = {}) {
    const at = await this.move(target)
    await evaluate(this.page, `window.__recCursor.ring(${at.x}, ${at.y})`)
    await this.page.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: at.x, y: at.y, button: 'left', clickCount: 1, modifiers })
    await pause(60)
    await this.page.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: at.x, y: at.y, button: 'left', clickCount: 1, modifiers })
    await pause(after)
  }

  /** Clicks a field and types into it at a readable pace (whole text at once when it is long). */
  async type(target, text, { clear = true } = {}) {
    await this.click(target, { after: 250 })
    if (clear) {
      await evaluate(this.page, `(() => { const el = document.activeElement; if (el && 'select' in el) el.select(); return true })()`)
    }
    if (text.length > 40) {
      await this.page.send('Input.insertText', { text })
    } else {
      for (const ch of text) {
        await this.page.send('Input.insertText', { text: ch })
        await pause(/[一-龥]/.test(ch) ? 110 : 55)
      }
    }
    await pause(400)
  }

  /** Clicks a field and sets its value in one go, for inputs that cannot be typed into (dates, numbers). */
  async fill(target, value) {
    const at = await this.move(target)
    await evaluate(this.page, `window.__recCursor.ring(${at.x}, ${at.y})`)
    await evaluate(this.page, `(() => {
      const el = document.elementFromPoint(${at.x}, ${at.y})?.closest('input,textarea')
      const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value').set
      setter.call(el, ${JSON.stringify(String(value))})
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    })()`)
    await pause(700)
  }

  /** Clicks a button that opens a file picker and answers the picker with a local file. */
  async upload(target, file) {
    await this.page.send('Page.setInterceptFileChooserDialog', { enabled: true })
    const opened = new Promise((done) => this.page.on('Page.fileChooserOpened', done))
    await this.click(target, { after: 0 })
    const { backendNodeId } = await opened
    this.page.on('Page.fileChooserOpened', null)
    await this.page.send('DOM.setFileInputFiles', { files: [file], backendNodeId })
    await this.page.send('Page.setInterceptFileChooserDialog', { enabled: false })
    await pause(1200)
  }

  /** Picks an option of a <select> by its visible text. */
  async choose(target, optionText) {
    await this.click(target, { after: 200 })
    const point = await this.locate(target)
    const ok = await evaluate(this.page, `(() => {
      const el = document.elementFromPoint(${point.x}, ${point.y})?.closest('select') || document.activeElement
      const option = [...el.options].find((o) => o.textContent.trim() === ${JSON.stringify(optionText)})
      if (!option) return false
      el.value = option.value
      el.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    })()`)
    if (!ok) throw new Error(`No option "${optionText}" in ${JSON.stringify(target)}`)
    await pause(600)
  }

  /** Clicks the confirming button when a confirm dialog opened. */
  async confirmIfAsked() {
    const asked = await evaluate(this.page, `!!document.querySelector('[role=alertdialog]')`)
    if (asked) await this.click({ css: '[role=alertdialog] button:last-child' }, { after: 1200 })
  }
}

function run(command, args) {
  return new Promise((done, fail) => {
    const child = spawn(command, args, { stdio: ['ignore', 'ignore', 'inherit'] })
    child.on('error', fail)
    child.on('exit', (code) => (code === 0 ? done() : fail(new Error(`${command} exited with ${code}`))))
  })
}
