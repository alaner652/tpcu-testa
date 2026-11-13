import { ref } from 'vue'

const dark = ref(document.documentElement.classList.contains('dark'))

export function useTheme() {
  function toggle() {
    dark.value = !dark.value
    document.documentElement.classList.toggle('dark', dark.value)
    try {
      localStorage.setItem('testa.theme', dark.value ? 'dark' : 'light')
    } catch {
      // 忽略：下次載入時依系統設定
    }
  }
  return { dark, toggle }
}
