<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api } from '@/lib/api'

/**
 * Email field that suggests colleagues by name or email as you type.
 * Typing a full email still works; the suggestions only save looking it up.
 */
const props = defineProps<{ unitId: number; exclude?: string[] }>()
const email = defineModel<string>({ required: true })

interface Option {
  id: number
  name: string
  email: string
}
const options = ref<Option[]>([])
const open = ref(false)
const input = ref<HTMLInputElement>()
/** The list is teleported to <body> (cards clip overflow) and opens upward when there is no room below */
const place = ref<Record<string, string>>({})
function position() {
  const r = input.value?.getBoundingClientRect()
  if (!r) return
  const up = r.bottom + 270 > window.innerHeight && r.top > 270
  place.value = {
    left: `${r.left}px`,
    width: `${r.width}px`,
    ...(up ? { bottom: `${window.innerHeight - r.top + 4}px` } : { top: `${r.bottom + 4}px` }),
  }
}
const reposition = () => open.value && position()
onMounted(() => {
  window.addEventListener('scroll', reposition, true)
  window.addEventListener('resize', reposition)
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', reposition, true)
  window.removeEventListener('resize', reposition)
})
const active = ref(-1)
const listId = `staff-picker-${props.unitId}`
let timer: ReturnType<typeof setTimeout> | undefined
let seq = 0

watch(email, (value) => {
  clearTimeout(timer)
  const q = value.trim()
  if (!q || options.value.some((o) => o.email === q)) return void (options.value = [])
  timer = setTimeout(async () => {
    const mine = ++seq
    try {
      const found = await api<Option[]>(`/units/${props.unitId}/staff-search?q=${encodeURIComponent(q)}`)
      if (mine !== seq) return
      options.value = found.filter((o) => !props.exclude?.includes(o.email))
      active.value = -1
      position()
      open.value = true
    } catch {
      options.value = []
    }
  }, 200)
})

function pick(o: Option) {
  options.value = [o]
  email.value = o.email
  open.value = false
}

function onKey(e: KeyboardEvent) {
  if (!open.value || !options.value.length) return
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault()
    const n = options.value.length
    active.value = (active.value + (e.key === 'ArrowDown' ? 1 : n - 1)) % n
  } else if (e.key === 'Enter' && active.value >= 0) {
    e.preventDefault()
    pick(options.value[active.value])
  } else if (e.key === 'Escape') {
    open.value = false
  }
}
</script>

<template>
  <div class="relative">
    <input
      ref="input"
      v-model="email"
      type="text"
      inputmode="email"
      autocomplete="off"
      class="field"
      placeholder="搜尋姓名或輸入 Email"
      aria-label="教職員的姓名或 Email"
      role="combobox"
      :aria-expanded="open && options.length > 0"
      :aria-controls="listId"
      :aria-activedescendant="active >= 0 ? `${listId}-${active}` : undefined"
      @keydown="onKey"
      @focus="(position(), (open = true))"
      @blur="open = false"
    />
    <Teleport to="body">
      <ul
        v-if="open && options.length"
        :id="listId"
        role="listbox"
        class="fixed z-50 max-h-64 overflow-auto rounded-control bg-paper py-1 shadow-lift"
        :style="place"
      >
        <li
          v-for="(o, i) in options"
          :id="`${listId}-${i}`"
          :key="o.id"
          role="option"
          :aria-selected="i === active"
          class="cursor-pointer px-3 py-2 text-[15px]"
          :class="i === active ? 'bg-brand-soft' : 'hover:bg-sunken'"
          @mousedown.prevent="pick(o)"
        >
          <span class="font-medium">{{ o.name }}</span>
          <span class="ml-2 text-sm text-ink-2">{{ o.email }}</span>
        </li>
      </ul>
    </Teleport>
  </div>
</template>
