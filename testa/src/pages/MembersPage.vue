<script setup lang="ts">
import { computed } from 'vue'
import UnitMembers from '@/components/app/UnitMembers.vue'
import { useAuth } from '@/composables/useAuth'

/** Unit owners manage their unit's members and roles. Accounts are created by system administrators. */
const { user } = useAuth()
const owned = computed(() => (user.value?.units ?? []).filter((u) => u.roles.includes('OWNER')))
</script>

<template>
  <div>
    <div class="mb-5 px-4 md:px-0">
      <h1 class="text-[26px] font-bold sm:text-[30px]">成員</h1>
      <p class="mt-1 text-[15px] text-ink-2">
        一個人可以有多個角色。對方還沒有帳號時，請系統管理員先建立。
      </p>
    </div>
    <div v-if="!owned.length" class="card px-5 py-10 text-[15px] text-ink-2 sm:px-10">你不是任何單位的負責人。</div>
    <div class="space-y-3">
      <section v-for="u in owned" :key="u.id" class="card px-5 py-5 sm:px-6">
        <h2 class="mb-3 text-[17px] font-semibold">{{ u.name }}</h2>
        <UnitMembers :unit="u" />
      </section>
    </div>
  </div>
</template>
