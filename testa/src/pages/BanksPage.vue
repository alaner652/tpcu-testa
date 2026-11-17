<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Eye, Plus, RotateCw, Trash2 } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import { useBanks } from '@/composables/useBanks'
import { useQuestionActions } from '@/composables/useQuestionActions'
import { useConfirm } from '@/composables/useConfirm'
import { useShortcuts } from '@/composables/useShortcuts'
import { issuesOf } from '@/lib/question'
import type { Bank } from '@/types/question'

const { banks, loading, loadError, load, removeBank, authorUnits } = useBanks()
const { createBank, importDemo } = useQuestionActions()
const { confirm } = useConfirm()
const router = useRouter()
const dev = import.meta.env.DEV

async function create() {
  const created = await createBank()
  if (created) router.push(`/banks/${created.id}`)
}

useShortcuts('題庫', () => [{ combo: 'n', label: '新增題庫', run: create, when: () => authorUnits.value.length > 0 }])

async function remove(bank: Bank) {
  const ok = await confirm({
    title: `刪除「${bank.name || '未命名題庫'}」？`,
    description: `題庫裡的 ${bank.questions.length} 題會一併刪除，無法復原。`,
    confirmLabel: '刪除題庫',
    tone: 'danger',
  })
  if (!ok) return
  try {
    await removeBank(bank.id)
    toast.success('已刪除題庫')
  } catch (e) {
    toast.error((e as Error).message)
  }
}

const points = (bank: Bank) => bank.questions.reduce((sum, q) => sum + q.points, 0)
const incomplete = (bank: Bank) => bank.questions.filter((q) => issuesOf(q).length).length
</script>

<template>
  <div>
    <div class="mb-5 flex items-center justify-between gap-3 px-4 md:px-0">
      <h1 class="text-[26px] font-bold sm:text-[30px]">題庫</h1>
      <Button v-if="authorUnits.length" variant="primary" size="md" @click="create"><Plus />新增題庫</Button>
    </div>

    <div v-if="loadError" class="card flex items-center gap-3 px-5 py-4 text-[15px] text-grade sm:px-6" role="alert">
      <span class="min-w-0 flex-1">{{ loadError }}</span>
      <Button variant="outline" @click="load"><RotateCw />重試</Button>
    </div>

    <div v-else-if="loading" class="grid gap-3 px-4 sm:grid-cols-2 md:px-0 lg:grid-cols-3">
      <Skeleton v-for="n in 3" :key="n" class="h-36 rounded-card" />
    </div>

    <div v-else-if="banks.length === 0" class="card px-5 py-12 sm:px-10">
      <h2 class="text-2xl font-bold">還沒有題庫</h2>
      <template v-if="authorUnits.length">
        <p class="mt-2 mb-6 text-[15px] text-ink-2">建立第一份題庫，開始出題。</p>
        <div class="flex flex-wrap gap-2">
          <Button variant="primary" size="md" @click="create"><Plus />新增題庫</Button>
          <Button v-if="dev" variant="outline" size="md" @click="importDemo">匯入示範題庫（開發用）</Button>
        </div>
      </template>
      <p v-else class="mt-2 text-[15px] text-ink-2">你目前沒有任何單位的出題權限。需要出題的話，請單位負責人把你加為「出題」角色。</p>
    </div>

    <ul v-else class="grid gap-3 px-4 sm:grid-cols-2 md:px-0 lg:grid-cols-3">
      <li v-for="bank in banks" :key="bank.id" class="group relative">
        <RouterLink
          :to="`/banks/${bank.id}`"
          class="flex h-full min-h-36 flex-col rounded-card bg-paper p-5 shadow-card transition-shadow hover:shadow-lift"
        >
          <!-- The subject is a category, not a status: plain text, so badges keep meaning status only -->
          <span class="flex flex-wrap items-center gap-2 pr-8">
            <span class="text-[13px] font-semibold tracking-wide text-brand-strong">{{ bank.subject }}</span>
            <Badge v-if="bank.editable === false"><Eye />只能檢視</Badge>
          </span>
          <span class="mt-3 line-clamp-2 pr-8 text-lg leading-snug font-semibold">{{ bank.name || '未命名題庫' }}</span>
          <span class="mt-auto pt-4 text-sm text-ink-2">
            {{ bank.questions.length }} 題，總分 {{ points(bank) }} 分<template v-if="incomplete(bank)"
              >，<span class="text-grade">{{ incomplete(bank) }} 題未完成</span></template
            >
          </span>
        </RouterLink>
        <button
          v-if="bank.editable !== false"
          type="button"
          :aria-label="`刪除題庫 ${bank.name}`"
          title="刪除題庫"
          class="absolute top-3 right-3 flex size-9 items-center justify-center rounded-full text-ink-3 transition-opacity hover:bg-grade-soft hover:text-grade focus-visible:opacity-100 md:opacity-0 md:group-hover:opacity-100"
          @click="remove(bank)"
        >
          <Trash2 class="size-4" />
        </button>
      </li>
    </ul>
  </div>
</template>
