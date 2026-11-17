<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui'
import { ArrowDown, ArrowUp, CircleAlert, Copy, GripHorizontal, Plus, Sparkles, Trash2 } from 'lucide-vue-next'
import type { Question } from '@/types/question'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import MenuContent from '@/components/ui/MenuContent.vue'
import MenuItem from '@/components/ui/MenuItem.vue'
import Switch from '@/components/ui/Switch.vue'
import Tip from '@/components/ui/Tip.vue'
import { useBanks } from '@/composables/useBanks'
import { useQuestionActions } from '@/composables/useQuestionActions'
import { keyLabel } from '@/lib/keys'
import { issuesOf, QUESTION_TYPES, typeMeta } from '@/lib/question'
import BlankEditor from './BlankEditor.vue'
import ChoiceEditor from './ChoiceEditor.vue'
import EssayEditor from './EssayEditor.vue'
import InlineMathField from './InlineMathField.vue'
import PointsStepper from './PointsStepper.vue'
import QuestionSummary from './QuestionSummary.vue'
import StemEditor from './StemEditor.vue'
import TypeSelect from './TypeSelect.vue'

const props = defineProps<{
  question: Question
  index: number
  total: number
  active: boolean
}>()

const store = useBanks()
const { add, remove, duplicate } = useQuestionActions()

function markReviewed() {
  props.question.review = false
  toast.success('已標示為已確認')
}

const issues = computed(() => issuesOf(props.question))
const showExplanation = ref(props.question.explanation !== '')
const stem = ref<InstanceType<typeof StemEditor> | null>(null)

// When a new blank question opens, put the cursor in its stem,
// after a short delay so the "add question" menu can close and release focus.
watch(
  () => props.active,
  async (active) => {
    if (!active || props.question.stem) return
    await nextTick()
    setTimeout(() => stem.value?.focus(), 80)
  },
  { immediate: true },
)
</script>

<template>
  <article
    :id="`q-${question.id}`"
    class="group/q relative scroll-mt-6 bg-paper transition-shadow duration-150 md:rounded-card"
    :class="active ? 'shadow-lift ring-2 ring-brand max-md:ring-inset' : 'cursor-pointer shadow-card hover:shadow-lift'"
    :tabindex="active ? undefined : 0"
    :role="active ? undefined : 'button'"
    :aria-label="active ? `第 ${index + 1} 題` : `編輯第 ${index + 1} 題`"
    @click="!active && store.select(question.id)"
    @keydown.enter.self="store.select(question.id)"
  >
    <!-- Drag handle: top centre of the card -->
    <div class="flex h-6 justify-center">
      <button
        type="button"
        aria-label="拖曳排序"
        title="拖曳排序"
        class="drag-handle flex h-6 w-16 cursor-grab touch-none items-center justify-center text-ink-3 hover:text-ink active:cursor-grabbing md:opacity-0 md:group-hover/q:opacity-100 md:focus-visible:opacity-100"
        :class="active && 'md:!opacity-100'"
        @click.stop
      >
        <GripHorizontal class="size-4" />
      </button>
    </div>

    <div class="px-5 pb-5 sm:px-10">
      <!-- Settings row, only on the selected card -->
      <div v-if="active" class="mb-4 flex flex-wrap items-center gap-x-3 gap-y-2">
        <TypeSelect :question="question" />
        <PointsStepper v-model="question.points" class="ml-auto" />
      </div>

      <div
        v-if="active && question.review"
        class="mb-4 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-control bg-marker px-3.5 py-2.5 text-sm text-marker-ink"
      >
        <Sparkles class="size-4 shrink-0" />
        <span class="min-w-0 flex-1">由 AI 匯入，請核對題目與答案。</span>
        <button
          type="button"
          class="rounded-sm font-medium underline underline-offset-2 hover:no-underline"
          @click="markReviewed"
        >
          標示為已確認
        </button>
      </div>

      <!-- The question itself, laid out as candidates see it -->
      <div class="flex items-start gap-3 sm:gap-4">
        <span class="w-6 shrink-0 text-[17px] leading-[1.7] font-semibold text-brand tabular-nums">{{ index + 1 }}</span>

        <div v-if="active" class="min-w-0 flex-1">
          <StemEditor ref="stem" v-model="question.stem" />
          <div class="mt-4">
            <ChoiceEditor v-if="question.type === 'single' || question.type === 'multiple'" :question="question" />
            <BlankEditor v-else-if="question.type === 'blank'" :question="question" />
            <EssayEditor v-else :question="question" />
          </div>
          <div v-if="showExplanation" class="mt-4">
            <label :for="`exp-${question.id}`" class="mb-1 block text-sm text-ink-2">解析</label>
            <InlineMathField
              :id="`exp-${question.id}`"
              v-model="question.explanation"
              placeholder="交卷後顯示給考生的解題說明，可使用 $…$ 公式"
              class="rounded-control bg-sunken px-3.5 py-2.5 focus-within:shadow-[inset_0_0_0_1.5px_var(--brand)]"
            />
          </div>
        </div>

        <template v-else>
          <div class="min-w-0 flex-1">
            <QuestionSummary :question="question" />
            <p v-if="question.review || issues.length" class="mt-3 flex flex-wrap items-center gap-2 text-[13px]">
              <Badge v-if="question.review" tone="warn">AI 匯入，待確認</Badge>
              <span v-if="issues.length" class="flex items-center gap-1 text-grade">
                <CircleAlert class="size-3.5" />
                {{ issues.join('、') }}
              </span>
            </p>
          </div>
          <div class="shrink-0 pt-1 text-right">
            <p class="text-sm text-ink-2 tabular-nums">{{ question.points }} 分</p>
            <p class="text-xs text-ink-3">{{ typeMeta(question.type).label }}</p>
          </div>
        </template>
      </div>

      <footer v-if="active" class="mt-5 flex flex-wrap items-center gap-x-3 gap-y-2 border-t border-line pt-3">
        <p v-if="issues.length" class="flex items-center gap-1.5 text-sm text-grade" role="status">
          <CircleAlert class="size-4 shrink-0" />
          {{ issues.join('、') }}
        </p>
        <div class="ml-auto flex items-center gap-0.5">
          <Tip label="上移" :shortcut="keyLabel('alt+arrowup')">
            <Button size="icon" aria-label="上移" :disabled="index === 0" @click="store.move(question.id, -1)">
              <ArrowUp />
            </Button>
          </Tip>
          <Tip label="下移" :shortcut="keyLabel('alt+arrowdown')">
            <Button size="icon" aria-label="下移" :disabled="index === total - 1" @click="store.move(question.id, 1)">
              <ArrowDown />
            </Button>
          </Tip>
          <Tip label="複製" :shortcut="keyLabel('mod+d')">
            <Button size="icon" aria-label="複製題目" @click="duplicate(question.id)"><Copy /></Button>
          </Tip>
          <Tip label="刪除">
            <Button size="icon" variant="danger" aria-label="刪除題目" @click="remove(question.id)">
              <Trash2 />
            </Button>
          </Tip>
          <span class="mx-2 h-6 w-px bg-line" aria-hidden="true" />
          <label class="inline-flex items-center gap-2 text-sm text-ink-2">
            解析
            <Switch v-model="showExplanation" />
          </label>
        </div>
      </footer>
    </div>

    <!-- Shown when hovering between two questions: insert a new one below this -->
    <div
      class="absolute inset-x-0 -bottom-5 z-10 flex h-7 justify-center opacity-0 transition-opacity group-hover/q:opacity-100 focus-within:opacity-100 has-[[data-state=open]]:opacity-100 max-md:hidden"
    >
      <DropdownMenuRoot>
        <DropdownMenuTrigger
          class="flex h-7 items-center gap-1 rounded-full bg-paper px-3 text-[13px] font-medium text-brand shadow-lift hover:bg-brand hover:text-brand-fg"
          @click.stop
        >
          <Plus class="size-3.5" />在這裡新增題目
        </DropdownMenuTrigger>
        <MenuContent align="center" class="w-40" @close-auto-focus.prevent>
          <MenuItem v-for="t in QUESTION_TYPES" :key="t.value" class="h-10 text-[15px]" @select="add(t.value, question.id)">
            <component :is="t.icon" />
            {{ t.label }}題
          </MenuItem>
        </MenuContent>
      </DropdownMenuRoot>
    </div>
  </article>
</template>
