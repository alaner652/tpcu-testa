<script setup lang="ts">
import { defineAsyncComponent, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import { Trash2 } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import PointsStepper from '@/components/editor/PointsStepper.vue'
import SelectionBar, { barButton } from '@/components/ui/SelectionBar.vue'
import SidePanel from '@/components/ui/SidePanel.vue'
import ShortcutHelp from '@/components/app/ShortcutHelp.vue'
import EditorToolbar from '@/components/editor/EditorToolbar.vue'
import EditorTopBar from '@/components/editor/EditorTopBar.vue'
import ExamPreview from '@/components/editor/ExamPreview.vue'
import QuestionOverview from '@/components/editor/QuestionOverview.vue'
import QuestionSheet from '@/components/editor/QuestionSheet.vue'
import { useAi } from '@/composables/useAi'
import { useBanks } from '@/composables/useBanks'
import { useConfirm } from '@/composables/useConfirm'
import { useQuestionActions } from '@/composables/useQuestionActions'
import { useSelection } from '@/composables/useSelection'
import { useShortcuts } from '@/composables/useShortcuts'
import { keyLabel } from '@/lib/keys'

const store = useBanks()
const { add, duplicate, removeMany, save } = useQuestionActions()
const { confirm } = useConfirm()

// Layout: one paper in the middle. At >= 1280 the question map sits on the left, narrower it is a drawer; phones get a bottom toolbar.
const wide = useMediaQuery('(min-width: 1280px)')

const mode = ref<'edit' | 'preview'>('edit')
const overviewOpen = ref(false)
// AI import appears only when the server has a model configured; the panel is loaded on demand
const AiImportPanel = defineAsyncComponent(() => import('@/components/editor/AiImportPanel.vue'))
const { importEnabled } = useAi()
const importOpen = ref(false)
const scroller = ref<HTMLElement | null>(null)
const exam = ref<InstanceType<typeof ExamPreview> | null>(null)

/** Switches mode; confirms before leaving a try-it attempt in progress. */
async function requestMode(next: 'edit' | 'preview') {
  if (next === mode.value) return
  if (next === 'edit' && store.bank.value?.editable === false) {
    toast.info('你只能檢視這份題庫。要修改請洽出題者。')
    return
  }
  if (mode.value === 'preview' && exam.value?.hasProgress()) {
    const ok = await confirm({
      title: '離開模擬作答？',
      description: '你還沒交卷，離開後這次的作答內容不會保留。',
      confirmLabel: '離開',
      cancelLabel: '繼續作答',
    })
    if (!ok) return
  }
  mode.value = next
}

watch(wide, (isWide) => isWide && (overviewOpen.value = false))

// Read-only users (unit owners) go straight to try-it mode
watch(
  () => store.bank.value,
  (bank) => bank?.editable === false && (mode.value = 'preview'),
  { immediate: true },
)

async function reveal(id: string | null) {
  if (!id) return
  await nextTick()
  document.getElementById(`q-${id}`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
}

// Keep the selected question in view (no scrolling on first load, keep the title card)
watch(store.activeId, (id, previous) => {
  if (previous && mode.value === 'edit') reveal(id)
})

watch(mode, () => scroller.value?.scrollTo({ top: 0 }))

async function editQuestion(id?: string) {
  await requestMode('edit')
  if (mode.value !== 'edit') return
  if (id) {
    store.select(id)
    await nextTick()
    reveal(id)
  }
}

function onOverviewNavigate() {
  overviewOpen.value = false
  if (mode.value === 'preview') editQuestion(store.activeId.value ?? undefined)
}

// Several questions at once: picked on the question map
const picking = useSelection(() => (mode.value === 'edit' ? store.questions.value : []), (q) => q.id)
const pointsDraft = ref<number | null>(null)
watch(picking.active, (on) => !on && (pointsDraft.value = null))

async function removePicked() {
  if (await removeMany(picking.selected.value.map((q) => q.id))) picking.clear()
}

function applyPoints() {
  const points = pointsDraft.value
  if (points === null) return
  for (const q of picking.selected.value) q.points = points
  toast.success(`已把 ${picking.count.value} 題設為 ${points} 分`)
  picking.clear()
}

const shortcutsOn = () => mode.value === 'edit' && !!store.activeId.value
useShortcuts('題庫編輯', () => [
  { combo: 'mod+s', label: '儲存', run: save, inFields: true },
  { combo: 'mod+d', label: '複製題目', run: () => duplicate(store.activeId.value!), when: shortcutsOn, inFields: true },
  {
    combo: 'mod+enter',
    label: '在下方新增題目',
    run: () => store.activeQuestion.value && add(store.activeQuestion.value.type, store.activeId.value),
    when: shortcutsOn,
    inFields: true,
  },
  { combo: 'alt+arrowup', label: '上移', run: () => store.move(store.activeId.value!, -1), when: shortcutsOn, inFields: true },
  { combo: 'alt+arrowdown', label: '下移', run: () => store.move(store.activeId.value!, 1), when: shortcutsOn, inFields: true },
  ...picking.shortcuts(() => mode.value === 'edit').filter((s) => s.combo),
  { combo: '', label: '選取或取消一題', keys: `題號卡上 ${keyLabel('mod')} 點一下`, when: () => mode.value === 'edit' },
])

// Keep the bank id in the URL in sync with the open bank
const route = useRoute()
const router = useRouter()
// Opening this URL directly or reloading: banks are still loading, so remember the bank from the URL.
// Otherwise loading lands on the first bank, then rewrites the URL to it, opening the wrong bank.
if (store.loading.value && route.params.id) store.bankId.value = route.params.id as string
watch(
  [() => route.params.id as string, store.loading],
  ([id, loading]) => {
    if (loading || !id || id === store.bankId.value) return
    if (store.banks.value.some((b) => b.id === id)) store.selectBank(id)
    else router.replace({ name: 'banks' })
  },
  { immediate: true },
)
watch(store.bankId, (id) => {
  if (id && route.name === 'editor' && id !== route.params.id) router.replace({ name: 'editor', params: { id } })
})
</script>

<template>
  <div class="flex h-dvh flex-col">
    <EditorTopBar :mode="mode" @update:mode="requestMode" @open-overview="overviewOpen = true" @import="importOpen = true" />

    <main ref="scroller" class="thin-scroll min-h-0 flex-1 overflow-y-auto">
      <ExamPreview v-if="mode === 'preview' && store.bank.value" :key="store.bank.value.id" ref="exam" :bank="store.bank.value" @edit="editQuestion" />
      <div
        v-else
        class="mx-auto grid max-w-[1080px] items-start gap-x-8 py-3 md:px-6 md:py-7 xl:grid-cols-[12.5rem_minmax(0,1fr)]"
      >
        <aside class="sticky top-7 hidden max-h-[calc(100dvh-7rem)] xl:flex xl:flex-col">
          <QuestionOverview :selection="picking" />
        </aside>
        <div class="mx-auto w-full max-w-[820px] min-w-0">
          <QuestionSheet @import="importOpen = true" />
        </div>
      </div>
    </main>

    <EditorToolbar
      v-if="mode === 'edit' && !store.loading.value"
      class="md:hidden"
      orientation="horizontal"
      @import="importOpen = true"
      @preview="requestMode('preview')"
    />

    <SidePanel v-model:open="overviewOpen" :docked="false" side="left" title="題號卡" class="bg-desk p-4">
      <QuestionOverview :selection="picking" @navigate="onOverviewNavigate" />
    </SidePanel>

    <SidePanel v-if="importEnabled" v-model:open="importOpen" :docked="false" side="right" title="AI 匯入題目">
      <AiImportPanel @close="importOpen = false" @done="importOpen = false" />
    </SidePanel>

    <SelectionBar :count="picking.count.value" :total="store.questions.value.length" unit="題" @all="picking.all" @clear="picking.clear">
      <form class="flex items-center gap-1.5 rounded-full bg-paper py-0.5 pr-0.5 pl-3 text-ink" @submit.prevent="applyPoints">
        <span class="text-sm text-ink-2">配分</span>
        <PointsStepper v-model="pointsDraft" allow-empty />
        <button type="submit" :class="[barButton, 'bg-brand text-brand-fg hover:bg-brand-strong']" :disabled="pointsDraft === null">套用</button>
      </form>
      <button type="button" :class="barButton" @click="removePicked"><Trash2 />刪除</button>
    </SelectionBar>
    <ShortcutHelp />
  </div>
</template>
