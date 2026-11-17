import { onBeforeUnmount, onMounted, ref, shallowReactive } from 'vue'
import { useEventListener } from '@vueuse/core'
import { isTyping, keyLabel, matches } from '@/lib/keys'

/**
 * Keyboard shortcuts. They only speed things up: every action also has a button.
 * - Single keys never fire while typing in a field or choosing characters with an input method.
 * - Pages register their shortcuts while mounted; the "?" list shows what the current page offers.
 * - The candidate exam page registers none.
 */
export interface Shortcut {
  /** 'mod+s', 'n', '/', 'escape'... see lib/keys */
  combo: string
  /** Shown in the "?" list; leave out for keys that need no explanation */
  label?: string
  /** Shown instead of the combo, e.g. '↑ ↓' for a pair */
  keys?: string
  /** Leave out for list-only entries handled elsewhere (row arrow keys) */
  run?: (event: KeyboardEvent) => void
  when?: () => boolean
  /** Also fire while typing in a field (Ctrl S) */
  inFields?: boolean
}

interface Group {
  title: string
  list: () => Shortcut[]
}

const groups = shallowReactive(new Set<Group>())
export const helpOpen = ref(false)

export function useShortcuts(title: string, list: () => Shortcut[]) {
  const group: Group = { title, list }
  onMounted(() => groups.add(group))
  onBeforeUnmount(() => groups.delete(group))
  useEventListener(window, 'keydown', (event: KeyboardEvent) => {
    if (event.defaultPrevented || event.isComposing || event.keyCode === 229) return
    // Dialogs and menus handle their own keys
    const target = event.target as HTMLElement | null
    if (target?.closest?.('[role="dialog"], [role="alertdialog"], [role="menu"], [role="listbox"]')) return
    const typing = isTyping(target)
    for (const s of list()) {
      if (!s.run || (typing && !s.inFields) || (s.when && !s.when()) || !matches(s.combo, event)) continue
      event.preventDefault()
      s.run(event)
      return
    }
  })
}

/** What the "?" list shows: the shortcuts available right now, by page. */
export function shortcutHelp() {
  return [...groups]
    .map((g) => ({
      title: g.title,
      items: g
        .list()
        .filter((s) => s.label && (!s.when || s.when()))
        .map((s) => ({ label: s.label!, keys: s.keys ?? keyLabel(s.combo) })),
    }))
    .filter((g) => g.items.length)
}
