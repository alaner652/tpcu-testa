import { ref } from 'vue'

const dark = ref(document.documentElement.classList.contains('dark'))

export function useTheme() {
  function toggle() {
    dark.value = !dark.value
    document.documentElement.classList.toggle('dark', dark.value)
    try {
      localStorage.setItem('testa.theme', dark.value ? 'dark' : 'light')
    } catch {
      // Ignore: the next load follows the system setting
    }
  }
  return { dark, toggle }
}
