<script setup lang="ts">
import { ref, watch } from 'vue'
import { Copy, Download, X } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import Button from '@/components/ui/Button.vue'

/** Temporary passwords just issued. They are shown only this once, so they can be copied or saved right here. */
export interface IssuedPassword {
  name: string
  email: string
  password: string
}
const props = defineProps<{ items: IssuedPassword[] }>()
const emit = defineEmits<{ close: [] }>()

const copied = ref(false)
watch(
  () => props.items,
  () => (copied.value = false),
)

const table = () => [['姓名', 'Email', '臨時密碼'], ...props.items.map((i) => [i.name, i.email, i.password])]

async function copy() {
  // One account: just the password. Several: a table that pastes into Excel.
  const text =
    props.items.length === 1
      ? props.items[0].password
      : table()
          .map((r) => r.join('\t'))
          .join('\n')
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    toast.success(props.items.length === 1 ? '已複製臨時密碼' : `已複製 ${props.items.length} 組帳號密碼`)
  } catch {
    toast.error('無法複製，請手動抄寫')
  }
}

function download() {
  const quote = (v: string) => `"${v.replace(/"/g, '""')}"`
  const csv =
    '﻿' +
    table()
      .map((r) => r.map(quote).join(','))
      .join('\r\n')
  const link = document.createElement('a')
  link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  link.download = `臨時密碼-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}
</script>

<template>
  <div class="bg-marker/40 px-5 py-4 sm:px-6" role="status">
    <div class="flex items-start gap-3">
      <p class="min-w-0 flex-1 text-[15px] font-medium">
        <template v-if="items.length === 1">{{ items[0].name }}（{{ items[0].email }}）的臨時密碼</template>
        <template v-else>已建立 {{ items.length }} 個帳號，以下是各自的臨時密碼</template>
      </p>
      <Button variant="ghost" size="icon" aria-label="關閉" @click="emit('close')"><X /></Button>
    </div>
    <div v-if="items.length === 1" class="mt-2 flex flex-wrap items-center gap-2">
      <code class="rounded-control bg-paper px-3 py-1.5 text-lg font-semibold tracking-wider select-all">{{ items[0].password }}</code>
      <Button variant="outline" @click="copy"><Copy />{{ copied ? '已複製' : '複製' }}</Button>
    </div>
    <template v-else>
      <div class="mt-3 max-h-72 overflow-auto rounded-control bg-paper">
        <table class="w-full text-sm">
          <thead class="sticky top-0 bg-paper text-left text-ink-2">
            <tr>
              <th class="px-3 py-2 font-medium">姓名</th>
              <th class="px-3 py-2 font-medium">Email</th>
              <th class="px-3 py-2 font-medium">臨時密碼</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-line">
            <tr v-for="i in items" :key="i.email">
              <td class="px-3 py-1.5">{{ i.name }}</td>
              <td class="px-3 py-1.5 break-all">{{ i.email }}</td>
              <td class="px-3 py-1.5 font-mono font-semibold tracking-wider select-all">
                {{ i.password }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="mt-3 flex flex-wrap gap-2">
        <Button variant="outline" @click="copy"><Copy />{{ copied ? '已複製' : '複製全部' }}</Button>
        <Button variant="outline" @click="download"><Download />下載 CSV</Button>
      </div>
    </template>
    <p class="mt-2 text-sm text-ink-2">
      密碼只會顯示這一次，請親自交給對方{{ items.length > 1 ? '，或下載後妥善保存，發完就刪掉檔案' : '' }}。對方登入後必須先換成自己的密碼。
    </p>
  </div>
</template>
