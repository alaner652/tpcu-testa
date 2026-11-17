import { createMemoryHistory, createRouter, createWebHashHistory } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

// Inside an iframe (e.g. a preview page) use memory history so the outer URL is untouched; otherwise hash history.
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
    { path: '/setup', name: 'setup', component: () => import('@/pages/SetupPage.vue'), meta: { public: true } },
    // The manual is open to everyone, signed in or not
    { path: '/help/:chapter?', name: 'help', component: () => import('@/pages/HelpPage.vue'), meta: { public: true } },
    { path: '/about', name: 'about', component: () => import('@/pages/AboutPage.vue'), meta: { public: true } },
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
        { path: 'members', name: 'members', component: () => import('@/pages/MembersPage.vue') },
        { path: 'admin', name: 'admin', component: () => import('@/pages/AdminPage.vue'), meta: { admin: true } },
        { path: 'student', name: 'student', component: () => import('@/pages/StudentExamsPage.vue'), meta: { student: true } },
      ],
    },
    // The bank editor is a focus mode with its own top bar
    { path: '/banks/:id', name: 'editor', component: () => import('@/pages/EditorPage.vue') },
    // Taking the exam is a focus mode too
    { path: '/student/exam/:id', name: 'student-exam', component: () => import('@/pages/StudentExamPage.vue'), meta: { student: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const { user, ready, isAdmin, isStudent } = useAuth()
  // Wait for the backend to say whether someone is signed in
  await ready
  if (!to.meta.public && !user.value) return { name: 'login' }
  if ((to.name === 'login' || to.name === 'setup') && user.value) return { name: 'home' }
  // After signing in with a temporary password, change it before anything else
  if (user.value?.mustChangePassword) return to.name === 'password' ? true : { name: 'password' }
  if (to.name === 'password') return isStudent.value ? { name: 'student' } : true
  // Candidates stay on the candidate side; staff never enter it
  if (!to.meta.public && isStudent.value !== !!to.meta.student) return { name: isStudent.value ? 'student' : 'home' }
  if (to.meta.admin && !isAdmin.value) return { name: 'home' }
  // For administrators who only manage accounts and units, home is the admin page
  if (to.name === 'home' && isAdmin.value && !user.value?.units?.length) return { name: 'admin' }
})
