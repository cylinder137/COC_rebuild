<template>
  <header class="hd" :class="{ scrolled }">
    <div class="container hd-inner">
      <div class="hd-left">
        <router-link to="/" class="brand">
          <span class="logo-tile">造</span>
          <span class="brand-name">造物集</span>
        </router-link>
        <span v-if="isConsole" class="zone-tag">
          {{ auth.isAdmin && isAdminArea ? '管理台' : '创作台' }}
        </span>
        <span v-if="isUserZone" class="zone-tag zone-user">用户内容区</span>
      </div>

      <nav class="hd-nav hide-md">
        <router-link to="/" class="nav-item">发现</router-link>
        <el-dropdown trigger="hover" @command="goCategory">
          <span class="nav-item">分类 <el-icon class="xs"><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :command="''">全部</el-dropdown-item>
              <el-dropdown-item v-for="c in categories" :key="c.category_id" :command="c.category_id">
                {{ c.name }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </nav>

      <div class="hd-right">
        <div class="hd-search hide-sm" @click="focusSearch">
          <el-icon><Search /></el-icon>
          <input v-model="kw" placeholder="搜索作品、作者…" @keyup.enter="doSearch" />
        </div>
        <el-tooltip content="切换主题" placement="bottom">
          <button class="icon-btn" @click="theme.toggle()">
            <el-icon><component :is="isDark ? 'Moon' : 'Sunny'" /></el-icon>
          </button>
        </el-tooltip>

        <template v-if="auth.isLoggedIn">
          <router-link to="/messages" class="icon-btn hide-sm" title="消息">
            <el-badge :value="ui.unreadTotal" :hidden="!ui.unreadTotal" :max="99">
              <el-icon><Bell /></el-icon>
            </el-badge>
          </router-link>
          <router-link v-if="auth.isDeveloper" to="/upload" class="hide-sm">
            <el-button type="primary" size="small" round>
              <el-icon><Upload /></el-icon>&nbsp;投稿
            </el-button>
          </router-link>
          <el-dropdown trigger="click" @command="onCommand">
            <span class="avatar-btn">
              <el-avatar :size="30" :src="auth.user?.avatar_path">
                {{ (auth.displayName || 'U').slice(0, 1) }}
              </el-avatar>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :command="`/users/${auth.user?.user_id}`">个人主页</el-dropdown-item>
                <el-dropdown-item v-if="auth.isDeveloper" command="/me/projects">我的作品</el-dropdown-item>
                <el-dropdown-item v-if="auth.isDeveloper" command="/me/dashboard">数据看板</el-dropdown-item>
                <el-dropdown-item v-if="auth.isAdmin" command="/admin" divided>管理后台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>

        <template v-else>
          <router-link to="/login"><el-button size="small" round>登录</el-button></router-link>
          <router-link to="/dev-apply" class="hide-sm">
            <el-button size="small" type="primary" round>注册开发者</el-button>
          </router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore, useUiStore } from '@/stores/ui'
import { categoryApi, safe, IS_USER_ZONE } from '@/api'

const auth = useAuthStore()
const theme = useThemeStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()

const scrolled = ref(false)
const kw = ref('')
const categories = ref([])
const isDark = computed(() => theme.isDark)
const isUserZone = IS_USER_ZONE
const isAdminArea = computed(() => route.path.startsWith('/admin') || route.meta.area === 'admin')
const isConsole = computed(
  () => isAdminArea.value || ['/me', '/upload', '/messages'].some((p) => route.path.startsWith(p))
)
const searchInput = ref(null)

function onScroll() { scrolled.value = window.scrollY > 8 }
onMounted(async () => {
  window.addEventListener('scroll', onScroll, { passive: true })
  const r = await safe(categoryApi.list())
  if (r.ok && Array.isArray(r.data)) categories.value = r.data
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))

function focusSearch() {
  const el = document.querySelector('.hd-search input')
  el?.focus()
}
function doSearch() {
  if (!kw.value.trim()) return
  router.push({ path: '/', query: { keyword: kw.value.trim() } })
}
function goCategory(cid) {
  router.push({ path: '/', query: cid ? { category_id: cid } : {} })
}
function onCommand(cmd) {
  if (cmd === 'logout') auth.logout()
  else router.push(cmd)
}
</script>

<style scoped>
.hd {
  position: sticky; top: 0; z-index: 100; height: var(--h-header);
  background: color-mix(in srgb, var(--c-bg-page) 82%, transparent);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid transparent;
  transition: height var(--dur-base) var(--ease-out), border-color var(--dur-base);
}
.hd.scrolled { border-bottom-color: var(--c-border); box-shadow: var(--sh-3); }
.hd-inner { display: flex; align-items: center; height: 100%; gap: 16px; }
.hd-left { display: flex; align-items: center; gap: 10px; }
.brand { display: flex; align-items: center; gap: 10px; color: var(--c-text-1); }
.brand-name { font-weight: 700; font-size: 17px; letter-spacing: .02em; }
.zone-tag {
  font-size: var(--fs-xs); padding: 2px 8px; border-radius: var(--r-pill);
  background: var(--c-primary-weak); color: var(--c-primary); border: 1px solid var(--c-primary-border);
}
.zone-user { background: rgba(168, 85, 247, .14); color: var(--c-accent-2); border-color: rgba(168, 85, 247, .35); }
.hd-nav { display: flex; align-items: center; gap: 18px; margin-left: 12px; }
.nav-item { color: var(--c-text-2); font-size: var(--fs-body); cursor: pointer; }
.nav-item:hover { color: var(--c-primary); }
.hd-right { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.hd-search {
  display: flex; align-items: center; gap: 8px; height: 36px; padding: 0 14px;
  border-radius: var(--r-pill); background: var(--c-bg-sunken);
  border: 1px solid var(--c-border); color: var(--c-text-3); min-width: 200px;
}
.hd-search input {
  border: none; outline: none; background: transparent; color: var(--c-text-1);
  font-size: var(--fs-sm); width: 150px;
}
.icon-btn {
  width: 34px; height: 34px; border-radius: var(--r-md); border: 1px solid var(--c-border);
  background: var(--c-bg-card); color: var(--c-text-2); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.icon-btn:hover { color: var(--c-primary); border-color: var(--c-primary-border); }
.avatar-btn { cursor: pointer; display: flex; }
</style>
