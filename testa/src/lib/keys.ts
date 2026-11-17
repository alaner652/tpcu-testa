/**
 * Keyboard shortcut helpers. A combo is written like 'mod+s', 'alt+arrowup', 'n', '/', '?' or 'escape';
 * mod is Ctrl on Windows and Linux, ⌘ on a Mac.
 */
type KeyInfo = Pick<KeyboardEvent, 'key' | 'ctrlKey' | 'metaKey' | 'altKey' | 'shiftKey'>

export const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)

export function matches(combo: string, e: KeyInfo): boolean {
  const parts = combo.toLowerCase().split('+')
  const key = parts.pop()!
  if ((e.ctrlKey || e.metaKey) !== parts.includes('mod') || e.altKey !== parts.includes('alt')) return false
  const shift = parts.includes('shift')
  if (shift && !e.shiftKey) return false
  // For letters Shift counts (Shift N is not N); for symbols such as ? it is part of typing the key
  if (/^[a-z]$/.test(key) && !shift && e.shiftKey) return false
  const pressed = e.key === ' ' ? 'space' : e.key.toLowerCase()
  return pressed === key
}

const NAMES: Record<string, string> = {
  arrowup: '↑',
  arrowdown: '↓',
  arrowleft: '←',
  arrowright: '→',
  escape: 'Esc',
  enter: 'Enter',
  space: '空白鍵',
  delete: 'Delete',
}

/** How a combo is shown to people: 'mod+d' is 'Ctrl D' (or '⌘ D' on a Mac). */
export function keyLabel(combo: string, mac = isMac): string {
  return combo
    .split('+')
    .map((p) => {
      const k = p.toLowerCase()
      if (k === 'mod') return mac ? '⌘' : 'Ctrl'
      if (k === 'alt') return mac ? '⌥' : 'Alt'
      if (k === 'shift') return 'Shift'
      return NAMES[k] ?? p.toUpperCase()
    })
    .join(' ')
}

/** Typing in a field: single-key shortcuts must not fire. */
export function isTyping(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null
  if (!el?.closest) return false
  const field = el.closest('input, textarea, select, [contenteditable=""], [contenteditable="true"]')
  if (!field) return false
  return !(field instanceof HTMLInputElement && ['checkbox', 'radio', 'button', 'submit', 'reset'].includes(field.type))
}
