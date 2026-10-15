<template>
  <div class="console">
    <AppHeader />
    <div class="console-body">
      <aside class="sidebar" :class="{ collapsed: ui.sidebarCollapsed }">
        <el-menu
          :default-active="activePath"
          :collapse="ui.sidebarCollapsed"
          :collapse-transition="false"
          router
          class="side-menu"
        >
          <template v-for="group in menu" :key="group.label">
            <div v-if="!ui.sidebarCollapsed" class="group-label">{{ group.label }}</div>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>
                <span class="menu-text">{{ item.title }}</span>
                <el-badge v-if="item.badge" :value="item.badge" class="menu-badge" />
              </template>
            </el-menu-item>
          </template>
        </el-menu>
        <div class="collapse-btn" @click="ui.toggleSidebar()">
          <el-icon><component :is="ui.sidebarCollapsed ? 'DArrowRight' : 'DArrowLeft'" /></el-icon>
        </div>
      </aside>
      <section class="console-content">
        <div class="console-inner">
          <router-view v-slot="{ Component }">
            <component :is="Component" />
          </router-view>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { useUiStore } from '@/stores/ui'
import { imApi, safe } from '@/api'

const ui = useUiStore()
const route = useRoute()
const activePath = computed(() => route.path)

const userMenu = [
  { label: '创作', items: [
    { title: '我的作品', path: '/me/projects', icon: 'Files' },
    { title: '投稿上传', path: '/upload', icon: 'Upload' }
  ]},
  { label: '数据', items: [
    { title: '数据看板', path: '/me/dashboard', icon: 'DataLine' }
  ]},
  { label: '社交', items: [
    { title: '消息中心', path: '/messages', icon: 'ChatDotRound' }
  ]},
  { label: '账号', items: [
    { title: '我的举报', path: '/me/reports', icon: 'Warning' },
    { title: '联系管理员', path: '/forgot', icon: 'Service' }
  ]}
]

const adminMenu = [
  { label: '审核', items: [
    { title: '审核队列', path: '/admin/review', icon: 'Checked' }
  ]},
  { label: '内容', items: [
    { title: '项目管理', path: '/admin/projects', icon: 'Folder' },
    { title: '评论管理', path: '/admin/comments', icon: 'ChatLineSquare' },
    { title: '举报处理', path: '/admin/reports', icon: 'Warning' }
  ]},
  { label: '用户', items: [
    { title: '开发者申请', path: '/admin/developers', icon: 'UserFilled' },
    { title: '找回申请', path: '/admin/password-resets', icon: 'Key' },
    { title: '账号管理', path: '/admin/users', icon: 'User' }
  ]},
  { label: '平台', items: [
    { title: '平台统计', path: '/admin/stats', icon: 'DataLine' }
  ]}
]

const menu = computed(() => (route.meta.area === 'admin' ? adminMenu : userMenu))

onMounted(async () => {
  const r = await safe(imApi.unread())
  if (r.ok && r.data) ui.unreadTotal = r.data.total || 0
})
</script>

<style scoped>
.console { display: flex; flex-direction: column; min-height: 100vh; }
.console-body { display: flex; flex: 1; min-height: 0; }
.sidebar {
  width: var(--w-sidebar); flex: 0 0 var(--w-sidebar);
  background: var(--c-bg-subtle); border-right: 1px solid var(--c-border);
  padding: 12px 0; position: relative; transition: width var(--dur-base) var(--ease-out);
}
.sidebar.collapsed { width: 64px; flex-basis: 64px; }
.side-menu { border: none; background: transparent; }
.group-label {
  padding: 14px 20px 6px; font-size: var(--fs-xs); color: var(--c-text-3);
  letter-spacing: .06em; text-transform: uppercase;
}
.menu-text { margin-right: 6px; }
.collapse-btn {
  position: absolute; bottom: 12px; left: 50%; transform: translateX(-50%);
  width: 34px; height: 26px; border-radius: var(--r-sm); border: 1px solid var(--c-border);
  display: flex; align-items: center; justify-content: center;
  color: var(--c-text-2); cursor: pointer; background: var(--c-bg-card);
}
.collapse-btn:hover { color: var(--c-primary); border-color: var(--c-primary-border); }
.console-content { flex: 1; min-width: 0; background: var(--c-bg-page); }
.console-inner { padding: 24px; max-width: 1400px; }
@media (max-width: 767px) {
  .sidebar { display: none; }
  .console-inner { padding: 16px; }
}
</style>
