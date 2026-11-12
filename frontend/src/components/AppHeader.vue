<template>
  <header class="hd" :class="{ scrolled, native: isApp }">
    <div class="container hd-inner">
      <div class="hd-left">
        <!-- App 内：只要一个返回键，标题彻底去掉（二期反馈第 4 条） -->
        <button v-if="isApp" class="navbtn" title="返回上一页" aria-label="返回上一页" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
        </button>

        <!-- 网页端：保留品牌与导航 -->
        <template v-else>
          <button class="navbtn" title="返回上一页" aria-label="返回上一页" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <button class="navbtn" title="回到首页" aria-label="回到首页" @click="goHome">
            <el-icon><HomeFilled /></el-icon>
          </button>
          <router-link to="/" class="brand">
            <span class="logo-tile">造</span>
            <span class="brand-name">造物集</span>
          </router-link>
          <span v-if="isConsole" class="zone-tag">
            {{ auth.isAdmin && isAdminArea ? '管理台' : '创作台' }}
          </span>
          <span v-if="isUserZone" class="zone-tag zone-user">用户内容区</span>
        </template>
      </div>

      <!-- App 内：顶栏主体就是搜索框 -->
      <div v-if="isApp" class="hd-search-app">
        <el-icon class="hs-ico"><Search /></el-icon>
        <input v-model="kw" placeholder="搜索作品、作者、标签…" @keyup.enter="doSearch" />
        <button v-if="kw" class="hs-clear" aria-label="清空" @click="kw = ''">
          <el-icon><Close /></el-icon>
        </button>
        <button class="hs-go" aria-label="搜索" @click="doSearch">
          <el-icon><Search /></el-icon>
        </button>
      </div>

      <nav v-else class="hd-nav hide-md">
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
        <el-dropdown trigger="hover" @command="goBiz">
          <span class="nav-item">其他业务 <el-icon class="xs"><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="https://tutor.collectionofcreations.uk">造物集家教平台</el-dropdown-item>
              <el-dropdown-item command="https://websocket.collectionofcreations.uk">WebSocket 跨端同步</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <a class="nav-item nav-download" href="/downloads/coc-app-latest.apk" download>
          <el-icon class="xs"><Download /></el-icon> 下载 App
        </a>
      </nav>

      <div class="hd-right">
        <a
          v-if="!isApp"
          class="icon-btn only-sm"
          href="/downloads/coc-app-latest.apk"
          download
          title="下载 App"
          aria-label="下载 App"
        >
          <el-icon><Download /></el-icon>
        </a>
        <div v-if="!isApp" class="hd-search hide-sm" @click="focusSearch">
          <el-icon><Search /></el-icon>
          <input v-model="kw" placeholder="搜索作品、作者…" @keyup.enter="doSearch" />
        </div>
        <el-tooltip content="切换主题" placement="bottom">
          <button class="icon-btn" @click="theme.toggle()">
            <el-icon><component :is="isDark ? 'Moon' : 'Sunny'" /></el-icon>
          </button>
        </el-tooltip>

        <template v-if="auth.isLoggedIn">
          <router-link v-if="!isApp" to="/messages" class="icon-btn hide-sm" title="消息">
            <el-badge :value="ui.unreadTotal" :hidden="!ui.unreadTotal" :max="99">
              <el-icon><Bell /></el-icon>
            </el-badge>
          </router-link>
          <router-link v-if="!isApp && auth.isDeveloper" to="/upload" class="hide-sm">
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
          <router-link v-if="!isApp" to="/dev-apply" class="hide-sm">
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
import { IS_APP, APP_BUILD } from '@/utils/platform'

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
/** App 内（Capacitor WebView）时隐藏兄弟站点入口与下载入口，顶栏只留搜索 */
const isApp = IS_APP
/** App 里的构建标记，用来一眼确认装的是不是新包 */
const buildTag = APP_BUILD

/** 回退上一步；没有历史时回首页 */
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}
function goHome() {
  if (route.path !== '/') router.push('/')
  else window.scrollTo({ top: 0, behavior: 'smooth' })
}
const isAdminArea = computed(() => route.path.startsWith('/admin') || route.meta.area === 'admin')
const isConsole = computed(
  () => isAdminArea.value || ['/me', '/upload', '/messages'].some((p) => route.path.startsWith(p))
)

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
  const q = kw.value.trim()
  if (!q) return
  router.push({ path: '/', query: { keyword: q } })
  if (isApp) kw.value = ''
}
function goCategory(cid) {
  router.push({ path: '/', query: cid ? { category_id: cid } : {} })
}
/** 其他业务：兄弟站点在别的子域名，直接新窗口打开，不占本站路由 */
function goBiz(url) {
  window.open(url, '_blank', 'noopener')
}
function onCommand(cmd) {
  if (cmd === 'logout') auth.logout()
  else router.push(cmd)
}
</script>

<style scoped>
.hd {
  position: sticky; top: 0; z-index: 100;
  /* 安全区：App/刘海屏下把内容推到状态栏下方，背景仍铺满状态栏区域
     （用 --safe-top：App 内原生已让位时为 0，避免双重留白） */
  height: calc(var(--h-header) + var(--safe-top));
  padding-top: var(--safe-top);
  background: color-mix(in srgb, var(--c-bg-page) 82%, transparent);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid transparent;
  transition: border-color var(--dur-base);
}
.hd.scrolled { border-bottom-color: var(--c-border); box-shadow: var(--sh-3); }
.hd-inner { display: flex; align-items: center; height: var(--h-header); gap: 16px; }
.hd-left { display: flex; align-items: center; gap: 10px; flex: 0 0 auto; }
.navbtn {
  width: 30px; height: 30px; border-radius: var(--r-md); flex: 0 0 auto;
  border: 1px solid var(--c-border); background: var(--c-bg-card); color: var(--c-text-2);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
}
.navbtn:hover { color: var(--c-primary); border-color: var(--c-primary-border); }
.brand { display: flex; align-items: center; gap: 10px; color: var(--c-text-1); }
.brand-name { font-weight: 700; font-size: 17px; letter-spacing: .02em; }
.build-tag {
  font-size: 10px; font-weight: 600; padding: 1px 6px; border-radius: var(--r-pill);
  background: var(--c-bg-sunken); color: var(--c-text-3); border: 1px solid var(--c-border);
  letter-spacing: .04em;
}
.zone-tag {
  font-size: var(--fs-xs); padding: 2px 8px; border-radius: var(--r-pill);
  background: var(--c-primary-weak); color: var(--c-primary); border: 1px solid var(--c-primary-border);
}
.zone-user { background: rgba(168, 85, 247, .14); color: var(--c-accent-2); border-color: rgba(168, 85, 247, .35); }
.hd-nav { display: flex; align-items: center; gap: 18px; margin-left: 12px; }
.nav-item { color: var(--c-text-2); font-size: var(--fs-body); cursor: pointer; }
.nav-item:hover { color: var(--c-primary); }
.nav-download {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 12px; border-radius: var(--r-pill);
  background: var(--c-primary-weak); color: var(--c-primary);
  border: 1px solid var(--c-primary-border); font-weight: 500;
}
.nav-download:hover { background: var(--c-primary); color: #fff; border-color: var(--c-primary); }
.hd-right { margin-left: auto; display: flex; align-items: center; gap: 10px; flex: 0 0 auto; }
.hd-search {
  display: flex; align-items: center; gap: 8px; height: 36px; padding: 0 14px;
  border-radius: var(--r-pill); background: var(--c-bg-sunken);
  border: 1px solid var(--c-border); color: var(--c-text-3); min-width: 200px;
}
.hd-search input {
  border: none; outline: none; background: transparent; color: var(--c-text-1);
  font-size: var(--fs-sm); width: 150px;
}

/* App 顶栏搜索框：占满中间剩余宽度 */
.hd-search-app {
  flex: 1 1 auto; min-width: 0; display: flex; align-items: center; gap: 6px;
  height: 36px; padding: 0 4px 0 12px;
  border-radius: var(--r-pill); background: var(--c-bg-sunken);
  border: 1px solid var(--c-border);
}
.hd-search-app .hs-ico { color: var(--c-text-3); flex: 0 0 auto; }
.hd-search-app input {
  flex: 1 1 auto; min-width: 0; border: none; outline: none; background: transparent;
  color: var(--c-text-1); font-size: var(--fs-sm);
}
.hd-search-app input::placeholder { color: var(--c-text-3); }
.hs-clear, .hs-go {
  flex: 0 0 auto; border: 0; background: transparent; color: var(--c-text-3);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border-radius: var(--r-pill);
}
.hs-clear:hover { color: var(--c-text-1); background: var(--c-bg-hover); }
.hs-go {
  width: 30px; height: 30px; background: var(--c-primary); color: #fff;
}
.hs-go:hover { background: var(--c-primary-hover); }

.icon-btn {
  width: 34px; height: 34px; border-radius: var(--r-md); border: 1px solid var(--c-border);
  background: var(--c-bg-card); color: var(--c-text-2); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.icon-btn:hover { color: var(--c-primary); border-color: var(--c-primary-border); }
.avatar-btn { cursor: pointer; display: flex; }
/* 窄屏时顶栏导航整体隐藏，下载入口改由 hd-right 的图标按钮接管（手机才是装 App 的主要场景） */
.only-sm { display: none; }
@media (max-width: 991px) {
  .only-sm { display: flex; }
}
/* App 内顶栏更紧凑 */
.hd.native .hd-inner { gap: 8px; }
</style>
