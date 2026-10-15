<template>
  <div class="page">
    <div class="container">
      <AppState :loading="loading && !user" :error="error" :empty="!loading && !error && !user" empty-text="用户不存在" @retry="load">
        <template v-if="user">
          <!-- 头部 -->
          <div class="card head">
            <el-avatar :size="72" :src="user.avatar_path">{{ (user.nickname || 'U').slice(0, 1) }}</el-avatar>
            <div class="grow">
              <div class="row center gap8 wrap">
                <h1 class="u-name">{{ user.nickname || '匿名用户' }}</h1>
                <el-tag v-if="user.type === 2 || user.type === 3" size="small" effect="plain" type="primary">开发者</el-tag>
                <el-tag v-if="user.status === 3" size="small" effect="plain" type="info">用户已注销</el-tag>
              </div>
              <div class="sm dim">
                <span class="num">{{ followers }}</span> 关注者 ·
                <span class="num">{{ user.project_count ?? published.length }}</span> 件作品
              </div>
            </div>
            <el-button
              v-if="!isSelf"
              :type="following ? 'default' : 'primary'"
              @click="toggleFollow"
            >{{ following ? '已关注' : '+ 关注' }}</el-button>
            <router-link v-else to="/me/projects"><el-button>我的作品</el-button></router-link>
          </div>

          <!-- 作品列表 -->
          <h2 class="section-title" style="margin:28px 0 16px">作品</h2>
          <AppState
            :loading="loading"
            :error="error"
            :empty="!loading && !error && !published.length"
            empty-text="还没有公开作品"
            empty-desc="作者还没有发布任何作品。"
            :skeleton-count="6"
            @retry="load"
          >
            <div class="grid">
              <ProjectCard v-for="p in published" :key="p.project_id || p.id" :p="p" />
            </div>
          </AppState>
        </template>
      </AppState>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppState from '@/components/AppState.vue'
import ProjectCard from '@/components/ProjectCard.vue'
import { userApi, interactApi, safe } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { pickList } from '@/utils/format'

const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()

const user = ref(null)
const published = ref([])
const followers = ref(0)
const following = ref(false)
const loading = ref(false)
const error = ref(null)

const isSelf = computed(() => auth.user?.user_id && auth.user.user_id === Number(route.params.id))

async function load() {
  const uid = route.params.id
  loading.value = true
  error.value = null
  const r = await safe(userApi.profile(uid))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  user.value = r.data?.user || r.data
  published.value = pickList(r.data?.projects || r.data?.list || [])
  followers.value = r.data?.followers ?? r.data?.follower_count ?? 0
  const fs = await safe(userApi.followStatus(uid))
  if (fs.ok && fs.data) {
    following.value = !!fs.data.following
    followers.value = fs.data.followers ?? followers.value
  }
}

async function toggleFollow() {
  if (!auth.isLoggedIn) { ui.promptLogin('关注需要登录', route.fullPath); return }
  const r = await safe(interactApi.follow(route.params.id))
  if (!r.ok) { ElMessage.error('操作失败，请稍后重试'); return }
  if (r.data) { following.value = !!r.data.following; followers.value = r.data.followers ?? followers.value }
  else following.value = !following.value
  ElMessage.success(following.value ? '已关注' : '已取消关注')
}

watch(() => route.params.id, load)
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 20px; padding: 24px; flex-wrap: wrap; }
.u-name { font-size: var(--fs-h1); font-weight: 700; margin: 0; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
</style>
