<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Plus, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import SidePanel from '@/components/ui/SidePanel.vue'
import { useAuth } from '@/composables/useAuth'
import { useBanks } from '@/composables/useBanks'
import { useConfirm } from '@/composables/useConfirm'
import { EXAM_STATUS, MODE_LABEL, toLocalInput, useExams, when, type Exam, type ExamMode } from '@/composables/useExams'

const { exams, loading, loadError, load, create, update, publish, withdraw, end, remove } = useExams()
const { banks } = useBanks()
const { user } = useAuth()
const { confirm } = useConfirm()

/** 我是負責人的單位：只有在這些單位可以建立考試，題庫也只能選這些單位的。 */
const ownedUnits = computed(() => (user.value?.units ?? []).filter((u) => u.roles.includes('OWNER')).map((u) => u.id))
const usableBanks = computed(() => banks.value.filter((b) => b.unitId != null && ownedUnits.value.includes(b.unitId)))
const fail = (e: unknown) => toast.error((e as Error).message)

// 建立、修改考試
const formOpen = ref(false)
const editing = ref<Exam | null>(null)
const form = reactive({ name: '', bankId: '', start: '', duration: 60, mode: 'CLASSROOM' as ExamMode })
const formError = ref('')
const saving = ref(false)

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

const percent = (exam: Exam) => (exam.takers ? Math.round((exam.submitted / exam.takers) * 100) : 0)
</script>

<template>
  <div>
    <div class="mb-5 flex items-center justify-between gap-3 px-4 md:px-0">
      <h1 class="text-[26px] font-bold sm:text-[30px]">考試</h1>
      <Button v-if="ownedUnits.length" variant="primary" size="md" class="rounded-full" @click="openForm()"><Plus />建立考試</Button>
    </div>

    <div v-if="loadError" class="card flex items-center gap-3 px-5 py-4 text-[15px] text-grade sm:px-6" role="alert">
      <span class="min-w-0 flex-1">{{ loadError }}</span>
      <Button variant="outline" class="rounded-full" @click="load">重試</Button>
    </div>

    <div v-else-if="loading" class="space-y-3 px-4 md:px-0">
      <Skeleton v-for="n in 2" :key="n" class="h-28 rounded-card" />
    </div>

    <div v-else-if="exams.length === 0" class="card px-5 py-12 sm:px-10">
      <h2 class="text-2xl font-bold">還沒有考試</h2>
      <p v-if="!ownedUnits.length" class="mt-2 text-[15px] text-ink-2">單位負責人建立考試後，會出現在這裡。</p>
      <p v-else-if="!usableBanks.length" class="mt-2 text-[15px] text-ink-2">建立考試需要一份題庫。請出題的老師先建立題庫。</p>
      <template v-else>
        <p class="mt-2 mb-6 text-[15px] text-ink-2">選一份題庫、設定時間，再匯入考生名單。</p>
        <Button variant="primary" size="md" class="rounded-full" @click="openForm()"><Plus />建立考試</Button>
      </template>
    </div>

    <ul v-else class="space-y-3">
      <li v-for="exam in exams" :key="exam.id" class="card px-5 py-5 sm:px-6">
        <div class="flex flex-wrap items-start gap-x-4 gap-y-3">
          <div class="min-w-0 flex-1 basis-64">
            <p class="flex flex-wrap items-center gap-2">
              <span class="rounded-full px-2.5 py-0.5 text-[13px] font-medium" :class="EXAM_STATUS[exam.status].class">
                {{ EXAM_STATUS[exam.status].label }}
              </span>
              <span class="text-lg font-semibold">{{ exam.name }}</span>
            </p>
            <p class="mt-1.5 text-sm text-ink-2">題庫：{{ exam.bankName ?? '題庫已刪除，請重新選擇' }}</p>
            <p class="text-sm text-ink-2">{{ when(exam.startAt) }}，作答 {{ exam.duration }} 分鐘，{{ MODE_LABEL[exam.mode] }}</p>
          </div>
          <div class="flex shrink-0 flex-wrap items-center gap-2">
            <RouterLink
              :to="`/exams/${exam.id}`"
              class="inline-flex h-9 items-center rounded-full border border-line-strong px-3 text-sm font-medium transition-colors hover:border-brand hover:text-brand"
            >
              考生與監考
            </RouterLink>
            <template v-if="exam.canManage">
              <template v-if="exam.status === 'draft'">
                <Button variant="outline" class="rounded-full" @click="openForm(exam)">修改</Button>
                <Button variant="danger" class="rounded-full" @click="doRemove(exam)">刪除</Button>
                <Button variant="primary" class="rounded-full" @click="doPublish(exam)">發布</Button>
              </template>
              <Button v-else-if="exam.status === 'scheduled'" variant="outline" class="rounded-full" @click="doWithdraw(exam)">
                取消發布
              </Button>
              <Button v-else-if="exam.status === 'live'" variant="outline" class="rounded-full hover:border-grade hover:text-grade" @click="doEnd(exam)">
                結束考試
              </Button>
            </template>
          </div>
        </div>

        <div v-if="exam.status === 'live'" class="mt-4">
          <p class="flex justify-between text-sm text-ink-2 tabular-nums">
            <span>已交卷 {{ exam.submitted }} / {{ exam.takers }} 人</span>
            <span>{{ percent(exam) }}%</span>
          </p>
          <div class="mt-1.5 h-1.5 overflow-hidden rounded-full bg-sunken">
            <div class="h-full rounded-full bg-brand" :style="{ width: `${percent(exam)}%` }" />
          </div>
        </div>
        <p v-else-if="exam.status === 'ended'" class="mt-3 text-sm text-ink-2 tabular-nums">{{ exam.submitted }} / {{ exam.takers }} 人交卷</p>
        <p v-else class="mt-3 text-sm text-ink-2">名單上有 {{ exam.takers }} 位考生</p>
      </li>
    </ul>

    <SidePanel v-model:open="formOpen" :docked="false" side="right" :title="editing ? '修改考試' : '建立考試'">
      <form class="flex h-full min-h-0 flex-col" novalidate @submit.prevent="submitForm">
        <header class="flex h-14 shrink-0 items-center border-b border-line pr-2 pl-5">
          <h2 class="text-[17px] font-semibold">{{ editing ? '修改考試' : '建立考試' }}</h2>
          <Button size="icon" class="ml-auto rounded-full" aria-label="關閉" @click="formOpen = false"><X /></Button>
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
            <span class="mt-1 block text-[13px] text-ink-3">可以先留空，發布前再設定。</span>
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">作答時間（分鐘）</span>
            <input v-model.number="form.duration" type="number" min="1" max="600" inputmode="numeric" class="field" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium">施測方式</span>
            <select v-model="form.mode" class="field">
              <option value="CLASSROOM">電腦教室（監考在場）</option>
              <option value="ONLINE">純線上（考生用自己的電腦）</option>
            </select>
          </label>
          <p v-if="formError" class="text-sm text-grade" role="alert">{{ formError }}</p>
        </div>
        <footer class="flex shrink-0 justify-end gap-2 border-t border-line px-5 py-3">
          <Button size="md" class="rounded-full" @click="formOpen = false">取消</Button>
          <Button variant="primary" size="md" class="rounded-full" type="submit" :disabled="saving">{{ editing ? '儲存' : '建立草稿' }}</Button>
        </footer>
      </form>
    </SidePanel>
  </div>
</template>
