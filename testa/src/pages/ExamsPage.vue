<script setup lang="ts">
import { computed, h, reactive, ref, type Component } from 'vue'
import { Archive, ArchiveRestore, CalendarClock, ChartColumn, ChevronRight, CircleAlert, CircleStop, Clock, Megaphone, Pencil, Plus, Save, Send, Trash2, Undo2, Upload, X } from 'lucide-vue-next'
import PageHeader from '@/components/ui/PageHeader.vue'
import FormError from '@/components/ui/FormError.vue'
import EmptyNote from '@/components/ui/EmptyNote.vue'
import ErrorNote from '@/components/ui/ErrorNote.vue'
import MoreMenu, { type MoreAction } from '@/components/ui/MoreMenu.vue'
import { toast } from 'vue-sonner'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import SidePanel from '@/components/ui/SidePanel.vue'
import { useAuth } from '@/composables/useAuth'
import { useBanks } from '@/composables/useBanks'
import { useConfirm } from '@/composables/useConfirm'
import { useShortcuts } from '@/composables/useShortcuts'
import { canPurge, EXAM_STATUS, retainLabel, toLocalInput, useExams, when, type Exam, type ExamMode } from '@/composables/useExams'

const { exams, loading, loadError, load, create, update, publish, withdraw, end, remove, archive, unarchive } = useExams()
const { banks } = useBanks()
const { user } = useAuth()
const { confirm } = useConfirm()

/** Units I own: exams can be created only there, with banks from those units. */
const ownedUnits = computed(() => (user.value?.units ?? []).filter((u) => u.roles.includes('OWNER')).map((u) => u.id))
const usableBanks = computed(() => banks.value.filter((b) => b.unitId != null && ownedUnits.value.includes(b.unitId)))
const fail = (e: unknown) => toast.error((e as Error).message)

// Create or edit an exam
const formOpen = ref(false)
const editing = ref<Exam | null>(null)
const form = reactive({ name: '', bankId: '', start: '', duration: 60, mode: 'CLASSROOM' as ExamMode })
const formError = ref('')
const saving = ref(false)

useShortcuts('考試', () => [{ combo: 'n', label: '建立考試', run: () => openForm(), when: () => ownedUnits.value.length > 0 && !formOpen.value }])

function openForm(exam?: Exam) {
  editing.value = exam ?? null
  const first = usableBanks.value[0]
  Object.assign(
    form,
    exam
      ? { name: exam.name, bankId: exam.bankId == null ? '' : String(exam.bankId), start: toLocalInput(exam.startAt), duration: exam.duration, mode: exam.mode }
      : { name: '', bankId: first?.id ?? '', start: '', duration: first?.duration ?? 60, mode: 'CLASSROOM' },
  )
  formError.value = ''
  formOpen.value = true
}

async function submitForm() {
  if (saving.value) return
  if (!form.name.trim() || !form.bankId) return void (formError.value = '請填寫考試名稱並選擇題庫。')
  const input = {
    name: form.name.trim(),
    bankId: Number(form.bankId),
    startAt: form.start ? new Date(form.start).toISOString() : null,
    duration: Math.round(form.duration) || 60,
    mode: form.mode,
  }
  saving.value = true
  try {
    if (editing.value) await update(editing.value.id, input)
    else await create(input)
    formOpen.value = false
    toast.success(editing.value ? '已儲存考試設定' : '已建立考試草稿')
  } catch (e) {
    formError.value = (e as Error).message
  } finally {
    saving.value = false
  }
}

async function doPublish(exam: Exam) {
  const ok = await confirm({
    title: `發布「${exam.name}」？`,
    description: '發布時會把題庫目前的題目固定下來，之後題庫再修改也不會影響這場考試。名單上的考生在開始時間後就可以登入作答。',
    confirmLabel: '發布',
  })
  if (!ok) return
  try {
    await publish(exam.id)
    toast.success('已發布考試')
  } catch (e) {
    fail(e)
  }
}

async function doEnd(exam: Exam) {
  const left = exam.takers - exam.submitted
  const ok = await confirm({
    title: `結束「${exam.name}」？`,
    description: (left > 0 ? `還有 ${left} 位考生尚未交卷。` : '') + '結束後無法重新開放。',
    confirmLabel: '結束考試',
    tone: 'danger',
  })
  if (!ok) return
  try {
    await end(exam.id)
    toast.success('已結束考試')
  } catch (e) {
    fail(e)
  }
}

/** The row's main action: publish a draft or end a live exam. Shown beside the name, or on its own line on phones. */
const hasAction = (exam: Exam) => exam.canManage && (exam.status === 'draft' || exam.status === 'live')
function ExamAction({ exam }: { exam: Exam }) {
  if (!hasAction(exam)) return null
  if (exam.status === 'draft') return h(Button, { variant: 'primary', class: 'relative z-10', onClick: () => doPublish(exam) }, () => [h(Send), '發布'])
  if (exam.status === 'live') return h(Button, { variant: 'outline', class: 'relative z-10', onClick: () => doEnd(exam) }, () => [h(CircleStop), '結束考試'])
  return null
}

async function doWithdraw(exam: Exam) {
  const ok = await confirm({ title: `取消發布「${exam.name}」？`, description: '考試會回到草稿，可以再修改設定。考生名單會保留。', confirmLabel: '取消發布' })
  if (!ok) return
  try {
    await withdraw(exam.id)
    toast.success('已取消發布')
  } catch (e) {
    fail(e)
  }
}

async function doRemove(exam: Exam) {
  const ok = await confirm({
    title: `刪除「${exam.name}」？`,
    description: exam.takers ? `名單上的 ${exam.takers} 位考生會一併刪除，無法復原。` : '刪除後無法復原。',
    confirmLabel: '刪除考試',
    tone: 'danger',
  })
  if (!ok) return
  try {
    await remove(exam.id)
    toast.success('已刪除考試')
  } catch (e) {
    fail(e)
  }
}

/** Most active first: live (most candidates writing on top), then upcoming, drafts, and ended ones, newest first */
const RANK: Record<Exam['status'], number> = { live: 0, scheduled: 1, draft: 2, ended: 3 }
/** Ended more than a week ago: shown in grey so the active ones stand out */
const STALE_MS = 7 * 86400 * 1000
const stale = (e: Exam) => e.status === 'ended' && !!e.startAt && Date.now() - new Date(e.startAt).getTime() - e.duration * 60000 > STALE_MS
const current = computed(() =>
  exams.value
    .filter((e) => !e.archivedAt)
    .sort((a, b) => RANK[a.status] - RANK[b.status] || b.inProgress - a.inProgress || (b.startAt ?? '').localeCompare(a.startAt ?? '') || b.id - a.id),
)
const archived = computed(() => exams.value.filter((e) => e.archivedAt))

async function doArchive(exam: Exam) {
  const ok = await confirm({
    title: `封存「${exam.name}」？`,
    description: `封存後這場考試變成唯讀，考生不能再登入查成績。資料會保留到 ${retainLabel(exam.retainUntil)}，之後才能刪除。封存可以取消。`,
    confirmLabel: '封存',
  })
  if (!ok) return
  try {
    await archive(exam.id)
    toast.success('已封存考試')
  } catch (e) {
    fail(e)
  }
}

async function doUnarchive(exam: Exam) {
  try {
    await unarchive(exam.id)
    toast.success('已取消封存')
  } catch (e) {
    fail(e)
  }
}

// Deleting an archived exam requires a reason
const purging = ref<Exam | null>(null)
const purgeReason = ref('')
async function doPurge() {
  const exam = purging.value
  if (!exam || !purgeReason.value.trim()) return
  const ok = await confirm({
    title: `永久刪除「${exam.name}」？`,
    description: `這場考試的 ${exam.takers} 位考生、所有答卷與評分會一起刪除，無法復原。操作紀錄只會留下考試名稱與人數。`,
    confirmLabel: '永久刪除',
    tone: 'danger',
  })
  if (!ok) return
  try {
    await remove(exam.id, purgeReason.value.trim())
    purging.value = null
    toast.success('已刪除考試')
  } catch (e) {
    fail(e)
  }
}

/** One line on the card: what to do next with this exam. New users just follow it. */
function nextStep(exam: Exam): { text: string; icon: Component; todo: boolean } {
  const time = `${when(exam.startAt)}，${exam.duration} 分鐘`
  if (!exam.bankName) return { text: '題庫已刪除，請按「⋯」→ 修改，重新選擇題庫', icon: CircleAlert, todo: true }
  switch (exam.status) {
    case 'draft':
      if (!exam.takers) return { text: '下一步：點進去匯入考生名單', icon: Upload, todo: true }
      if (!exam.startAt) return { text: '下一步：按「⋯」→ 修改，設定開始時間', icon: CalendarClock, todo: true }
      return { text: `${time}，${exam.takers} 位考生。確認無誤就可以發布`, icon: Send, todo: true }
    case 'scheduled':
      return { text: `${time}。記得先點進去印登入單`, icon: CalendarClock, todo: false }
    case 'live':
      return exam.takers && exam.submitted === exam.takers
        ? { text: '全部考生都交卷了，可以結束考試', icon: CircleStop, todo: true }
        : { text: `${time}，考試進行中`, icon: Clock, todo: false }
    default:
      return exam.resultsPublishedAt
        ? { text: `${when(exam.startAt)}，成績已公布`, icon: Megaphone, todo: false }
        : { text: '下一步：點進去評分、公布成績', icon: ChartColumn, todo: true }
  }
}

function moreActions(exam: Exam): MoreAction[] {
  switch (exam.status) {
    case 'draft':
      return [
        { label: '修改', icon: Pencil, run: () => openForm(exam) },
        { label: '刪除', icon: Trash2, run: () => doRemove(exam), danger: true },
      ]
    case 'scheduled':
      return [{ label: '取消發布（改回草稿）', icon: Undo2, run: () => doWithdraw(exam) }]
    case 'ended':
      return [{ label: '封存', icon: Archive, run: () => doArchive(exam) }]
    default:
      return []
  }
}

const percent = (exam: Exam) => (exam.takers ? Math.round((exam.submitted / exam.takers) * 100) : 0)
</script>

<template>
  <div>
    <PageHeader title="考試">
      <template #actions><Button v-if="ownedUnits.length" variant="primary" size="md" @click="openForm()"><Plus />建立考試</Button></template>
    </PageHeader>

    <ErrorNote v-if="loadError" class="card" :message="loadError" :retry="load" />

    <div v-else-if="loading" class="space-y-3 px-4 md:px-0">
      <Skeleton v-for="n in 2" :key="n" class="h-28 rounded-card" />
    </div>

    <div v-else-if="exams.length === 0" class="card px-5 py-12 sm:px-10">
      <h2 class="text-2xl font-bold">還沒有考試</h2>
      <p v-if="!ownedUnits.length" class="mt-2 text-[15px] text-ink-2">單位負責人建立考試後，會出現在這裡。</p>
      <p v-else-if="!usableBanks.length" class="mt-2 text-[15px] text-ink-2">建立考試需要一份題庫。請出題的老師先建立題庫。</p>
      <template v-else>
        <p class="mt-2 mb-6 text-[15px] text-ink-2">選一份題庫、設定時間，再匯入考生名單。</p>
        <Button variant="primary" size="md" @click="openForm()"><Plus />建立考試</Button>
      </template>
    </div>

    <template v-else>
    <EmptyNote v-if="!current.length" class="card">目前沒有進行中或待處理的考試，其餘都已封存。</EmptyNote>
    <ul v-else class="card divide-y divide-line">
      <li
        v-for="exam in current"
        :key="exam.id"
        class="relative px-5 py-4 transition-colors sm:px-6"
        :class="stale(exam) ? 'bg-sunken/70 text-ink-2 hover:bg-sunken' : 'hover:bg-sunken'"
      >
        <div class="flex items-center gap-3">
          <div class="min-w-0 flex-1">
            <p class="flex items-start gap-2">
              <Badge class="mt-px shrink-0" :tone="EXAM_STATUS[exam.status].tone">{{ EXAM_STATUS[exam.status].label }}</Badge>
              <!-- The whole row is clickable: the link covers it and buttons sit above -->
              <RouterLink :to="`/exams/${exam.id}`" class="min-w-0 text-[17px] font-semibold after:absolute after:inset-0">{{ exam.name }}</RouterLink>
            </p>
            <p class="mt-1 flex items-center gap-1.5 text-sm tabular-nums" :class="nextStep(exam).todo ? 'font-medium text-brand-strong' : 'text-ink-2'">
              <component :is="nextStep(exam).icon" class="size-4 shrink-0" aria-hidden="true" />{{ nextStep(exam).text }}
            </p>
          </div>
          <div v-if="hasAction(exam)" class="max-sm:hidden"><ExamAction :exam="exam" /></div>
          <MoreMenu :actions="exam.canManage ? moreActions(exam) : []" />
          <ChevronRight class="size-5 shrink-0 text-ink-3" aria-hidden="true" />
        </div>

        <div v-if="exam.status === 'live'" class="mt-3 flex items-center gap-3">
          <div class="h-1.5 min-w-0 flex-1 overflow-hidden rounded-full bg-sunken">
            <div class="h-full rounded-full bg-brand" :style="{ width: `${percent(exam)}%` }" />
          </div>
          <p class="shrink-0 text-sm text-ink-2 tabular-nums">已交卷 {{ exam.submitted }} / {{ exam.takers }}</p>
        </div>
        <!-- Phone: the main action gets its own line instead of squeezing the name -->
        <div v-if="hasAction(exam)" class="mt-3 sm:hidden"><ExamAction :exam="exam" /></div>
      </li>
    </ul>

    <details v-if="archived.length" class="mt-6">
      <summary class="cursor-pointer px-4 text-[15px] font-medium text-ink-2 hover:text-ink md:px-0">已封存的考試（{{ archived.length }}）</summary>
      <ul class="card mt-3 divide-y divide-line">
        <li v-for="exam in archived" :key="exam.id" class="relative px-5 py-4 transition-colors hover:bg-sunken sm:px-6">
          <div class="flex items-center gap-x-3">
            <div class="min-w-0 flex-1">
              <RouterLink :to="`/exams/${exam.id}`" class="text-base font-medium after:absolute after:inset-0">{{ exam.name }}</RouterLink>
              <p class="mt-0.5 text-sm text-ink-2 tabular-nums">
                {{ when(exam.startAt) }}，{{ exam.submitted }} / {{ exam.takers }} 人交卷。<template v-if="canPurge(exam)">保存期限已過（{{ retainLabel(exam.retainUntil) }}），可以刪除。</template
                ><template v-else>資料保留到 {{ retainLabel(exam.retainUntil) }}。</template>
              </p>
            </div>
            <MoreMenu
              :actions="
                exam.canManage
                  ? [
                      { label: '取消封存', icon: ArchiveRestore, run: () => doUnarchive(exam) },
                      ...(canPurge(exam) ? [{ label: '永久刪除', icon: Trash2, run: () => ((purging = exam), (purgeReason = '')), danger: true }] : []),
                    ]
                  : []
              "
            />
            <ChevronRight class="size-5 shrink-0 text-ink-3" aria-hidden="true" />
          </div>
          <form v-if="purging === exam" class="relative z-10 mt-3 flex flex-wrap gap-2" @submit.prevent="doPurge">
            <input v-model="purgeReason" class="field min-w-0 flex-1 basis-64" maxlength="500" placeholder="刪除的理由（必填，會留在操作紀錄）" aria-label="刪除的理由" />
            <Button size="md" @click="purging = null"><X />取消</Button>
            <Button variant="destructive" size="md" type="submit" :disabled="!purgeReason.trim()"><Trash2 />確定刪除</Button>
          </form>
        </li>
      </ul>
    </details>
    </template>

    <SidePanel v-model:open="formOpen" :docked="false" side="right" :title="editing ? '修改考試' : '建立考試'">
      <form class="flex h-full min-h-0 flex-col" novalidate @submit.prevent="submitForm">
        <header class="flex h-14 shrink-0 items-center border-b border-line pr-2 pl-5">
          <h2 class="text-[17px] font-semibold">{{ editing ? '修改考試' : '建立考試' }}</h2>
          <Button size="icon" class="ml-auto" aria-label="關閉" @click="formOpen = false"><X /></Button>
        </header>
        <div class="thin-scroll min-h-0 flex-1 space-y-4 overflow-y-auto px-5 py-5">
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">考試名稱</span>
            <input v-model="form.name" class="field" placeholder="例如：115 學年度數學科入學測驗" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">使用的題庫</span>
            <select v-model="form.bankId" class="field">
              <option v-for="b in usableBanks" :key="b.id" :value="b.id">{{ b.name || '未命名題庫' }}（{{ b.questions.length }} 題）</option>
            </select>
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">開始時間</span>
            <input v-model="form.start" type="datetime-local" class="field" />
            <span class="mt-1 block text-[13px] text-ink-3">可以先空著，發布前再填。</span>
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">作答時間（分鐘）</span>
            <input v-model.number="form.duration" type="number" min="1" max="600" inputmode="numeric" class="field" />
          </label>
          <FormError v-if="formError">{{ formError }}</FormError>
        </div>
        <footer class="flex shrink-0 justify-end gap-2 border-t border-line px-5 py-3">
          <Button size="md" @click="formOpen = false"><X />取消</Button>
          <Button variant="primary" size="md" type="submit" :disabled="saving"><Save />{{ editing ? '儲存' : '建立草稿' }}</Button>
        </footer>
      </form>
    </SidePanel>
  </div>
</template>
