<script setup lang="ts">
import CardHeader from '@/components/ui/CardHeader.vue'
import EmptyNote from '@/components/ui/EmptyNote.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { computed } from 'vue'
import UnitMembers from '@/components/app/UnitMembers.vue'
import { useAuth } from '@/composables/useAuth'

/** Unit owners manage their unit's members and roles. Accounts are created by system administrators. */
const { user } = useAuth()
const owned = computed(() => (user.value?.units ?? []).filter((u) => u.roles.includes('OWNER')))
</script>

<template>
  <div>
    <PageHeader title="成員" hint="一個人可以有多個角色。對方還沒有帳號時，請系統管理員先建立。" />
    <EmptyNote v-if="!owned.length" class="card">你不是任何單位的負責人。</EmptyNote>
    <div class="space-y-4">
      <section v-for="u in owned" :key="u.id" class="card">
        <CardHeader :title="u.name" />
        <UnitMembers class="border-t border-line px-5 py-4 sm:px-6" :unit="u" />
      </section>
    </div>
  </div>
</template>
