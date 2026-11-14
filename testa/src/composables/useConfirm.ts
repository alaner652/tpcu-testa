import { reactive } from 'vue'

export interface ConfirmOptions {
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  /** danger: hard-to-undo actions such as delete or clear */
  tone?: 'default' | 'danger'
}

const state = reactive({
  open: false,
  options: { title: '' } as ConfirmOptions,
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
    state.open = true
    return new Promise<boolean>((resolve) => (resolver = resolve))
  }
  return { state, confirm, settle }
}
