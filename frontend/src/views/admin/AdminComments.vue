<template>
  <div>
    <div class="row between center wrap gap12" style="margin-bottom:20px">
      <div>
        <h2 class="section-title" style="margin:0">评论管理</h2>
        <div class="sm dim">按项目查看评论并删除违规内容（作者本人无删评权限，仅管理员可删；删除需填理由并入审计）</div>
      </div>
      <div class="row center gap8">
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width:280px" @change="load">
          <el-option v-for="p in projects" :key="p.project_id" :label="`#${p.project_id} ${p.title}`" :value="p.project_id" />
        </el-select>
        <el-button @click="load"><el-icon><Refresh /></el-icon></el-button>
      </div>
    </div>

    <AppState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && !comments.length"
      :empty-text="projectId ? '该项目暂无评论' : '请先选择一个项目'"
      empty-desc="选择项目后即可查看其评论列表。"
      :skeleton-count="4"
      @retry="load"
    >
      <div class="card" v-for="c in comments" :key="c.comment_id">
        <div class="row between center wrap gap8">
          <div class="row center gap8">
            <el-avatar :size="28" :src="c.user?.avatar_path">{{ (c.user?.nickname || 'U').slice(0, 1) }}</el-avatar>
            <span class="sm" style="font-weight:600">{{ c.user?.nickname || '匿名' }}</span>
            <el-tag v-if="c.is_author" size="small" effect="plain" type="primary">作者</el-tag>
            <span class="xs dim">{{ fmtDateTime(c.created_at) }}</span>
          </div>
          <el-button size="small" type="danger" text @click="remove(c)">删除评论</el-button>
        </div>
        <div style="margin-top:8px">{{ c.content }}</div>
        <div v-if="c.replies?.length" class="replies sm dim">
          <div v-for="r in c.replies" :key="r.comment_id" class="row between center gap8">
            <span>└ {{ r.user?.nickname || '匿名' }}：{{ r.content }}</span>
            <el-button size="small" type="danger" text @click="remove(r)">删除</el-button>
          </div>
        </div>
      </div>
    </AppState>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppState from '@/components/AppState.vue'
import { adminApi, projectApi, safe } from '@/api'
import { fmtDateTime, pickList } from '@/utils/format'

const projects = ref([])
const projectId = ref(null)
const comments = ref([])
const loading = ref(false)
const error = ref(null)

async function loadProjects() {
  const r = await safe(adminApi.projects({ page: 1, size: 100 }))
  if (r.ok) projects.value = pickList(r.data)
}

async function load() {
  if (!projectId.value) { comments.value = []; return }
  loading.value = true
  error.value = null
  const r = await safe(projectApi.comments(projectId.value, { page: 1, size: 100 }))
  loading.value = false
  if (!r.ok) { error.value = r.error || true; return }
  comments.value = pickList(r.data)
}

async function remove(c) {
  try {
    const { value } = await ElMessageBox.prompt('请填写删除理由（写入审计日志）', '删除评论', {
      inputValidator: (v) => (!!v && v.trim()) || '必须填写理由'
    })
    const r = await safe(adminApi.removeComment(c.comment_id, { reason: value }))
    if (!r.ok) { ElMessage.error('删除失败'); return }
    ElMessage.success('评论已删除')
    load()
  } catch (e) {}
}

onMounted(loadProjects)
</script>

<style scoped>
.replies { margin-top: 8px; padding-left: 12px; border-left: 2px solid var(--c-border); display: flex; flex-direction: column; gap: 6px; }
.card { margin-bottom: 12px; }
</style>
