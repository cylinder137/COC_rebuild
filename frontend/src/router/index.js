import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const SiteLayout = () => import('@/layouts/SiteLayout.vue')
const ConsoleLayout = () => import('@/layouts/ConsoleLayout.vue')

const routes = [
  /* ---------------- 站点壳（访客可读） ---------------- */
  {
    path: '/',
    component: SiteLayout,
    children: [
      { path: '', name: 'home', component: () => import('@/views/Home.vue'), meta: { title: '首页' } },
      { path: 'projects/:id', name: 'project-detail', component: () => import('@/views/ProjectDetail.vue'), meta: { title: '项目详情' } },
      { path: 'users/:id', name: 'user-profile', component: () => import('@/views/UserProfile.vue'), meta: { title: '个人主页' } },
      { path: 'dev-apply', name: 'dev-apply', component: () => import('@/views/DevApply.vue'), meta: { title: '开发者申请' } },
      { path: 'forgot', name: 'forgot', component: () => import('@/views/Forgot.vue'), meta: { title: '联系管理员' } }
    ]
  },

  /* ---------------- 登录（单页，按 type 落地） ---------------- */
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { title: '登录' } },

  /* ---------------- 工作台壳（开发者 / 管理员） ---------------- */
  {
    path: '/',
    component: ConsoleLayout,
    meta: { requiresAuth: true, area: 'user' },
    children: [
      { path: 'upload', name: 'upload', component: () => import('@/views/Upload.vue'), meta: { title: '投稿上传', requiresAuth: true } },
      { path: 'me/projects', name: 'me-projects', component: () => import('@/views/MeProjects.vue'), meta: { title: '我的作品', requiresAuth: true } },
      { path: 'me/dashboard', name: 'me-dashboard', component: () => import('@/views/MeDashboard.vue'), meta: { title: '数据看板', requiresAuth: true } },
      { path: 'me/reports', name: 'me-reports', component: () => import('@/views/MeReports.vue'), meta: { title: '我的举报', requiresAuth: true } },
      { path: 'messages', name: 'messages', component: () => import('@/views/Messages.vue'), meta: { title: '消息中心', requiresAuth: true } }
    ]
  },

  /* ---------------- 管理后台 ---------------- */
  {
    path: '/admin',
    component: ConsoleLayout,
    meta: { requiresAuth: true, requiresAdmin: true, area: 'admin' },
    children: [
      { path: '', name: 'admin-home', component: () => import('@/views/admin/AdminStats.vue'), meta: { title: '平台统计', requiresAdmin: true } },
      { path: 'review', name: 'admin-review', component: () => import('@/views/admin/AdminReview.vue'), meta: { title: '审核队列', requiresAdmin: true } },
      { path: 'projects', name: 'admin-projects', component: () => import('@/views/admin/AdminProjects.vue'), meta: { title: '项目管理', requiresAdmin: true } },
      { path: 'projects/:id/storage', name: 'admin-storage', component: () => import('@/views/admin/AdminStorage.vue'), meta: { title: '存储路径', requiresAdmin: true } },
      { path: 'comments', name: 'admin-comments', component: () => import('@/views/admin/AdminComments.vue'), meta: { title: '评论管理', requiresAdmin: true } },
      { path: 'reports', name: 'admin-reports', component: () => import('@/views/admin/AdminReports.vue'), meta: { title: '举报处理', requiresAdmin: true } },
      { path: 'developers', name: 'admin-developers', component: () => import('@/views/admin/AdminDevApplications.vue'), meta: { title: '开发者申请', requiresAdmin: true } },
      { path: 'password-resets', name: 'admin-resets', component: () => import('@/views/admin/AdminPasswordResets.vue'), meta: { title: '找回申请', requiresAdmin: true } },
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/AdminUsers.vue'), meta: { title: '账号管理', requiresAdmin: true } },
      { path: 'stats', name: 'admin-stats', component: () => import('@/views/admin/AdminStats.vue'), meta: { title: '平台统计', requiresAdmin: true } }
    ]
  },

  /* ---------------- 错误页 ---------------- */
  { path: '/403', name: 'forbidden', component: () => import('@/views/Forbidden.vue'), meta: { title: '403' } },
  { path: '/404', name: 'not-found', component: () => import('@/views/NotFound.vue'), meta: { title: '404' } },
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.title) document.title = `${to.meta.title} · 造物集`

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath, reason: '该页面需要登录后访问' } }
  }
  if (to.meta.requiresAdmin && auth.isLoggedIn && !auth.isAdmin) {
    return { path: '/403' }
  }
  return true
})

export default router
