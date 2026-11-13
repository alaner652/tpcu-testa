import { createMemoryHistory, createRouter, createWebHashHistory } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

// 嵌在 iframe（例如預覽頁）時使用記憶體路由，避免改動外層網址；獨立開啟時使用 hash 路由。
const embedded = (() => {
  try {
    return window.self !== window.top
  } catch {
    return true
  }
})()

export const router = createRouter({
  history: embedded ? createMemoryHistory() : createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/pages/LoginPage.vue'), meta: { public: true } },
    { path: '/password', name: 'password', component: () => import('@/pages/PasswordPage.vue') },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', name: 'home', component: () => import('@/pages/HomePage.vue') },
        { path: 'banks', name: 'banks', component: () => import('@/pages/BanksPage.vue') },
        { path: 'exams', name: 'exams', component: () => import('@/pages/ExamsPage.vue') },
        { path: 'exams/:id', name: 'exam', component: () => import('@/pages/ExamDetailPage.vue') },
        { path: 'grading', name: 'grading', component: () => import('@/pages/GradingPage.vue') },
        { path: 'admin', name: 'admin', component: () => import('@/pages/AdminPage.vue'), meta: { admin: true } },
        { path: 'student', name: 'student', component: () => import('@/pages/StudentExamsPage.vue'), meta: { student: true } },
      ],
    },
    // 題庫編輯器是專注模式，有自己的頂部列
    { path: '/banks/:id', name: 'editor', component: () => import('@/pages/EditorPage.vue') },
    // 考生作答同樣是專注模式
    { path: '/student/exam/:id', name: 'student-exam', component: () => import('@/pages/StudentExamPage.vue'), meta: { student: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const { user, ready, isAdmin, isStudent } = useAuth()
  // 先等後端回答「目前有沒有登入」
  await ready
  if (!to.meta.public && !user.value) return { name: 'login' }
  if (to.name === 'login' && user.value) return { name: 'home' }
  // 臨時密碼登入後，必須先換密碼才能做其他事
  if (user.value?.mustChangePassword) return to.name === 'password' ? true : { name: 'password' }
  if (to.name === 'password') return isStudent.value ? { name: 'student' } : true
  // 考生只能進考生端；教師與管理員不進考生端
  if (to.name !== 'login' && isStudent.value !== !!to.meta.student) return { name: isStudent.value ? 'student' : 'home' }
  if (to.meta.admin && !isAdmin.value) return { name: 'home' }
  // 只管帳號與單位的系統管理員，首頁就是後台管理
  if (to.name === 'home' && isAdmin.value && !user.value?.units?.length) return { name: 'admin' }
})
