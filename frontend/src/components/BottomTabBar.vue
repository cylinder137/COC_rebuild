<template>
  <!-- 底部 tab：仅窄屏 / App 内出现，宽屏走顶栏导航 -->
  <nav class="btab" role="tablist" aria-label="主导航">
    <button
      v-for="t in tabs"
      :key="t.key"
      class="btab-item"
      type="button"
      :class="{ on: isActive(t), primary: t.primary }"
      role="tab"
      :aria-selected="isActive(t)"
      @click="go(t)"
    >
      <span class="btab-ico" :class="{ 'ico-fill': t.primary }">
        <el-icon :size="19"><component :is="t.icon" /></el-icon>
      </span>
      <span class="btab-txt">{{ t.label }}</span>
    </button>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/* 顺序按二期反馈第 5 条：全部项目 / html / scratch / mcstructure / 上传项目 / 个人主页
   「上传项目」用主题色背景板强调 */
const tabs = computed(() => [
  { key: 'all', label: '全部项目', icon: 'Grid', query: {} },
  { key: 'html', label: 'HTML', icon: 'Monitor', query: { project_type: 'html' } },
  { key: 'scratch', label: 'Scratch', icon: 'Cpu', query: { project_type: 'scratch' } },
  { key: 'structure', label: 'MC结构', icon: 'Box', query: { project_type: 'structure' } },
  { key: 'upload', label: '上传项目', icon: 'Upload', to: '/upload', primary: true },
  { key: 'me', label: '个人主页', icon: 'User', to: 'me' }
])

function meTarget() {
  if (!auth.isLoggedIn) return '/login'
  return `/users/${auth.user?.user_id || ''}`
}

function isActive(t) {
  if (t.to === 'me') return route.path.startsWith('/users/')
  if (t.to) return route.path === t.to || route.path.startsWith(t.to + '/')
  if (route.path !== '/') return false
  const cur = route.query.project_type || ''
  if (t.key === 'all') return !cur
  return cur === (t.query.project_type || '')
}

function go(t) {
  if (t.to === 'me') { router.push(meTarget()); return }
  if (t.to) { router.push(t.to); return }
  router.push({ path: '/', query: t.query })
}
</script>

<style scoped>
.btab {
  display: none;
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 900;
  background: color-mix(in srgb, var(--c-bg-card) 94%, transparent);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--c-border);
  /* 安全区：底栏背景铺到屏幕最底，内容抬高到导航键之上（二期反馈第 3 条）
     用 --safe-bottom：App 内原生已让位，这里不再重复补 */
  padding-bottom: var(--safe-bottom);
}
.btab-item {
  flex: 1 1 0; min-width: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 3px; height: 56px; border: 0; background: transparent; cursor: pointer;
  color: var(--c-text-3); font-size: 11px; padding: 0 2px;
}
.btab-item.on { color: var(--c-primary); }
.btab-item:active { background: var(--c-bg-sunken); }
.btab-ico { display: flex; align-items: center; justify-content: center; }

/* 「上传项目」主题色强调块 */
.btab-item.primary { color: var(--c-primary); }
.btab-item.primary .btab-ico {
  width: 40px; height: 30px; border-radius: var(--r-md);
  background: var(--c-primary-weak);
  border: 1px solid var(--c-primary-border);
  color: var(--c-primary);
}
.btab-item.primary.on .btab-ico,
.btab-item.primary:active .btab-ico {
  background: var(--c-primary); border-color: var(--c-primary); color: #fff;
}
.btab-item.primary .btab-txt { font-weight: 600; }
.btab-txt { line-height: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }

@media (max-width: 767px) {
  .btab { display: flex; }
}
/* App 内即使横屏平板宽度也用底部 tab */
:global(.shell.is-native) .btab,
:global(.console.is-native) .btab { display: flex; }
</style>
