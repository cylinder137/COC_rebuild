<template>
  <router-view v-slot="{ Component }">
    <component :is="Component" />
  </router-view>

  <!-- 全局登录引导：访客点任何互动都会弹它（不静默失败） -->
  <el-dialog
    :model-value="ui.loginPrompt.visible"
    title="需要登录"
    width="380px"
    align-center
    @close="ui.closeLoginPrompt()"
  >
    <div class="lp-body">
      <div class="lp-icon"><el-icon :size="26"><Lock /></el-icon></div>
      <p class="lp-reason">{{ ui.loginPrompt.reason }}</p>
      <p class="xs dim">访客可以自由浏览和运行作品；评论、点赞、收藏、举报、投稿等互动需要登录。</p>
    </div>
    <template #footer>
      <el-button @click="ui.closeLoginPrompt()">再逛逛</el-button>
      <el-button type="primary" @click="goLogin">去登录</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUiStore, useThemeStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'

const ui = useUiStore()
const theme = useThemeStore()
const auth = useAuthStore()
const router = useRouter()

onMounted(() => {
  theme.apply()
  // 静默校验一次登录态（后端没起来也不会打断页面）
  auth.fetchMe()
})

function goLogin() {
  const redirect = ui.loginPrompt.redirect || router.currentRoute.value.fullPath
  ui.closeLoginPrompt()
  router.push({ path: '/login', query: { redirect } })
}
</script>

<style scoped>
.lp-body { text-align: center; padding: 4px 0 8px; }
.lp-icon {
  width: 52px; height: 52px; margin: 0 auto 12px; border-radius: var(--r-pill);
  background: var(--c-primary-weak); color: var(--c-primary);
  display: flex; align-items: center; justify-content: center;
}
.lp-reason { margin: 0 0 8px; font-size: var(--fs-body-l); font-weight: 600; }
</style>
