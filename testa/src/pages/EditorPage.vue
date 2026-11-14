<script setup lang="ts">
import { defineAsyncComponent, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useEventListener, useMediaQuery } from '@vueuse/core'
import { toast } from 'vue-sonner'
import SidePanel from '@/components/ui/SidePanel.vue'
import { DEMO_FEATURES } from '@/lib/flags'
import EditorToolbar from '@/components/editor/EditorToolbar.vue'
import EditorTopBar from '@/components/editor/EditorTopBar.vue'
import ExamPreview from '@/components/editor/ExamPreview.vue'
import QuestionOverview from '@/components/editor/QuestionOverview.vue'
import QuestionSheet from '@/components/editor/QuestionSheet.vue'
import { useBanks } from '@/composables/useBanks'
import { useConfirm } from '@/composables/useConfirm'
import { useQuestionActions } from '@/composables/useQuestionActions'

const store = useBanks()
const { add, duplicate, save } = useQuestionActions()
const { confirm } = useConfirm()

// 版面：中央是一張試卷。≥1280 左側顯示題號卡，更窄時改為抽屜；手機另有底部工具列。
const wide = useMediaQuery('(min-width: 1280px)')

const mode = ref<'edit' | 'preview'>('edit')
const overviewOpen = ref(false)
// AI 匯入還是示範（假資料），只在開發模式才有入口、才會載入
const AiImportPanel = DEMO_FEATURES ? defineAsyncComponent(() => import('@/components/editor/AiImportPanel.vue')) : null
const importOpen = ref(false)
const scroller = ref<HTMLElement | null>(null)
const exam = ref<InstanceType<typeof ExamPreview> | null>(null)

/** 切換模式；離開作答到一半的模擬作答前先確認。 */
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

// 只有檢視權限的人（單位負責人）直接進模擬作答
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

// 選到的題目保持在可視範圍內（剛載入時不捲動，保留標題卡）
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

useEventListener(window, 'keydown', (event: KeyboardEvent) => {
  if (event.isComposing) return
  const mod = event.metaKey || event.ctrlKey
  const id = store.activeId.value
  const key = event.key.toLowerCase()
  if (mod && key === 's') {
    event.preventDefault()
    save()
  } else if (mode.value !== 'edit') {
    return
  } else if (mod && key === 'd' && id) {
    event.preventDefault()
    duplicate(id)
  } else if (mod && key === 'enter' && id && store.activeQuestion.value) {
    event.preventDefault()
    add(store.activeQuestion.value.type, id)
  } else if (event.altKey && (key === 'arrowup' || key === 'arrowdown') && id) {
    event.preventDefault()
    store.move(id, key === 'arrowup' ? -1 : 1)
  }
})

// 網址中的題庫 id 與目前開啟的題庫保持一致
const route = useRoute()
const router = useRouter()
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
      <ExamPreview v-if="mode === 'preview' && store.bank.value" ref="exam" :bank="store.bank.value" @edit="editQuestion" />
      <div
        v-else
        class="mx-auto grid max-w-[1080px] items-start gap-x-8 py-3 md:px-6 md:py-7 xl:grid-cols-[12.5rem_minmax(0,1fr)]"
      >
        <aside class="sticky top-7 hidden max-h-[calc(100dvh-7rem)] xl:flex xl:flex-col">
          <QuestionOverview />
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
      <QuestionOverview @navigate="onOverviewNavigate" />
    </SidePanel>

    <SidePanel v-if="AiImportPanel" v-model:open="importOpen" :docked="false" side="right" title="AI 匯入題目">
      <AiImportPanel @close="importOpen = false" @done="importOpen = false" />
    </SidePanel>
  </div>
</template>
