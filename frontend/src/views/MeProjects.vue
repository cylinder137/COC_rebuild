<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">我的作品</h2>
        <div class="sm dim">状态徽标一目了然：待审 / 已发布 / 已下架 / 已拒绝</div>
      </div>
      <router-link to="/upload"><el-button type="primary"><el-icon><Upload /></el-icon>&nbsp;新建投稿</el-button></router-link>
    </div>

    <el-radio-group v-model="status" class="mb16" @change="load">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button value="0">草稿</el-radio-button>
      <el-radio-button value="1">待审</el-radio-button>
      <el-radio-button value="2">已发布</el-radio-button>
      <el-radio-button value="3">已下架</el-radio-button>
      <el-radio-button value="4">已拒绝</el-radio-button>
    </el-radio-group>

    <AppState
      :loading="loading && !list.length"
      :error="error"
      :empty="!loading && !error && !list.length"
      empty-text="还没有作品"
      empty-desc="投稿你的第一个小造物，Scratch / 结构文件免审直发。"
      :skeleton-count="6"
      @retry="load"
    >
      <template #action>
        <router-link to="/upload"><el-button type="primary">去投稿</el-button></router-link>
      </template>
    </AppState>

    <div v-if="list.length" class="grid">
      <div v-for="p in list" :key="p.project_id || p.id" class="card">
        <ProjectCard :p="p" :show-status="true" :show-reason="true" />
        <div class="ops">
          <router-link :to="`/projects/${p.project_id || p.id}`">
            <el-button size="small" text>查看</el-button>
          </router-link>
          <el-button v-if="p.status === 2" size="small" text @click="goDashboard(p)">看数据</el-button>
          <el-button v-if="isRejectedOrDown(p)" size="small" text type="primary" @click="resubmit(p)">
            重新编辑投稿
          </el-button>
        </div>
      </div>
    </div>

    <div v-if="list.length >= total && list.length" class="xs dim" style="text-align:center;padding:20px">
      共 {{ total }} 件作品
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppState from '@/components/AppState.vue'
import ProjectCard from '@/components/ProjectCard.vue'
import { uploadApi, safe } from '@/api'
import { pickList, pickTotal } from '@/utils/format'

const router = useRouter()
const list = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref(null)
const status = ref('')

function isRejectedOrDown(p) { return p.status === 3 || p.status === 4 }

async function load() {
  loading.value = true
  error.value = null
  const params = { page: 1, size: 50 }
  if (status.value !== '') params.status = status.value
  const r = await safe(uploadApi.myProjects(params))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  list.value = pickList(r.data)
  total.value = pickTotal(r.data)
}

function goDashboard(p) { router.push({ path: '/me/dashboard', query: { projectId: p.project_id || p.id } }) }
function resubmit(p) {
  ElMessage.info('编辑功能：MVP 阶段请新建投稿（后端 PUT /projects/{id} 已就绪，前端编辑页二期补）')
  router.push('/upload')
}

onMounted(load)
</script>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.card { padding: 12px; }
.ops { display: flex; gap: 4px; margin-top: 10px; justify-content: flex-end; }
.mb16 { margin-bottom: 16px; }
</style>
