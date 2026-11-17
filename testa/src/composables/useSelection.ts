import { computed, ref, watch } from 'vue'
import { keyLabel } from '@/lib/keys'
import type { Shortcut } from './useShortcuts'

const LONG_PRESS_MS = 450

/** Clicks on a row's own buttons, links and fields are theirs, not the row's. */
const onControl = (e: Event) =>
  !!(e.target as HTMLElement | null)?.closest?.('button, a, input, select, textarea, label, form, [role="menuitem"]')

/**
 * Picking several rows of a list for one action.
 * - Long press (touch or mouse), Ctrl or ⌘ click, or the row's check box starts it; then a click picks or unpicks.
 * - Shift click picks everything from the last picked row.
 * - Rows are keyboard friendly: ↑ ↓ move, Space picks.
 * Rows that leave the list (filtered out, removed, refreshed away) are unpicked, so an action only touches what is visible.
 */
export function useSelection<T>(items: () => T[], keyOf: (item: T) => number | string) {
  type Key = number | string
  const picked = ref(new Set<Key>())
  let anchor: Key | null = null

  const count = computed(() => picked.value.size)
  const active = computed(() => picked.value.size > 0)
  const selected = computed(() => items().filter((i) => picked.value.has(keyOf(i))))

  watch(
    () => items().map(keyOf),
    (keys) => {
      const present = new Set(keys)
      for (const k of [...picked.value]) if (!present.has(k)) picked.value.delete(k)
    },
  )

  const has = (item: T) => picked.value.has(keyOf(item))
  function toggle(item: T) {
    const k = keyOf(item)
    anchor = k
    if (picked.value.has(k)) picked.value.delete(k)
    else picked.value.add(k)
  }
  function extend(item: T) {
    // Shift click also extends the browser's text selection; that is not what was meant
    window.getSelection()?.removeAllRanges()
    const keys = items().map(keyOf)
    const from = anchor === null ? -1 : keys.indexOf(anchor)
    const to = keys.indexOf(keyOf(item))
    if (from < 0) return toggle(item)
    for (const k of keys.slice(Math.min(from, to), Math.max(from, to) + 1)) picked.value.add(k)
  }
  const all = () => items().forEach((i) => picked.value.add(keyOf(i)))
  function clear() {
    picked.value.clear()
    anchor = null
  }

  /** The row's check box: Shift picks a range, otherwise it toggles */
  const mark = (item: T, e: MouseEvent) => (e.shiftKey && anchor !== null ? extend(item) : toggle(item))

  /** A click: true when it was taken for picking (Shift for a range, Ctrl or ⌘, or any click while picking). */
  function click(item: T, e: MouseEvent) {
    if (e.shiftKey && anchor !== null) extend(item)
    else if (e.metaKey || e.ctrlKey || active.value) toggle(item)
    else return false
    return true
  }

  let timer: ReturnType<typeof setTimeout> | undefined
  let start = { x: 0, y: 0 }
  let pressed = false
  const cancel = () => clearTimeout(timer)

  /** Spread on each row: v-bind="selection.row(item)" */
  function row(item: T, onOpen?: () => void) {
    return {
      tabindex: 0,
      'data-row': '',
      'aria-selected': has(item),
      onPointerdown(e: PointerEvent) {
        if (e.button !== 0 || onControl(e)) return
        pressed = false
        start = { x: e.clientX, y: e.clientY }
        cancel()
        timer = setTimeout(() => {
          pressed = true
          toggle(item)
          navigator.vibrate?.(12)
        }, LONG_PRESS_MS)
      },
      onPointermove(e: PointerEvent) {
        if (Math.hypot(e.clientX - start.x, e.clientY - start.y) > 10) cancel()
      },
      onPointerup: cancel,
      onPointercancel: cancel,
      onPointerleave: cancel,
      // Android opens a context menu on long press; the press already picked the row
      onContextmenu(e: Event) {
        if (pressed) e.preventDefault()
      },
      onClick(e: MouseEvent) {
        if (pressed) return void (pressed = false)
        if (!onControl(e)) click(item, e)
      },
      onKeydown(e: KeyboardEvent) {
        const el = e.currentTarget as HTMLElement
        if (e.target !== el) return
        const rows = [...(el.parentElement?.querySelectorAll<HTMLElement>(':scope > [data-row]') ?? [])]
        const at = rows.indexOf(el)
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
          e.preventDefault()
          rows[at + (e.key === 'ArrowDown' ? 1 : -1)]?.focus()
        } else if (e.key === ' ') {
          e.preventDefault()
          if (e.shiftKey && anchor !== null) extend(item)
          else toggle(item)
        } else if (e.key === 'Enter' && onOpen) {
          e.preventDefault()
          onOpen()
        }
      },
    }
  }

  /** Shortcuts to merge into the page's own: Ctrl A, Esc, and the mouse and list keys for the "?" list. */
  function shortcuts(when: () => boolean = () => true): Shortcut[] {
    return [
      { combo: 'mod+a', label: '全選', run: all, when: () => when() && items().length > 0 },
      { combo: 'escape', label: '取消選取', run: clear, when: () => when() && active.value },
      { combo: '', label: '選取或取消一列', keys: `長按，或 ${keyLabel('mod')} 點一下`, when },
      { combo: '', label: '選取一整段', keys: 'Shift 點一下', when },
      { combo: '', label: '在列表中上下移動', keys: '↑ ↓', when },
      { combo: '', label: '選取目前這一列', keys: '空白鍵', when },
    ]
  }

  return { picked, count, active, selected, has, toggle, mark, all, clear, click, row, shortcuts }
}
