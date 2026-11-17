<script setup lang="ts">
import { computed } from 'vue'

/**
 * A dropdown of fixed choices with the unit in each label ("30 天"), so no text trails the box.
 * The default choice says so; a stored value outside the list is still shown.
 */
const props = defineProps<{ id?: string; options: [number, string][]; defaultValue?: number; format?: (n: number) => string }>()
const model = defineModel<number>({ required: true })

const choices = computed(() => {
  const list = [...props.options]
  if (!list.some(([v]) => v === model.value)) {
    list.push([model.value, props.format ? props.format(model.value) : String(model.value)])
    list.sort((a, b) => a[0] - b[0])
  }
  return list.map(([value, label]) => ({ value, label: value === props.defaultValue ? `${label}（預設）` : label }))
})
</script>

<template>
  <select :id="id" v-model.number="model" class="field w-full">
    <option v-for="c in choices" :key="c.value" :value="c.value">{{ c.label }}</option>
  </select>
</template>
