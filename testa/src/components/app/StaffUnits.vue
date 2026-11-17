<script setup lang="ts">
import { computed, reactive } from 'vue'
import { Plus, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import { ROLE_CHOICES, UNIT_ROLE_LABEL, rolesOf, type UnitRole } from '@/composables/useAuth'
import { useConfirm } from '@/composables/useConfirm'
import { api } from '@/lib/api'

/** One person's units and roles, seen from the person (the unit view is UnitMembers). System administrators only. */
const props = defineProps<{
  staff: {
    name: string
    email: string
    roles: { id: number; unitId: number; unitName: string; role: UnitRole }[]
  }
  units: { id: number; name: string; active: boolean }[]
}>()
const emit = defineEmits<{ changed: [] }>()
const { confirm } = useConfirm()

const byUnit = computed(() => {
  const map = new Map<number, { unitId: number; unitName: string; roles: typeof props.staff.roles }>()
  for (const r of props.staff.roles) {
    const row = map.get(r.unitId) ?? {
      unitId: r.unitId,
      unitName: r.unitName,
      roles: [],
    }
    row.roles.push(r)
    map.set(r.unitId, row)
  }
  return [...map.values()]
})

const draft = reactive({ unitId: null as number | null, choice: 'teacher' })

async function add() {
  const unit = props.units.find((u) => u.id === draft.unitId)
  if (!unit) return
  try {
    await api(`/units/${unit.id}/members`, {
      method: 'POST',
      body: { email: props.staff.email, roles: rolesOf(draft.choice) },
    })
    toast.success(`已將 ${props.staff.name} 加入「${unit.name}」`)
    draft.unitId = null
    emit('changed')
  } catch (e) {
    toast.error((e as Error).message)
  }
}

async function remove(r: (typeof props.staff.roles)[number]) {
  const ok = await confirm({
    title: `移除 ${props.staff.name} 在「${r.unitName}」的「${UNIT_ROLE_LABEL[r.role]}」角色？`,
    description: `移除後立即生效，${props.staff.name} 在「${r.unitName}」不能再做這個角色的工作。`,
    confirmLabel: '移除',
    tone: 'danger',
  })
  if (!ok) return
  try {
    await api(`/units/${r.unitId}/members/${r.id}`, { method: 'DELETE' })
    toast.success(`已移除 ${props.staff.name} 的「${UNIT_ROLE_LABEL[r.role]}」角色`)
    emit('changed')
  } catch (e) {
    toast.error((e as Error).message)
  }
}
</script>

<template>
  <div>
    <p v-if="!byUnit.length" class="text-[15px] text-ink-2">還沒有加入任何單位。</p>
    <ul v-else class="space-y-2">
      <li v-for="u in byUnit" :key="u.unitId" class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
        <p class="min-w-0 basis-40 text-[15px] font-medium">{{ u.unitName }}</p>
        <span v-for="r in u.roles" :key="r.id" class="inline-flex h-8 items-center gap-1 rounded-full bg-paper pr-1 pl-3 text-sm shadow-card">
          {{ UNIT_ROLE_LABEL[r.role] }}
          <button
            type="button"
            class="flex size-6 items-center justify-center rounded-full text-ink-3 hover:bg-grade-soft hover:text-grade"
            :aria-label="`移除${u.unitName}的${UNIT_ROLE_LABEL[r.role]}角色`"
            @click="remove(r)"
          >
            <X class="size-3.5" />
          </button>
        </span>
      </li>
    </ul>
    <form v-if="units.length" class="mt-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_14rem_auto]" novalidate @submit.prevent="add">
      <select v-model="draft.unitId" class="field" aria-label="單位">
        <option :value="null" disabled>選擇單位</option>
        <option v-for="u in units" :key="u.id" :value="u.id">{{ u.name }}{{ u.active ? '' : '（已停用）' }}</option>
      </select>
      <select v-model="draft.choice" class="field" aria-label="角色">
        <option v-for="c in ROLE_CHOICES" :key="c.value" :value="c.value">
          {{ c.label }}
        </option>
      </select>
      <Button variant="primary" size="md" type="submit" :disabled="draft.unitId === null"><Plus />加入</Button>
    </form>
    <p v-else class="mt-3 text-sm text-ink-2">還沒有任何單位，請先到「單位」分頁新增。</p>
  </div>
</template>
