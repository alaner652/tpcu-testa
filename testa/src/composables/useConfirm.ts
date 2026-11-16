import { reactive } from 'vue'

export interface ConfirmOptions {
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  /** danger: hard-to-undo actions such as delete or clear */
  tone?: 'default' | 'danger'
  /** For especially destructive actions, the confirm button stays disabled until this exact text is entered. */
  confirmText?: string
}

const state = reactive({
  open: false,
  options: { title: '' } as ConfirmOptions,
  input: '',
})

let resolver: ((value: boolean) => void) | null = null

function settle(value: boolean) {
  state.open = false
  resolver?.(value)
  resolver = null
}

/**
 * Every action that needs confirmation goes through here: `if (await confirm({...})) { ... }`.
 * The dialog itself is the single <ConfirmDialog /> in App.
 */
export function useConfirm() {
  function confirm(options: ConfirmOptions) {
    resolver?.(false)
    state.options = options
    state.input = ''
    state.open = true
    return new Promise<boolean>((resolve) => (resolver = resolve))
  }
  return { state, confirm, settle }
}
